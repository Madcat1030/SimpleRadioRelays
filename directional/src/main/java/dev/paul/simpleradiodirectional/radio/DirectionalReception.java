package dev.paul.simpleradiodirectional.radio;

import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import net.minecraft.server.level.ServerLevel;

public final class DirectionalReception {
    private DirectionalReception() {}
    public static float reception(float original, RadioReceiver receiver, WorldlyPosition from, Frequency.Modulation mode) {
        return from == null || !(from.level instanceof ServerLevel level) ? original
                : reception(original, receiver, level, from.x, from.z, mode);
    }
    public static float reception(float original, RadioReceiver receiver, ServerLevel from, double x, double z, Frequency.Modulation mode) {
        var location = receiver.getLocation();
        if (receiver.owner != null || location == null || location.level != from) return original;
        var transmitter = SimpleRadioFrequencing.TRANSMITTER;
        double nominal = transmitter.getTransmissionPower(mode) + receiver.antennaPower * (double) transmitter.antennaAptitude;
        double best = 0;
        for (Dish dish : DishManager.receivers(receiver)) best = Math.max(best,
                DirectionalMath.bonus(original, nominal, transmitter.transmissionDiminishment, dish.weight(x,z)));
        return (float) (original + best);
    }
}
