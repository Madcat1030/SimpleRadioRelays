package dev.paul.simpleradiorepeaters.monitor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.paul.simpleradiorepeaters.monitor.MonitorMap.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class MonitorMapTest {
    @Test void usesOneNorthUpScaleAndKeepsOnlyDisplayCellsInThePayload() {
        var map = MonitorMap.plot(List.of(new MonitorMap.Site(4000, -4000, RX_ONLY, MonitorMap.address(0, 0)),
                new MonitorMap.Site(8000, 0, TX_RX, MonitorMap.address(0, 1)),
                new MonitorMap.Site(-3000, 0, ESTIMATE, MonitorMap.estimateAddress(0)),
                new MonitorMap.Site(0, 3500, ESTIMATE, MonitorMap.estimateAddress(-1))), true);
        assertEquals(List.of("O-A", "A-1", "A-?", "0-?"), map.markers().stream().map(MonitorMap.Marker::label).toList());
        assertEquals(List.of(RX_ONLY, TX_RX, ESTIMATE, ESTIMATE), map.markers().stream().map(MonitorMap.Marker::kind).toList());
        assertEquals(5, MonitorMap.east(map.markers().get(1).cell(), map.density()));
        double east = MonitorMap.east(map.markers().get(0).cell(), map.density());
        double south = MonitorMap.south(map.markers().get(0).cell(), map.density());
        assertTrue(east > 0 && south < 0 && Math.abs(east + south) <= 1.34);
        assertTrue(MonitorMap.east(map.markers().get(2).cell(), map.density()) < 0);
        assertEquals(map, MonitorMap.fromCells(map.cells(), true));
    }

    @Test void colocatedTowersGetSeparateCellsAndLeaveTheOriginFree() {
        var sites = java.util.stream.IntStream.range(0, 85)
                .mapToObj(i -> new MonitorMap.Site(0, 0, TX_RX, i)).toList();
        var map = MonitorMap.plot(sites, false);
        assertEquals(85L, map.markers().stream().map(MonitorMap.Marker::cell).distinct().count());
        assertEquals(85L, map.markers().stream().map(MonitorMap.Marker::label).distinct().count());
        for (var marker : map.markers()) {
            double east = MonitorMap.east(marker.cell(), map.density()), south = MonitorMap.south(marker.cell(), map.density());
            assertFalse(east == 0 && south == 0);
            assertTrue(Math.abs(east) <= 5 && Math.abs(south) <= 3);
        }
    }
}
