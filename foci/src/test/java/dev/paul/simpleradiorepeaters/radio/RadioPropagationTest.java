package dev.paul.simpleradiorepeaters.radio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadioPropagationTest {
    @Test void ordinaryNetherRangeIsQuarteredAndAFocusHalvesIt() {
        var ordinary = dev.paul.simpleradiofoci.FocusPropagation.profile(true, true, false, false);
        var focused = dev.paul.simpleradiofoci.FocusPropagation.profile(true, true, false, true);
        assertEquals(400, 800 * ordinary.distanceMultiplier());
        assertEquals(200, 800 * focused.distanceMultiplier());
        assertEquals(10, ordinary.noiseFloorAddition());
        assertEquals(0, focused.noiseFloorAddition());
    }
    @Test void NetherCrossingsRetainMappingAndDoubleTheDimensionalPenalty() {
        for (boolean fromNether : new boolean[]{false, true}) {
            var crossing = dev.paul.simpleradiofoci.FocusPropagation.profile(fromNether, !fromNether, true, true);
            assertEquals(1, crossing.distanceMultiplier());
            assertEquals(2, crossing.dimensionalMultiplier());
        }
        // A Nether projection is dimensional even though both numeric sites are in the Nether.
        assertEquals(2, dev.paul.simpleradiofoci.FocusPropagation.profile(true, true, true, true).dimensionalMultiplier());
        assertEquals(1, dev.paul.simpleradiofoci.FocusPropagation.profile(false, false, true, false).dimensionalMultiplier());
    }
}
