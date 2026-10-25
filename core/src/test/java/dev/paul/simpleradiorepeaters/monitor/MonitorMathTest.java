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
}
