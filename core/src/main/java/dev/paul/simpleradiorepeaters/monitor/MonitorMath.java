package dev.paul.simpleradiorepeaters.monitor;

/** Minecraft bearings: +X east, +Z south. TX-only estimates reveal no exact position. */
public final class MonitorMath {
    private static final String[] BEARINGS = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
    private MonitorMath() {}
    public static String bearing(double dx, double dz) {
        if (Math.hypot(dx, dz) < .001) return "LOCAL";
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        return BEARINGS[Math.floorMod((int) Math.floor((angle + 11.25) / 22.5), 16)];
    }
    public static long approximateDistance(double distance) {
        long step = distance < 500 ? 50 : 500;
        return Math.max(step, Math.round(distance / step) * step);
    }
}
