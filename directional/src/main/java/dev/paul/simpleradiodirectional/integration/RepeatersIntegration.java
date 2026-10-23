package dev.paul.simpleradiodirectional.integration;

import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import dev.paul.simpleradiodirectional.radio.DirectionalMath;
import dev.paul.simpleradiodirectional.radio.DirectionalReception;
import dev.paul.simpleradiodirectional.radio.DishManager;
import dev.paul.simpleradiorepeaters.api.RelayIntegration;
import dev.paul.simpleradiorepeaters.network.ChannelKey;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import dev.paul.simpleradiorepeaters.network.TowerRecord;
import dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter;
import dev.paul.simpleradiorepeaters.radio.RadioSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Fabric loads this optional entrypoint only when Repeaters requests it. */
public final class RepeatersIntegration implements RelayIntegration {
    public RepeatersIntegration() {
        DishManager.onRelayChange(server -> TowerManager.resourcesReloaded(server, "directional_reception_changed"));
    }

    @Override
    public double receptionAtTower(double original, RadioSpace.Leg leg, TowerRecord tower, ChannelKey channel) {
        if (leg.from().projected() || leg.to().projected() || leg.from().level() != leg.to().level()) return original;
        double nominal = NativeRadioAdapter.departurePower(tower, channel, 1);
        double best = 0;
        for (var dish : DishManager.relays(tower.dimension(), tower.position())) {
            best = Math.max(best, DirectionalMath.bonus(original, nominal,
                    SimpleRadioFrequencing.TRANSMITTER.transmissionDiminishment,
                    dish.weight(leg.from().position().x, leg.from().position().z)));
        }
        return original + best;
    }

    @Override
    public float receptionAtReceiver(float original, RadioSpace.Leg leg, RadioReceiver receiver, ChannelKey channel) {
        return leg.from().projected() || leg.to().projected() ? original
                : DirectionalReception.reception(original, receiver, leg.from().level(),
                        leg.from().position().x, leg.from().position().z, channel.mode());
    }

    @Override
    public void relayTick(ServerLevel level, BlockPos relay, BlockPos core) {
        DishManager.refreshRelay(level, relay, core);
    }

    @Override
    public void antennaDirty(ServerLevel level, BlockPos relay) {
        DishManager.dirtyRelay(level, relay);
    }

    @Override
    public int configurationStamp() { return DishManager.relayRevision(); }
}
