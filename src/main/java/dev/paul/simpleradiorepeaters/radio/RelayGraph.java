package dev.paul.simpleradiorepeaters.radio;

import java.util.*;

/** Immutable directed topology with cached minimax paths, including hop/quality alternatives. */
public final class RelayGraph {
    public record Edge(int destination, double severity) {}
    public record Reach(double severity, int hops) {}
    private final List<List<Edge>> edges;
    private final int maxHops;
    private final Map<Integer, double[][]> paths = new HashMap<>();

    public RelayGraph(List<List<Edge>> edges, int maxHops) {
        this.edges = edges.stream().map(List::copyOf).toList();
        this.maxHops = Math.max(1, maxHops);
    }

    public Reach[] reachable(Map<Integer, Double> entrances) {
        Reach[] result = new Reach[edges.size()];
        for (var entrance : entrances.entrySet()) {
            double[][] cached = paths.computeIfAbsent(entrance.getKey(), this::calculate);
            for (int node = 0; node < result.length; node++) {
                for (int hops = 1; hops <= maxHops; hops++) {
                    if (!Double.isFinite(cached[node][hops])) continue;
                    double severity = Math.max(entrance.getValue(), cached[node][hops]);
                    Reach previous = result[node];
                    if (previous == null || severity < previous.severity()
                            || (severity == previous.severity() && hops < previous.hops()))
                        result[node] = new Reach(severity, hops);
                }
            }
        }
        return result;
    }

    private double[][] calculate(int entrance) {
        double[][] costs = new double[edges.size()][maxHops + 1];
        for (double[] row : costs) Arrays.fill(row, Double.POSITIVE_INFINITY);
        costs[entrance][1] = 0;
        for (int hops = 1; hops < maxHops; hops++) {
            for (int from = 0; from < edges.size(); from++) {
                if (!Double.isFinite(costs[from][hops])) continue;
                for (Edge edge : edges.get(from)) {
                    double next = Math.max(costs[from][hops], edge.severity());
                    costs[edge.destination()][hops + 1] = Math.min(costs[edge.destination()][hops + 1], next);
                }
            }
        }
        return costs;
    }
}
