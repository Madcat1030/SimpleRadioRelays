package dev.paul.simpleradiofoci;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;

/** Metadata only: do not load Minecraft or the Foci initializer before this check. */
public final class FociCompatibility implements PreLaunchEntrypoint {
    private static final String API_KEY = "simpleradio_repeaters:foci_api";

    @Override public void onPreLaunch() {
        var loader = FabricLoader.getInstance();
        var core = loader.getModContainer("simpleradio_repeaters").orElseThrow(
                () -> new IllegalStateException("Dimensional Foci requires Simple Radio Repeaters.")).getMetadata();
        var foci = loader.getModContainer("simpleradio_repeaters_foci").orElseThrow().getMetadata();
        int api = requireCompatible(core, foci);
        LoggerFactory.getLogger("simpleradio_repeaters_foci").info(
                "Foci API {} accepted: Repeaters {}, Dimensional Foci {}", api, core.getVersion(), foci.getVersion());
    }

    static int requireCompatible(ModMetadata core, ModMetadata foci) {
        int offered = api(core), required = api(foci);
        if (offered != required)
            throw new IllegalStateException("Dimensional Foci " + foci.getVersion().getFriendlyString()
                    + " requires Foci API " + required + ", but Repeaters " + core.getVersion().getFriendlyString()
                    + " provides API " + offered + ". Install a compatible Dimensional Foci release.");
        return offered;
    }

    private static int api(ModMetadata metadata) {
        var value = metadata.getCustomValue(API_KEY);
        if (value != null && value.getType() == CustomValue.CvType.NUMBER) {
            try {
                int api = new BigDecimal(value.getAsNumber().toString()).intValueExact();
                if (api > 0) return api;
            } catch (NumberFormatException | ArithmeticException ignored) { }
        }
        throw new IllegalStateException(metadata.getName() + " " + metadata.getVersion().getFriendlyString()
                + " has no valid Foci API marker (" + API_KEY + "). Install a compatible mod build.");
    }
}
