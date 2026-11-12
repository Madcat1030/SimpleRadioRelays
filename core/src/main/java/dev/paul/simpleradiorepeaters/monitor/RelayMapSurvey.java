package dev.paul.simpleradiorepeaters.monitor;

import dev.paul.simpleradiorepeaters.RepeatersConfig;
import dev.paul.simpleradiorepeaters.network.ChannelKey;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import dev.paul.simpleradiorepeaters.network.TowerRecord;
import dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter;
import dev.paul.simpleradiorepeaters.radio.RadioLinkMath;
import dev.paul.simpleradiorepeaters.radio.RadioSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** The map follows physical, same-channel legs without creating live graph or relay state. */
public final class RelayMapSurvey {
    private record Station(TowerRecord tower, RadioSpace.Site site) {}

    private RelayMapSurvey() {}

    public record View(MonitorMap map, Map<Integer, UUID> targets) {}

    public record Tower(UUID id, MonitorMap.Kind kind, BlockPos compassTarget, @Nullable BlockPos anchor,
                        long approximateDistance, String bearing) {
        public boolean estimated() { return kind == MonitorMap.Kind.ESTIMATE; }
        public String estimate() { return "~" + approximateDistance + " " + bearing; }
        public String location() {
            return estimated() ? estimate() + " from X " + anchor.getX() + " Z " + anchor.getZ()
                    : coordinates(compassTarget);
        }
    }

    private record PlottedTower(Tower tower, double east, double south) {}

    public static final class Network {
        private final Map<Integer, PlottedTower> towers;
        private final Map<UUID, Integer> indices = new HashMap<>();
        private final List<MonitorNetworkReach.Connection> connections;
        private final boolean pending;

        private Network(Map<Integer, PlottedTower> towers, List<MonitorNetworkReach.Connection> connections, boolean pending) {
            this.towers = towers;
            towers.forEach((index, tower) -> indices.put(tower.tower().id(), index));
            this.connections = connections;
            this.pending = pending;
        }

        @Nullable public Tower tower(UUID id) {
            Integer index = indices.get(id);
            return index == null ? null : towers.get(index).tower();
        }

        public View view(@Nullable UUID focus) {
            int center = focus == null ? 0 : indices.getOrDefault(focus, 0);
            var origin = towers.get(center);
            Set<Integer> visible = center == 0 ? towers.keySet() : neighborhood(center, connections);
            List<MonitorMap.Site> sites = new ArrayList<>();
            for (var entry : towers.entrySet()) {
                int index = entry.getKey();
                if (index == center || !visible.contains(index)) continue;
                var target = entry.getValue();
                sites.add(new MonitorMap.Site(target.east() - origin.east(), target.south() - origin.south(),
                        target.tower().kind(), index));
            }
            var edges = connections.stream().filter(c -> visible.contains(c.from()) && visible.contains(c.to())).toList();
            var plot = MonitorMap.plot(sites, edges, center, origin.tower().kind(), origin.tower().location(), pending);
            Map<Integer, UUID> targets = new HashMap<>();
            plot.towersByCell().forEach((cell, index) -> targets.put(cell, towers.get(index).tower().id()));
            return new View(plot.map(), Map.copyOf(targets));
        }
    }

    /** Follow drawn legs in either direction, without solving reachability from a different transmitter. */
    static Set<Integer> neighborhood(int center, List<MonitorNetworkReach.Connection> connections) {
        Set<Integer> visible = Set.of(center);
        for (int hop = 0; hop < 2; hop++) {
            Set<Integer> next = new HashSet<>(visible);
            for (var link : connections) {
                if (visible.contains(link.from())) next.add(link.to());
                if (visible.contains(link.to())) next.add(link.from());
            }
            visible = next;
        }
        return visible;
    }

    public static String coordinates(BlockPos pos) {
        return "X " + pos.getX() + " Y " + pos.getY() + " Z " + pos.getZ();
    }

