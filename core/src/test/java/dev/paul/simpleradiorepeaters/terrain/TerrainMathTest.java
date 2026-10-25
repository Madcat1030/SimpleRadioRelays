package dev.paul.simpleradiorepeaters.terrain;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TerrainMathTest {
    private static final double[][] CURVE = new dev.paul.simpleradiorepeaters.RepeatersConfig.Terrain().clearanceCurve;
    @Test void clearTouchingAndBuriedPathsHaveBoundedSmoothLoss() {
        assertEquals(0, TerrainMath.loss(8, CURVE));
        assertEquals(.05, TerrainMath.loss(0, CURVE));
        assertEquals(.50, TerrainMath.loss(-32, CURVE));
        double last = 0;
        for (double clearance = 64; clearance >= -160; clearance -= .25) {
            double loss = TerrainMath.loss(clearance, CURVE);
            assertTrue(loss >= last && loss <= 3); last = loss;
        }
    }
    @Test void samplingDensityDoesNotMultiplyThePenalty() {
        for (int size : new int[]{2, 17, 126, 1024}) {
            int[] ground = new int[size]; Arrays.fill(ground, 80);
            assertEquals(.05, TerrainMath.profileLoss(ground, 80, 80, CURVE), 1e-12);
        }
    }
    @Test void aMountainAcrossTheLinkCanBeClearedByHigherMasts() {
        int[] ground = {64, 64, 150, 64, 64};
        assertTrue(TerrainMath.profileLoss(ground, 100, 100, CURVE) > 0);
        assertEquals(0, TerrainMath.profileLoss(ground, 200, 200, CURVE));
    }
    @Test void aSlopingRayUsesEachSamplesPosition() {
        int[] ground = {64, 110, 150, 180};
        double rising = TerrainMath.profileLoss(ground, 102, 226, CURVE);
        double flat = TerrainMath.profileLoss(ground, 100, 100, CURVE);
        assertEquals(0, rising); assertTrue(flat > 0);
        assertEquals(130, TerrainMath.height(100, 220, .25));
    }
    @Test void swappingEndpointsPreservesTheTerrainResult() {
        int[] ground = {63, 80, 160, 90, 110};
        int[] reverse = {110, 90, 160, 80, 63};
        assertEquals(TerrainMath.profileLoss(ground, 90, 160, CURVE),
                TerrainMath.profileLoss(reverse, 160, 90, CURVE), 1e-12);
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

    @Test void aboveGroundAndUndergroundHaveTheRequestedSeparateAnchors() {
        double[] clearances = {10, 8, 5, 2, 1, 0, -4, -8, -12, -16, -20, -30, -32, -40, -48, -56, -64, -96, -128, -160};
        double[] expected = {0, 0, .005, .01, .03, .05, .075, .10, .175, .25, .3125, .46875, .50, .75, 1, 1.5, 2, 2.5, 3, 3};
        for (int i = 0; i < clearances.length; i++)
            assertEquals(expected[i], TerrainMath.loss(clearances[i], CURVE), 1e-12);
    }
    @Test void undergroundPenaltiesAddDistanceAndCannotBecomeNonFinite() {
        assertEquals(1000, TerrainMath.extraDistance(1000, 1), 1e-12);
        assertEquals(2000, TerrainMath.extraDistance(1000, 2), 1e-12);
        assertEquals(Double.POSITIVE_INFINITY, TerrainMath.extraDistance(1000, Double.POSITIVE_INFINITY));
        assertEquals(0, TerrainMath.extraDistance(1000, 0));
    }
    @Test void deepestUniformPathsHaveOneQuarterRangeAndPartialObstructionsAreAveraged() {
        int[] deep = {-1, -1};
        double penalty = TerrainMath.profileLoss(deep, -129, -129, CURVE);
        assertEquals(3, penalty);
        assertEquals(2000, 500 + TerrainMath.extraDistance(500, penalty));
        int[] mixed = {-10, 127, -10};
        assertEquals(1.5, TerrainMath.profileLoss(mixed, -1, -1, CURVE));
        assertEquals(3, TerrainMath.loss(-1024, CURVE));
    }
    @Test void aFiveHundredBlockMountainIsWeightedAcrossTheWholeTwoThousandBlockPath() {
        int[] ground = new int[2001]; Arrays.fill(ground, 90);
        Arrays.fill(ground, 750, 1250, 130);
        double loss = TerrainMath.profileLoss(ground, 100, 100, CURVE);
        assertEquals(.25 * .46875, loss, 1e-12);
        assertEquals(2234.375, 2000 + TerrainMath.extraDistance(2000, loss), 1e-9);
    }
    @Test void samplingBudgetYieldsAsServerTicksBecomeBusy() {
        assertEquals(1_000_000, TerrainMath.samplingBudgetNanos(1, 30));
        assertEquals(500_000, TerrainMath.samplingBudgetNanos(1, 45));
        assertEquals(100_000, TerrainMath.samplingBudgetNanos(1, 50));
        assertEquals(100_000, TerrainMath.samplingBudgetNanos(1, 90));
    }
}
