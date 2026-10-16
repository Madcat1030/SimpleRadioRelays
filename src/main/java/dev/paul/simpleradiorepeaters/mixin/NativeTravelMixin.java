package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.SimpleRadioServerConfig;
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
    @Redirect(method = "travel", at = @At(value = "INVOKE", target =
            "Lcom/codinglitch/simpleradio/central/WorldlyPosition;distance(Lcom/codinglitch/simpleradio/central/WorldlyPosition;)F"))
    private float repeaters$distance(WorldlyPosition from, WorldlyPosition to, Router sender, Router receiver, Medium medium) {
        return (float) (from.distance(to) * (medium instanceof Frequency && sender instanceof RadioTransmitter
                ? RadioSpace.nativePropagation(from, to).distanceMultiplier() : 1));
    }
    @Redirect(method = "travel", at = @At(value = "FIELD", target =
            "Lcom/codinglitch/simpleradio/SimpleRadioServerConfig$Frequency;dimensionalInterference:Ljava/lang/Double;"))
    private Double repeaters$dimensionalInterference(SimpleRadioServerConfig.Frequency config, Router from, Router to, Medium medium) {
        return config.dimensionalInterference * (from instanceof RadioTransmitter
                ? RadioSpace.nativePropagation(from.getLocation(), to.getLocation()).dimensionalMultiplier() : 1);
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
