package dev.paul.simpleradiorepeaters.monitor;

import java.util.Arrays;
import java.util.List;

/** Reachability only: accumulated noise affects quality, but never improves a later radio leg. */
final class MonitorReach {
    /** A leg works at every decay count from one through lastDecay. Node zero is the monitor. */
    record Link(int to, int lastDecay) {}
    record Result(boolean[] audible, int[] via) {}

    private MonitorReach() {}

    static Result solve(List<List<Link>> links, boolean[] resets, int maxVisits) {
        int size = links.size();
        // For each tower, find the most decay a return path can tolerate within the visit budget.
        int[] allowed = new int[size];
        for (int visits = 1; visits <= maxVisits; visits++) {
            int[] next = allowed.clone();
            for (int from = 1; from < size; from++) for (Link link : links.get(from)) {
                int to = link.to();
                int limit = to == 0 || resets[to] && allowed[to] > 0 ? maxVisits : allowed[to] - 1;
                next[from] = Math.max(next[from], Math.min(link.lastDecay(), limit));
            }
            if (Arrays.equals(next, allowed)) break;
            allowed = next;
        }
        boolean[] audible = new boolean[size];
        for (int i = 1; i < size; i++) audible[i] = allowed[i] > 0;

        int[] via = new int[size];
        int[] decay = new int[size];
        Arrays.fill(via, -1);
        Arrays.fill(decay, Integer.MAX_VALUE);
        // Home is a virtual transmitter. The first real relay enters at visit and decay one.
        for (Link link : links.get(0)) {
            via[link.to()] = 0;
            if (audible[link.to()]) decay[link.to()] = 1;
        }
        for (int visits = 1; visits < maxVisits; visits++) {
            int[] next = decay.clone();
            for (int from = 1; from < size; from++) {
                if (decay[from] == Integer.MAX_VALUE) continue;
                for (Link link : links.get(from)) {
                    int to = link.to();
                    if (to == 0 || decay[from] > link.lastDecay()) continue;
                    if (via[to] < 0) via[to] = from;
                    // Unheard towers are terminal markers. Only audible ones can extend the search.
                    if (audible[to]) next[to] = Math.min(next[to], resets[to] ? 1 : decay[from] + 1);
                }
            }
            if (Arrays.equals(next, decay)) break;
            decay = next;
        }
        return new Result(audible, via);
    }
}
