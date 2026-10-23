package dev.paul.simpleradiorepeaters.api;

import com.codinglitch.simpleradio.radio.RadioReceiver;
import dev.paul.simpleradiorepeaters.network.ChannelKey;
import dev.paul.simpleradiorepeaters.network.TowerRecord;
import dev.paul.simpleradiorepeaters.radio.RadioSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional reception extension, discovered through the {@code simpleradio_repeaters:integration}
 * Fabric entrypoint. Implementations are instantiated only when Repeaters is installed.
 * Calls run on the server thread; reception hooks must only read cached state, never scan chunks.
 * This is independent of the exclusive dimensional propagation policy.
 */
public interface RelayIntegration {
    default double receptionAtTower(double original, RadioSpace.Leg leg, TowerRecord tower, ChannelKey channel) {
        return original;
    }

    default float receptionAtReceiver(float original, RadioSpace.Leg leg, RadioReceiver receiver, ChannelKey channel) {
        return original;
    }

    default void relayTick(ServerLevel level, BlockPos relay, BlockPos core) {}

    default void antennaDirty(ServerLevel level, BlockPos relay) {}

    /** Changes only when cached relay reception changes, not when an ordinary receiver loads. */
    default int configurationStamp() { return 0; }
}
