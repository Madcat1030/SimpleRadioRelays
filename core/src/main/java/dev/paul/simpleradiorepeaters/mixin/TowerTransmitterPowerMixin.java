package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.RadioTransmitter;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.paul.simpleradiorepeaters.radio.TowerPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RadioTransmitter.class, remap = false)
public abstract class TowerTransmitterPowerMixin {
    @ModifyReturnValue(method = "getPower(Lcom/codinglitch/simpleradio/central/Frequency$Modulation;)F", at = @At("RETURN"))
    private float repeaters$basePower(float original, Frequency.Modulation mode) {
        var transmitter = (RadioTransmitter) (Object) this;
        return transmitter.frequencingType == SimpleRadioFrequencing.TRANSMITTER
                ? (float) TowerPower.apply(original, transmitter.frequencingType.getTransmissionPower(mode), transmitter.antennaPower)
                : original;
    }
}
