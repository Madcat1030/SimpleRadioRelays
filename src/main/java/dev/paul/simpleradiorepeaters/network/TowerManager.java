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
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class TowerManager {
    private static final Map<MinecraftServer, TowerManager> MANAGERS = new IdentityHashMap<>();
    public record Scope(String dimension, ChannelKey channel) {}
    public record Network(List<TowerRecord> towers, List<List<RelayGraph.Edge>> edges, RelayGraph graph) {}
    private final MinecraftServer server;
    private final TowerSavedData data;
    private final Map<UUID, RepeaterBankBlockEntity> loaded = new HashMap<>();
    private final Map<Scope, Network> networks = new HashMap<>();
    private int configStamp;
    public long forwardedFrames, receiverDeliveries;

    private TowerManager(MinecraftServer server, TowerSavedData data) {
        this.server = server; this.data = data;
    }
    public static TowerManager get(MinecraftServer server) { return MANAGERS.get(server); }
    public static void start(MinecraftServer server) {
        TowerSavedData data = server.overworld().getDataStorage().computeIfAbsent(TowerSavedData::load, TowerSavedData::new, TowerSavedData.NAME);
        MANAGERS.put(server, new TowerManager(server, data));
        SimpleRadioRepeaters.LOGGER.info("Restored {} repeater records without loading tower chunks", data.towers.size());
    }
    public static void stop(MinecraftServer server) { MANAGERS.remove(server); }
    public static void tick(MinecraftServer server) {
        TowerManager manager = get(server); if (manager == null) return;
        if (server.getTickCount() % 100 == 0) {
            int stamp = NativeRadioAdapter.configurationStamp();
            if (stamp != manager.configStamp) { manager.configStamp = stamp; manager.networks.clear(); }
        }
    }
    private void put(TowerRecord node) {
        TowerRecord old = data.towers.get(node.id()); if (node.equals(old)) return;
        data.towers.put(node.id(), node); data.setDirty(); networks.clear();
    }
    public boolean loaded(RepeaterBankBlockEntity bank) {
        RepeaterBankBlockEntity previous = loaded.put(bank.bankId(), bank);
        if (previous == null && !RepeatersConfig.INSTANCE.persistentUnloadedTowers) networks.clear();
        TowerRecord saved = data.towers.get(bank.bankId());
        if (saved != null && (!saved.position().equals(bank.getBlockPos())
                || !saved.dimension().equals(bank.getLevel().dimension().location().toString()))) {
            loaded.remove(bank.bankId()); bank.regenerateId(); loaded.put(bank.bankId(), bank);
        }
        if (previous != bank) settingsChanged(bank);
        return previous != bank;
    }
    public void unloaded(RepeaterBankBlockEntity bank) {
        settingsChanged(bank); loaded.remove(bank.bankId(), bank);
        if (!RepeatersConfig.INSTANCE.persistentUnloadedTowers) networks.clear();
    }
    public void settingsChanged(RepeaterBankBlockEntity bank) {
        TowerRecord saved = data.towers.get(bank.bankId());
        if (saved != null) put(saved.settings(bank.channels(), bank.enabled(), bank.hasCatalyst()));
    }
    public void publish(RepeaterBankBlockEntity bank, AntennaScanner.Result antenna) {
        updateSnapshot(new TowerRecord(bank.bankId(), bank.getLevel().dimension().location().toString(), bank.getBlockPos(), bank.channels(),
                bank.enabled(), bank.hasCatalyst(), true, antenna.score(), antenna.footprint()));
    }
    /** Accept a verified snapshot from a stationary tower adapter, on the server thread. */
    public void updateSnapshot(TowerRecord record) {
        if (!server.isSameThread()) throw new IllegalStateException("Repeater snapshots must be updated on the server thread");
        put(record);
    }
    public void removeAt(String dimension, BlockPos pos) {
        List<TowerRecord> removal = data.towers.values().stream().filter(n -> n.dimension().equals(dimension) && n.position().equals(pos)).toList();
        for (TowerRecord node : removal) { data.towers.remove(node.id()); loaded.remove(node.id()); }
        if (!removal.isEmpty()) { data.setDirty(); networks.clear(); }
    }
    public boolean isEligible(UUID id) { TowerRecord node = data.towers.get(id); return node != null && node.eligible(); }
    public TowerRecord snapshot(UUID id) { return data.towers.get(id); }
    public Network network(ServerLevel level, ChannelKey channel) {
        Scope scope = new Scope(level.dimension().location().toString(), channel);
        return networks.computeIfAbsent(scope, key -> {
            List<TowerRecord> nodes = data.towers.values().stream().filter(n -> n.eligible() && n.dimension().equals(key.dimension())
                    && n.channels().contains(channel) && (RepeatersConfig.INSTANCE.persistentUnloadedTowers || loaded.containsKey(n.id())))
                    .sorted(Comparator.comparing(n -> n.id().toString())).toList();
            List<List<RelayGraph.Edge>> edges = new ArrayList<>();
            for (int from = 0; from < nodes.size(); from++) {
                List<RelayGraph.Edge> row = new ArrayList<>(); TowerRecord sender = nodes.get(from);
                for (int to = 0; to < nodes.size(); to++) if (to != from) {
                    TowerRecord receiver = nodes.get(to);
                    double distance = sender.position().getCenter().distanceTo(receiver.position().getCenter()) * level.dimensionType().coordinateScale();
                    RadioLinkMath.Result link = NativeRadioAdapter.receiveAtTower(distance, NativeRadioAdapter.towerPower(sender, channel),
                            NativeRadioAdapter.towerPower(sender, channel), SimpleRadioFrequencing.TRANSMITTER, receiver, channel);
                    if (link != null) row.add(new RelayGraph.Edge(to, link.severity()));
                }
                edges.add(List.copyOf(row));
            }
            return new Network(nodes, List.copyOf(edges), new RelayGraph(edges, RepeatersConfig.INSTANCE.maxRelayHops));
        });
    }
    public int peers(UUID id) {
        TowerRecord tower = data.towers.get(id); if (tower == null || !tower.eligible()) return 0;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tower.dimension())));
        if (level == null) return 0;
        Set<UUID> peers = new HashSet<>();
        for (ChannelKey channel : tower.channels()) {
            Network network = network(level, channel);
            for (int i = 0; i < network.towers().size(); i++) if (network.towers().get(i).id().equals(id))
                for (RelayGraph.Edge edge : network.edges().get(i)) peers.add(network.towers().get(edge.destination()).id());
        }
        return peers.size();
    }
    public void refreshLoaded() { loaded.values().stream().filter(b -> !b.isRemoved()).forEach(RepeaterBankBlockEntity::markDirty); }
    public String summary() {
        long eligible = data.towers.values().stream().filter(TowerRecord::eligible).count();
        long live = data.towers.values().stream().filter(n -> n.eligible() && loaded.containsKey(n.id())).count();
        return "Repeaters: " + data.towers.size() + " configured; " + eligible + " ready (" + live + " loaded, " + (eligible-live)
                + " saved). Forwarded " + forwardedFrames + " frames; delivered " + receiverDeliveries + " receiver packets.";
    }
    public List<String> describe() {
        List<String> result = new ArrayList<>(); result.add(summary());
        data.towers.values().stream().sorted(Comparator.comparing(n -> n.id().toString())).forEach(n -> result.add(n.dimension() + " "
                + n.position().toShortString() + " | " + (n.eligible() ? loaded.containsKey(n.id()) ? "loaded" : "saved" : "inactive")
                + " | antenna " + n.antennaScore() + " | " + n.channels().stream().sorted().toList()));
        return result;
    }
    public Collection<TowerRecord> records() { return List.copyOf(data.towers.values()); }
}
