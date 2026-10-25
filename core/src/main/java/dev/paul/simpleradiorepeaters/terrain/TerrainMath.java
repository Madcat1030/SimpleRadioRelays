package dev.paul.simpleradiorepeaters.terrain;

/** Extra-distance fractions are averaged along the path, independently of sample density. */
public final class TerrainMath {
    public static final double DEVICE_FREE_RADIUS = 256;
    public static final double MOVEMENT_THRESHOLD = 16;
    private TerrainMath() {}

    public static int sampleCount(double horizontalDistance, int spacing, int limit) {
        return (int) Math.max(2, Math.min(limit, Math.ceil(horizontalDistance / spacing) + 1));
    }
    public static double height(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }
    public static double loss(double clearance, double nearHeight, double near, double[] underground) {
        if (clearance >= nearHeight) return 0;
        if (clearance >= 0) return near * (nearHeight - clearance) / nearHeight;
        double depth = -clearance, start = 0, previous = near;
        for (int i = 0; i < underground.length; i++) {
            double end = i < 4 ? 8 * (1 << i) : 64 + 32 * (i - 3);
            if (depth <= end) return height(previous, underground[i], (depth - start) / (end - start));
            start = end; previous = underground[i];
        }
        return previous;
    }

    public static double profileLoss(int[] ground, double fromY, double toY, double nearHeight, double near, double[] underground) {
        if (ground.length < 2) return 0;
        double sum = 0;
        for (int i = 0; i < ground.length; i++) {
            double t = (double) i / (ground.length - 1);
            sum += (i == 0 || i == ground.length - 1 ? .5 : 1)
                    * loss(height(fromY, toY, t) - ground[i], nearHeight, near, underground);
        }
        return sum / (ground.length - 1);
    }
    public static double extraDistance(double distance, double extraFraction) {
        return Double.isFinite(extraFraction) ? distance * Math.max(0, extraFraction) : Double.POSITIVE_INFINITY;
    }
    public static boolean moved(double distanceSquared) {
        return distanceSquared > MOVEMENT_THRESHOLD * MOVEMENT_THRESHOLD;
    }
    public static boolean free(double distanceSquared) {
        return distanceSquared <= DEVICE_FREE_RADIUS * DEVICE_FREE_RADIUS;
    }
    /** Yield progressively as recent server ticks approach their 50 ms allowance. */
    public static long samplingBudgetNanos(double milliseconds, double averageTickMs) {
        double fraction = Math.max(.1, Math.min(1, (50 - averageTickMs) / 10));
        return (long) (milliseconds * fraction * 1_000_000);
    }
    public static double elevationMultiplier(double tipY, int referenceY, int fullBonusY, double maximum) {
        return 1 + maximum * Math.max(0, Math.min(1, (tipY - referenceY) / (fullBonusY - referenceY)));
    }
}
