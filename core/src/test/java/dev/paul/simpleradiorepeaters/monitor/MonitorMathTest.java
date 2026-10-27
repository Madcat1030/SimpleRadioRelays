package dev.paul.simpleradiorepeaters.monitor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonitorMathTest {
    @Test void allSixteenBearingsUseMinecraftWorldDirections() {
        String[] expected = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        for (int i = 0; i < expected.length; i++) {
            double angle = Math.toRadians(i * 22.5);
            assertEquals(expected[i], MonitorMath.bearing(Math.sin(angle), -Math.cos(angle)));
        }
        assertEquals("LOCAL", MonitorMath.bearing(0, 0));
    }
    @Test void boundariesRoundToTheNearestBearingAndWrapAtNorth() {
        for (double angle : new double[]{-11.24, 0, 11.24, 348.76, 359.9})
            assertEquals("N", MonitorMath.bearing(Math.sin(Math.toRadians(angle)), -Math.cos(Math.toRadians(angle))));
        assertEquals("NNE", MonitorMath.bearing(Math.sin(Math.toRadians(11.26)), -Math.cos(Math.toRadians(11.26))));
    }
    @Test void distanceEstimatesNeverExposeAnExactLongDistanceOrReportZeroForANearbyTower() {
        assertEquals(3500, MonitorMath.approximateDistance(3467));
        assertEquals(4000, MonitorMath.approximateDistance(3762));
        assertEquals(50, MonitorMath.approximateDistance(12));
        assertEquals(250, MonitorMath.approximateDistance(237));
    }
    @Test void compassUsesTheCentreOfEveryDisplayedBearing() {
        String[] bearings = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        for (int i = 0; i < bearings.length; i++) {
            var point = MonitorMath.compassEstimate(100, -200, bearings[i], 3467, 1);
            double angle = Math.toRadians(i * 22.5);
            assertEquals(100 + Math.sin(angle) * 3500, point.x(), .000001);
            assertEquals(-200 - Math.cos(angle) * 3500, point.z(), .000001);
        }
    }
    @Test void differentTowersWithTheSameVisibleEstimateProduceTheSameCompassPoint() {
        double ax = 3230, az = -1200, bx = 3300, bz = -1400;
        assertEquals(MonitorMath.bearing(ax, az), MonitorMath.bearing(bx, bz));
        assertEquals(MonitorMath.approximateDistance(Math.hypot(ax, az)), MonitorMath.approximateDistance(Math.hypot(bx, bz)));
        assertEquals(MonitorMath.compassEstimate(0, 0, MonitorMath.bearing(ax, az), Math.hypot(ax, az), 1),
                MonitorMath.compassEstimate(0, 0, MonitorMath.bearing(bx, bz), Math.hypot(bx, bz), 1));
    }
    @Test void scaledRadioDistancesConvertBackToWorldBlocks() {
        for (double units : new double[]{1, 2, 4, 8}) {
            var point = MonitorMath.compassEstimate(100, 200, "E", 3467, units);
            assertEquals(100 + 3500 / units, point.x(), .000001);
            assertEquals(200, point.z(), .000001);
        }
    }
    @Test void localAndCloseEstimatesStayCoarseAndInvalidScalesDoNotInventATarget() {
        assertEquals(new MonitorMath.Point(20, 30), MonitorMath.compassEstimate(20, 30, "LOCAL", 0, 1));
        var close = MonitorMath.compassEstimate(20, 30, "E", 12, 1);
        assertEquals(70, close.x(), .000001);
        assertEquals(30, close.z(), .000001);
        for (double scale : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(new MonitorMath.Point(20, 30), MonitorMath.compassEstimate(20, 30, "E", 3467, scale));
    }
}
