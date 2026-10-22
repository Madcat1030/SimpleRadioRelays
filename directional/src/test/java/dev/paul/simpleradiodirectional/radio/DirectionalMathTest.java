package dev.paul.simpleradiodirectional.radio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectionalMathTest {
    @Test void fullCoreIncludesBothEdges() {
        for (double a : new double[]{-30,-15,0,15,30}) assertEquals(1,DirectionalMath.angularWeight(a));
    }
    @Test void fifteenDegreesPastTheEdgeIsHalfGain() {
        assertEquals(0.5,DirectionalMath.angularWeight(45),1e-12);
        assertEquals(0.5,DirectionalMath.angularWeight(-45),1e-12);
    }
    @Test void thirtyDegreesPastTheEdgeHasNoBonus() {
        for (double a : new double[]{-180,-90,-60,60,90,180}) assertEquals(0,DirectionalMath.angularWeight(a));
    }
    @Test void shoulderIsMonotoneSymmetricAndContinuous() {
        double previous=1;
        for (double a=30;a<=60;a+=0.01) {
            double gain=DirectionalMath.angularWeight(a);
            assertTrue(gain<=previous+1e-12 && gain>=0);
            assertEquals(gain,DirectionalMath.angularWeight(-a)); previous=gain;
        }
        assertEquals(1,DirectionalMath.angularWeight(30+1e-7),1e-12);
        assertEquals(0,DirectionalMath.angularWeight(60-1e-7),1e-12);
    }
    @Test void cardinalDirectionsAndForwardArcAgree() {
        assertEquals(1,DirectionalMath.weight(0,-100,0,-1));
        assertEquals(0,DirectionalMath.weight(0,100,0,-1));
        assertEquals(1,DirectionalMath.weight(100,0,1,0));
        assertEquals(1,DirectionalMath.weight(-100,0,-1,0));
        assertEquals(0,DirectionalMath.weight(100,0,0,1));
        assertEquals(.5,DirectionalMath.weight(Math.sin(Math.PI/4)*100,-Math.cos(Math.PI/4)*100,0,-1),1e-12);
    }
    @Test void defaultThirtyTwoPointAntennaHasModestTowerScaledAllowance() {
        // AM: 4400 + 32*10; ordinary RX: 300+32*10. Total directional RX reaches 75% of tower radius.
        assertEquals(2920,DirectionalMath.bonus(620,4720,1,1));
        assertEquals(730,DirectionalMath.bonus(620,4720,1,.25));
        assertEquals(0,DirectionalMath.bonus(620,4720,1,0));
    }
    @Test void alreadyStrongerNativeReceptionIsNeverReduced() {
        assertEquals(0,DirectionalMath.bonus(8000,4000,1,1));
    }
    @Test void nonfiniteAndUnlimitedNativeLossAreSafe() {
        assertEquals(0,DirectionalMath.bonus(300,4000,0,1));
        assertEquals(0,DirectionalMath.bonus(300,4000,1,Double.NaN));
        assertEquals(0,DirectionalMath.angularWeight(Double.NaN));
        assertEquals(0,DirectionalMath.weight(Double.POSITIVE_INFINITY,1,0,1));
        assertEquals(0,DirectionalMath.weight(0,0,0,1));
    }
}
