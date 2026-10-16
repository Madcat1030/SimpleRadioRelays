import dev.paul.simpleradiorepeaters.radio.RelayGraph;
import java.util.*;

/** Synthetic graph-only comparison, excluding Minecraft receivers, audio and networking. */
public final class RoutingBenchmark {
    private static volatile int sink;
    private static int size(List<? extends List<?>> routes) { return routes.stream().mapToInt(List::size).sum(); }
    private static double time(Runnable task, int repeats) {
        for (int i = 0; i < 20; i++) task.run();
        double[] samples = new double[7];
        for (int sample = 0; sample < samples.length; sample++) {
            long start = System.nanoTime();
            for (int run = 0; run < repeats; run++) task.run();
            samples[sample] = (System.nanoTime() - start) / 1e6 / repeats;
        }
        Arrays.sort(samples); return samples[samples.length / 2];
    }
    public static void main(String[] args) {
        System.out.println("towers,entrances,old_options,new_options,old_warm_ms,new_cold_ms,new_warm_ms");
        for (int n : new int[]{5, 25, 100}) {
            List<List<RelayGraph.Edge>> edges = new ArrayList<>();
            List<List<ReferenceRelayGraph.Edge>> referenceEdges = new ArrayList<>();
            for (int from = 0; from < n; from++) {
                List<RelayGraph.Edge> row = new ArrayList<>();
                List<ReferenceRelayGraph.Edge> oldRow = new ArrayList<>();
                for (int to = 0; to < n; to++) if (to != from) {
                    row.add(new RelayGraph.Edge(to, 15)); oldRow.add(new ReferenceRelayGraph.Edge(to, 15));
                }
                edges.add(row); referenceEdges.add(oldRow);
            }
            for (int entranceCount : new int[]{1, n}) {
                Map<Integer, Double> entries = new LinkedHashMap<>();
                for (int node = 0; node < entranceCount; node++) entries.put(node, 15d);
                ReferenceRelayGraph old = new ReferenceRelayGraph(referenceEdges, 64);
                RelayGraph current = new RelayGraph(edges, 64);
                int oldSize = size(old.alternatives(entries, 0)), currentSize = size(current.alternatives(entries, 0));
                double oldTime = time(() -> sink = size(old.alternatives(entries, 0)), 100);
                double coldTime = time(() -> sink = size(new RelayGraph(edges, 64).alternatives(entries, 0)), n == 100 ? 10 : 100);
                double warmTime = time(() -> sink = size(current.alternatives(entries, 0)), 300);
                System.out.printf(Locale.ROOT, "%d,%d,%d,%d,%.6f,%.6f,%.6f%n", n, entranceCount, oldSize, currentSize, oldTime, coldTime, warmTime);
            }
        }
    }
}
