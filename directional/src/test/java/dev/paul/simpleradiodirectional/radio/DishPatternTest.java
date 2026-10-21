package dev.paul.simpleradiodirectional.radio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DishPatternTest {
    @Test void wideArrayProvidesHalfBonusThroughoutIts135DegreeCore() {
        for (double angle : new double[]{-67.5, -60, -30, 0, 30, 60, 67.5})
            assertEquals(.5, DishPattern.WIDE.angularWeight(angle), 1e-12);
        assertEquals(1460, DirectionalMath.bonus(620, 4720, 1, DishPattern.WIDE.angularWeight(0)), 1e-9);
    }
    @Test void broadShoulderIsContinuousMonotoneAndSymmetric() {
        double previous = .5;
        for (double angle = 67.5; angle <= 90; angle += .01) {
            double next = DishPattern.WIDE.angularWeight(angle);
            assertTrue(next >= 0 && next <= previous + 1e-12);
            assertEquals(next, DishPattern.WIDE.angularWeight(-angle), 1e-12);
            previous = next;
        }
        assertEquals(.5, DishPattern.WIDE.angularWeight(67.5 + 1e-7), 1e-12);
        assertEquals(0, DishPattern.WIDE.angularWeight(90 - 1e-7), 1e-12);
        assertEquals(0, DishPattern.WIDE.angularWeight(180));
        assertEquals(0, DishPattern.WIDE.angularWeight(Double.NaN));
    }
    @Test void originalDishProfileAndForwardBearingRemainIntact() {
        assertEquals(1, DishPattern.PARABOLIC.angularWeight(45));
        assertEquals(.25, DishPattern.PARABOLIC.angularWeight(60));
        assertEquals(0, DishPattern.PARABOLIC.angularWeight(75));
        assertEquals(.5, DirectionalMath.weight(0, -100, 0, -1, DishPattern.WIDE));
        assertEquals(0, DirectionalMath.weight(0, 100, 0, -1, DishPattern.WIDE));
        assertEquals(0, DirectionalMath.weight(0, 0, 0, -1, DishPattern.WIDE));
    }
}
