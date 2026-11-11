package dev.paul.simpleradiorepeaters.monitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Display cells only: the client does not need tower IDs, world coordinates, or paths. */
public record MonitorMap(List<Marker> markers, boolean pending) {
    public enum Kind { TX_RX, RX_ONLY, ESTIMATE }
    public record Marker(int cell, Kind kind, String label) {}
    record Site(double east, double south, Kind kind) {}
    private static final Kind[] KINDS = Kind.values();
    private static final int HALF_WIDTH = 5, HALF_HEIGHT = 3;

    public MonitorMap { markers = List.copyOf(markers); }

    static MonitorMap plot(List<Site> sites, boolean pending) {
        int density = density(sites.size()), width = width(density), height = height(density);
        boolean[] used = new boolean[width * height];
        used[used.length / 2] = true; // Keep zero fixed, even for a tower with the same X/Z.
        double extent = sites.stream().mapToDouble(s -> Math.max(Math.abs(s.east()) / HALF_WIDTH,
                Math.abs(s.south()) / HALF_HEIGHT)).max().orElse(1);
        double scale = extent > 0 ? density / extent : 0;
        // One scale for both axes. Move a label only to the nearest available display cell.
        var ordered = sites.stream().sorted(Comparator.comparing((Site s) -> s.kind() == Kind.ESTIMATE)
                .thenComparingDouble(s -> Math.hypot(s.east(), s.south()))).toList();
        int[] cells = new int[sites.size()];
        for (int i = 0; i < ordered.size(); i++) {
            Site site = ordered.get(i);
            double x = width / 2 + site.east() * scale, z = height / 2 + site.south() * scale;
            int best = -1;
            double distance = Double.POSITIVE_INFINITY;
            for (int cell = 0; cell < used.length; cell++) {
                if (used[cell]) continue;
                double dx = x - cell % width, dz = z - cell / width;
                double candidate = dx * dx + dz * dz;
                if (candidate < distance) { best = cell; distance = candidate; }
            }
            used[best] = true;
            cells[i] = best * KINDS.length + site.kind().ordinal();
        }
        return fromCells(cells, pending);
    }

    public static MonitorMap fromCells(int[] cells, boolean pending) {
        int density = density(cells.length), size = width(density) * height(density), number = 1;
        boolean[] used = new boolean[size];
        used[size / 2] = true;
        List<Marker> markers = new ArrayList<>(cells.length);
        for (int code : cells) {
            int cell = code / KINDS.length;
            if (code < 0 || cell >= size || used[cell]) return new MonitorMap(List.of(), pending);
            used[cell] = true;
            Kind kind = KINDS[code % KINDS.length];
            markers.add(new Marker(cell, kind, kind == Kind.ESTIMATE ? "?" : Integer.toString(number++)));
        }
        return new MonitorMap(markers, pending);
    }

    public int[] cells() {
        return markers.stream().mapToInt(m -> m.cell() * KINDS.length + m.kind().ordinal()).toArray();
    }

    public int density() { return density(markers.size()); }
    public static double east(int cell, int density) {
        return (cell % width(density) - HALF_WIDTH * density) / (double) density;
    }
    public static double south(int cell, int density) {
        return (cell / width(density) - HALF_HEIGHT * density) / (double) density;
    }

    private static int density(int count) {
        int density = 1;
        while ((long) width(density) * height(density) <= count) density++;
        return density;
    }
    private static int width(int density) { return 2 * HALF_WIDTH * density + 1; }
    private static int height(int density) { return 2 * HALF_HEIGHT * density + 1; }
}
