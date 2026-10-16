package dev.paul.simpleradiorepeaters.radio;

/** RF regeneration at the current relay; count includes the first relay and survives wired re-entry. */
public final class RelayPower {
    private RelayPower() {}
    public static float departure(double maximum, double efficiency, int relayCount) {
        if (!Double.isFinite(maximum) || maximum <= 0 || !Double.isFinite(efficiency) || efficiency < 0 || relayCount < 1) return 0;
        double power = maximum * Math.pow(efficiency, relayCount);
        return Double.isFinite(power) && power > 0 ? (float) Math.min(power, Float.MAX_VALUE) : 0;
    }
}
