package dev.paul.simpleradiorepeaters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class RepeatersConfigTest {
    @TempDir Path directory;

    @Test void antennaBalanceDefaultsOnAndCanBeDisabledIndependentlyOfTerrain() {
        assertTrue(RepeatersConfig.parse("{}").antennaDependentBasePower);
        var cfg = RepeatersConfig.parse("{\"antennaDependentBasePower\":false,\"terrain\":{\"mode\":\"full\"}}");
        assertFalse(cfg.antennaDependentBasePower);
        assertEquals(dev.paul.simpleradiorepeaters.terrain.TerrainMode.FULL, cfg.terrain.mode);
        assertFalse(RepeatersConfig.parse(new com.google.gson.Gson().toJson(cfg)).antennaDependentBasePower);
        assertTrue(RepeatersConfig.parse("{\"terrain\":{\"mode\":\"off\"}}").antennaDependentBasePower);
    }

    @Test void validReadOnlyConfigIsAppliedWithoutRequiringANormalizationSave() throws Exception {
        Path archive = directory.resolve("read-only.jar");
        String json = "{\"maxRelayHops\":23,\"persistentUnloadedTowers\":false,\"terrain\":{\"mode\":\"off\"}}";
        // A multi-release ZIP view is genuinely read-only, even when tests run as root.
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0\nMulti-Release: true\n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("simpleradio-repeaters.json"));
            zip.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        var previous = RepeatersConfig.INSTANCE;
        try (var fs = FileSystems.newFileSystem(archive, Map.of("multi-release", "runtime"))) {
            assertTrue(fs.isReadOnly());
            Path config = fs.getPath("/simpleradio-repeaters.json");
            assertThrows(ReadOnlyFileSystemException.class, () -> Files.writeString(config, json));
            RepeatersConfig.INSTANCE = new RepeatersConfig();
            assertTrue(RepeatersConfig.load(config));
            assertEquals(dev.paul.simpleradiorepeaters.terrain.TerrainMode.OFF, RepeatersConfig.INSTANCE.terrain.mode);
            assertEquals(23, RepeatersConfig.INSTANCE.maxRelayHops);
            assertFalse(RepeatersConfig.INSTANCE.persistentUnloadedTowers);
            assertEquals(json, Files.readString(config));
        } finally { RepeatersConfig.INSTANCE = previous; }
    }

    @Test void writableConfigNormalizesButMalformedConfigKeepsSettingsAndFile() throws Exception {
        Path config = directory.resolve("simpleradio-repeaters.json");
        var previous = RepeatersConfig.INSTANCE;
        try {
            Files.writeString(config, "{\"maxRelayHops\":999,\"terrain\":{\"mode\":\"off\"}}");
            assertTrue(RepeatersConfig.load(config));
            assertEquals(128, RepeatersConfig.INSTANCE.maxRelayHops);
            assertTrue(Files.readString(config).contains("\"formatVersion\": 3"));
            var live = RepeatersConfig.INSTANCE;
            Files.writeString(config, "{invalid");
            assertFalse(RepeatersConfig.load(config));
            assertSame(live, RepeatersConfig.INSTANCE);
            assertEquals("{invalid", Files.readString(config));
            String badCurve = "{\"terrain\":{\"clearanceCurve\":[[8,0],[8,1]]}}";
            Files.writeString(config, badCurve);
            assertFalse(RepeatersConfig.load(config));
            assertSame(live, RepeatersConfig.INSTANCE);
            assertEquals(badCurve, Files.readString(config));
        } finally { RepeatersConfig.INSTANCE = previous; }
    }

    @Test void oldDefaultsMigrateButCustomSettingsSurvive() {
        var migrated = RepeatersConfig.parse("{\"terrain\":{\"nearGroundLoss\":0.1,\"samplesPerTick\":4,\"clearance\":32,\"buriedLoss\":0.35}}");
        assertEquals(64, migrated.terrain.samplesPerTick);
        assertEquals(.05, loss(migrated, 0));
        assertEquals(3, loss(migrated, -128));
        var previous = RepeatersConfig.parse("{\"terrain\":{\"formatVersion\":2,\"mode\":\"off\",\"nearGroundHeight\":8,\"nearGroundLoss\":0.01,\"undergroundLosses\":[0.1,0.25,0.5,1,1.5,2]}}");
        assertEquals(dev.paul.simpleradiorepeaters.terrain.TerrainMode.OFF, previous.terrain.mode);
        assertEquals(.01, loss(previous, 2));
        assertEquals(1, loss(previous, -48));
        assertEquals(3, loss(previous, -128));
        var custom = RepeatersConfig.parse("{\"terrain\":{\"nearGroundLoss\":0.015,\"samplesPerTick\":12}}");
        assertEquals(.015, loss(custom, 0));
        assertEquals(.0075, loss(custom, 4));
        assertEquals(1, loss(custom, -64));
        assertEquals(2, loss(custom, -128));
        assertEquals(12, custom.terrain.samplesPerTick);
        var modern = RepeatersConfig.parse("{\"terrain\":{\"formatVersion\":2,\"nearGroundLoss\":0.1,\"samplesPerTick\":4}}");
        assertEquals(.1, loss(modern, 0));
        assertEquals(4, modern.terrain.samplesPerTick);
    }
    private static double loss(RepeatersConfig cfg, double clearance) {
        return dev.paul.simpleradiorepeaters.terrain.TerrainMath.loss(clearance, cfg.terrain.clearanceCurve);
    }
    @Test void malformedConfigurationIsRejectedRatherThanReplacedWithDefaults() {
        assertThrows(RuntimeException.class, () -> RepeatersConfig.parse("{\"terrain\":"));
        assertThrows(RuntimeException.class, () -> RepeatersConfig.parse("[]"));
        assertThrows(RuntimeException.class, () -> RepeatersConfig.parse("null"));
    }
    @Test void malformedCurvesAreRejectedAndSamplingBudgetsAreValidated() {
        var cfg = new RepeatersConfig.Terrain();
        cfg.sampleBudgetMs = Double.NaN; cfg.validate();
        assertEquals(1, cfg.sampleBudgetMs);
        for (double[][] invalid : new double[][][]{null, {}, {{8, 0}}, {{8, 0}, {8, 1}}, {{8, .01}, {0, .1}},
                {{8, 0}, {0, -1}}, {{8, 0}, {0, Double.NaN}}, {{8, 0}, {0, 5}}, {{8, 0}, {0}},
                {{8, 0}, {0, 1}, {-8, .5}}}) {
            cfg.clearanceCurve = invalid;
            assertThrows(IllegalArgumentException.class, cfg::validate);
        }
    }
    @Test void explicitCurvesRoundTripAndTakePrecedenceOverRetiredFields() {
        var cfg = RepeatersConfig.parse("{\"terrain\":{\"nearGroundLoss\":0.1,\"clearanceCurve\":[[10,0],[0,0.02],[-64,1.5]]}}");
        assertEquals(.02, loss(cfg, 0));
        assertEquals(1.5, loss(cfg, -64));
        String normalized = new com.google.gson.Gson().toJson(cfg);
        assertFalse(normalized.contains("nearGroundLoss"));
        assertFalse(normalized.contains("undergroundLosses"));
        var restored = RepeatersConfig.parse(normalized);
        assertEquals(loss(cfg, -32), loss(restored, -32));
        assertEquals(3, restored.terrain.formatVersion);
    }
}
