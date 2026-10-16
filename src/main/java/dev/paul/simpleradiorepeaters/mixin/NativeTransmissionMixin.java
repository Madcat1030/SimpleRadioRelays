package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.radio.RadioTransmitter;
import dev.paul.simpleradiorepeaters.radio.RadioSpace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** The native reach gate and travel step must measure local Nether distance identically. */
@Mixin(value = RadioTransmitter.class, remap = false)
public abstract class NativeTransmissionMixin {
    @Redirect(method = "shouldRouteTo", at = @At(value = "INVOKE", target =
            "Lcom/codinglitch/simpleradio/central/WorldlyPosition;distance(Lcom/codinglitch/simpleradio/central/WorldlyPosition;)F"))
    private float repeaters$distance(WorldlyPosition from, WorldlyPosition to) {
        return (float) (from.distance(to) * RadioSpace.nativePropagation(from, to).distanceMultiplier());
    }
}
