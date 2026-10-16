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
        assertEquals(360, RelayPower.departure(1000, .6, 2), .001);
        assertEquals(216, RelayPower.departure(1000, .6, 3), .001);
    }
    @Test void mixedCatalystsUseTheCurrentTowersEfficiencyAndMaximum() {
        assertEquals(900, RelayPower.departure(1000, .9, 1), .001);
        assertEquals(720, RelayPower.departure(2000, .6, 2), .001);
        assertEquals(729, RelayPower.departure(1000, .9, 3), .001);
    }
    @Test void lessThanOnePercentCanStillReachANearbyReceiver() {
        float power = RelayPower.departure(1000, .6, 10);
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
}
