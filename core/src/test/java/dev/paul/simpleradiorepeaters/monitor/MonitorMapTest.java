package dev.paul.simpleradiorepeaters.monitor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static dev.paul.simpleradiorepeaters.monitor.MonitorMap.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class MonitorMapTest {
    @Test void plotsAndHitTestsTheSameCellsAfterRecenteringOnAnEstimate() {
        var plot = MonitorMap.plot(List.of(new MonitorMap.Site(-4000, 0, HOME, 0),
                new MonitorMap.Site(-3000, -4000, RX_ONLY, 2), new MonitorMap.Site(-1500, 0, TX_RX, 1)),
                List.of(link(0, 1), link(1, 0), link(2, 1), link(1, 3)),
                3, ESTIMATE, "~1500 E from X -1500 Z 0", true);
        var map = plot.map();
        var decoded = MonitorMap.fromCells(map.cells(), map.linkCells(), map.pending(), map.centerKind().ordinal(), map.location());
        assertEquals(map, decoded);
        assertEquals(3, plot.towersByCell().get(map.cellAt(MonitorMap.CENTER_X, MonitorMap.CENTER_Y)));
        int density = map.density();
        for (var marker : map.markers())
            assertEquals(marker.cell(), map.cellAt(MonitorMap.modelX(marker.cell(), density), MonitorMap.modelY(marker.cell(), density)));
        assertEquals(3, map.links().size());
        assertEquals(1L, map.links().stream().filter(MonitorMap.Link::estimated).count());
        assertTrue(map.links().stream().anyMatch(l -> l.estimated() && (l.fromCell() == map.centerCell() || l.toCell() == map.centerCell())));
        assertTrue(map.markers().stream().anyMatch(m -> m.kind() == HOME && MonitorMap.east(m.cell(), density) < 0));
        assertTrue(map.markers().stream().anyMatch(m -> m.kind() == RX_ONLY && MonitorMap.south(m.cell(), density) < 0));
    }

    @Test void focusedViewFollowsAtMostTwoDrawnLinksInEitherDirection() {
        var links = List.of(link(0, 1), link(1, 2), link(2, 3), link(3, 4), link(4, 5), link(6, 4), link(3, 7));
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7), RelayMapSurvey.neighborhood(3, links));
        assertEquals(Set.of(0, 1, 2), RelayMapSurvey.neighborhood(0, links));
    }

    @Test void colocatedTowersGetSeparateClickableCellsAndLeaveTheCenterFree() {
        var sites = IntStream.rangeClosed(1, 85).mapToObj(i -> new MonitorMap.Site(0, 0, TX_RX, i)).toList();
        var plot = MonitorMap.plot(sites, List.of(), 0, HOME, "", false);
        var map = plot.map();
        assertEquals(86, plot.towersByCell().size());
        assertEquals(0, plot.towersByCell().get(map.centerCell()));
        assertEquals(85L, map.markers().stream().map(MonitorMap.Marker::cell).distinct().count());
        assertFalse(map.markers().stream().anyMatch(marker -> marker.cell() == map.centerCell()));
    }

    private static MonitorNetworkReach.Connection link(int from, int to) {
        return new MonitorNetworkReach.Connection(from, to);
    }
}
