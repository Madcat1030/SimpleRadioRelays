package dev.paul.simpleradiorepeaters.network;

import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;

import dev.paul.simpleradiorepeaters.*;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.radio.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

public final class TowerManager {
    private static final Map<MinecraftServer, TowerManager> MANAGERS = new IdentityHashMap<>();

    public record Scope(
            String dimension,
            ChannelKey channel,
            boolean nativeCrossDimensional,
            int policyFingerprint) {}

    public record Network(
            List<TowerRecord> towers,
            List<List<RelayGraph.Edge>> edges,
            RelayGraph graph,
            List<RelayPort> ports,
            boolean nativeCrossDimensional) {}

    private record ChunkKey(String dimension, long position) {}

    private final MinecraftServer server;
    private final TowerSavedData data;
    private final Map<UUID, RelayBlockEntity> loaded = new HashMap<>();
    private final Map<Scope, Network> networks = new HashMap<>();
    private final Map<ChunkKey, Set<UUID>> towersByChunk = new HashMap<>();
    private final Set<ChunkKey> pendingChunks = new LinkedHashSet<>();
    private int configStamp;
    public long forwardedFrames, receiverDeliveries;

    private TowerManager(MinecraftServer server, TowerSavedData data) {
        this.server = server;
        this.data = data;
        configStamp = NativeRadioAdapter.configurationStamp();
        for (TowerRecord record : data.towers.values()) index(record);
    }

    public static TowerManager get(MinecraftServer server) {
        return MANAGERS.get(server);
    }

    public static void start(MinecraftServer server) {
        TowerSavedData data =
                server.overworld()
                        .getDataStorage()
                        .computeIfAbsent(
                                TowerSavedData::load, TowerSavedData::new, TowerSavedData.NAME);
        TowerManager manager = new TowerManager(server, data);
        MANAGERS.put(server, manager);
        manager.reconcileLoadedChunks(); // Spawn chunks may have loaded before SERVER_STARTED.
        SimpleRadioRepeaters.LOGGER.info(
                "Restored {} repeater records without loading tower chunks", data.towers.size());
    }

    public static void stop(MinecraftServer server) {
        MANAGERS.remove(server);
    }

    public static void tick(MinecraftServer server) {
        TowerManager manager = get(server);
        if (manager == null) return;
        manager.reconcilePendingChunks();
        if (server.getTickCount() % 100 == 0) {
            manager.checkConfiguration();
        }
    }

    public static void resourcesReloaded(MinecraftServer server) {
        resourcesReloaded(server, "extension_or_external_reload");
    }

    public static void resourcesReloaded(MinecraftServer server, String reason) {
        TowerManager manager = get(server);
        if (manager == null) return;
        RelayPerformance.invalidated(server, reason, manager.networks.size(), null, null);
        manager.networks.clear();
        manager.configStamp = NativeRadioAdapter.configurationStamp();
    }

    private void checkConfiguration() {
        int stamp = NativeRadioAdapter.configurationStamp();
        if (stamp != configStamp) {
            RelayPerformance.invalidated(
                    server, "rf_configuration_changed", networks.size(), null, null);
            configStamp = stamp;
            networks.clear();
        }
    }

    private void put(TowerRecord node) {
        TowerRecord old = data.towers.get(node.id());
        if (node.equals(old)) {
            RelayPerformance.count(RelayPerformance.Counter.SNAPSHOTS_UNCHANGED);
            return;
        }
        RelayPerformance.invalidated(
                server,
                old == null ? "tower_added" : "tower_snapshot_changed",
                networks.size(),
                old,
                node);
        if (old != null) unindex(old);
        data.towers.put(node.id(), node);
        data.setDirty();
        networks.clear();
        index(node);
        pendingChunks.addAll(chunkKeys(node));
    }

    private static ChunkKey chunkKey(TowerRecord record) {
        return new ChunkKey(record.dimension(), new ChunkPos(record.position()).toLong());
    }

    private static Set<ChunkKey> chunkKeys(TowerRecord record) {
        Set<ChunkKey> keys = new HashSet<>();
        keys.add(chunkKey(record));
        if (record.cardBank() != null)
            keys.add(
                    new ChunkKey(
                            record.dimension(),
                            new ChunkPos(record.cardBank().position()).toLong()));
        return keys;
    }

    private void index(TowerRecord record) {
        for (ChunkKey key : chunkKeys(record))
            towersByChunk.computeIfAbsent(key, ignored -> new HashSet<>()).add(record.id());
    }

