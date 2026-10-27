package dev.paul.simpleradiofoci;

import com.google.gson.*;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.metadata.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FociCompatibilityTest {
    private static final String API_KEY = "simpleradio_repeaters:foci_api";
    @TempDir Path directory;

    private JsonObject resource(boolean core) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(core
                ? "../core/build/resources/main/fabric.mod.json" : "build/resources/main/fabric.mod.json"))).getAsJsonObject();
    }
    private ModMetadata metadata(JsonObject json) throws Exception {
        try (var input = new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8))) {
            return ModMetadataParser.parseMetadata(input, "compatibility-fixture", List.of(),
                    new VersionOverrides(), new DependencyOverrides(directory), true);
        }
    }

    @Test void actualMetadataAcceptsDifferentCoreVersionsWithTheSameApi() throws Exception {
        var core = resource(true); var foci = resource(false);
        var installed = metadata(foci);
        var dependency = installed.getDependencies().stream().filter(d -> d.getKind() == ModDependency.Kind.DEPENDS
                && d.getModId().equals("simpleradio_repeaters")).findFirst().orElseThrow();
        assertFalse(dependency.matches(Version.parse("0.7.2")));
        assertTrue(dependency.matches(Version.parse("0.7.3")));
        assertTrue(dependency.matches(Version.parse("0.9.0-rc.1")));
        assertTrue(dependency.matches(Version.parse("0.99.0")));
        assertEquals(FociCompatibility.class.getName(), foci.getAsJsonObject("entrypoints")
                .getAsJsonArray("preLaunch").get(0).getAsString());
        core.addProperty("version", "0.99.0");
        assertNotEquals(installed.getVersion().getFriendlyString(), "0.99.0");
        assertEquals(1, FociCompatibility.requireCompatible(metadata(core), installed));
    }

    @Test void differentApiIsRejectedWithInstalledVersionsAndRequiredApi() throws Exception {
        var core = resource(true); core.addProperty("version", "0.99.0");
        core.getAsJsonObject("custom").addProperty(API_KEY, 2);
        var offered = metadata(core); var required = metadata(resource(false));
        var failure = assertThrows(IllegalStateException.class, () -> FociCompatibility.requireCompatible(offered, required));
        assertTrue(failure.getMessage().contains("Dimensional Foci " + required.getVersion().getFriendlyString()));
        assertTrue(failure.getMessage().contains("requires Foci API 1"));
        assertTrue(failure.getMessage().contains("Repeaters 0.99.0 provides API 2"));
    }

    @Test void missingOrMalformedMarkersFailClosedIncludingFractionalNumbers() throws Exception {
        var foci = metadata(resource(false));
        for (String json : new String[]{"null", "\"1\"", "1.5", "0", "-1", "2147483648", "{}"}) {
            var core = resource(true); core.getAsJsonObject("custom").add(API_KEY, JsonParser.parseString(json));
            var offered = metadata(core);
            var failure = assertThrows(IllegalStateException.class, () -> FociCompatibility.requireCompatible(offered, foci));
            assertTrue(failure.getMessage().contains("no valid Foci API marker"));
        }
        var core = resource(true); core.getAsJsonObject("custom").remove(API_KEY);
        var absent = metadata(core);
        assertThrows(IllegalStateException.class, () -> FociCompatibility.requireCompatible(absent, foci));
        var malformedFoci = resource(false); malformedFoci.getAsJsonObject("custom").remove(API_KEY);
        var addon = metadata(malformedFoci); var normal = metadata(resource(true));
        assertThrows(IllegalStateException.class, () -> FociCompatibility.requireCompatible(normal, addon));
    }
}
