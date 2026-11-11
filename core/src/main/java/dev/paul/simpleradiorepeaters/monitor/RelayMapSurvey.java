package dev.paul.simpleradiorepeaters.monitor;

import dev.paul.simpleradiorepeaters.RepeatersConfig;
import dev.paul.simpleradiorepeaters.network.ChannelKey;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import dev.paul.simpleradiorepeaters.network.TowerRecord;
import dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter;
import dev.paul.simpleradiorepeaters.radio.RadioSpace;
import dev.paul.simpleradiorepeaters.radio.RelayPower;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
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

        int maxVisits = Math.max(1, RepeatersConfig.INSTANCE.maxRelayHops);
        boolean[] resets = new boolean[stations.size()];
        List<List<MonitorReach.Link>> links = new ArrayList<>();
        boolean pending = false;
        for (int from = 0; from < stations.size(); from++) {
            Station sender = stations.get(from);
            TowerRecord tower = sender.tower();
            resets[from] = tower.attachment().resetsDecay();
            double nominal = NativeRadioAdapter.towerPower(tower, channel), efficiency = tower.efficiency();
            double power = RelayPower.departure(nominal, efficiency, 1);
            List<MonitorReach.Link> row = new ArrayList<>();
            for (int to = 0; to < stations.size(); to++) {
                if (from == to) continue;
                Station receiver = stations.get(to);
                var leg = RadioSpace.between(sender.site(), receiver.site(), false);
                if (leg == null) continue;
                var probe = RelaySurvey.probe(leg, tower, receiver.tower(), channel, power);
                pending |= probe.pending();
                var arrival = probe.arrival();
                if (arrival == null || !(arrival.signal().severity() < 100)) continue;
                // Propagation uses the same receiver math as the live graph. Find the last
                // usable departure with that link's sampled terrain, without sampling again.
                int low = 1, high = from == 0 || resets[from] ? 1 : maxVisits;
                while (low < high) {
                    int mid = (low + high + 1) / 2;
                    double decayed = RelayPower.departure(nominal, efficiency, mid);
                    var signal = RelaySurvey.receive(leg, receiver.tower(), channel, decayed, arrival.extra());
                    if (signal != null && signal.severity() < 100) low = mid;
                    else high = mid - 1;
                }
                row.add(new MonitorReach.Link(to, resets[from] ? maxVisits : low));
            }
            links.add(row);
        }

        MonitorReach.Result reach = MonitorReach.solve(links, resets, maxVisits);
        var center = origin.site().position();
        List<MonitorMap.Site> visible = new ArrayList<>();
        for (int i = 1; i < stations.size(); i++) {
            var target = stations.get(i).site().position();
            int via = reach.via()[i];
            if (reach.audible()[i]) {
                var kind = via < 0 ? MonitorMap.Kind.RX_ONLY : MonitorMap.Kind.TX_RX;
                visible.add(new MonitorMap.Site(target.x - center.x, target.z - center.z, kind));
            } else if (via >= 0) {
                var anchor = stations.get(via).site().position();
                double dx = target.x - anchor.x, dz = target.z - anchor.z;
                var estimate = MonitorMath.compassEstimate(anchor.x, anchor.z, MonitorMath.bearing(dx, dz),
                        Math.hypot(dx, dz), 1);
                visible.add(new MonitorMap.Site(estimate.x() - center.x, estimate.z() - center.z, MonitorMap.Kind.ESTIMATE));
            }
        }
        return MonitorMap.plot(visible, pending);
    }

    private static Station station(ServerLevel level, TowerRecord tower) {
        for (var site : RadioSpace.sites(level.getServer(), tower, false))
            if (site.level() == level && !site.projected()) return new Station(tower, site);
        return null;
    }
}
