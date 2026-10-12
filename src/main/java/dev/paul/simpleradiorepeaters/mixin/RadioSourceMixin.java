package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.radio.RadioSource;
import dev.paul.simpleradiorepeaters.radio.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RadioSource.class, remap = false)
public abstract class RadioSourceMixin implements RelaySourceState {
    @Unique private RelayFrame repeaters$frame;
    @Unique private double repeaters$inheritedSeverity;
    @Override public RelayFrame repeaters$getFrame() { return repeaters$frame; }
    @Override public void repeaters$setFrame(RelayFrame value) { repeaters$frame = value; }
    @Override public double repeaters$getInheritedSeverity() { return repeaters$inheritedSeverity; }
    @Override public void repeaters$setInheritedSeverity(double value) { repeaters$inheritedSeverity = value; }

    @Inject(method = "copy()Lcom/codinglitch/simpleradio/radio/RadioSource;", at = @At("RETURN"))
    private void repeaters$copy(CallbackInfoReturnable<RadioSource> cir) {
        RelaySourceState copy = (RelaySourceState) cir.getReturnValue();
        if (repeaters$frame == null) repeaters$frame = new RelayFrame();
        copy.repeaters$setFrame(repeaters$frame);
        copy.repeaters$setInheritedSeverity(repeaters$inheritedSeverity);
        if (repeaters$frame.managed) cir.getReturnValue().activity = ((RadioSource) (Object) this).activity;
    }

    @Inject(method = "computeSeverity()D", at = @At("RETURN"), cancellable = true)
    private void repeaters$keepNoise(CallbackInfoReturnable<Double> cir) {
        if (repeaters$inheritedSeverity > cir.getReturnValue()) cir.setReturnValue(repeaters$inheritedSeverity);
    }
}