    public static Network scan(ServerLevel level, TowerManager manager, TowerRecord home, ChannelKey channel) {
        Map<Integer, PlottedTower> visible = new LinkedHashMap<>();
        var homeBase = home.antennaBase();
        visible.put(0, new PlottedTower(new Tower(home.id(), MonitorMap.Kind.HOME, homeBase, null, 0, ""),
                homeBase.getX() + .5, homeBase.getZ() + .5));
        Station origin = station(level, home);
        if (origin == null) return new Network(visible, List.of(), false);

        List<Station> stations = new ArrayList<>();
        stations.add(origin);
        manager.activeTowers().stream().filter(t -> !t.id().equals(home.id()) && t.dimension().equals(home.dimension()))
                .filter(t -> t.ports().stream().anyMatch(p -> p.input().equals(channel) && p.output().equals(channel)))
                .sorted(Comparator.comparing(TowerRecord::id))
                .forEach(t -> { Station station = station(level, t); if (station != null) stations.add(station); });
        var center = origin.site().position();
        stations.subList(1, stations.size()).sort(Comparator.comparingDouble(s ->
                Math.hypot(s.site().position().x - center.x, s.site().position().z - center.z)));
        var links = new SurveyLinks(stations, channel);
        var result = MonitorNetworkReach.solve(stations.size(), RepeatersConfig.INSTANCE.maxRelayHops, links,
                index -> stations.get(index).tower().attachment().resetsDecay());
        for (var node : result.nodes()) visible.put(node.tower(), plottedTower(stations, node));
        return new Network(visible, result.connections(), links.pending);
    }

    private static PlottedTower plottedTower(List<Station> stations, MonitorNetworkReach.Node node) {
        var station = stations.get(node.tower());
        var target = station.site().position();
        var record = station.tower();
        if (node.rx()) {
            var kind = node.tx() ? MonitorMap.Kind.TX_RX : MonitorMap.Kind.RX_ONLY;
            return new PlottedTower(new Tower(record.id(), kind, record.antennaBase(), null, 0, ""), target.x, target.z);
        }
        var source = stations.get(node.parent());
        var anchor = source.site().position();
        double dx = target.x - anchor.x, dz = target.z - anchor.z;
        double distance = Math.hypot(dx, dz);
        String bearing = MonitorMath.bearing(dx, dz);
        var estimate = MonitorMath.compassEstimate(anchor.x, anchor.z, bearing, distance, 1);
        var anchorBase = source.tower().antennaBase();
        var point = BlockPos.containing(estimate.x(), anchorBase.getY(), estimate.z());
        var tower = new Tower(record.id(), MonitorMap.Kind.ESTIMATE, point, anchorBase,
                MonitorMath.approximateDistance(distance), bearing);
        return new PlottedTower(tower, estimate.x(), estimate.z());
    }

    private static final class SurveyLinks implements MonitorNetworkReach.Links {
        private final List<Station> stations;
        private final ChannelKey channel;
        private boolean pending;

        SurveyLinks(List<Station> stations, ChannelKey channel) { this.stations = stations; this.channel = channel; }

        @Override @Nullable public RadioLinkMath.Result receive(int from, int to, int decay) {
            var sender = stations.get(from);
            var receiver = stations.get(to);
            var leg = RadioSpace.between(sender.site(), receiver.site(), false);
            if (leg == null) return null;
            double power = NativeRadioAdapter.departurePower(sender.tower(), channel, decay);
            var probe = RelaySurvey.probe(leg, sender.tower(), receiver.tower(), channel, power);
            pending |= probe.pending();
            var arrival = probe.arrival();
            return arrival != null && arrival.signal().severity() < 100 ? arrival.signal() : null;
        }
    }

    @Nullable private static Station station(ServerLevel level, TowerRecord tower) {
        for (var site : RadioSpace.sites(level.getServer(), tower, false))
            if (site.level() == level && !site.projected()) return new Station(tower, site);
        return null;
    }
}
