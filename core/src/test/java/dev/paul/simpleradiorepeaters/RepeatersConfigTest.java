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
            assertTrue(Files.readString(config).contains("\"formatVersion\": 2"));
            var live = RepeatersConfig.INSTANCE;
            Files.writeString(config, "{invalid");
            assertFalse(RepeatersConfig.load(config));
            assertSame(live, RepeatersConfig.INSTANCE);
            assertEquals("{invalid", Files.readString(config));
        } finally { RepeatersConfig.INSTANCE = previous; }
    }

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
