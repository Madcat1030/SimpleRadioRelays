package dev.paul.simpleradiorepeaters.monitor;

/** Minecraft bearings: +X east, +Z south. TX-only estimates reveal no exact position. */
public final class MonitorMath {
    public record Point(double x, double z) {}
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

    /** Use only the displayed sector and distance bucket, converted to destination-world blocks. */
    public static Point compassEstimate(double originX, double originZ, String bearing, double distance, double radioUnitsPerBlock) {
        if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(radioUnitsPerBlock) || radioUnitsPerBlock <= 0)
            return new Point(originX, originZ);
        for (int i = 0; i < BEARINGS.length; i++) if (BEARINGS[i].equals(bearing)) {
            double angle = Math.toRadians(i * 22.5);
            double radius = approximateDistance(distance) / radioUnitsPerBlock;
            return new Point(originX + Math.sin(angle) * radius, originZ - Math.cos(angle) * radius);
        }
        return new Point(originX, originZ); // LOCAL has no horizontal direction.
    }
}
