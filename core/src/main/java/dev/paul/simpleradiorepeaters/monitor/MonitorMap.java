package dev.paul.simpleradiorepeaters.monitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Display cells, labels and links; tower IDs and world coordinates stay on the server. */
public record MonitorMap(List<Marker> markers, List<Link> links, boolean pending, boolean wide) {
    public enum Kind { TX_RX, RX_ONLY, ESTIMATE }
    public record Marker(int cell, Kind kind, int address, String label) {}
    public record Link(int fromCell, int toCell, boolean estimated) {}
    record Site(double east, double south, Kind kind, int address, int tower) {
        Site(double east, double south, Kind kind, int address) { this(east, south, kind, address, -1); }
    }
    static final int BRANCHES = 14, CHILDREN = 9;
    private static final Kind[] KINDS = Kind.values();
    private static final int HALF_WIDTH = 5, HALF_HEIGHT = 3;
    private static final int LABELS = BRANCHES * (CHILDREN + 1);
    private static final int CODES_PER_CELL = LABELS * KINDS.length;

    public MonitorMap {
        markers = List.copyOf(markers);
        links = List.copyOf(links);
    }

    static MonitorMap plot(List<Site> sites, boolean pending) {
        return plot(sites, pending, false);
    }

    static MonitorMap plot(List<Site> sites, boolean pending, boolean wide) {
        return plot(sites, pending, wide, List.of());
    }

    static MonitorMap plot(List<Site> sites, boolean pending, boolean wide,
                           List<MonitorNetworkReach.Connection> connections) {
        int density = density(sites.size(), wide), side = side(density);
        boolean[] used = new boolean[side * side];
        int origin = used.length / 2;
        used[origin] = true; // Reserve the origin even for a tower with the same X/Z.
        Map<Integer, Integer> towerCells = new HashMap<>();
        towerCells.put(0, origin);
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
            if (wide && site.tower() > 0) towerCells.put(site.tower(), best);
            cells[i] = encode(best, site.kind(), site.address());
        }
        int[] edges = new int[connections.size() * 2];
        for (int i = 0; i < connections.size(); i++) {
            var connection = connections.get(i);
            edges[i * 2] = towerCells.getOrDefault(connection.from(), -1);
            edges[i * 2 + 1] = towerCells.getOrDefault(connection.to(), -1);
        }
        return fromCells(cells, edges, pending, wide);
    }

    public static MonitorMap fromCells(int[] cells, boolean pending, boolean wide) {
        return fromCells(cells, new int[0], pending, wide);
    }

    public static MonitorMap fromCells(int[] cells, int[] edges, boolean pending, boolean wide) {
        int side = side(density(cells.length, wide)), size = side * side;
        Kind[] kinds = new Kind[size];
        int origin = size / 2;
        kinds[origin] = Kind.TX_RX;
        List<Marker> markers = new ArrayList<>(cells.length);
        for (int code : cells) {
            int cell = code / CODES_PER_CELL;
            if (code < 0 || cell >= size || kinds[cell] != null) return new MonitorMap(List.of(), List.of(), pending, wide);
            Kind kind = KINDS[code % KINDS.length];
            int address = code / KINDS.length % LABELS;
            if (kind == Kind.ESTIMATE && address > BRANCHES)
                return new MonitorMap(List.of(), List.of(), pending, wide);
            kinds[cell] = kind;
            markers.add(new Marker(cell, kind, address, wide ? "o" : label(kind, address)));
        }
        var links = new LinkedHashSet<Link>();
        if (wide) for (int i = 1; i < edges.length; i += 2) {
            int from = Math.min(edges[i - 1], edges[i]), to = Math.max(edges[i - 1], edges[i]);
            if (from < 0 || from == to || to >= size || kinds[from] == null || kinds[to] == null) continue;
            if (kinds[from] == Kind.ESTIMATE && kinds[to] == Kind.ESTIMATE) continue;
            links.add(new Link(from, to, kinds[from] == Kind.ESTIMATE || kinds[to] == Kind.ESTIMATE));
        }
        return new MonitorMap(markers, new ArrayList<>(links), pending, wide);
    }

    public int[] cells() {
        return markers.stream().mapToInt(m -> encode(m.cell(), m.kind(), m.address())).toArray();
    }

    public int[] linkCells() {
        int[] edges = new int[links.size() * 2];
        for (int i = 0; i < links.size(); i++) {
            edges[i * 2] = links.get(i).fromCell();
            edges[i * 2 + 1] = links.get(i).toCell();
        }
        return edges;
    }

    private static int encode(int cell, Kind kind, int address) {
        return cell * CODES_PER_CELL + address * KINDS.length + kind.ordinal();
    }
    static int address(int branch, int child) { return branch * (CHILDREN + 1) + child; }
    // Estimates carry only their last known tower's letter; -1 is the origin.
    static int estimateAddress(int branch) { return branch + 1; }
    private static char letter(int branch) { return (char) ('A' + branch); }
    private static String label(Kind kind, int address) {
        if (kind == Kind.ESTIMATE) return address == 0 ? "0-?" : letter(address - 1) + "-?";
        char branch = letter(address / (CHILDREN + 1));
        int child = address % (CHILDREN + 1);
        return child == 0 ? "O-" + branch : branch + "-" + child;
    }

    public int density() { return density(markers.size(), wide); }
    public static double east(int cell, int density) {
        return (cell % side(density) - 3 * density) * columnSpacing(density);
    }
    public static double south(int cell, int density) {
        return (cell / side(density) - 3 * density) * rowSpacing(density);
    }
    public static double columnSpacing(int density) { return HALF_WIDTH / (3.0 * density); }
    public static double rowSpacing(int density) { return HALF_HEIGHT / (3.0 * density); }

    private static int density(int count, boolean wide) {
        int density = wide ? 2 : 1;
        while ((long) side(density) * side(density) <= count) density++;
        return density;
    }
    private static int side(int density) { return 6 * density + 1; }
}
