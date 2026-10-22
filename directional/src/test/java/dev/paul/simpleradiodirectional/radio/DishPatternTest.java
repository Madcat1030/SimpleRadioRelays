package dev.paul.simpleradiodirectional.radio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DishPatternTest {
    @Test void wideArrayProvidesSeventyFivePercentThroughoutItsNinetyDegreeCore() {
        for (double angle : new double[]{-45, -30, 0, 30, 45})
            assertEquals(.75, DishPattern.WIDE.angularWeight(angle), 1e-12);
        assertEquals(.625, DishPattern.WIDE.angularWeight(52.5), 1e-12);
        assertEquals(.5, DishPattern.WIDE.angularWeight(60), 1e-12);
        assertEquals(.25, DishPattern.WIDE.angularWeight(67.5), 1e-12);
        assertEquals(2190, DirectionalMath.bonus(620, 4720, 1, DishPattern.WIDE.angularWeight(0)), 1e-9);
    }
    @Test void broadShoulderIsContinuousMonotoneAndSymmetric() {
        double previous = .75;
        for (double angle = 45; angle <= 75; angle += .01) {
            double next = DishPattern.WIDE.angularWeight(angle);
            assertTrue(next >= 0 && next <= previous + 1e-12);
            assertEquals(next, DishPattern.WIDE.angularWeight(-angle), 1e-12);
            previous = next;
        }
        assertEquals(.75, DishPattern.WIDE.angularWeight(45 + 1e-7), 1e-12);
        assertEquals(.5, DishPattern.WIDE.angularWeight(60 - 1e-7), 1e-12);
        assertEquals(.5, DishPattern.WIDE.angularWeight(60 + 1e-7), 1e-12);
        assertEquals(0, DishPattern.WIDE.angularWeight(75 - 1e-7), 1e-12);
        assertEquals(0, DishPattern.WIDE.angularWeight(75));
        assertEquals(0, DishPattern.WIDE.angularWeight(180));
        assertEquals(0, DishPattern.WIDE.angularWeight(Double.NaN));
        assertEquals(0, DishPattern.WIDE.angularWeight(Double.POSITIVE_INFINITY));
    }
    @Test void dishAndArrayUseTheSameBonusScaleAndForwardBearing() {
        assertEquals(1, DishPattern.PARABOLIC.angularWeight(30));
        assertEquals(.5, DishPattern.PARABOLIC.angularWeight(45));
        assertEquals(0, DishPattern.PARABOLIC.angularWeight(60));
        assertEquals(.75, DirectionalMath.weight(0, -100, 0, -1, DishPattern.WIDE));
        assertEquals(0, DirectionalMath.weight(0, 100, 0, -1, DishPattern.WIDE));
        assertEquals(0, DirectionalMath.weight(0, 0, 0, -1, DishPattern.WIDE));
    }
}
