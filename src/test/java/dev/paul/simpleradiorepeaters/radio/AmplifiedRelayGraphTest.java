package dev.paul.simpleradiorepeaters.radio;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AmplifiedRelayGraphTest {
    @Test void anAmplifierDetourCanRestoreAReachableRouteWithoutResettingTheBudget() {
        RelayGraph graph = new RelayGraph(3, (from, decay) -> from == 0
                ? decay <= 2 ? List.of(new RelayGraph.Edge(1, 20), new RelayGraph.Edge(2, 20)) : List.of(new RelayGraph.Edge(1, 20))
                : from == 1 ? List.of(new RelayGraph.Edge(0, 20)) : List.of(), 16, node -> node == 1);
        var routes = graph.alternatives(Map.of(0, 20d), 10, 10, true);
        assertEquals(List.of(new RelayGraph.Reach(20, 11, 11), new RelayGraph.Reach(20, 13, 2)), routes.get(0));
        assertEquals(List.of(new RelayGraph.Reach(20, 14, 3)), routes.get(2));
        assertEquals(List.of(new RelayGraph.Reach(20, 12, 3)), graph.alternatives(Map.of(0, 20d), 10, 1, true).get(2));
    }
    @Test void amplifierCyclesStillConsumeTheTotalVisitBudget() {
        RelayGraph graph = new RelayGraph(2, (from, decay) -> List.of(new RelayGraph.Edge(1 - from, 15)), 64, node -> true);
        var full = graph.alternatives(Map.of(0, 15d), 61, 30, false);
        assertEquals(List.of(new RelayGraph.Reach(15, 62, 1), new RelayGraph.Reach(15, 64, 1)), full.get(0));
        assertEquals(List.of(new RelayGraph.Reach(15, 63, 1)), full.get(1));
        assertTrue(graph.alternatives(Map.of(0, 15d), 64, 1, true).stream().allMatch(List::isEmpty));
    }
    @Test void warmRoutesRetainTheWorstIngressNoiseAfterAnAmplifier() {
        RelayGraph graph = new RelayGraph(2, (from, decay) -> from == 0 ? List.of(new RelayGraph.Edge(1, 20)) : List.of(), 64, node -> node == 1);
        assertEquals(new RelayGraph.Reach(60, 31, 1), graph.alternatives(Map.of(0, 60d), 29, 29, true).get(1).get(0));
        assertEquals(new RelayGraph.Reach(90, 31, 1), graph.alternatives(Map.of(0, 90d), 29, 29, true).get(1).get(0));
    }
    @Test void randomizedAmplifiedNetworksMatchAnExhaustiveIndependentReference() {
        Random random = new Random(4001);
        for (int test = 0; test < 1000; test++) {
            int n = 2 + random.nextInt(5), limit = 4 + random.nextInt(5), previous = random.nextInt(3), decay = random.nextInt(previous + 1);
            boolean[] resets = new boolean[n]; double[][] noise = new double[n][n]; int[][] cutoffs = new int[n][n];
            for (int i = 0; i < n; i++) { resets[i] = random.nextBoolean(); for (int j = 0; j < n; j++) {
                noise[i][j] = random.nextDouble() * 60; cutoffs[i][j] = random.nextInt(limit + 1);
            }}
            RelayGraph.Links links = (from, count) -> {
                List<RelayGraph.Edge> edges = new ArrayList<>();
                for (int to = 0; to < n; to++) if (to != from && count <= cutoffs[from][to])
                    edges.add(new RelayGraph.Edge(to, noise[from][to] + count * .125));
                return edges;
            };
            RelayGraph graph = new RelayGraph(n, links, limit, node -> resets[node]); Map<Integer, Double> entries = new HashMap<>();
            for (int node = 0; node < n; node++) if (random.nextBoolean()) entries.put(node, random.nextDouble() * 80);
            for (int packet = 0; packet < 2; packet++) {
                var full = reference(n, links, resets, limit, previous, decay, entries);
                assertEquals(full, graph.alternatives(entries, previous, decay, false), "full " + test);
                List<List<RelayGraph.Reach>> frontier = full.stream().map(row -> row.stream().filter(candidate -> row.stream().noneMatch(other ->
                        other != candidate && other.hops() <= candidate.hops() && other.decay() <= candidate.decay()
                                && other.severity() <= candidate.severity())).toList()).toList();
                assertEquals(frontier, graph.alternatives(entries, previous, decay, true), "frontier " + test);
                entries.replaceAll((node, old) -> random.nextDouble() * 95);
            }
        }
    }
    private static List<List<RelayGraph.Reach>> reference(int n, RelayGraph.Links links, boolean[] resets, int limit,
                                                         int previous, int previousDecay, Map<Integer, Double> entries) {
        double[][][] costs = new double[n][limit + 1][limit + 1];
        for (var node : costs) for (var row : node) Arrays.fill(row, Double.POSITIVE_INFINITY);
        entries.forEach((node, noise) -> costs[node][previous + 1][resets[node] ? 1 : previousDecay + 1] = noise);
        for (int hops = previous + 1; hops < limit; hops++) for (int node = 0; node < n; node++) for (int decay = 1; decay <= hops; decay++)
            if (Double.isFinite(costs[node][hops][decay])) for (var edge : links.from(node, decay)) {
                int next = resets[edge.destination()] ? 1 : decay + 1;
                costs[edge.destination()][hops + 1][next] = Math.min(costs[edge.destination()][hops + 1][next],
                        Math.max(costs[node][hops][decay], edge.severity()));
            }
        List<List<RelayGraph.Reach>> result = new ArrayList<>();
        for (int node = 0; node < n; node++) {
            List<RelayGraph.Reach> row = new ArrayList<>();
            for (int hops = previous + 1; hops <= limit; hops++) for (int decay = 1; decay <= hops; decay++)
                if (Double.isFinite(costs[node][hops][decay])) row.add(new RelayGraph.Reach(costs[node][hops][decay], hops, decay));
            result.add(row);
        }
        return result;
    }
    @Test void dimensionalLossUsesNativeReachThenNativeTravelRatherThanASecondRangeGate() {
        var received = RadioLinkMath.receive(200, 100, 100, 300, 20, 1, 3,
                RadioLinkMath.Loss.ADDITIVE, 500, 15);
        assertNotNull(received); assertEquals(40, received.power(), .00001);
        assertNull(RadioLinkMath.receive(200, 100, 100, 300, 20, 3,
                RadioLinkMath.Loss.ADDITIVE, 500, 15));
    }
}
