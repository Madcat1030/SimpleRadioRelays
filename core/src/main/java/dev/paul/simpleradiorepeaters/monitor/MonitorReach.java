package dev.paul.simpleradiorepeaters.monitor;

import dev.paul.simpleradiorepeaters.radio.RadioLinkMath;
import dev.paul.simpleradiorepeaters.radio.RelayDeliverySelection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A direct layer and one relay beyond it. Node zero is Home; other nodes are ordered by distance. */
final class MonitorReach {
    @FunctionalInterface interface Links { RadioLinkMath.Result receive(int from, int to, int decay); }
    record Node(int tower, int parent, boolean tx, boolean rx) {}

    private MonitorReach() {}

    static List<Node> solve(int size, int maxVisits, Links links) {
        int[] rx = new int[size], tx = new int[size];
        Arrays.fill(rx, -1);
        Arrays.fill(tx, -1);
        var directTx = new RadioLinkMath.Result[size];
        var returns = new RadioLinkMath.Result[size];
        var direct = new boolean[size];
        var first = new ArrayList<Integer>();
        for (int tower = 1; tower < size; tower++) {
            directTx[tower] = links.receive(0, tower, 1);
            var directRx = links.receive(tower, 0, 1);
            direct[tower] = directTx[tower] != null || directRx != null;
            if (!direct[tower] || first.size() == MonitorMap.BRANCHES) continue;
            first.add(tower);
            if (directTx[tower] != null) tx[tower] = 0;
            if (directRx != null) {
                rx[tower] = 0;
                if (maxVisits > 1) returns[tower] = links.receive(tower, 0, 2);
            }
        }
        if (maxVisits > 1) {
            // Only a direct return leg can finish a two-visit inbound path. That leg leaves
            // the second relay at decay two (or one when it resets decay).
            for (int tower = 1; tower < size; tower++) {
                if (rx[tower] >= 0 || direct[tower] && !first.contains(tower)) continue;
                var candidates = new ArrayList<RelayDeliverySelection.Candidate<Integer>>();
                for (int via : first) if (via != tower && returns[via] != null)
                    offer(candidates, via, links.receive(tower, via, 1), returns[via]);
                rx[tower] = best(candidates);
            }
            for (int tower = 1; tower < size; tower++) {
                if (tx[tower] >= 0 || direct[tower] && !first.contains(tower)) continue;
                var candidates = new ArrayList<RelayDeliverySelection.Candidate<Integer>>();
                for (int via : first) if (via != tower && rx[via] >= 0 && directTx[via] != null)
                    offer(candidates, via, directTx[via], links.receive(via, tower, 1));
                tx[tower] = best(candidates);
            }
        }
        List<Node> visible = new ArrayList<>();
        for (int tower : first) visible.add(new Node(tower, 0, tx[tower] >= 0, rx[tower] >= 0));
        for (int tower = 1; tower < size; tower++) {
            if (direct[tower] || (rx[tower] < 0 && tx[tower] < 0)) continue;
            // Prefer the best outward route for the label; a heard-only tower uses its return route.
            visible.add(new Node(tower, tx[tower] >= 0 ? tx[tower] : rx[tower], tx[tower] >= 0, rx[tower] >= 0));
        }
        return visible;
    }

    private static void offer(List<RelayDeliverySelection.Candidate<Integer>> candidates, int via,
                              RadioLinkMath.Result first, RadioLinkMath.Result last) {
        if (first != null && last != null)
            candidates.add(new RelayDeliverySelection.Candidate<>(via, Math.max(first.severity(), last.severity()), last.power(), 2));
    }

    private static int best(List<RelayDeliverySelection.Candidate<Integer>> candidates) {
        var chosen = RelayDeliverySelection.select(candidates);
        return chosen == null ? -1 : chosen.source();
    }
}
