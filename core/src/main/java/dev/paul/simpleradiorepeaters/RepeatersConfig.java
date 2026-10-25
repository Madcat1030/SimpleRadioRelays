package dev.paul.simpleradiorepeaters;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;

public final class RepeatersConfig {
    public static RepeatersConfig INSTANCE = new RepeatersConfig();
    public int maxRelayHops = 64;
    public boolean persistentUnloadedTowers = true;
    public Terrain terrain = new Terrain();

    public static final class Terrain {
        public dev.paul.simpleradiorepeaters.terrain.TerrainMode mode = dev.paul.simpleradiorepeaters.terrain.TerrainMode.RELAY_MAPPING;
        public int sampleSpacing = 64;
        public int maxSamplesPerPath = 1024;
        public int samplesPerTick = 4;
        public double clearance = 32;
        public double nearGroundLoss = 0.10;
        public double buriedLoss = 0.35;
        public double maxElevationAntennaBonus = 0.10;
        public int elevationReferenceY = 64;
        public int elevationFullBonusY = 192;

        public void validate() {
            if (mode == null) mode = dev.paul.simpleradiorepeaters.terrain.TerrainMode.RELAY_MAPPING;
            sampleSpacing = Math.max(16, Math.min(256, sampleSpacing));
            maxSamplesPerPath = Math.max(2, Math.min(4096, maxSamplesPerPath));
            samplesPerTick = Math.max(1, Math.min(64, samplesPerTick));
            clearance = finite(clearance, 32, 1, 256);
            nearGroundLoss = finite(nearGroundLoss, .10, 0, 2);
            buriedLoss = finite(buriedLoss, .35, nearGroundLoss, 4);
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
    public static void load() {
        var path = FabricLoader.getInstance().getConfigDir().resolve("simpleradio-repeaters.json");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) {
                RepeatersConfig loaded = gson.fromJson(Files.readString(path), RepeatersConfig.class);
                if (loaded != null) INSTANCE = loaded;
            }
            INSTANCE.maxRelayHops = Math.max(1, Math.min(128, INSTANCE.maxRelayHops));
            if (INSTANCE.terrain == null) INSTANCE.terrain = new Terrain();
            INSTANCE.terrain.validate();
            Files.createDirectories(path.getParent());
            save();
        } catch (Exception e) { SimpleRadioRepeaters.LOGGER.error("Cannot load repeater configuration; using defaults", e); }
    }
}
