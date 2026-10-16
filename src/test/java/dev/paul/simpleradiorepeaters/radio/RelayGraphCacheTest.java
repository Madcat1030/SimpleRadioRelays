package dev.paul.simpleradiorepeaters.radio;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RelayGraphCacheTest {
    @Test void uniformClustersRetainOneOptionPerTowerAndFallbackKeepsOriginalCounts() {
        for (int n : new int[]{5, 25, 100}) {
            RelayGraph graph = dense(n);
            var compact = graph.alternatives(Map.of(0, 15d), 0);
            assertEquals(n, compact.stream().mapToInt(List::size).sum());
            assertEquals(63 * n, graph.alternatives(Map.of(0, 15d), 0, false).stream().mapToInt(List::size).sum());
            assertEquals(compact, graph.alternatives(Map.of(0, 15d), 0));
        }
    }
    @Test void warmRoutesReapplyCurrentIngressNoiseAndUseTheCorrectPreviousCount() {
        int[] calls = {0};
        RelayGraph graph = new RelayGraph(2, (from, count) -> {
            calls[0]++;
            return from == 0 ? List.of(new RelayGraph.Edge(1, 20)) : List.of();
        }, 8);
        var initial = graph.alternatives(Map.of(0, 15d), 0); int cold = calls[0];
        var noisy = graph.alternatives(Map.of(0, 60d), 0);
        assertEquals(cold, calls[0]); assertEquals(20, initial.get(1).get(0).severity());
        assertEquals(60, noisy.get(1).get(0).severity());
        var reentry = graph.alternatives(Map.of(0, 15d), 4);
        assertEquals(5, reentry.get(0).get(0).hops()); assertEquals(6, reentry.get(1).get(0).hops());
        assertTrue(calls[0] > cold);
        assertThrows(UnsupportedOperationException.class, () -> initial.get(1).clear());
    }
    @Test void evenASmallQualityImprovementKeepsTheLongerAlternative() {
        RelayGraph graph = new RelayGraph(List.of(List.of(new RelayGraph.Edge(2, 20.5), new RelayGraph.Edge(1, 20)),
                List.of(new RelayGraph.Edge(2, 20)), List.of()), 8);
        assertEquals(List.of(new RelayGraph.Reach(20.5, 2), new RelayGraph.Reach(20, 3)), graph.alternatives(Map.of(0, 0d), 0).get(2));
        assertEquals(List.of(new RelayGraph.Reach(60, 2)), graph.alternatives(Map.of(0, 60d), 0).get(2));
    }
    @Test void cacheEvictionOnlyRecomputesAndNeverChangesResults() {
        RelayGraph graph = new RelayGraph(List.of(List.of(new RelayGraph.Edge(1, 0)), List.of()), 128);
        var first = graph.alternatives(Map.of(0, 0d), 0).get(1).get(0);
        assertSame(first, graph.alternatives(Map.of(0, 0d), 0).get(1).get(0));
        for (int previous = 0; previous < 128; previous++) graph.alternatives(Map.of(0, 0d), previous, false);
        var rebuilt = graph.alternatives(Map.of(0, 0d), 0).get(1).get(0);
        assertEquals(first, rebuilt); assertNotSame(first, rebuilt);
    }
    @Test void directEntrancesPruneColdClusterSearchWithoutReadingAnyLinkRows() {
        int[] calls = {0};
        RelayGraph graph = new RelayGraph(100, (from, count) -> { calls[0]++; return List.of(); }, 64);
        Map<Integer, Double> entrances = new HashMap<>();
        for (int node = 0; node < 100; node++) entrances.put(node, 15d);
        var result = graph.alternatives(entrances, 0);
        assertEquals(0, calls[0]); assertTrue(result.stream().allMatch(row -> row.equals(List.of(new RelayGraph.Reach(15, 1)))));
    }
    @Test void randomizedDirectedNetworksMatchUncachedReferenceBeforeAndAfterCacheWarmup() {
        Random random = new Random(3101);
        for (int test = 0; test < 2000; test++) {
            int n = 2 + random.nextInt(7), limit = 3 + random.nextInt(7), previous = random.nextInt(3);
            double[][] base = new double[n][n], slope = new double[n][n]; int[][] cutoff = new int[n][n];
            for (int from = 0; from < n; from++) for (int to = 0; to < n; to++) {
                base[from][to] = random.nextDouble() * 60; slope[from][to] = random.nextDouble() * 3;
                cutoff[from][to] = random.nextInt(limit + 1);
            }
            RelayGraph.Links links = (from, count) -> {
                List<RelayGraph.Edge> row = new ArrayList<>();
                for (int to = 0; to < n; to++) if (to != from && count <= cutoff[from][to])
                    row.add(new RelayGraph.Edge(to, base[from][to] + slope[from][to] * count));
                return row;
            };
            RelayGraph graph = new RelayGraph(n, links, limit);
            Map<Integer, Double> entries = new LinkedHashMap<>();
            for (int node = 0; node < n; node++) if (random.nextBoolean()) entries.put(node, random.nextDouble() * 70);
            for (int packet = 0; packet < 2; packet++) {
                var reference = reference(n, links, limit, entries, previous);
                assertEquals(frontier(reference), graph.alternatives(entries, previous), "pruned network " + test);
                assertEquals(reference, graph.alternatives(entries, previous, false), "custom-predicate fallback " + test);
                entries.replaceAll((node, noise) -> random.nextDouble() * 90);
            }
        }
    }
    private static RelayGraph dense(int n) {
        List<List<RelayGraph.Edge>> edges = new ArrayList<>();
        for (int from = 0; from < n; from++) {
            List<RelayGraph.Edge> row = new ArrayList<>();
            for (int to = 0; to < n; to++) if (to != from) row.add(new RelayGraph.Edge(to, 15));
            edges.add(row);
        }
        return new RelayGraph(edges, 64);
    }
    private static List<List<RelayGraph.Reach>> frontier(List<List<RelayGraph.Reach>> all) {
        List<List<RelayGraph.Reach>> result = new ArrayList<>();
        for (var row : all) {
            List<RelayGraph.Reach> kept = new ArrayList<>(); double best = Double.POSITIVE_INFINITY;
            for (var path : row) if (path.severity() < best) { kept.add(path); best = path.severity(); }
            result.add(kept);
        }
        return result;
    }
    /** Exhaustive tower/count dynamic program with no route cache or dominance pruning. */
    private static List<List<RelayGraph.Reach>> reference(int n, RelayGraph.Links links, int limit,
                                                         Map<Integer, Double> entries, int previous) {
        double[][] costs = new double[n][limit + 1];
        for (var row : costs) Arrays.fill(row, Double.POSITIVE_INFINITY);
        entries.forEach((node, noise) -> costs[node][previous + 1] = noise);
        for (int count = previous + 1; count < limit; count++) for (int from = 0; from < n; from++)
            if (Double.isFinite(costs[from][count])) for (var edge : links.from(from, count))
                costs[edge.destination()][count + 1] = Math.min(costs[edge.destination()][count + 1], Math.max(costs[from][count], edge.severity()));
        List<List<RelayGraph.Reach>> result = new ArrayList<>();
        for (var row : costs) {
            List<RelayGraph.Reach> choices = new ArrayList<>();
            for (int count = previous + 1; count <= limit; count++) if (Double.isFinite(row[count])) choices.add(new RelayGraph.Reach(row[count], count));
            result.add(choices);
        }
        return result;
    }
}