    private void unindex(TowerRecord record) {
        for (ChunkKey key : chunkKeys(record)) {
            Set<UUID> ids = towersByChunk.get(key);
            if (ids != null && ids.remove(record.id()) && ids.isEmpty()) towersByChunk.remove(key);
        }
    }

    private void removeRecord(UUID id) {
        TowerRecord record = data.towers.remove(id);
        if (record == null) return;
        RelayPerformance.invalidated(server, "tower_removed", networks.size(), record, null);
        unindex(record);
        loaded.remove(id);
        data.setDirty();
        networks.clear();
    }

    public static void chunkLoaded(ServerLevel level, LevelChunk chunk) {
        TowerManager manager = get(level.getServer());
        if (manager == null) return;
        ChunkKey key =
                new ChunkKey(level.dimension().location().toString(), chunk.getPos().toLong());
        if (manager.towersByChunk.containsKey(key)) manager.pendingChunks.add(key);
    }

    public static void chunkUnloaded(ServerLevel level, LevelChunk chunk) {
        TowerManager manager = get(level.getServer());
        if (manager == null) return;
        manager.pendingChunks.remove(
                new ChunkKey(level.dimension().location().toString(), chunk.getPos().toLong()));
    }

    /**
     * Queue saved positions; the actual check only reads chunks already available without loading
     * them.
     */
    public void reconcileLoadedChunks() {
        pendingChunks.addAll(towersByChunk.keySet());
    }

