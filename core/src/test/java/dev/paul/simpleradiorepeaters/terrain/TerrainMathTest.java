package dev.paul.simpleradiorepeaters.terrain;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TerrainMathTest {
    @Test void clearTouchingAndBuriedPathsHaveBoundedSmoothLoss() {
        assertEquals(0, TerrainMath.loss(32, 32, .1, .35));
        assertEquals(.1, TerrainMath.loss(0, 32, .1, .35));
        assertEquals(.35, TerrainMath.loss(-32, 32, .1, .35));
        double last = 0;
        for (double clearance = 64; clearance >= -64; clearance -= .25) {
            double loss = TerrainMath.loss(clearance, 32, .1, .35);
            assertTrue(loss >= last && loss <= .35); last = loss;
        }
    }
    @Test void samplingDensityDoesNotMultiplyThePenalty() {
        for (int size : new int[]{2, 17, 126, 1024}) {
            int[] ground = new int[size]; Arrays.fill(ground, 80);
            assertEquals(.1, TerrainMath.profileLoss(ground, 80, 80, 32, .1, .35), 1e-12);
        }
    }
    @Test void aMountainAcrossTheLinkCanBeClearedByHigherMasts() {
        int[] ground = {64, 64, 150, 64, 64};
        assertTrue(TerrainMath.profileLoss(ground, 100, 100, 32, .1, .35) > 0);
        assertEquals(0, TerrainMath.profileLoss(ground, 200, 200, 32, .1, .35));
    }
    @Test void aSlopingRayUsesEachSamplesPosition() {
        int[] ground = {64, 110, 150, 180};
        double rising = TerrainMath.profileLoss(ground, 102, 226, 32, .1, .35);
        double flat = TerrainMath.profileLoss(ground, 100, 100, 32, .1, .35);
        assertEquals(0, rising); assertTrue(flat > 0);
        assertEquals(130, TerrainMath.height(100, 220, .25));
    }
    @Test void swappingEndpointsPreservesTheTerrainResult() {
        int[] ground = {63, 80, 160, 90, 110};
        int[] reverse = {110, 90, 160, 80, 63};
        assertEquals(TerrainMath.profileLoss(ground, 90, 160, 32, .1, .35),
                TerrainMath.profileLoss(reverse, 160, 90, 32, .1, .35), 1e-12);
    }
    @Test void sparseSamplesHaveAHardWorkBound() {
        assertEquals(126, TerrainMath.sampleCount(8000, 64, 1024));
        assertEquals(251, TerrainMath.sampleCount(8000, 32, 1024));
        assertEquals(1024, TerrainMath.sampleCount(2_000_000, 64, 1024));
        assertEquals(2, TerrainMath.sampleCount(0, 64, 1024));
    }
    @Test void deviceMovementMustExceedSixteenBlocks() {
        assertFalse(TerrainMath.moved(16 * 16)); assertTrue(TerrainMath.moved(16.01 * 16.01));
    }
    @Test void theDeviceFreeRadiusIncludesItsBoundary() {
        assertTrue(TerrainMath.free(256 * 256)); assertFalse(TerrainMath.free(256.01 * 256.01));
    }
    @Test void elevationBonusHasNoBelowSeaPenaltyAndCannotGrowBeyondTheCap() {
        assertEquals(1, TerrainMath.elevationMultiplier(-64, 64, 192, .1));
        assertEquals(1, TerrainMath.elevationMultiplier(64, 64, 192, .1));
        assertEquals(1.05, TerrainMath.elevationMultiplier(128, 64, 192, .1));
        assertEquals(1.1, TerrainMath.elevationMultiplier(320, 64, 192, .1));
    }
    @Test void threeModesHaveOneDefaultAndStableConfigurationValues() {
        var gson = new com.google.gson.Gson();
        var cfg = new dev.paul.simpleradiorepeaters.RepeatersConfig.Terrain();
        assertEquals(TerrainMode.RELAY_MAPPING, cfg.mode);
        assertFalse(TerrainMode.OFF.relayLinks()); assertFalse(TerrainMode.OFF.pointToPoint());
        assertTrue(TerrainMode.RELAY_MAPPING.relayLinks()); assertFalse(TerrainMode.RELAY_MAPPING.pointToPoint());
        assertTrue(TerrainMode.FULL.relayLinks()); assertTrue(TerrainMode.FULL.pointToPoint());
        for (TerrainMode mode : TerrainMode.values()) {
            cfg.mode = mode;
            assertTrue(gson.toJson(cfg).contains("\"mode\":\"" + mode.id() + "\""));
            assertEquals(mode, gson.fromJson(gson.toJson(cfg), cfg.getClass()).mode);
        }
    }
}
