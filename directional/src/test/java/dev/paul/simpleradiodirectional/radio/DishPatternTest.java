package dev.paul.simpleradiodirectional.radio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DishPatternTest {
    @Test void wideArrayFollowsEveryNewAnchorWithoutAbruptSteps() {
        var pattern = DishPattern.WIDE;
        assertEquals(.66, pattern.angularWeight(0), 1e-12);
        assertEquals(.66, pattern.angularWeight(45), 1e-12);
        assertEquals(.44, pattern.angularWeight(60), 1e-12);
        assertEquals(.22, pattern.angularWeight(75), 1e-12);
        assertEquals(0, pattern.angularWeight(90), 1e-12);
        for (double anchor : new double[]{45, 60, 75, 90})
            assertEquals(pattern.angularWeight(anchor - 1e-7), pattern.angularWeight(anchor + 1e-7), 1e-12);
        double previous = .66;
        for (int step = 0; step <= 1800; step++) {
            double angle = step / 10d, weight = pattern.angularWeight(angle);
            assertTrue(weight >= 0 && weight <= previous + 1e-12);
            assertEquals(weight, pattern.angularWeight(-angle), 1e-12); previous = weight;
        }
        assertEquals(0, pattern.angularWeight(Double.NaN));
        assertEquals(0, pattern.angularWeight(Double.POSITIVE_INFINITY));
    }
    @Test void theDishKeepsItsNarrowerAndStrongerBeam() {
        assertEquals(1, DishPattern.PARABOLIC.angularWeight(30));
        assertEquals(.5, DishPattern.PARABOLIC.angularWeight(45));
        assertEquals(0, DishPattern.PARABOLIC.angularWeight(60));
        assertEquals(.66, DirectionalMath.weight(0, -100, 0, -1, DishPattern.WIDE), 1e-12);
        assertEquals(0, DirectionalMath.weight(0, 100, 0, -1, DishPattern.WIDE));
        assertEquals(0, DirectionalMath.weight(0, 0, 0, -1, DishPattern.WIDE));
    }
}
