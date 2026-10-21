package dev.paul.simpleradiodirectional.radio;

import dev.paul.simpleradiorepeaters.network.TowerManager;
import net.minecraft.server.MinecraftServer;

/** Loaded only when Repeaters is installed; invalidation stays off the voice-packet path. */
final class RepeatersLinkCache {
    private RepeatersLinkCache() {}
    static void invalidate(MinecraftServer server) { TowerManager.resourcesReloaded(server); }
}
