package dev.paul.simpleradiodirectional.radio;

import com.codinglitch.simpleradio.core.registry.blocks.ReceiverBlockEntity;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import com.codinglitch.simpleradio.radio.RadioRouter;
import com.codinglitch.simpleradio.core.registry.entities.Wire;
import net.minecraft.world.entity.Entity;

import dev.paul.simpleradiodirectional.block.ParabolicReceiverBlock;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * Mutable state belongs to the server thread; packet hooks read only the published immutable view.
 */
public final class DishManager {
    private record NativeBinding(String dimension, BlockPos core, Set<Long> masts) {}

    private record Pending(long nextScan, BlockPos core, Set<Long> candidate, long missingSince) {
        Pending(long nextScan, BlockPos core) { this(nextScan, core, null, -1); }
    }
    private static final int RESTORE_GRACE = 200;
    private static final int RESTORE_RETRY = 20;

    private record View(
            int revision,
            int relayRevision,
            Map<UUID, List<Dish>> receivers,
            Map<DishSavedData.Key, List<Dish>> relays) {
        static final View EMPTY = new View(0, 0, Map.of(), Map.of());
    }

    private static volatile DishManager current;
    private static Consumer<MinecraftServer> relayChange = server -> {};

    /** Optional integration callback; standalone operation never resolves a Repeaters class. */
    public static void onRelayChange(Consumer<MinecraftServer> listener) {
        relayChange = Objects.requireNonNull(listener);
    }

    private volatile View view = View.EMPTY;
    private final MinecraftServer server;
    private final DishSavedData data;
    private final Map<UUID, NativeBinding> nativeBindings = new HashMap<>();
    private final Map<Object, Pending> pending = new HashMap<>();
    private final Set<Wire> pendingWires = Collections.newSetFromMap(new IdentityHashMap<>());

    private DishManager(MinecraftServer server) {
        this.server = server;
        data =
                server.overworld()
                        .getDataStorage()
                        .computeIfAbsent(
                                DishSavedData::load, DishSavedData::new, DishSavedData.NAME);
        publish();
    }

    public static void start(MinecraftServer server) {
        if (current == null || current.server != server) current = new DishManager(server);
    }

    public static void stop(MinecraftServer server) {
        if (current != null && current.server == server) current = null;
    }

    private static DishManager get(ServerLevel level) {
        start(level.getServer());
        return current;
    }

    public static int revision() {
        var manager = current;
        return manager == null ? 0 : manager.view.revision();
    }

    public static int relayRevision() {
        var manager = current;
        return manager == null ? 0 : manager.view.relayRevision();
    }

    public static List<Dish> receivers(RadioReceiver receiver) {
        var manager = current;
        return manager == null
                ? List.of()
                : manager.view.receivers().getOrDefault(receiver.reference, List.of());
    }

    public static List<Dish> relays(String dimension, BlockPos position) {
        var manager = current;
        return manager == null
                ? List.of()
                : manager.view
                        .relays()
                        .getOrDefault(
                                new DishSavedData.Key(dimension, position.asLong()), List.of());
    }

    public static void verify(ServerLevel level, Dish dish) {
        var manager = get(level);
        if (!dish.equals(manager.data.dishes.put(DishSavedData.Key.of(dish), dish)))
            manager.changed();
    }

    public static void remove(Level world, BlockPos pos) {
        if (!(world instanceof ServerLevel level)) return;
        var manager = get(level);
        if (manager.data.dishes.remove(
                        new DishSavedData.Key(
                                level.dimension().location().toString(), pos.asLong()))
                != null) manager.changed();
    }

    private void changed() {
        data.setDirty();
        publish();
    }

    private List<Dish> connected(String dimension, Set<Long> masts) {
        return data.dishes.values().stream()
                .filter(
                        d ->
                                d.dimension().toString().equals(dimension)
                                        && masts.contains(d.mast().asLong()))
                .sorted(Comparator.comparingLong(dish -> dish.position().asLong()))
                .toList();
    }

    private void publish() {
        Map<UUID, List<Dish>> nativeView = new HashMap<>();
        nativeBindings.forEach((id, b) -> nativeView.put(id, connected(b.dimension(), b.masts())));
        Map<DishSavedData.Key, List<Dish>> relayView = new HashMap<>();
        data.relays.forEach(
                (key, hosts) -> {
                    var dishes = connected(key.dimension(), hosts);
                    if (!dishes.isEmpty()) relayView.put(key, dishes);
                });
        boolean relayChanged = !view.relays().equals(relayView);
        view =
                new View(
                        view.revision() + 1,
                        view.relayRevision() + (relayChanged ? 1 : 0),
                        Map.copyOf(nativeView),
                        Map.copyOf(relayView));
        if (relayChanged) relayChange.accept(server);
    }

