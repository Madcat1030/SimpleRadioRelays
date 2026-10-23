package dev.paul.simpleradiorepeaters.radio;

import com.codinglitch.simpleradio.radio.RadioReceiver;
import dev.paul.simpleradiorepeaters.api.RelayIntegration;
import dev.paul.simpleradiorepeaters.network.ChannelKey;
import dev.paul.simpleradiorepeaters.network.TowerRecord;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.List;

/** Resolved once during initialization; no classpath discovery on the packet path. */
public final class RelayIntegrations {
    private static List<RelayIntegration> extensions = List.of();

    private RelayIntegrations() {}

    public static void initialize() {
        extensions = List.copyOf(FabricLoader.getInstance().getEntrypoints(
                "simpleradio_repeaters:integration", RelayIntegration.class));
    }

    public static double receptionAtTower(double original, RadioSpace.Leg leg, TowerRecord tower, ChannelKey channel) {
        for (var extension : extensions) original = extension.receptionAtTower(original, leg, tower, channel);
        return original;
    }

    public static float receptionAtReceiver(float original, RadioSpace.Leg leg, RadioReceiver receiver, ChannelKey channel) {
        for (var extension : extensions) original = extension.receptionAtReceiver(original, leg, receiver, channel);
        return original;
    }

    public static void relayTick(ServerLevel level, BlockPos relay, BlockPos core) {
        for (var extension : extensions) extension.relayTick(level, relay, core);
    }

    public static void antennaDirty(ServerLevel level, BlockPos relay) {
        for (var extension : extensions) extension.antennaDirty(level, relay);
    }

    public static int configurationStamp() {
        int stamp = 0;
        for (var extension : extensions) stamp = 31 * stamp + extension.configurationStamp();
        return stamp;
    }
}
