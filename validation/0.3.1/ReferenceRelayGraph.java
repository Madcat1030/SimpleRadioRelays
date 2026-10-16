

import java.util.*;

/** Bounded minimax propagation; directed link costs depend on the sender's total relay count. */
public final class ReferenceRelayGraph {
    public record Edge(int destination, double severity) {}
    /** hops is the total number of relay visits, including those before a wired re-entry. */
    public record Reach(double severity, int hops) {}
    @FunctionalInterface public interface Links { List<Edge> from(int tower, int relayCount); }
    private final int nodeCount, maxHops;
    private final Links links;

    public ReferenceRelayGraph(List<List<Edge>> edges, int maxHops) {
        List<List<Edge>> fixed = edges.stream().map(List::copyOf).toList();
        nodeCount = fixed.size(); links = (tower, count) -> fixed.get(tower); this.maxHops = Math.max(1, maxHops);
    }
    public ReferenceRelayGraph(int nodeCount, Links links, int maxHops) {
        this.nodeCount = nodeCount; this.links = links; this.maxHops = Math.max(1, maxHops);
    }
    /** Keep each count alternative: shorter routes can beat cleaner, weaker routes on the final leg. */
    public List<List<Reach>> alternatives(Map<Integer, Double> entrances, int previousRelays) {
        if (previousRelays >= maxHops) return Collections.nCopies(nodeCount, List.of());
        int first = Math.max(0, previousRelays) + 1;
        double[][] costs = new double[nodeCount][maxHops + 1];
        for (double[] row : costs) Arrays.fill(row, Double.POSITIVE_INFINITY);
        for (var entry : entrances.entrySet()) costs[entry.getKey()][first] = entry.getValue();
        for (int count = first; count < maxHops; count++) {
            for (int from = 0; from < nodeCount; from++) {
                if (!Double.isFinite(costs[from][count])) continue;
                for (Edge edge : links.from(from, count)) {
                    double severity = Math.max(costs[from][count], edge.severity());
                    costs[edge.destination()][count + 1] = Math.min(costs[edge.destination()][count + 1], severity);
                }
            }
        }
        List<List<Reach>> result = new ArrayList<>();
        for (double[] row : costs) {
            List<Reach> choices = new ArrayList<>();
            for (int count = first; count <= maxHops; count++)
                if (Double.isFinite(row[count])) choices.add(new Reach(row[count], count));
            result.add(List.copyOf(choices));
        }
        return List.copyOf(result);
    }
}
