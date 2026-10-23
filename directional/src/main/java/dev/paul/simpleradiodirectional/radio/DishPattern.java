package dev.paul.simpleradiodirectional.radio;

/** Reception patterns share mounting, aiming and the strongest-bonus selection. */
public enum DishPattern {
    PARABOLIC("parabolic_receiver"),
    WIDE("wide_angle_receiver");

    private final String id;
    DishPattern(String id) { this.id = id; }
    public String id() { return id; }
    public double angularWeight(double degrees) {
        if (this == PARABOLIC) return DirectionalMath.angularWeight(degrees);
        if (!Double.isFinite(degrees)) return 0;
        double angle = Math.abs(degrees);
        // Relative to the dish's maximum: 66% through 45 degrees, 44% at 60,
        // 22% at 75 and zero at 90, with smooth shoulders between anchors.
        if (angle <= 45) return .66;
        if (angle >= 90) return 0;
        if (angle <= 60) return .44 + .22 * DirectionalMath.smoothFade((angle - 45) / 15);
        if (angle <= 75) return .22 + .22 * DirectionalMath.smoothFade((angle - 60) / 15);
        return .22 * DirectionalMath.smoothFade((angle - 75) / 15);
    }
    public static DishPattern parse(String id) {
        for (var pattern : values()) if (pattern.id.equals(id)) return pattern;
        return null;
    }
}
