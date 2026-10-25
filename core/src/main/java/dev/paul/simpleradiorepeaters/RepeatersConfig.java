package dev.paul.simpleradiorepeaters;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RepeatersConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("simpleradio_repeaters");
    public static RepeatersConfig INSTANCE = new RepeatersConfig();
    public int maxRelayHops = 64;
    public boolean persistentUnloadedTowers = true;
    public Terrain terrain = new Terrain();

    public static final class Terrain {
        public int formatVersion = 2;
        public dev.paul.simpleradiorepeaters.terrain.TerrainMode mode = dev.paul.simpleradiorepeaters.terrain.TerrainMode.RELAY_MAPPING;
        public int sampleSpacing = 64;
        public int maxSamplesPerPath = 1024;
        public int samplesPerTick = 64;
        public double sampleBudgetMs = 1;
        public double nearGroundHeight = 8;
        public double nearGroundLoss = 0.01;
        /** Extra-distance fractions at depths 8, 16, 32, 64, 96 and 128. */
        public double[] undergroundLosses = {.10, .25, .50, 1, 1.5, 2};
        public double maxElevationAntennaBonus = 0.10;
        public int elevationReferenceY = 64;
        public int elevationFullBonusY = 192;

        public void validate() {
            if (mode == null) mode = dev.paul.simpleradiorepeaters.terrain.TerrainMode.RELAY_MAPPING;
            sampleSpacing = Math.max(16, Math.min(256, sampleSpacing));
            maxSamplesPerPath = Math.max(2, Math.min(4096, maxSamplesPerPath));
            samplesPerTick = Math.max(1, Math.min(64, samplesPerTick));
            sampleBudgetMs = finite(sampleBudgetMs, 1, .1, 5);
            nearGroundHeight = finite(nearGroundHeight, 8, 1.01, 64);
            nearGroundLoss = finite(nearGroundLoss, .01, 0, .25);
            double[] defaults = {.10, .25, .50, 1, 1.5, 2};
            if (undergroundLosses == null || undergroundLosses.length != defaults.length) undergroundLosses = defaults.clone();
            double previous = nearGroundLoss;
            for (int i = 0; i < undergroundLosses.length; i++)
                previous = undergroundLosses[i] = finite(undergroundLosses[i], Math.max(previous, defaults[i]), previous, 4);
            maxElevationAntennaBonus = finite(maxElevationAntennaBonus, .10, 0, .50);
            elevationReferenceY = Math.max(-64, Math.min(319, elevationReferenceY));
            elevationFullBonusY = Math.max(elevationReferenceY + 1, Math.min(320, elevationFullBonusY));
        }
        private static double finite(double v, double fallback, double min, double max) {
            return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : fallback;
        }
    }
    public static void save() throws IOException {
        Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("simpleradio-repeaters.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(INSTANCE) + "\n");
    }
    /** Parse and validate before replacing the live settings or touching the file. */
    static RepeatersConfig parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        RepeatersConfig loaded = new Gson().fromJson(root, RepeatersConfig.class);
        if (loaded == null) throw new JsonParseException("Expected a configuration object");
        if (loaded.terrain == null) loaded.terrain = new Terrain();
        JsonObject terrain = root.has("terrain") && root.get("terrain").isJsonObject() ? root.getAsJsonObject("terrain") : null;
        if (terrain != null && !terrain.has("formatVersion")) {
            // Migrate only the old defaults; preserve deliberately different server settings.
            if (terrain.has("nearGroundLoss") && loaded.terrain.nearGroundLoss == .10) loaded.terrain.nearGroundLoss = .01;
            if (terrain.has("samplesPerTick") && loaded.terrain.samplesPerTick == 4) loaded.terrain.samplesPerTick = 64;
        }
        loaded.terrain.formatVersion = 2;
        loaded.maxRelayHops = Math.max(1, Math.min(128, loaded.maxRelayHops));
        loaded.terrain.validate();
        if (terrain != null && (terrain.has("clearance") || terrain.has("buriedLoss")))
            LOGGER.warn("Terrain settings 'clearance' and 'buriedLoss' are no longer used; review 'nearGroundHeight', 'nearGroundLoss' and 'undergroundLosses'.");
        return loaded;
    }
    public static boolean load() {
        return load(FabricLoader.getInstance().getConfigDir().resolve("simpleradio-repeaters.json"));
    }
    static boolean load(Path path) {
        RepeatersConfig loaded;
        try {
            loaded = Files.exists(path) ? parse(Files.readString(path)) : new RepeatersConfig();
        } catch (Exception e) {
            LOGGER.error("Cannot load repeater configuration; keeping current settings", e);
            return false;
        }
        INSTANCE = loaded;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(loaded) + "\n");
        } catch (Exception e) {
            LOGGER.warn("Repeater configuration is active, but its normalized file could not be saved to {}", path, e);
        }
        return true;
    }
}
