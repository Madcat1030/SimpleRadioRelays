package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.paul.simpleradiorepeaters.radio.TowerPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RadioReceiver.class, remap = false)
public abstract class TowerReceiverPowerMixin {
    @ModifyReturnValue(method = "getPower()F", at = @At("RETURN"))
    private float repeaters$basePower(float original) {
        var receiver = (RadioReceiver) (Object) this;
        return receiver.frequencingType == SimpleRadioFrequencing.RECEIVER
                ? (float) TowerPower.apply(original, receiver.frequencingType.receptionPower, receiver.antennaPower)
                : original;
    }
}
