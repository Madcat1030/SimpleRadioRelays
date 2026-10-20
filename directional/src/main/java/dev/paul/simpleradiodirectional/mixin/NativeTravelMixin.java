package dev.paul.simpleradiodirectional.mixin;

import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.radio.*;
import com.codinglitch.simpleradio.routers.Router;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.paul.simpleradiodirectional.radio.DirectionalReception;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RadioSource.class, remap = false)
public abstract class NativeTravelMixin {
    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target = "Lcom/codinglitch/simpleradio/radio/RadioReceiver;getPower()F"))
    private float directional$reception(float original, Router from, Router to, Medium medium) {
        return to instanceof RadioReceiver receiver && medium instanceof Frequency frequency
                ? DirectionalReception.reception(original, receiver, from.getLocation(), frequency.getModulation()) : original;
    }
}
