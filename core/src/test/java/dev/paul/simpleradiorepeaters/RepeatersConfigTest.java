package dev.paul.simpleradiorepeaters;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepeatersConfigTest {
    @Test void oldDefaultsMigrateButCustomSettingsSurvive() {
        var migrated = RepeatersConfig.parse("{\"terrain\":{\"nearGroundLoss\":0.1,\"samplesPerTick\":4,\"clearance\":32,\"buriedLoss\":0.35}}");
        assertEquals(.01, migrated.terrain.nearGroundLoss);
        assertEquals(64, migrated.terrain.samplesPerTick);
        assertEquals(8, migrated.terrain.nearGroundHeight);
        assertArrayEquals(new double[]{.10, .25, .50, 1, 1.5, 2}, migrated.terrain.undergroundLosses);
        var custom = RepeatersConfig.parse("{\"terrain\":{\"nearGroundLoss\":0.015,\"samplesPerTick\":12}}");
        assertEquals(.015, custom.terrain.nearGroundLoss);
        assertEquals(12, custom.terrain.samplesPerTick);
        var modern = RepeatersConfig.parse("{\"terrain\":{\"formatVersion\":2,\"nearGroundLoss\":0.1,\"samplesPerTick\":4}}");
        assertEquals(.1, modern.terrain.nearGroundLoss);
        assertEquals(4, modern.terrain.samplesPerTick);
    }
    @Test void malformedConfigurationIsRejectedRatherThanReplacedWithDefaults() {
        assertThrows(RuntimeException.class, () -> RepeatersConfig.parse("{\"terrain\":"));
        assertThrows(RuntimeException.class, () -> RepeatersConfig.parse("[]"));
        assertThrows(RuntimeException.class, () -> RepeatersConfig.parse("null"));
    }
    @Test void undergroundCurveAndSamplingBudgetsAreValidated() {
        var cfg = new RepeatersConfig.Terrain();
        cfg.undergroundLosses = new double[]{.5, .2, Double.NaN, 1, 1.5, 8};
        cfg.sampleBudgetMs = Double.NaN; cfg.validate();
        assertArrayEquals(new double[]{.5, .5, .5, 1, 1.5, 4}, cfg.undergroundLosses);
        assertEquals(1, cfg.sampleBudgetMs);
        cfg.undergroundLosses = null; cfg.validate();
        assertArrayEquals(new double[]{.10, .25, .50, 1, 1.5, 2}, cfg.undergroundLosses);
    }
}
