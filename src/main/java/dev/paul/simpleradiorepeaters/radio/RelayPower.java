package dev.paul.simpleradiorepeaters.radio;

/** The catalyst sets the first-relay ceiling; each subsequent relay retains 90% of that ceiling. */
public final class RelayPower {
    public static final double HOP_RETENTION = .9;
    public static final double AMPLIFIER_ANTENNA_MULTIPLIER = 1.5;
    private RelayPower() {}
    public static double antennaScore(int score, boolean amplified) {
        return score * (amplified ? AMPLIFIER_ANTENNA_MULTIPLIER : 1);
    }
    public static float departure(double maximum, double efficiency, int relayCount) {
        if (!Double.isFinite(maximum) || maximum <= 0 || !Double.isFinite(efficiency) || efficiency < 0 || relayCount < 1) return 0;
        double power = maximum * (efficiency * Math.pow(HOP_RETENTION, relayCount - 1));
        return (float) Math.min(power, Float.MAX_VALUE);
    }
}
