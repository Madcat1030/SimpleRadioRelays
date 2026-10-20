package dev.paul.simpleradiodirectional.mixin;

import com.codinglitch.simpleradio.central.FrequencingType;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import com.llamalad7.mixinextras.injector.*;
import dev.paul.simpleradiodirectional.radio.*;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Optional: never loaded on an installation without Simple Radio Repeaters. */
@Pseudo
@Mixin(targets = "dev.paul.simpleradiorepeaters.radio.NativeRadioAdapter", remap = false)
public abstract class RepeatersAdapterMixin {
    @ModifyArgs(method = "receiveAtTower(Ldev/paul/simpleradiorepeaters/radio/RadioSpace$Leg;DD" +
            "Lcom/codinglitch/simpleradio/central/FrequencingType;Ldev/paul/simpleradiorepeaters/network/TowerRecord;" +
            "Ldev/paul/simpleradiorepeaters/network/ChannelKey;)Ldev/paul/simpleradiorepeaters/radio/RadioLinkMath$Result;",
            at = @At(value = "INVOKE", target = "Ldev/paul/simpleradiorepeaters/radio/NativeRadioAdapter;receive(DDD" +
                    "Lcom/codinglitch/simpleradio/central/FrequencingType;DDLdev/paul/simpleradiorepeaters/network/ChannelKey;" +
                    "ZLdev/paul/simpleradiorepeaters/radio/RadioPropagation$Profile;)Ldev/paul/simpleradiorepeaters/radio/RadioLinkMath$Result;"))
    private static void directional$tower(Args args, RadioSpace.Leg leg, double power, double cap,
                                          FrequencingType sender, TowerRecord receiver, ChannelKey channel) {
        if (leg.from().projected() || leg.to().projected() || leg.from().level() != leg.to().level()) return;
        double original = args.get(4), best = 0;
        double nominal = NativeRadioAdapter.departurePower(receiver, channel, 1);
        for (var dish : DishManager.relays(receiver.dimension(), receiver.position())) best = Math.max(best,
                DirectionalMath.bonus(original, nominal, SimpleRadioFrequencing.TRANSMITTER.transmissionDiminishment,
                        dish.weight(leg.from().position().x, leg.from().position().z)));
        args.set(4, original + best);
    }
    @ModifyExpressionValue(method = "receiveAtReceiver(Ldev/paul/simpleradiorepeaters/radio/RadioSpace$Leg;DD" +
            "Lcom/codinglitch/simpleradio/central/FrequencingType;Lcom/codinglitch/simpleradio/radio/RadioReceiver;" +
            "Ldev/paul/simpleradiorepeaters/network/ChannelKey;)Ldev/paul/simpleradiorepeaters/radio/RadioLinkMath$Result;",
            at = @At(value = "INVOKE", target = "Lcom/codinglitch/simpleradio/radio/RadioReceiver;getPower()F"))
    private static float directional$receiver(float original, RadioSpace.Leg leg, double power, double cap,
                                               FrequencingType sender, RadioReceiver receiver, ChannelKey channel) {
        return leg.from().projected() || leg.to().projected() ? original
                : DirectionalReception.reception(original, receiver, leg.from().level(), leg.from().position().x, leg.from().position().z, channel.mode());
    }
    @ModifyReturnValue(method = "configurationStamp", at = @At("RETURN"))
    private static int directional$invalidateLinks(int original) { return 31 * original + DishManager.revision(); }
}
