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
        // Both patterns use the same maximum-bonus scale: 75% through 45 degrees,
        // 50% at 60, then zero at 75, with continuous shoulders between anchors.
        if (angle <= 45) return .75;
        if (angle >= 75) return 0;
        if (angle <= 60) return .5 + .25 * DirectionalMath.smoothFade((angle - 45) / 15);
        return .5 * DirectionalMath.smoothFade((angle - 60) / 15);
    }
    public static DishPattern parse(String id) {
        for (var pattern : values()) if (pattern.id.equals(id)) return pattern;
        return null;
    }
}
