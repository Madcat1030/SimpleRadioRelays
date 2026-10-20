package dev.paul.simpleradiorepeaters.radio;

import dev.paul.simpleradiorepeaters.network.TowerRecord;
import net.minecraft.server.MinecraftServer;
import java.util.List;

/** Optional propagation policy. The default preserves Simple Radio exactly. */
public interface RadioPolicy {
    RadioPolicy NATIVE = new RadioPolicy() {};
    default boolean nativeCrossDimensional(boolean configured) { return configured; }
    default List<RadioSpace.Site> sites(MinecraftServer server, TowerRecord tower, RadioSpace.Site physical) {
        return List.of(physical);
    }
    default RadioPropagation.Profile propagation(RadioSpace.Site from, RadioSpace.Site to, boolean dimensional) {
        return RadioPropagation.NORMAL;
    }
    /** Cheap cache scope; no terrain access or configuration rebuilding here. */
    default int fingerprint() { return 0; }
}
