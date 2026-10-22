package dev.paul.simpleradiorepeaters.radio;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.IntPredicate;

/** Cached minimax routes over directed channel ports. Amplifiers reset decay, never the total visit budget. */
public final class RelayGraph {
    // Optional observation has no dependency on Minecraft and never changes cache contents or policy.
    private static volatile java.util.function.BiConsumer<String, String> cacheObserver;
    public static void observeCaches(java.util.function.BiConsumer<String, String> observer) { cacheObserver = observer; }
    private static void observedCache(String kind, String outcome) {
        var observer = cacheObserver; if (observer != null) observer.accept(kind, outcome);
    }
    public record Edge(int destination, double severity) {}
    /** Total relay visits, including those before a wired re-entry. */
    public record Reach(double severity, int hops, int decay) {
        public Reach(double severity, int hops) { this(severity, hops, hops); }
    }
    @FunctionalInterface public interface Links { List<Edge> from(int tower, int relayCount); }
    private record RouteKey(int entry, int previous, int previousDecay, boolean prune) {}
    private record LinkKey(int tower, int count) {}
    private record Routes(List<List<Reach>> choices, int size) {}
    private final int nodeCount, maxHops;
    private final Links links;
    private final boolean[] resets;
    private final boolean anyReset;
    private final Cache<RouteKey, Routes> routes = new Cache<>("path", 128, 32768, Routes::size);
    private final Cache<LinkKey, List<Edge>> rows = new Cache<>("link_row", 512, 65536, List::size);

    public RelayGraph(List<List<Edge>> edges, int maxHops) {
        List<List<Edge>> fixed = edges.stream().map(List::copyOf).toList();
        nodeCount = fixed.size(); links = (tower, count) -> fixed.get(tower); this.maxHops = Math.max(1, maxHops);
        resets = new boolean[nodeCount]; anyReset = false;
    }
    public RelayGraph(int nodeCount, Links links, int maxHops) {
        this(nodeCount, links, maxHops, tower -> false);
    }
    public RelayGraph(int nodeCount, Links links, int maxHops, IntPredicate resets) {
        this.nodeCount = nodeCount; this.links = links; this.maxHops = Math.max(1, maxHops);
        this.resets = new boolean[nodeCount]; boolean any = false;
        for (int tower = 0; tower < nodeCount; tower++) any |= this.resets[tower] = resets.test(tower);
        anyReset = any;
    }
    public List<Edge> outgoing(int tower, int count) {
        return rows.get(new LinkKey(tower, count), key -> List.copyOf(links.from(key.tower(), key.count())));
    }
    public List<List<Reach>> alternatives(Map<Integer, Double> entrances, int previousRelays) {
        return alternatives(entrances, previousRelays, true);
    }
    /** Custom routing predicates can request the original unpruned count alternatives. */
    public List<List<Reach>> alternatives(Map<Integer, Double> entrances, int previousRelays, boolean prune) {
        return alternatives(entrances, previousRelays, previousRelays, prune);
    }
    public List<List<Reach>> alternatives(Map<Integer, Double> entrances, int previousRelays, int previousDecay, boolean prune) {
        if (previousRelays >= maxHops) return Collections.nCopies(nodeCount, List.of());
        int previous = Math.max(0, previousRelays);
        int decay = Math.max(0, Math.min(previous, previousDecay));
        List<List<Reach>> merged = emptyChoices();
        var entries = entrances.entrySet().stream().filter(entry -> entry.getKey() >= 0 && entry.getKey() < nodeCount
                && Double.isFinite(entry.getValue())).sorted(Map.Entry.comparingByValue()).toList();
        // Every entrance is already a viable first departure. Seed these before considering detours.
        if (prune) for (var entry : entries) merged.get(entry.getKey())
                .add(new Reach(entry.getValue(), previous + 1, resets[entry.getKey()] ? 1 : decay + 1));
        for (var entry : entries) {
            if (prune && (previous + 1 == maxHops || descendantsDominated(merged, entry.getValue(), previous + 2,
                    anyReset ? 1 : decay + 2))) continue;
            Routes cached = routes.get(new RouteKey(entry.getKey(), previous, decay, prune), this::calculate);
            for (int tower = 0; tower < nodeCount; tower++) for (Reach path : cached.choices().get(tower)) {
                double noise = Math.max(entry.getValue(), path.severity());
                offer(merged.get(tower), noise == path.severity() ? path : new Reach(noise, path.hops(), path.decay()), prune);
            }
        }
        return freeze(merged).choices();
    }

