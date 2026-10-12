package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.radio.RelayRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Predicate;

@Mixin(value = RadioRouter.class, remap = false)
public abstract class RadioRouterMixin {
    @Inject(method = "route(Lcom/codinglitch/simpleradio/radio/Source;Ljava/util/function/Predicate;)V",
            at = @At("HEAD"), cancellable = true)
    private void repeaters$route(Source source, Predicate<RadioRouter> criteria, CallbackInfo ci) {
        if ((Object) this instanceof RadioTransmitter transmitter && source instanceof RadioSource radioSource
                && RelayRouter.tryRoute(transmitter, radioSource, criteria)) ci.cancel();
    }
}
