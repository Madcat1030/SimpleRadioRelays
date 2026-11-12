package dev.paul.simpleradiorepeaters.monitor;

/** Shared monitor layout in north-facing model pixels. */
public final class MonitorScreen {
    public static final double FOOTER_Y = 6.47;
    public static final double BACK_X = 4;
    public static final double HOME_X = 12;
    public static final double HOME_Y = 13.68;
    public static final int DIRECT_ROWS = 6;
    private static final double FIRST_LINE_Y = 13.3, LINE_SPACING = .86;

    private MonitorScreen() {}

    public static boolean contains(double x, double y) { return hit(x, y, 2, 14, 6, 14); }
    public static boolean footer(double x, double y) { return hit(x, y, 2, 14, 6, 7); }
    public static boolean back(double x, double y) { return hit(x, y, 2, 6, 6, 7); }
    public static boolean home(double x, double y) { return hit(x, y, 10, 14, 13.24, 14); }

    public static double lineY(int line) { return FIRST_LINE_Y - line * LINE_SPACING; }

    public static int directRow(double x, double y) {
        if (x * 16 < 2 || x * 16 > 14) return -1;
        int line = (int) Math.floor((FIRST_LINE_Y - y * 16) / LINE_SPACING + .5);
        return line >= 1 && line <= DIRECT_ROWS ? line - 1 : -1;
    }

    private static boolean hit(double x, double y, double left, double right, double bottom, double top) {
        return x * 16 >= left && x * 16 <= right && y * 16 >= bottom && y * 16 <= top;
    }
}
