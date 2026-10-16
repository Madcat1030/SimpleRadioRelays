package dev.paul.simpleradiorepeaters.radio;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Cached minimax routes; count-dependent power falls at the same rate for every catalyst. Server-thread owned. */
public final class RelayGraph {
    public record Edge(int destination, double severity) {}
    /** Total relay visits, including those before a wired re-entry. */
    public record Reach(double severity, int hops) {}
    @FunctionalInterface public interface Links { List<Edge> from(int tower, int relayCount); }
    private record RouteKey(int entry, int previous, boolean prune) {}
    private record LinkKey(int tower, int count) {}
    private record Routes(List<List<Reach>> choices, int size) {}
    private final int nodeCount, maxHops;
    private final Links links;
    private final Cache<RouteKey, Routes> routes = new Cache<>(128, 32768, Routes::size);
    private final Cache<LinkKey, List<Edge>> rows = new Cache<>(512, 65536, List::size);

    public RelayGraph(List<List<Edge>> edges, int maxHops) {
        List<List<Edge>> fixed = edges.stream().map(List::copyOf).toList();
        nodeCount = fixed.size(); links = (tower, count) -> fixed.get(tower); this.maxHops = Math.max(1, maxHops);
    }
    public RelayGraph(int nodeCount, Links links, int maxHops) {
        this.nodeCount = nodeCount; this.links = links; this.maxHops = Math.max(1, maxHops);
    }
    public List<Edge> outgoing(int tower, int count) {
        return rows.get(new LinkKey(tower, count), key -> List.copyOf(links.from(key.tower(), key.count())));
    }
    public List<List<Reach>> alternatives(Map<Integer, Double> entrances, int previousRelays) {
        return alternatives(entrances, previousRelays, true);
    }
    /** Custom routing predicates can request the original unpruned count alternatives. */
    public List<List<Reach>> alternatives(Map<Integer, Double> entrances, int previousRelays, boolean prune) {
        if (previousRelays >= maxHops) return Collections.nCopies(nodeCount, List.of());
        int previous = Math.max(0, previousRelays);
        List<List<Reach>> merged = emptyChoices();
        var entries = entrances.entrySet().stream().filter(entry -> entry.getKey() >= 0 && entry.getKey() < nodeCount
                && Double.isFinite(entry.getValue())).sorted(Map.Entry.comparingByValue()).toList();
        // Every entrance is already a viable first departure. Seed these before considering detours.
        if (prune) for (var entry : entries) merged.get(entry.getKey()).add(new Reach(entry.getValue(), previous + 1));
        for (var entry : entries) {
            if (prune && (previous + 1 == maxHops || descendantsDominated(merged, entry.getValue(), previous + 2))) continue;
            Routes cached = routes.get(new RouteKey(entry.getKey(), previous, prune), this::calculate);
            for (int tower = 0; tower < nodeCount; tower++) for (Reach path : cached.choices().get(tower)) {
                double noise = Math.max(entry.getValue(), path.severity());
                offer(merged.get(tower), noise == path.severity() ? path : new Reach(noise, path.hops()), prune);
            }
        }
        return freeze(merged).choices();
    }

    private static boolean descendantsDominated(List<List<Reach>> choices, double ingressNoise, int firstDescendantCount) {
        // Descendants cannot be cleaner than ingress or use fewer than one additional visit.
        for (List<Reach> row : choices) {
            boolean covered = false;
            for (Reach path : row) if (path.severity() <= ingressNoise && path.hops() <= firstDescendantCount) { covered = true; break; }
            if (!covered) return false;
        }
        return true;
    }

    private Routes calculate(RouteKey key) {
        int first = key.previous() + 1;
        double[][] costs = new double[nodeCount][maxHops + 1];
        for (double[] row : costs) Arrays.fill(row, Double.POSITIVE_INFINITY);
        costs[key.entry()][first] = 0;
        double[] best = new double[nodeCount]; Arrays.fill(best, Double.POSITIVE_INFINITY);
        List<List<Reach>> result = emptyChoices();
        for (int count = first; count <= maxHops; count++) {
            boolean active = false;
            for (int from = 0; from < nodeCount; from++) {
                double noise = costs[from][count];
                if (!Double.isFinite(noise) || (key.prune() && noise >= best[from])) continue;
                best[from] = Math.min(best[from], noise);
                result.get(from).add(new Reach(noise, count)); active = true;
                if (count < maxHops) for (Edge edge : outgoing(from, count)) {
                    double severity = Math.max(noise, edge.severity());
                    costs[edge.destination()][count + 1] = Math.min(costs[edge.destination()][count + 1], severity);
                }
            }
            if (!active) break;
        }
        return freeze(result);
    }
    private List<List<Reach>> emptyChoices() {
        List<List<Reach>> result = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) result.add(new ArrayList<>());
        return result;
    }
    private static Routes freeze(List<List<Reach>> choices) {
        int size = choices.stream().mapToInt(List::size).sum();
        for (List<Reach> row : choices) row.sort(Comparator.comparingInt(Reach::hops));
        return new Routes(choices.stream().map(List::copyOf).toList(), size + choices.size());
    }
    private static boolean offer(List<Reach> choices, Reach candidate, boolean prune) {
        for (Reach existing : choices)
            if ((prune ? existing.hops() <= candidate.hops() : existing.hops() == candidate.hops())
                    && existing.severity() <= candidate.severity()) return false;
        choices.removeIf(existing -> (prune ? candidate.hops() <= existing.hops() : candidate.hops() == existing.hops())
                && candidate.severity() <= existing.severity());
        choices.add(candidate); return true;
    }

    /** Eviction costs recomputation only. Both entry count and numeric element count are bounded. */
    private static final class Cache<K, V> {
        private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, .75f, true);
        private final int maxEntries, maxWeight;
        private final ToIntFunction<V> weigh;
        private int weight;
        Cache(int maxEntries, int maxWeight, ToIntFunction<V> weigh) {
            this.maxEntries = maxEntries; this.maxWeight = maxWeight; this.weigh = weigh;
        }
        V get(K key, Function<K, V> create) {
            V cached = entries.get(key); if (cached != null) return cached;
            V value = create.apply(key); int cost = weigh.applyAsInt(value);
            if (cost <= maxWeight) {
                entries.put(key, value); weight += cost;
                var iterator = entries.entrySet().iterator();
                while (entries.size() > maxEntries || weight > maxWeight) {
                    weight -= weigh.applyAsInt(iterator.next().getValue()); iterator.remove();
                }
            }
            return value;
        }
    }
}
