package dev.paul.simpleradiorepeaters.radio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.paul.simpleradiorepeaters.radio.RadioLinkMath.Loss.ADDITIVE;

class RelayPowerTest {
    @Test void firstRelayAlreadyPaysItsCatalystEfficiency() {
        assertEquals(900, RelayPower.departure(1000, .9, 1), .001);
        assertEquals(810, RelayPower.departure(1000, .9, 2), .001);
        assertEquals(729, RelayPower.departure(1000, .9, 3), .001);
    }
    @Test void amethystDecaysAtEachRelayCount() {
        assertEquals(600, RelayPower.departure(1000, .6, 1), .001);
        assertEquals(540, RelayPower.departure(1000, .6, 2), .001);
        assertEquals(486, RelayPower.departure(1000, .6, 3), .001);
    }
    @Test void mixedCatalystsUseTheCurrentTowersEfficiencyAndMaximum() {
        assertEquals(900, RelayPower.departure(1000, .9, 1), .001);
        assertEquals(1080, RelayPower.departure(2000, .6, 2), .001);
        assertEquals(729, RelayPower.departure(1000, .9, 3), .001);
    }
    @Test void lessThanOnePercentCanStillReachANearbyReceiver() {
        float power = RelayPower.departure(1000, .6, 50);
        assertTrue(power > 0 && power < 10);
        assertNotNull(RadioLinkMath.receive(1, power, power, 300, 20, 1, ADDITIVE, 500, 15));
        assertNull(RadioLinkMath.receive(1000, power, power, 300, 20, 1, ADDITIVE, 500, 15));
    }
    @Test void invalidInputsNeverCreateUsablePower() {
        assertEquals(0, RelayPower.departure(1000, .9, 0));
        assertEquals(0, RelayPower.departure(1000, Double.NaN, 1));
        assertEquals(0, RelayPower.departure(Double.POSITIVE_INFINITY, .9, 1));
        assertEquals(0, RelayPower.departure(1000, 0, 1));
    }
    @Test void customCatalystsStartAtTheirStrengthAndAlwaysDecay() {
        assertEquals(1000, RelayPower.departure(1000, 1, 1), .001);
        assertEquals(900, RelayPower.departure(1000, 1, 2), .001);
        assertEquals(2000, RelayPower.departure(1000, 2, 1), .001);
        assertEquals(1800, RelayPower.departure(1000, 2, 2), .001);
        assertEquals(1620, RelayPower.departure(1000, 2, 3), .001);
        for (double strength : new double[]{.6, .9, 1, 2, 100})
            for (int count = 2; count <= 128; count++)
                assertTrue(RelayPower.departure(1000, strength, count) < RelayPower.departure(1000, strength, count - 1));
    }
    @Test void nativeCatalystRatioIsConstantAtEveryCount() {
        for (int count = 1; count <= 128; count++)
            assertEquals(2d / 3, RelayPower.departure(1000, .6, count) / (double) RelayPower.departure(1000, .9, count), .000001);
    }
}
