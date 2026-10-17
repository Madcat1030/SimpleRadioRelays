package dev.paul.simpleradiorepeaters.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.radio.RadioSource;
import com.codinglitch.simpleradio.radio.RadioTransmitter;
import com.codinglitch.simpleradio.routers.Router;
import dev.paul.simpleradiorepeaters.network.ChannelKey;
import dev.paul.simpleradiorepeaters.radio.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native endpoints share the relay propagation policy; wires retain their native calculations. */
@Mixin(value = RadioSource.class, remap = false)
public abstract class NativeTravelMixin {
    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target =
            "Lcom/codinglitch/simpleradio/central/WorldlyPosition;distance(Lcom/codinglitch/simpleradio/central/WorldlyPosition;)F"))
    private float repeaters$distance(float original, Router sender, Router receiver, Medium medium) {
        return (float) (original * (medium instanceof Frequency && sender instanceof RadioTransmitter
                ? RadioSpace.nativePropagation(sender.getLocation(), receiver.getLocation()).distanceMultiplier() : 1));
    }
    @Inject(method = "travel", at = @At("RETURN"))
    private void repeaters$noiseFloor(Router from, Router to, Medium medium, CallbackInfo ci) {
        if (!(from instanceof RadioTransmitter) || !(medium instanceof Frequency frequency)) return;
        double extra = RadioSpace.nativePropagation(from.getLocation(), to.getLocation()).noiseFloorAddition();
        if (extra <= 0) return;
        RelaySourceState state = (RelaySourceState) this;
        state.repeaters$setInheritedSeverity(Math.max(state.repeaters$getInheritedSeverity(),
                Math.min(100, NativeRadioAdapter.base(ChannelKey.from(frequency)) + extra)));
    }
}
