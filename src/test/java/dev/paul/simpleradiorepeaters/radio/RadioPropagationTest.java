package dev.paul.simpleradiorepeaters.radio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadioPropagationTest {
    @Test void ordinaryNetherRangeIsQuarteredAndAFocusHalvesIt() {
        var ordinary = RadioPropagation.profile(true, true, false, false);
        var focused = RadioPropagation.profile(true, true, false, true);
        assertEquals(400, 800 * ordinary.distanceMultiplier());
        assertEquals(200, 800 * focused.distanceMultiplier());
        assertEquals(10, ordinary.noiseFloorAddition());
        assertEquals(0, focused.noiseFloorAddition());
    }
    @Test void NetherCrossingsRetainMappingAndDoubleTheDimensionalPenalty() {
        for (boolean fromNether : new boolean[]{false, true}) {
            var crossing = RadioPropagation.profile(fromNether, !fromNether, true, true);
            assertEquals(1, crossing.distanceMultiplier());
            assertEquals(2, crossing.dimensionalMultiplier());
        }
        // A Nether projection is dimensional even though both numeric sites are in the Nether.
        assertEquals(2, RadioPropagation.profile(true, true, true, true).dimensionalMultiplier());
        assertEquals(1, RadioPropagation.profile(false, false, true, false).dimensionalMultiplier());
    }
    @Test void AmplificationBoostsAntennaScoreWithoutRoundingOrCreatingAnAntenna() {
        assertEquals(48, RelayPower.antennaScore(32, true));
        assertEquals(4.5, RelayPower.antennaScore(3, true));
        assertEquals(0, RelayPower.antennaScore(0, true));
        assertEquals(32, RelayPower.antennaScore(32, false));
    }
}
