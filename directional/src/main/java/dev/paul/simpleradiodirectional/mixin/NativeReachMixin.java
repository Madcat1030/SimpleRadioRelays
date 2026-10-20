package dev.paul.simpleradiodirectional.mixin;

import com.codinglitch.simpleradio.radio.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.paul.simpleradiodirectional.radio.DirectionalReception;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RadioTransmitter.class, remap = false)
public abstract class NativeReachMixin {
    @ModifyExpressionValue(method = "shouldRouteTo", at = @At(value = "INVOKE", target = "Lcom/codinglitch/simpleradio/radio/RadioReceiver;getPower()F"))
    private float directional$reception(float original, RadioSource source, RadioRouter destination) {
        var transmitter = (RadioTransmitter) (Object) this;
        return destination instanceof RadioReceiver receiver && transmitter.frequency != null
                ? DirectionalReception.reception(original, receiver, transmitter.getLocation(), transmitter.frequency.getModulation()) : original;
    }
}
