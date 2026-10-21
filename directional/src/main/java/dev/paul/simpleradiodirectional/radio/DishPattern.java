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
        if (angle <= 67.5) return .5;
        if (angle >= 90) return 0;
        double t = (angle - 67.5) / 22.5;
        return .5 * (1 - t * t * (3 - 2 * t));
    }
    public static DishPattern parse(String id) {
        for (var pattern : values()) if (pattern.id.equals(id)) return pattern;
        return null;
    }
}
