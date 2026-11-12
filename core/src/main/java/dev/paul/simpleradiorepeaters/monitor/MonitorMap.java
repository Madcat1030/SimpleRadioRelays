package dev.paul.simpleradiorepeaters.monitor;

import java.util.*;

/** Display cells and links. The only location sent as text is for the centered tower. */
public record MonitorMap(List<Marker> markers, List<Link> links, boolean pending, Kind centerKind, String location) {
    public enum Kind { TX_RX, RX_ONLY, ESTIMATE, HOME }
    public record Marker(int cell, Kind kind) {}
    public record Link(int fromCell, int toCell, boolean estimated) {}
    record Site(double east, double south, Kind kind, int tower) {}
    record Plot(MonitorMap map, Map<Integer, Integer> towersByCell) {}
    private static final Kind[] KINDS = Kind.values();
    public static final double CENTER_X = .5, CENTER_Y = .625;
    private static final double HALF_WIDTH = 5, HALF_HEIGHT = 2.8;
    // Preserve the existing cell stride and the first three marker codes.
    private static final int CODES_PER_CELL = 420;

    public MonitorMap {
        markers = List.copyOf(markers);
        links = List.copyOf(links);
    }

    static Plot plot(List<Site> sites, List<MonitorNetworkReach.Connection> connections,
                     int centerTower, Kind centerKind, String location, boolean pending) {
        int density = density(sites.size()), side = side(density);
        boolean[] used = new boolean[side * side];
        int center = used.length / 2;
        used[center] = true;
        Map<Integer, Integer> cellsByTower = new HashMap<>();
        Map<Integer, Integer> towersByCell = new HashMap<>();
        cellsByTower.put(centerTower, center);
        towersByCell.put(center, centerTower);
        double extent = sites.stream().mapToDouble(s -> Math.max(Math.abs(s.east()) / HALF_WIDTH,
                Math.abs(s.south()) / HALF_HEIGHT)).max().orElse(1);
        double scale = extent > 0 ? 1 / extent : 0;
        // One scale for both axes. Move overlapping rings to the nearest free cells.
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
            cellsByTower.put(site.tower(), best);
            towersByCell.put(best, site.tower());
            cells[i] = encode(best, site.kind());
        }
        int[] edges = new int[connections.size() * 2];
        for (int i = 0; i < connections.size(); i++) {
            var connection = connections.get(i);
            edges[i * 2] = cellsByTower.getOrDefault(connection.from(), -1);
            edges[i * 2 + 1] = cellsByTower.getOrDefault(connection.to(), -1);
        }
        return new Plot(fromCells(cells, edges, pending, centerKind.ordinal(), location), Map.copyOf(towersByCell));
    }

    public static MonitorMap fromCells(int[] cells, int[] edges, boolean pending, int centerCode, String location) {
        int side = side(density(cells.length)), size = side * side;
        Kind centerKind = centerCode >= 0 && centerCode < KINDS.length ? KINDS[centerCode] : Kind.HOME;
        Kind[] kinds = new Kind[size];
        kinds[size / 2] = centerKind;
        List<Marker> markers = new ArrayList<>(cells.length);
        for (int code : cells) {
            int cell = code / CODES_PER_CELL, kind = code % CODES_PER_CELL;
            if (code < 0 || cell >= size || kinds[cell] != null || kind >= KINDS.length)
                return new MonitorMap(List.of(), List.of(), pending, centerKind, location);
            kinds[cell] = KINDS[kind];
            markers.add(new Marker(cell, kinds[cell]));
        }
        var links = new LinkedHashSet<Link>();
        for (int i = 1; i < edges.length; i += 2) {
            int from = Math.min(edges[i - 1], edges[i]), to = Math.max(edges[i - 1], edges[i]);
            if (from < 0 || from == to || to >= size || kinds[from] == null || kinds[to] == null) continue;
            if (kinds[from] == Kind.ESTIMATE && kinds[to] == Kind.ESTIMATE) continue;
            links.add(new Link(from, to, kinds[from] == Kind.ESTIMATE || kinds[to] == Kind.ESTIMATE));
        }
        return new MonitorMap(markers, new ArrayList<>(links), pending, centerKind, location);
    }

    public int[] cells() { return markers.stream().mapToInt(m -> encode(m.cell(), m.kind())).toArray(); }

    public int[] linkCells() {
        int[] edges = new int[links.size() * 2];
        for (int i = 0; i < links.size(); i++) {
            edges[i * 2] = links.get(i).fromCell();
            edges[i * 2 + 1] = links.get(i).toCell();
        }
        return edges;
    }

    private static int encode(int cell, Kind kind) { return cell * CODES_PER_CELL + kind.ordinal(); }
    public int density() { return density(markers.size()); }
    public int centerCell() { int side = side(density()); return side * side / 2; }
    public int cellAt(double x, double y) {
        int density = density(), center = centerCell();
        if (hits(center, density, x, y)) return center;
        for (var marker : markers) if (hits(marker.cell(), density, x, y)) return marker.cell();
        return -1;
    }
    private static boolean hits(int cell, int density, double x, double y) {
        double margin = ringRadius(density) * 1.25;
        return Math.abs(x - modelX(cell, density)) <= margin && Math.abs(y - modelY(cell, density)) <= margin;
    }
    public static double modelX(int cell, int density) { return CENTER_X - east(cell, density) / 16; }
    public static double modelY(int cell, int density) { return CENTER_Y - south(cell, density) / 16; }
    public static double ringRadius(int density) { return rowSpacing(density) * .37 / 16; }
    static double east(int cell, int density) { return (cell % side(density) - 3 * density) * HALF_WIDTH / (3 * density); }
    static double south(int cell, int density) { return (cell / side(density) - 3 * density) * rowSpacing(density); }
    private static double rowSpacing(int density) { return HALF_HEIGHT / (3 * density); }
    private static int density(int count) {
        int density = 2;
        while ((long) side(density) * side(density) <= count) density++;
        return density;
    }
    private static int side(int density) { return 6 * density + 1; }
}
