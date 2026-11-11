package dev.paul.simpleradiorepeaters.monitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Display cells and labels only: the client does not need tower IDs, world coordinates, or paths. */
public record MonitorMap(List<Marker> markers, boolean pending) {
    public enum Kind { TX_RX, RX_ONLY, ESTIMATE }
    public record Marker(int cell, Kind kind, int address, String label) {}
    record Site(double east, double south, Kind kind, int address) {}
    static final int BRANCHES = 14, CHILDREN = 9;
    private static final Kind[] KINDS = Kind.values();
    private static final int HALF_WIDTH = 5, HALF_HEIGHT = 3;
    private static final int LABELS = BRANCHES * (CHILDREN + 1);
    private static final int CODES_PER_CELL = LABELS * KINDS.length;

    public MonitorMap { markers = List.copyOf(markers); }

    static MonitorMap plot(List<Site> sites, boolean pending) {
        int density = density(sites.size()), side = side(density);
        boolean[] used = new boolean[side * side];
        used[used.length / 2] = true; // Reserve the origin even for a tower with the same X/Z.
        double extent = sites.stream().mapToDouble(s -> Math.max(Math.abs(s.east()) / HALF_WIDTH,
                Math.abs(s.south()) / HALF_HEIGHT)).max().orElse(1);
        double scale = extent > 0 ? 1 / extent : 0;
        // One scale for both axes. Move a label only to the nearest available display cell.
        var ordered = sites.stream().sorted(Comparator.comparing((Site s) -> s.kind() == Kind.ESTIMATE)
                .thenComparingDouble(s -> Math.hypot(s.east(), s.south()))).toList();
        int[] cells = new int[sites.size()];
        for (int i = 0; i < ordered.size(); i++) {
            Site site = ordered.get(i);
            double x = site.east() * scale, z = site.south() * scale;
            int best = -1;
            double distance = Double.POSITIVE_INFINITY;
            for (int cell = 0; cell < used.length; cell++) {
                if (used[cell]) continue;
                double dx = x - east(cell, density), dz = z - south(cell, density);
                double candidate = dx * dx + dz * dz;
                if (candidate < distance) { best = cell; distance = candidate; }
            }
            used[best] = true;
            cells[i] = encode(best, site.kind(), site.address());
        }
        return fromCells(cells, pending);
    }

    public static MonitorMap fromCells(int[] cells, boolean pending) {
        int side = side(density(cells.length)), size = side * side;
        boolean[] used = new boolean[size];
        used[size / 2] = true;
        List<Marker> markers = new ArrayList<>(cells.length);
        for (int code : cells) {
            int cell = code / CODES_PER_CELL;
            if (code < 0 || cell >= size || used[cell]) return new MonitorMap(List.of(), pending);
            used[cell] = true;
            Kind kind = KINDS[code % KINDS.length];
            int address = code / KINDS.length % LABELS;
            markers.add(new Marker(cell, kind, address, kind == Kind.ESTIMATE ? "?" : label(address)));
        }
        return new MonitorMap(markers, pending);
    }

    public int[] cells() {
        return markers.stream().mapToInt(m -> encode(m.cell(), m.kind(), m.address())).toArray();
    }

    private static int encode(int cell, Kind kind, int address) {
        return cell * CODES_PER_CELL + (kind == Kind.ESTIMATE ? 0 : address) * KINDS.length + kind.ordinal();
    }
    static int address(int branch, int child) { return branch * (CHILDREN + 1) + child; }
    private static String label(int address) {
        char branch = (char) ('a' + address / (CHILDREN + 1));
        int child = address % (CHILDREN + 1);
        return child == 0 ? "O-" + branch : branch + "-" + child;
    }

    public int density() { return density(markers.size()); }
    public static double east(int cell, int density) {
        return (cell % side(density) - 3 * density) * columnSpacing(density);
    }
    public static double south(int cell, int density) {
        return (cell / side(density) - 3 * density) * rowSpacing(density);
    }
    public static double columnSpacing(int density) { return HALF_WIDTH / (3.0 * density); }
    public static double rowSpacing(int density) { return HALF_HEIGHT / (3.0 * density); }

    private static int density(int count) {
        int density = 1;
        while ((long) side(density) * side(density) <= count) density++;
        return density;
    }
    private static int side(int density) { return 6 * density + 1; }
}
