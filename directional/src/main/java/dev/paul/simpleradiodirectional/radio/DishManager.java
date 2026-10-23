package dev.paul.simpleradiodirectional.radio;

import com.codinglitch.simpleradio.core.registry.blocks.ReceiverBlockEntity;
import com.codinglitch.simpleradio.radio.RadioReceiver;

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
    private record NativeBinding(String dimension, Set<Long> masts) {}

    private record Pending(long nextScan, BlockPos core) {}

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
        long now = level.getGameTime();
        var previous = manager.pending.get(receiver.reference);
        if (previous != null && now < previous.nextScan() && core.equals(previous.core())) return;
        manager.pending.put(receiver.reference, new Pending(now + 200, core));
        Set<Long> hosts = MastScanner.scan(level, core);
        if (hosts == null) return;
        var next = new NativeBinding(level.dimension().location().toString(), hosts);
        if (!next.equals(manager.nativeBindings.put(receiver.reference, next))) manager.publish();
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
        if (manager != null) manager.pending.remove(receiver.reference);
    }

    public static void dirtyRelay(ServerLevel level, BlockPos pos) {
        var manager = current;
        if (manager != null)
            manager.pending.remove(
                    new DishSavedData.Key(level.dimension().location().toString(), pos.asLong()));
    }

    /** Called only by the optional relay ticker. Exact native mast membership is persisted. */
    public static void refreshRelay(ServerLevel level, BlockPos relay, BlockPos core) {
        var manager = get(level);
        var key = new DishSavedData.Key(level.dimension().location().toString(), relay.asLong());
        if (manager.data.dishes.isEmpty() && !manager.data.relays.containsKey(key)) return;
        long now = level.getGameTime();
        var previous = manager.pending.get(key);
        if (previous != null && now < previous.nextScan() && core.equals(previous.core())) return;
        manager.pending.put(key, new Pending(now + 200, core));
        Set<Long> hosts = MastScanner.scan(level, core);
        if (hosts != null && !hosts.equals(manager.data.relays.put(key, hosts))) manager.changed();
    }

    public static void tick(MinecraftServer server) {
        start(server);
        var manager = current;
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
