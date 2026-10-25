package dev.paul.simpleradiorepeaters.terrain;

/** Pure clearance math. Loss is a fraction of path length, independent of sample density. */
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
    public static double loss(double clearance, double band, double near, double buried) {
        if (clearance >= band) return 0;
        if (clearance <= -band) return buried;
        return clearance >= 0 ? near * smooth(1 - clearance / band)
                : near + (buried - near) * smooth(-clearance / band);
    }
    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    public static double profileLoss(int[] ground, double fromY, double toY, double band, double near, double buried) {
        if (ground.length < 2) return 0;
        double sum = 0;
        for (int i = 0; i < ground.length; i++) {
            double t = (double) i / (ground.length - 1);
            sum += (i == 0 || i == ground.length - 1 ? .5 : 1)
                    * loss(height(fromY, toY, t) - ground[i], band, near, buried);
        }
        return sum / (ground.length - 1);
    }
    public static boolean moved(double distanceSquared) {
        return distanceSquared > MOVEMENT_THRESHOLD * MOVEMENT_THRESHOLD;
    }
    public static boolean free(double distanceSquared) {
        return distanceSquared <= DEVICE_FREE_RADIUS * DEVICE_FREE_RADIUS;
    }
    public static double elevationMultiplier(double tipY, int referenceY, int fullBonusY, double maximum) {
        return 1 + maximum * Math.max(0, Math.min(1, (tipY - referenceY) / (fullBonusY - referenceY)));
    }
}