    public static void refreshReceiver(ServerLevel level, ReceiverBlockEntity entity) {
        var manager = get(level);
        if (!(entity.receiver instanceof RadioReceiver receiver)) return;
        if (!entity.catalyzed || entity.catalyst == null) {
            forget(receiver);
            return;
        }
        if (manager.data.dishes.isEmpty()
                && !manager.nativeBindings.containsKey(receiver.reference)) return;
        BlockPos core = entity.getAdaptorLocation();
        NativeBinding old = manager.nativeBindings.get(receiver.reference);
        Set<Long> known = old != null && old.core().equals(core) ? old.masts() : Set.of();
        Set<Long> hosts = manager.scanBinding(receiver.reference, level, core, known, false);
        if (hosts == null) return;
        var next = new NativeBinding(level.dimension().location().toString(), core.immutable(), hosts);
        if (!next.equals(manager.nativeBindings.put(receiver.reference, next))) manager.publish();
    }

    /** A temporary loss of wire/router membership must not erase a verified dish connection. */
    private Set<Long> scanBinding(Object key, ServerLevel level, BlockPos core, Set<Long> known, boolean confirmed) {
        long now = level.getGameTime();
        Pending previous = pending.get(key);
        if (!confirmed && previous != null && now < previous.nextScan() && core.equals(previous.core())) return null;
        Set<Long> hosts = MastScanner.scan(level, core);
        if (hosts == null) {
            pending.put(key, new Pending(now + RESTORE_RETRY, core.immutable()));
            return null;
        }
        if (!confirmed && known != null && !hosts.containsAll(known)
                && (previous == null || core.equals(previous.core()))) {
            boolean available = MastScanner.dependenciesAvailable(level, known);
            long since = previous != null && hosts.equals(previous.candidate()) && available
                    ? previous.missingSince() : now;
            if (!available || now - since < RESTORE_GRACE) {
                pending.put(key, new Pending(now + RESTORE_RETRY, core.immutable(), hosts, since));
                return null;
            }
        }
        pending.put(key, new Pending(now + RESTORE_GRACE, core.immutable()));
        return hosts;
    }

    private void dirty(Object key) {
        pending.computeIfPresent(key, (ignored, old) ->
                new Pending(0, old.core(), old.candidate(), old.missingSince()));
    }

    public static void forget(RadioReceiver receiver) {
        forget(receiver.reference);
    }

    public static void forget(UUID reference) {
        var manager = current;
        if (manager != null) {
            manager.pending.remove(reference);
            if (manager.nativeBindings.remove(reference) != null) manager.publish();
        }
    }

    public static void dirtyReceiver(RadioReceiver receiver) {
        var manager = current;
        if (manager != null) manager.dirty(receiver.reference);
    }

    public static void dirtyRelay(ServerLevel level, BlockPos pos) {
        var manager = current;
        if (manager != null)
            manager.dirty(new DishSavedData.Key(level.dimension().location().toString(), pos.asLong()));
    }

    /** Called only by the optional relay ticker. Exact native mast membership is persisted. */
    public static void refreshRelay(ServerLevel level, BlockPos relay, BlockPos core) {
        var manager = get(level);
        var key = new DishSavedData.Key(level.dimension().location().toString(), relay.asLong());
        if (manager.data.dishes.isEmpty() && !manager.data.relays.containsKey(key)) return;
        Set<Long> hosts = manager.scanBinding(key, level, core, manager.data.relays.get(key), false);
        if (hosts != null && !hosts.equals(manager.data.relays.put(key, hosts))) manager.changed();
    }

    public static void wireLoaded(Entity entity, ServerLevel level) {
        var manager = current;
        if (manager != null && manager.server == level.getServer() && entity instanceof Wire wire)
            manager.pendingWires.add(wire);
    }

