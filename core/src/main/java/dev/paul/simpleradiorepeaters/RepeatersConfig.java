package dev.paul.simpleradiorepeaters;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;

public final class RepeatersConfig {
    public static RepeatersConfig INSTANCE = new RepeatersConfig();
    public int maxRelayHops = 64;
    public boolean persistentUnloadedTowers = true;
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
            Files.createDirectories(path.getParent());
            save();
        } catch (Exception e) { SimpleRadioRepeaters.LOGGER.error("Cannot load repeater configuration; using defaults", e); }
    }
}
