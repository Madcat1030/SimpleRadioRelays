package dev.paul.simpleradiorepeaters;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RepeatersConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("simpleradio_repeaters");
    public static RepeatersConfig INSTANCE = new RepeatersConfig();
    public int maxRelayHops = 64;
    public boolean persistentUnloadedTowers = true;
    public boolean antennaDependentBasePower = true;
    public Terrain terrain = new Terrain();

    public static final class Terrain {
        public int formatVersion = 3;
        public dev.paul.simpleradiorepeaters.terrain.TerrainMode mode = dev.paul.simpleradiorepeaters.terrain.TerrainMode.RELAY_MAPPING;
        public int sampleSpacing = 64;
        public int maxSamplesPerPath = 1024;
        public int samplesPerTick = 64;
        public double sampleBudgetMs = 1;
        /** Rows are {clearance in blocks, extra-distance fraction}, highest clearance first. */
        public double[][] clearanceCurve = {
                {8, 0}, {2, .01}, {0, .05}, {-8, .10}, {-16, .25},
                {-32, .50}, {-48, 1}, {-64, 2}, {-128, 3}};
        public double maxElevationAntennaBonus = 0.10;
        public int elevationReferenceY = 64;
        public int elevationFullBonusY = 192;

        public void validate() {
            if (mode == null) mode = dev.paul.simpleradiorepeaters.terrain.TerrainMode.RELAY_MAPPING;
            sampleSpacing = Math.max(16, Math.min(256, sampleSpacing));
            maxSamplesPerPath = Math.max(2, Math.min(4096, maxSamplesPerPath));
            samplesPerTick = Math.max(1, Math.min(64, samplesPerTick));
            sampleBudgetMs = finite(sampleBudgetMs, 1, .1, 5);
            if (clearanceCurve == null || clearanceCurve.length < 2 || clearanceCurve.length > 64)
                throw new IllegalArgumentException("terrain.clearanceCurve needs 2 to 64 [clearance, extra-distance] points");
            double previousHeight = Double.POSITIVE_INFINITY, previousLoss = 0;
            for (double[] point : clearanceCurve) {
                if (point == null || point.length != 2 || !Double.isFinite(point[0]) || Math.abs(point[0]) > 4096
                        || !Double.isFinite(point[1]) || point[0] >= previousHeight || point[1] < previousLoss || point[1] > 4)
                    throw new IllegalArgumentException("terrain.clearanceCurve requires descending clearances within +/-4096 and nondecreasing extra-distance fractions from 0 to 4");
                previousHeight = point[0]; previousLoss = point[1];
            }
            if (clearanceCurve[0][1] != 0)
                throw new IllegalArgumentException("terrain.clearanceCurve must start with zero extra distance");
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
            if (terrain.has("samplesPerTick") && loaded.terrain.samplesPerTick == 4) loaded.terrain.samplesPerTick = 64;
        }
        if (terrain != null) migrateCurve(terrain, loaded.terrain);
        loaded.terrain.formatVersion = 3;
        loaded.maxRelayHops = Math.max(1, Math.min(128, loaded.maxRelayHops));
        loaded.terrain.validate();
        return loaded;
    }
    private static void migrateCurve(JsonObject terrain, Terrain loaded) {
        boolean legacy = terrain.has("nearGroundHeight") || terrain.has("nearGroundLoss") || terrain.has("undergroundLosses")
                || terrain.has("clearance") || terrain.has("buriedLoss");
        if (!legacy) return;
        if (!terrain.has("clearanceCurve")) {
            double top = terrain.has("nearGroundHeight") ? terrain.get("nearGroundHeight").getAsDouble() : 8;
            double surface = terrain.has("nearGroundLoss") ? terrain.get("nearGroundLoss").getAsDouble() : .01;
            if (!terrain.has("formatVersion") && surface == .10) surface = .01;
            double[] defaults = {.10, .25, .50, 1, 1.5, 2};
            double[] losses = terrain.has("undergroundLosses") ? new Gson().fromJson(terrain.get("undergroundLosses"), double[].class) : defaults;
            if (losses == null || losses.length != 6)
                throw new JsonParseException("Legacy undergroundLosses needs six values; use terrain.clearanceCurve for a new curve");
            if (top != 8 || surface != .01 || !Arrays.equals(losses, defaults)) {
                loaded.clearanceCurve = new double[][]{{top, 0}, {0, surface}, {-8, losses[0]}, {-16, losses[1]},
                        {-32, losses[2]}, {-64, losses[3]}, {-96, losses[4]}, {-128, losses[5]}};
            }
        }
        LOGGER.warn("Retired terrain curve settings were replaced by terrain.clearanceCurve; old defaults use the new balance curve, customized curves are converted. Review the saved points.");
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