    private void reconcilePendingChunks() {
        for (ChunkKey key : List.copyOf(pendingChunks)) {
            pendingChunks.remove(key);
            ResourceLocation dimension = ResourceLocation.tryParse(key.dimension());
            ServerLevel level =
                    dimension == null
                            ? null
                            : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) continue;
            ChunkPos position = new ChunkPos(key.position());
            LevelChunk chunk = level.getChunkSource().getChunkNow(position.x, position.z);
            if (chunk == null) continue; // Unloaded saved records remain authoritative.
            boolean retry = false;
            for (UUID id : Set.copyOf(towersByChunk.getOrDefault(key, Set.of()))) {
                TowerRecord record = data.towers.get(id);
                if (record == null) continue;
                if (chunkKey(record).equals(key)) {
                    if (!chunk.getBlockState(record.position()).is(SimpleRadioRepeaters.RELAY)) {
                        removeRecord(id);
                        SimpleRadioRepeaters.LOGGER.info(
                                "Removed stale repeater {} at {} {}: relay block is absent",
                                id,
                                key.dimension(),
                                record.position());
                        continue;
                    }
                    if (!level.areEntitiesLoaded(key.position())) retry = true;
                    else {
                        // A pending Relay entity must not postpone checking its Bank in this same
                        // chunk.
                        var entity =
                                chunk.getBlockEntity(
                                        record.position(), LevelChunk.EntityCreationType.CHECK);
                        if (!(entity instanceof RelayBlockEntity relay)) retry = true;
                        else if (!relay.relayId().equals(id)) {
                            removeRecord(id);
                            SimpleRadioRepeaters.LOGGER.info(
                                    "Removed stale repeater {} at {} {}: relay identity changed",
                                    id,
                                    key.dimension(),
                                    record.position());
                            continue;
                        }
                    }
                }
                CardBankRef controller = record.cardBank();
                if (controller != null
                        && new ChunkPos(controller.position()).toLong() == key.position()) {
                    if (!(chunk.getBlockState(controller.position()).getBlock()
                            instanceof ChannelBankBlock)) {
                        clearController(record);
                        continue;
                    }
                    if (!level.areEntitiesLoaded(key.position())) {
                        retry = true;
                        continue;
                    }
                    var entity =
                            chunk.getBlockEntity(
                                    controller.position(), LevelChunk.EntityCreationType.CHECK);
                    if (!(entity instanceof ChannelBankBlockEntity bank)) {
                        retry = true;
                        continue;
                    }
                    if (!bank.bankId().equals(controller.id())) clearController(record);
                    else controllerChanged(level, bank);
                }
            }
            if (retry) pendingChunks.add(key);
        }
    }

    private void clearController(TowerRecord record) {
        CardBankRef controller = record.cardBank();
        if (controller == null) return;
        put(record.settings(Set.of(), record.enabled(), record.catalystItem(), null));
        RelayBlockEntity relay = loaded.get(record.id());
        if (relay != null && !relay.isRemoved()) relay.controllerRemoved(controller.position());
    }

    public void controllerRemovedAt(ServerLevel level, BlockPos pos) {
        ChunkKey key =
                new ChunkKey(level.dimension().location().toString(), new ChunkPos(pos).toLong());
        for (UUID id : Set.copyOf(towersByChunk.getOrDefault(key, Set.of()))) {
            TowerRecord record = data.towers.get(id);
            if (record != null
                    && record.cardBank() != null
                    && record.cardBank().position().equals(pos)) clearController(record);
        }
    }

    /** A loaded card module can update an unloaded Relay's authoritative channel snapshot. */
    public void controllerChanged(ServerLevel level, ChannelBankBlockEntity bank) {
        ChunkKey key =
                new ChunkKey(
                        level.dimension().location().toString(),
                        new ChunkPos(bank.getBlockPos()).toLong());
        Set<ChannelKey> channels = new TreeSet<>();
        for (var card : bank.cardStates()) {
            ChannelKey channel = card.channel();
            if (channel != null) channels.add(channel);
        }
        for (UUID id : Set.copyOf(towersByChunk.getOrDefault(key, Set.of()))) {
            TowerRecord record = data.towers.get(id);
            CardBankRef controller = record == null ? null : record.cardBank();
            if (controller != null
                    && controller.position().equals(bank.getBlockPos())
                    && controller.id().equals(bank.bankId())) {
                put(
                        record.settings(
                                channels,
                                record.enabled(),
                                record.catalystItem(),
                                controller,
                                record.attachment(),
                                bank.transponderChannels()));
                RelayBlockEntity relay = loaded.get(id);
                if (relay != null && !relay.isRemoved()) relay.bankChanged();
            }
        }
    }

    public boolean loaded(RelayBlockEntity relay) {
        TowerRecord saved = data.towers.get(relay.relayId());
        if (saved != null
                && (!saved.position().equals(relay.getBlockPos())
                        || !saved.dimension()
                                .equals(relay.getLevel().dimension().location().toString()))) {
            relay.regenerateId();
        }
        RelayBlockEntity previous = loaded.put(relay.relayId(), relay);
        if (previous == null && !RepeatersConfig.INSTANCE.persistentUnloadedTowers) {
            RelayPerformance.invalidated(
                    server, "nonpersistent_relay_loaded", networks.size(), null, saved);
            networks.clear();
        }
        if (previous != relay) {
            RelayPerformance.count(RelayPerformance.Counter.RELAY_LOADS);
            pendingChunks.add(
                    new ChunkKey(
                            relay.getLevel().dimension().location().toString(),
                            new ChunkPos(relay.getBlockPos()).toLong()));
        }
        return previous != relay;
    }

    public void unloaded(RelayBlockEntity relay) {
        if (loaded.get(relay.relayId()) == relay) {
            RelayPerformance.count(RelayPerformance.Counter.RELAY_UNLOADS);
            settingsChanged(relay);
            loaded.remove(relay.relayId(), relay);
        }
        if (!RepeatersConfig.INSTANCE.persistentUnloadedTowers) {
            RelayPerformance.invalidated(
                    server,
                    "nonpersistent_relay_unloaded",
                    networks.size(),
                    data.towers.get(relay.relayId()),
                    null);
            networks.clear();
        }
    }

    public void settingsChanged(RelayBlockEntity relay) {
        TowerRecord saved = data.towers.get(relay.relayId());
        // A placement notification can precede the copy's first tick and UUID reassignment.
        if (saved != null
                && saved.position().equals(relay.getBlockPos())
                && saved.dimension().equals(relay.getLevel().dimension().location().toString()))
            put(
                    saved.settings(
                            relay.channels(),
                            relay.enabled(),
                            relay.catalystItem(),
                            relay.cardBank(),
                            relay.attachmentType(),
                            relay.transponderChannels()));
    }

    public void publish(RelayBlockEntity relay, AntennaScanner.Result antenna) {
        updateSnapshot(
                new TowerRecord(
                        relay.relayId(),
                        relay.getLevel().dimension().location().toString(),
                        relay.getBlockPos(),
                        relay.channels(),
                        relay.enabled(),
                        relay.catalystItem(),
                        true,
                        antenna.score(),
                        antenna.footprint(),
                        relay.cardBank(),
                        relay.attachmentType(),
                        relay.transponderChannels(),
                        antenna.antennaBase()));
    }

    /** Accept a verified snapshot from a stationary tower adapter, on the server thread. */
    public void updateSnapshot(TowerRecord record) {
        if (!server.isSameThread())
            throw new IllegalStateException(
                    "Repeater snapshots must be updated on the server thread");
        put(record);
    }

    public void removeAt(String dimension, BlockPos pos) {
        List<TowerRecord> removal =
                data.towers.values().stream()
                        .filter(n -> n.dimension().equals(dimension) && n.position().equals(pos))
                        .toList();
        for (TowerRecord node : removal) removeRecord(node.id());
    }

    public boolean isEligible(UUID id) {
        TowerRecord node = data.towers.get(id);
        return node != null && node.eligible();
    }

    public TowerRecord snapshot(UUID id) {
        return data.towers.get(id);
    }

    public Network network(ServerLevel level, ChannelKey channel) {
        // One cheap policy flag makes mode changes immediate; full fingerprints stay off the packet
        // path.
        boolean nativeCrossDimensional = RadioSpace.nativeCrossDimensional();
        Scope scope =
                new Scope(
                        level.dimension().location().toString(),
                        channel,
                        nativeCrossDimensional,
                        RadioSpace.policyFingerprint());
        boolean recording = RelayPerformance.running(server),
                hit = recording && networks.containsKey(scope);
        Network network = networks.computeIfAbsent(scope, key -> buildNetwork(level, key));
        if (recording) RelayPerformance.networkLookup(server, hit, network);
        return network;
    }

    public record Inspection(Network cached, Network fresh) {}

    /**
     * Builds an independent numeric graph for diagnostics without replacing the live cached graph.
     */
    public Inspection inspectNetwork(ServerLevel level, ChannelKey channel) {
        Scope scope =
                new Scope(
                        level.dimension().location().toString(),
                        channel,
                        RadioSpace.nativeCrossDimensional(),
                        RadioSpace.policyFingerprint());
        return new Inspection(networks.get(scope), buildNetwork(level, scope));
    }

    private Network buildNetwork(ServerLevel sourceLevel, Scope scope) {
        long started = RelayPerformance.clock(server);
        Network result = null;
        try {
            return result = buildNetworkObserved(sourceLevel, scope);
        } finally {
            RelayPerformance.graphBuilt(server, started, result, scope);
        }
    }

    private Network buildNetworkObserved(ServerLevel sourceLevel, Scope scope) {
        List<RelayPort> all = new ArrayList<>();
        Map<ChannelKey, List<RelayPort>> listeners = new HashMap<>();
        for (TowerRecord tower :
                data.towers.values().stream()
                        .filter(
                                n ->
                                        n.eligible()
                                                && (RepeatersConfig.INSTANCE
                                                                .persistentUnloadedTowers
                                                        || loaded.containsKey(n.id())))
                        .sorted(Comparator.comparing(n -> n.id().toString()))
                        .toList()) {
            List<RadioSpace.Site> sites =
                    RadioSpace.sites(server, tower, scope.nativeCrossDimensional());
            if (sites.isEmpty()) continue;
            for (var conversion : tower.ports()) {
                RelayPort port =
                        new RelayPort(tower, conversion.input(), conversion.output(), sites);
                all.add(port);
                listeners.computeIfAbsent(port.input(), ignored -> new ArrayList<>()).add(port);
            }
        }
        // Channel/dimension closure is topology work, once per snapshot. Actual RF reach still
        // depends on decay.
        Set<RelayPort> included = new LinkedHashSet<>();
        ArrayDeque<RelayPort> pending = new ArrayDeque<>();
        for (RelayPort port : all)
            if (port.input().equals(scope.channel())
                    && (scope.nativeCrossDimensional()
                            || port.sites().stream()
                                    .anyMatch(site -> site.level() == sourceLevel))) {
                included.add(port);
                pending.add(port);
            }
        while (!pending.isEmpty()) {
            RelayPort from = pending.remove();
            for (RelayPort to : listeners.getOrDefault(from.output(), List.of()))
                if (!from.tower().id().equals(to.tower().id())
                        && !included.contains(to)
                        && !RadioSpace.between(
                                        from.sites(), to.sites(), scope.nativeCrossDimensional())
                                .isEmpty()) {
                    included.add(to);
                    pending.add(to);
                }
        }
        List<RelayPort> ports = List.copyOf(included);
        List<TowerRecord> nodes = ports.stream().map(RelayPort::tower).toList();
        List<Map<Integer, List<RadioSpace.Leg>>> geometry = new ArrayList<>();
        float[] nominal = new float[ports.size()];
        double[] efficiency = new double[ports.size()];
        for (int from = 0; from < ports.size(); from++) {
            RelayPort sender = ports.get(from);
            nominal[from] = NativeRadioAdapter.towerPower(sender.tower(), sender.output());
            efficiency[from] = sender.tower().efficiency();
            Map<Integer, List<RadioSpace.Leg>> row = new LinkedHashMap<>();
            for (int to = 0; to < ports.size(); to++) {
                RelayPort receiver = ports.get(to);
                if (sender.tower().id().equals(receiver.tower().id())
                        || !sender.output().equals(receiver.input())) continue;
                var legs =
                        RadioSpace.between(
                                sender.sites(), receiver.sites(), scope.nativeCrossDimensional());
                if (!legs.isEmpty()) row.put(to, legs);
            }
            geometry.add(Map.copyOf(row));
        }
        RelayGraph.Links links =
                (from, decay) -> {
                    List<RelayGraph.Edge> row = new ArrayList<>();
                    RelayPort sender = ports.get(from);
                    float power =
                            RelayPower.departure(
                                    nominal[from],
                                    efficiency[from],
                                    sender.tower().attachment().resetsDecay() ? 1 : decay);
                    if (power > 0)
                        for (var target : geometry.get(from).entrySet()) {
                            double best = Double.POSITIVE_INFINITY;
                            for (RadioSpace.Leg leg : target.getValue()) {
                                var link =
                                        NativeRadioAdapter.receiveAtTower(
                                                leg,
                                                power,
                                                power,
                                                SimpleRadioFrequencing.TRANSMITTER,
                                                nodes.get(target.getKey()),
                                                sender.output());
                                if (link != null) best = Math.min(best, link.severity());
                            }
                            if (Double.isFinite(best))
                                row.add(new RelayGraph.Edge(target.getKey(), best));
                        }
                    return List.copyOf(row);
                };
        RelayGraph graph =
                new RelayGraph(
                        ports.size(),
                        links,
                        RepeatersConfig.INSTANCE.maxRelayHops,
                        node -> nodes.get(node).attachment().resetsDecay());
        List<List<RelayGraph.Edge>> edges = new ArrayList<>();
        for (int from = 0; from < ports.size(); from++) edges.add(graph.outgoing(from, 1));
        return new Network(nodes, List.copyOf(edges), graph, ports, scope.nativeCrossDimensional());
    }

    public int peers(UUID id) {
        TowerRecord tower = data.towers.get(id);
        if (tower == null || !tower.eligible()) return 0;
        ServerLevel level =
                server.getLevel(
                        ResourceKey.create(
                                Registries.DIMENSION, new ResourceLocation(tower.dimension())));
        if (level == null) return 0;
        Set<UUID> peers = new HashSet<>();
        for (ChannelKey channel : tower.channels()) {
            Network network = network(level, channel);
            for (int i = 0; i < network.towers().size(); i++)
                if (network.towers().get(i).id().equals(id))
                    for (RelayGraph.Edge edge : network.edges().get(i))
                        peers.add(network.towers().get(edge.destination()).id());
        }
        return peers.size();
    }

    public void refreshLoaded() {
        checkConfiguration();
        reconcileLoadedChunks();
        loaded.values().stream().filter(b -> !b.isRemoved()).forEach(RelayBlockEntity::markDirty);
    }

    public String summary() {
        long eligible = data.towers.values().stream().filter(TowerRecord::eligible).count();
        long live =
                data.towers.values().stream()
                        .filter(n -> n.eligible() && loaded.containsKey(n.id()))
                        .count();
        return "Repeaters: "
                + data.towers.size()
                + " configured; "
                + eligible
                + " ready ("
                + live
                + " loaded, "
                + (eligible - live)
                + " saved). Forwarded "
                + forwardedFrames
                + " frames; delivered "
                + receiverDeliveries
                + " receiver packets.";
    }

    public List<String> describe() {
        List<String> result = new ArrayList<>();
        result.add(summary());
        data.towers.values().stream()
                .sorted(Comparator.comparing(n -> n.id().toString()))
                .forEach(
                        n ->
                                result.add(
                                        n.dimension()
                                                + " "
                                                + n.position().toShortString()
                                                + " | "
                                                + (n.eligible()
                                                        ? loaded.containsKey(n.id())
                                                                ? "loaded"
                                                                : "saved"
                                                        : "inactive")
                                                + " | antenna "
                                                + RelayPower.antennaScore(
                                                        n.antennaScore(),
                                                        n.attachment().antennaMultiplier())
                                                + " at "
                                                + n.antennaBase().toShortString()
                                                + " | catalyst "
                                                + n.catalystItem()
                                                + " ("
                                                + Math.round(n.efficiency() * 100)
                                                + "%)"
                                                + " | "
                                                + n.attachment()
                                                + " | "
                                                + (n.transponder() == null
                                                        ? "bank"
                                                        : n.transponder()
                                                                .mode()
                                                                .name()
                                                                .toLowerCase(Locale.ROOT))
                                                + " | "
                                                + n.channels().stream().sorted().toList()));
        return result;
    }

    public Collection<TowerRecord> records() {
        return List.copyOf(data.towers.values());
    }
}
