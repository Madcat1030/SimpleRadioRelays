package dev.paul.simpleradiorepeaters.gametest;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** QA-only sentinel, appended to Foci's preLaunch list by the compatibility smoke test. */
public final class FociCompatibilityProbe implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        throw new IllegalStateException("FOCI_COMPATIBILITY_PROBE_COMPLETE: deliberate stop before game initialization");
    }
}
