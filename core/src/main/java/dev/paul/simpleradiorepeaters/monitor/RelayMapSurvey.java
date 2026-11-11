package dev.paul.simpleradiorepeaters.monitor;

import dev.paul.simpleradiorepeaters.RepeatersConfig;
import dev.paul.simpleradiorepeaters.network.ChannelKey;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import dev.paul.simpleradiorepeaters.network.TowerRecord;
import dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter;
import dev.paul.simpleradiorepeaters.radio.RadioLinkMath;
import dev.paul.simpleradiorepeaters.radio.RadioSpace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** The map follows physical, same-channel legs without creating live graph or relay state. */
public final class RelayMapSurvey {
    private record Station(TowerRecord tower, RadioSpace.Site site) {}

    private RelayMapSurvey() {}

    public static MonitorMap scan(ServerLevel level, TowerManager manager, TowerRecord home, ChannelKey channel) {
        List<Station> stations = new ArrayList<>();
        Station origin = station(level, home);
        if (origin == null) return MonitorMap.plot(List.of(), false);
        stations.add(origin);
        manager.activeTowers().stream().filter(t -> !t.id().equals(home.id()) && t.dimension().equals(home.dimension()))
                .filter(t -> t.ports().stream().anyMatch(p -> p.input().equals(channel) && p.output().equals(channel)))
                .sorted(Comparator.comparing(TowerRecord::id))
                .forEach(t -> { Station station = station(level, t); if (station != null) stations.add(station); });

        var center = origin.site().position();
        stations.subList(1, stations.size()).sort(Comparator.comparingDouble(s -> distance(s.site().position(), center)));
        var links = new SurveyLinks(stations, channel);
        var nodes = MonitorReach.solve(stations.size(), RepeatersConfig.INSTANCE.maxRelayHops, links);

        List<MonitorMap.Site> visible = new ArrayList<>();
        int[] branch = new int[stations.size()], children = new int[stations.size()];
        Arrays.fill(branch, -1);
        int next = 0;
        for (var node : nodes) if (node.parent() == 0) {
            if (node.rx()) branch[node.tower()] = next++;
            visible.add(marker(stations, center, node, branch[node.tower()], 0));
        }
        var secondary = nodes.stream().filter(n -> n.parent() != 0)
                .sorted(Comparator.comparing((MonitorReach.Node n) -> !n.rx()).thenComparingDouble(n ->
                        distance(stations.get(n.tower()).site().position(), stations.get(n.parent()).site().position())))
                .toList();
        for (var node : secondary) {
            int parent = node.parent();
            if (branch[parent] >= 0 && children[parent] < MonitorMap.CHILDREN)
                visible.add(marker(stations, center, node, branch[parent], ++children[parent]));
        }
        return MonitorMap.plot(visible, links.pending);
    }

    private static double distance(Vec3 a, Vec3 b) { return Math.hypot(a.x - b.x, a.z - b.z); }

    private static MonitorMap.Site marker(List<Station> stations, Vec3 center, MonitorReach.Node node, int branch, int child) {
        var target = stations.get(node.tower()).site().position();
        if (node.rx()) return new MonitorMap.Site(target.x - center.x, target.z - center.z,
                node.tx() ? MonitorMap.Kind.TX_RX : MonitorMap.Kind.RX_ONLY, MonitorMap.address(branch, child));
        var anchor = stations.get(node.parent()).site().position();
        double dx = target.x - anchor.x, dz = target.z - anchor.z;
        var estimate = MonitorMath.compassEstimate(anchor.x, anchor.z, MonitorMath.bearing(dx, dz), Math.hypot(dx, dz), 1);
        return new MonitorMap.Site(estimate.x() - center.x, estimate.z() - center.z,
                MonitorMap.Kind.ESTIMATE, MonitorMap.estimateAddress(branch));
    }

    private static final class SurveyLinks implements MonitorReach.Links {
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
