package dev.paul.simpleradiodirectional.radio;

/** Seven clockwise offsets from the fixed mast attachment; index 3 points straight out. */
public final class DishAim {
    public static final int MIN = 0, MAX = 6, STRAIGHT = 3, STEP_DEGREES = 15;
    private static final double[] SIN = new double[MAX + 1], COS = new double[MAX + 1];
    static {
        for (int aim = MIN; aim <= MAX; aim++) {
            double radians = Math.toRadians(degrees(aim));
            SIN[aim] = Math.sin(radians); COS[aim] = Math.cos(radians);
        }
    }
    private DishAim() {}
    public static boolean valid(int aim) { return aim >= MIN && aim <= MAX; }
    public static int degrees(int aim) { return (aim - STRAIGHT) * STEP_DEGREES; }
    public static int adjust(int aim, boolean counterclockwise) {
        return Math.max(MIN, Math.min(MAX, aim + (counterclockwise ? -1 : 1)));
    }
    public static int mirror(int aim) { return MAX - aim; }
    public static double x(double x, double z, int aim) {
        return x * COS[aim] - z * SIN[aim];
    }
    public static double z(double x, double z, int aim) {
        return x * SIN[aim] + z * COS[aim];
    }
}