    private static boolean descendantsDominated(List<List<Reach>> choices, double ingressNoise, int firstDescendantCount, int minimumDecay) {
        // Descendants cannot be cleaner than ingress or use fewer than one additional visit.
        for (List<Reach> row : choices) {
            boolean covered = false;
            for (Reach path : row) if (path.severity() <= ingressNoise && path.hops() <= firstDescendantCount
                    && path.decay() <= minimumDecay) { covered = true; break; }
            if (!covered) return false;
        }
        return true;
    }

    private Routes calculate(RouteKey key) {
        List<List<Reach>> result = emptyChoices();
        record State(int tower, Reach path) {}
        PriorityQueue<State> pending = new PriorityQueue<>(Comparator.comparingInt((State s) -> s.path().hops())
                .thenComparingDouble(s -> s.path().severity()).thenComparingInt(State::tower).thenComparingInt(s -> s.path().decay()));
        Reach first = new Reach(0, key.previous() + 1, resets[key.entry()] ? 1 : key.previousDecay() + 1);
        result.get(key.entry()).add(first); pending.add(new State(key.entry(), first));
        while (!pending.isEmpty()) {
            State state = pending.remove(); Reach path = state.path();
            if (!result.get(state.tower()).contains(path) || path.hops() == maxHops) continue;
            for (Edge edge : outgoing(state.tower(), path.decay())) {
                Reach next = new Reach(Math.max(path.severity(), edge.severity()), path.hops() + 1,
                        resets[edge.destination()] ? 1 : path.decay() + 1);
                if (offer(result.get(edge.destination()), next, key.prune())) pending.add(new State(edge.destination(), next));
            }
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
        for (List<Reach> row : choices) row.sort(Comparator.comparingInt(Reach::hops).thenComparingInt(Reach::decay));
        return new Routes(choices.stream().map(List::copyOf).toList(), size + choices.size());
    }
    private static boolean offer(List<Reach> choices, Reach candidate, boolean prune) {
        for (Reach existing : choices)
            if ((prune ? existing.hops() <= candidate.hops() && existing.decay() <= candidate.decay()
                    : existing.hops() == candidate.hops() && existing.decay() == candidate.decay())
                    && existing.severity() <= candidate.severity()) return false;
        choices.removeIf(existing -> (prune ? candidate.hops() <= existing.hops() && candidate.decay() <= existing.decay()
                : candidate.hops() == existing.hops() && candidate.decay() == existing.decay())
                && candidate.severity() <= existing.severity());
        choices.add(candidate); return true;
    }

    /** Eviction costs recomputation only. Both entry count and numeric element count are bounded. */
    private static final class Cache<K, V> {
        private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, .75f, true);
        private final int maxEntries, maxWeight;
        private final String kind;
        private final ToIntFunction<V> weigh;
        private int weight;
        Cache(String kind, int maxEntries, int maxWeight, ToIntFunction<V> weigh) {
            this.kind = kind; this.maxEntries = maxEntries; this.maxWeight = maxWeight; this.weigh = weigh;
        }
        V get(K key, Function<K, V> create) {
            V cached = entries.get(key);
            observedCache(kind, cached == null ? "miss" : "hit");
            if (cached != null) return cached;
            V value = create.apply(key); int cost = weigh.applyAsInt(value);
            if (cost <= maxWeight) {
                entries.put(key, value); weight += cost;
                var iterator = entries.entrySet().iterator();
                while (entries.size() > maxEntries || weight > maxWeight) {
                    weight -= weigh.applyAsInt(iterator.next().getValue()); iterator.remove();
                    observedCache(kind, "eviction");
                }
            } else observedCache(kind, "not_retained_over_weight");
            return value;
        }
    }
}
