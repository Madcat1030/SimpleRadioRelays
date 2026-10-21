package dev.paul.simpleradiodirectional.radio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DishAimTest {
    @Test void exactlySevenFifteenDegreeOffsets() {
        for (int aim = 0; aim <= 6; aim++) assertEquals(-45 + 15 * aim, DishAim.degrees(aim));
        assertFalse(DishAim.valid(-1)); assertFalse(DishAim.valid(7));
    }
    @Test void clicksStopAtBothEndsRatherThanWrapping() {
        int aim = DishAim.STRAIGHT;
        for (int i = 0; i < 10; i++) aim = DishAim.adjust(aim, false);
        assertEquals(DishAim.MAX, aim);
        for (int i = 0; i < 10; i++) aim = DishAim.adjust(aim, true);
        assertEquals(DishAim.MIN, aim);
    }
    @Test void clockwiseSwivelOfNorthTurnsTowardEast() {
        double root = Math.sqrt(.5);
        assertEquals(root, DishAim.x(0, -1, 6), 1e-12);
        assertEquals(-root, DishAim.z(0, -1, 6), 1e-12);
        assertEquals(-root, DishAim.x(0, -1, 0), 1e-12);
    }
    @Test void allFourMountsKeepTheSamePatternForEveryAim() {
        for (double[] mount : new double[][]{{0,-1},{1,0},{0,1},{-1,0}}) for (int aim = 0; aim <= 6; aim++) {
            double x = DishAim.x(mount[0], mount[1], aim), z = DishAim.z(mount[0], mount[1], aim);
            assertEquals(1, Math.hypot(x,z), 1e-12);
            for (double relative : new double[]{0,45,60,75,90,180}) {
                double radians = Math.toRadians(relative), dx = x*Math.cos(radians)-z*Math.sin(radians),
                        dz = x*Math.sin(radians)+z*Math.cos(radians);
                assertEquals(DirectionalMath.angularWeight(relative), DirectionalMath.weight(dx*100,dz*100,x,z), 1e-12);
            }
        }
    }
    @Test void mirroringTwiceRestoresEveryAim() {
        for (int aim = 0; aim <= 6; aim++) {
            assertEquals(-DishAim.degrees(aim), DishAim.degrees(DishAim.mirror(aim)));
            assertEquals(aim, DishAim.mirror(DishAim.mirror(aim)));
        }
    }
}
