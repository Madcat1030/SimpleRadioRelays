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
    public static double loss(double clearance, double[][] curve) {
        if (clearance >= curve[0][0]) return curve[0][1];
        for (int i = 1; i < curve.length; i++) {
            double[] upper = curve[i - 1], lower = curve[i];
            if (clearance >= lower[0])
                return height(upper[1], lower[1], (upper[0] - clearance) / (upper[0] - lower[0]));
        }
        return curve[curve.length - 1][1];
    }

    public static double profileLoss(int[] ground, double fromY, double toY, double[][] curve) {
        if (ground.length < 2) return 0;
        double sum = 0;
        for (int i = 0; i < ground.length; i++) {
            double t = (double) i / (ground.length - 1);
            sum += (i == 0 || i == ground.length - 1 ? .5 : 1)
                    * loss(height(fromY, toY, t) - ground[i], curve);
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
