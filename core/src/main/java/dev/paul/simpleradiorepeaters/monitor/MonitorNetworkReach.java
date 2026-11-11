package dev.paul.simpleradiorepeaters.monitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

/** Bounded return and outbound searches. Node zero is the monitor's virtual endpoint. */
final class MonitorNetworkReach {
    static final int MAX_HOPS = 12;

    private MonitorNetworkReach() {}

    static List<MonitorReach.Node> solve(int size, int maxVisits, MonitorReach.Links links, IntPredicate resets) {
        int budget = Math.min(MAX_HOPS, maxVisits);
        int[] inbound = returnAllowance(size, budget, links, resets);
        int[] via = new int[size], decay = new int[size];
        Arrays.fill(via, -1);
        Arrays.fill(decay, Integer.MAX_VALUE);
        decay[0] = 1;

        List<Integer> frontier = List.of(0);
        for (int hop = 0; hop < budget && !frontier.isEmpty(); hop++) {
            int[] next = decay.clone();
            List<Integer> improved = new ArrayList<>();
            for (int from : frontier) {
                for (int to = 1; to < size; to++) {
                    if (to == from) continue;
                    int arrival = from == 0 || resets.test(to) ? 1 : decay[from] + 1;
                    if (via[to] >= 0 && (inbound[to] == 0 || next[to] <= arrival)) continue;
                    if (links.receive(from, to, decay[from]) == null) continue;
                    if (via[to] < 0) via[to] = from;
                    // Plot an unheard tower at the edge of a branch, but do not survey onward from it.
                    if (inbound[to] == 0 || next[to] <= arrival) continue;
                    if (next[to] == decay[to]) improved.add(to);
                    next[to] = arrival;
                }
            }
            decay = next;
            frontier = improved;
        }
        List<MonitorReach.Node> visible = new ArrayList<>();
        for (int tower = 1; tower < size; tower++)
            if (inbound[tower] > 0 || via[tower] >= 0)
                visible.add(new MonitorReach.Node(tower, via[tower], via[tower] >= 0, inbound[tower] > 0));
        return visible;
    }

    private static int[] returnAllowance(int size, int budget, MonitorReach.Links links, IntPredicate resets) {
        int[] allowed = new int[size];
        List<Integer> frontier = List.of(0);
        for (int hop = 0; hop < budget && !frontier.isEmpty(); hop++) {
            int[] next = allowed.clone();
            List<Integer> improved = new ArrayList<>();
            for (int to : frontier) {
                // Entering an ordinary relay adds one decay; an Ender reset always enters at one.
                int ceiling = to == 0 || resets.test(to) ? budget : allowed[to] - 1;
                for (int from = 1; from < size; from++) {
                    if (from == to || next[from] >= ceiling) continue;
                    int found = strongestAllowance(links, from, to, next[from], ceiling);
                    if (found <= next[from]) continue;
                    if (next[from] == allowed[from]) improved.add(from);
                    next[from] = found;
                }
            }
            allowed = next;
            frontier = improved;
        }
        return allowed;
    }

    private static int strongestAllowance(MonitorReach.Links links, int from, int to, int known, int ceiling) {
        // Each additional decay step can only lower departure power. Try the ceiling first,
        // then search only if this leg can improve on the route we already know.
        if (links.receive(from, to, ceiling) != null) return ceiling;
        int low = known + 1, high = ceiling - 1;
        if (low > high || links.receive(from, to, low) == null) return known;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (links.receive(from, to, middle) == null) high = middle - 1;
            else low = middle;
        }
        return low;
    }
}
