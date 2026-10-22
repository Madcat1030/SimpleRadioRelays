package dev.paul.simpleradiodirectional.radio;

/** Horizontal azimuth pattern; no game state or terrain access. */
public final class DirectionalMath {
    public static final double FULL_HALF_ANGLE = 30;
    public static final double FADE_HALF_ANGLE = 60;
    public static final double RANGE_FRACTION = 0.75;
    private DirectionalMath() {}

    public static double angularWeight(double degrees) {
        if (!Double.isFinite(degrees)) return 0;
        double angle = Math.abs(degrees);
        if (angle <= FULL_HALF_ANGLE) return 1;
        if (angle >= FADE_HALF_ANGLE) return 0;
        double t = (angle - FULL_HALF_ANGLE) / (FADE_HALF_ANGLE - FULL_HALF_ANGLE);
        return smoothFade(t);
    }

    /** Smooth interpolation from one to zero; callers bound t to [0, 1]. */
    static double smoothFade(double t) { return 1 - t * t * (3 - 2 * t); }

    public static double weight(double dx, double dz, double facingX, double facingZ) {
        return weight(dx, dz, facingX, facingZ, DishPattern.PARABOLIC);
    }
    public static double weight(double dx, double dz, double facingX, double facingZ, DishPattern pattern) {
        double length = Math.hypot(dx, dz);
        if (!Double.isFinite(length) || length < 1.0e-9) return 0;
        double dot = Math.max(-1, Math.min(1, (dx * facingX + dz * facingZ) / length));
        return pattern.angularWeight(Math.toDegrees(Math.acos(dot)));
    }

    /** Gain is a distance allowance, matching native receiver power. Keep ordinary reception intact. */
    public static double bonus(double reception, double towerPower, double towerLoss, double weight) {
        if (!Double.isFinite(reception) || !Double.isFinite(towerPower) || !Double.isFinite(towerLoss)
                || !Double.isFinite(weight) || towerPower <= 0 || towerLoss <= 0 || weight <= 0) return 0;
        double desired = RANGE_FRACTION * towerPower / towerLoss;
        return Math.min(10_000_000, Math.max(0, desired - reception)) * Math.min(1, weight);
    }
}