    public static void wireUnloaded(Entity entity, ServerLevel level) {
        var manager = current;
        if (manager == null || manager.server != level.getServer() || !(entity instanceof Wire wire)) return;
        manager.pendingWires.remove(wire);
        var reason = wire.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) manager.wireChanged(level, wire, true);
    }

    private static boolean usesEndpoint(BlockPos core, Set<Long> masts, List<BlockPos> endpoints) {
        for (var endpoint : endpoints) {
            if (core != null && endpoint.distManhattan(core) == 1) return true;
            // A remote insulator is adjacent to the core immediately below a connected mast.
            for (var side : Direction.values())
                if (masts.contains(endpoint.relative(side).above().asLong())) return true;
        }
        return false;
    }

    private void wireChanged(ServerLevel level, Wire wire, boolean broken) {
        List<BlockPos> endpoints = new ArrayList<>();
        for (var router : new RadioRouter[]{wire.getFromRouter(), wire.getToRouter()}) {
            if (router != null && router.getLocation() != null && router.getLocation().level == level)
                endpoints.add(router.getLocation().blockPos());
        }
        if (endpoints.isEmpty()) return;
        String dimension = level.dimension().location().toString();
        boolean nativeChanged = false, relayChanged = false;
        for (var entry : Map.copyOf(nativeBindings).entrySet()) {
            var binding = entry.getValue();
            if (!binding.dimension().equals(dimension) || !usesEndpoint(binding.core(), binding.masts(), endpoints)) continue;
            dirty(entry.getKey());
            Set<Long> hosts = scanBinding(entry.getKey(), level, binding.core(), binding.masts(), broken);
            if (hosts == null && broken) hosts = Set.of();
            if (hosts != null && !hosts.equals(binding.masts())) {
                nativeBindings.put(entry.getKey(), new NativeBinding(dimension, binding.core(), hosts));
                nativeChanged = true;
            }
        }
        for (var entry : Map.copyOf(data.relays).entrySet()) {
            var key = entry.getKey();
            var prior = pending.get(key);
            BlockPos core = prior == null ? null : prior.core();
            if (!key.dimension().equals(dimension)
                    || (!usesEndpoint(core, entry.getValue(), endpoints)
                        && !(core == null && endpoints.stream().anyMatch(pos -> pos.distManhattan(BlockPos.of(key.position())) <= 2)))) continue;
            dirty(key);
            Set<Long> hosts = core == null ? null : scanBinding(key, level, core, entry.getValue(), broken);
            if (hosts == null && broken) hosts = Set.of();
            if (hosts != null && !hosts.equals(entry.getValue())) {
                data.relays.put(key, hosts);
                relayChanged = true;
            }
        }
        if (relayChanged) changed();
        else if (nativeChanged) publish();
    }

    public static void tick(MinecraftServer server) {
        start(server);
        var manager = current;
        for (Wire wire : List.copyOf(manager.pendingWires)) {
            manager.pendingWires.remove(wire);
            if (wire.isAlive() && wire.level() instanceof ServerLevel level)
                manager.wireChanged(level, wire, false);
        }
        if (server.getTickCount() % 20 != 0) return;
        boolean dirty =
                manager.data
                        .dishes
                        .entrySet()
                        .removeIf(
                                entry -> {
                                    var dish = entry.getValue();
                                    var level = manager.level(dish.dimension());
                                    if (level == null
                                            || !level.hasChunkAt(dish.position())
                                            || !level.areEntitiesLoaded(
                                                    ChunkPos.asLong(
                                                            dish.position().getX() >> 4,
                                                            dish.position().getZ() >> 4)))
                                        return false;
                                    var state = level.getBlockState(dish.position());
                                    return !(state.getBlock()
                                                    instanceof ParabolicReceiverBlock receiver)
                                            || receiver.pattern() != dish.pattern()
                                            || state.getValue(ParabolicReceiverBlock.FACING)
                                                    != dish.facing();
                                });
        dirty |=
                manager.data
                        .relays
                        .keySet()
                        .removeIf(
                                key -> {
                                    var level =
                                            manager.level(
                                                    ResourceLocation.tryParse(key.dimension()));
                                    var pos = BlockPos.of(key.position());
                                    if (level == null
                                            || !level.hasChunkAt(pos)
                                            || !level.areEntitiesLoaded(
                                                    ChunkPos.asLong(
                                                            pos.getX() >> 4, pos.getZ() >> 4)))
                                        return false;
                                    boolean absent =
                                            !BuiltInRegistries.BLOCK
                                                    .getKey(level.getBlockState(pos).getBlock())
                                                    .toString()
                                                    .equals("simpleradio_repeaters:relay");
                                    if (absent) manager.pending.remove(key);
                                    return absent;
                                });
        if (dirty) manager.changed();
    }

    private ServerLevel level(ResourceLocation id) {
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }
}
