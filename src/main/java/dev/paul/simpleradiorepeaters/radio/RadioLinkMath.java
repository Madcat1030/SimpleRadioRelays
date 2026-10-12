package dev.paul.simpleradiorepeaters.radio;

/** Numeric equivalent of Simple Radio 4.5.7.9's wireless eligibility and attenuation. */
public final class RadioLinkMath {
    public enum Loss { ADDITIVE, MULTIPLICATIVE }
    public record Result(double power, double severity) {}
    private RadioLinkMath() {}

    public static Result receive(double distance, double power, double cap, double reception,
                                 double floor, double diminishment, Loss method,
                                 double threshold, double baseInterference) {
        if (!Double.isFinite(distance) || distance < 0 || power <= 0
                || power + reception < distance * diminishment) return null;
        double effective = distance > floor ? Math.max(floor, distance - reception) : distance;
        double remaining = Math.max(0, power - (method == Loss.ADDITIVE
                ? effective * diminishment : cap * diminishment));
        if (remaining <= 0) return null;
        double fraction = Math.max(0, Math.min(1, remaining / threshold));
        double severity = Math.max(0, Math.min(100, baseInterference + (1 - fraction) * (100 - baseInterference)));
        return severity >= 100 ? null : new Result(remaining, severity);
    }
}
