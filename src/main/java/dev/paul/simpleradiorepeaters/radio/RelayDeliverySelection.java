package dev.paul.simpleradiorepeaters.radio;

import java.util.List;

/** Bounded audio-quality tradeoff for final delivery; never alters the selected signal. */
public final class RelayDeliverySelection {
    public static final double QUALITY_TOLERANCE = 1.0;
    public record Candidate<T>(T source, double severity, double power, int hops) {}
    private RelayDeliverySelection() {}

    public static <T> Candidate<T> select(List<Candidate<T>> candidates) {
        double cleanest = candidates.stream().mapToDouble(Candidate::severity).min().orElse(Double.POSITIVE_INFINITY);
        Candidate<T> best = null;
        for (Candidate<T> candidate : candidates) {
            // Anchor the band to the global minimum, not the current winner: pairwise
            // near-ties can otherwise drift beyond the tolerance as towers are visited.
            if (candidate.severity() > cleanest + QUALITY_TOLERANCE) continue;
            if (best == null || candidate.power() > best.power()
                    || (candidate.power() == best.power() && (candidate.severity() < best.severity()
                    || (candidate.severity() == best.severity() && candidate.hops() < best.hops())))) best = candidate;
        }
        return best;
    }
}
