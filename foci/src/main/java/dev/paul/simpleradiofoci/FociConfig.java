package dev.paul.simpleradiofoci;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.io.IOException;
public final class FociConfig {
    public static FociConfig INSTANCE = new FociConfig();
    public boolean netherTweaks = true;
    public static java.nio.file.Path path() { return FabricLoader.getInstance().getConfigDir().resolve("simpleradio-repeaters-foci.json"); }
    public static void save() throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(INSTANCE) + "\n");
    }
    public static void load() {
        try {
            if (Files.exists(path())) {
                FociConfig value = new GsonBuilder().create().fromJson(Files.readString(path()), FociConfig.class);
                if (value != null) INSTANCE = value;
            }
            save();
        } catch (Exception error) { DimensionalFoci.LOGGER.error("Cannot load dimensional foci settings", error); }
    }
}
