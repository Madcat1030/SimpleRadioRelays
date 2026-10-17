package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.radio.RadioTransmitter;
import com.codinglitch.simpleradio.radio.RadioRouter;
import com.codinglitch.simpleradio.radio.RadioSource;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.paul.simpleradiorepeaters.radio.RadioSpace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** The native reach gate and travel step must measure local Nether distance identically. */
@Mixin(value = RadioTransmitter.class, remap = false)
public abstract class NativeTransmissionMixin {
    @ModifyExpressionValue(method = "shouldRouteTo", at = @At(value = "INVOKE", target =
            "Lcom/codinglitch/simpleradio/central/WorldlyPosition;distance(Lcom/codinglitch/simpleradio/central/WorldlyPosition;)F"))
    private float repeaters$distance(float original, RadioSource source, RadioRouter destination) {
        return (float) (original * RadioSpace.nativePropagation(((RadioTransmitter) (Object) this).getLocation(),
                destination.getLocation()).distanceMultiplier());
    }
}
