package dev.paul.simpleradiorepeaters.monitor;

import dev.paul.simpleradiorepeaters.radio.RelayGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

class MonitorReachTest {
    @Test void showsTheFirstUnheardTowerButDoesNotTracePastIt() {
        // Home ⇄ B → C → D, plus E → Home. D can reach Home only at fresh power.
        var links = List.of(List.of(link(1, 8)), List.of(link(0, 8), link(2, 8)),
                List.of(link(3, 8)), List.of(link(0, 1)), List.of(link(0, 8)));
        var reach = MonitorReach.solve(links, new boolean[5], 8);
        assertArrayEquals(new boolean[]{false, true, false, true, true}, reach.audible());
        assertArrayEquals(new int[]{-1, 0, 1, -1, -1}, reach.via());
        var noReturn = new ArrayList<>(links);
        noReturn.set(3, List.of());
        var hidden = MonitorReach.solve(noReturn, new boolean[5], 8);
        assertFalse(hidden.audible()[3]);
        assertEquals(-1, hidden.via()[3]);
        noReturn.set(4, List.of(link(0, 2), link(3, 8)));
        noReturn.set(0, List.of(link(1, 8), link(4, 8)));
        noReturn.set(3, List.of(link(4, 8)));
        var alternate = MonitorReach.solve(noReturn, new boolean[5], 8);
        assertFalse(alternate.audible()[2]);
        assertTrue(alternate.audible()[3]);
        assertEquals(4, alternate.via()[3]);
    }

    @Test void inboundUsesActualFinalDecayAndEnderResetDoesNotGiveExtraVisits() {
        var links = List.of(List.<MonitorReach.Link>of(), List.of(link(2, 4)),
                List.of(link(3, 4)), List.of(link(0, 1)));
        boolean[] resets = new boolean[4];
        assertFalse(MonitorReach.solve(links, resets, 3).audible()[1]);
        resets[3] = true;
        assertTrue(MonitorReach.solve(links, resets, 3).audible()[1]);
        assertFalse(MonitorReach.solve(links, resets, 2).audible()[1]);
    }

    @Test void reachabilityMatchesTheForwardingGraphOnDirectedDecayingNetworks() {
        var random = new Random(3492);
        for (int run = 0; run < 120; run++) {
            int size = 2 + random.nextInt(8), budget = 1 + random.nextInt(7);
            List<List<MonitorReach.Link>> links = new ArrayList<>();
            boolean[] resets = new boolean[size];
            for (int from = 0; from < size; from++) {
                resets[from] = random.nextInt(4) == 0;
                List<MonitorReach.Link> row = new ArrayList<>();
                for (int to = 0; to < size; to++) if (from != to && random.nextBoolean())
                    row.add(link(to, resets[from] ? budget : 1 + random.nextInt(budget)));
                links.add(row);
            }
            var reach = MonitorReach.solve(links, resets, budget);
            var inbound = graph(links, resets, budget, from -> true);
            for (int origin = 1; origin < size; origin++) {
                var departures = inbound.alternatives(Map.of(origin, 0.0), 0);
                boolean heard = false;
                for (int from = 1; from < size; from++) for (var path : departures.get(from))
                    heard |= links.get(from).stream().anyMatch(l -> l.to() == 0 && l.lastDecay() >= path.decay());
                assertEquals(heard, reach.audible()[origin], "return path on graph " + run);
            }
            var outbound = graph(links, resets, budget, from -> reach.audible()[from]);
            Map<Integer, Double> entrances = new HashMap<>();
            for (var link : links.get(0)) entrances.put(link.to(), 0.0);
            var departures = outbound.alternatives(entrances, 0);
            for (int target = 1; target < size; target++)
                assertEquals(!departures.get(target).isEmpty(), reach.via()[target] >= 0, "outbound path on graph " + run);
        }
    }

    private static MonitorReach.Link link(int to, int lastDecay) { return new MonitorReach.Link(to, lastDecay); }

    private static RelayGraph graph(List<List<MonitorReach.Link>> links, boolean[] resets, int budget, IntPredicate expand) {
        return new RelayGraph(links.size(), (from, decay) -> from == 0 || !expand.test(from) ? List.of()
                : links.get(from).stream().filter(l -> l.to() != 0 && l.lastDecay() >= decay)
                        .map(l -> new RelayGraph.Edge(l.to(), (from * 7 + l.to()) % 99)).toList(),
                budget, node -> resets[node]);
    }
}
