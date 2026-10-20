package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.radio.*;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.*;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RadioReceiver.class, remap = false)
public abstract class RadioReceiverMixin {
    @Inject(method = "take(Lcom/codinglitch/simpleradio/radio/Source;)V",
            at = @At(value = "INVOKE", target = "Lcom/codinglitch/simpleradio/radio/RadioReceiver;compileActivity(Lcom/codinglitch/simpleradio/radio/Source;)V"), cancellable = true)
    private void repeaters$oneCopy(Source source, CallbackInfo ci) {
        RadioReceiver receiver = (RadioReceiver) (Object) this;
        // Native active, acceptCriteria and power checks have passed; rejected copies never reserve delivery.
        if (!(source instanceof RelaySourceState state) || receiver.frequency == null) return;
        RelayFrame frame = state.repeaters$getFrame(); if (frame == null || !frame.managed) return;
        if (!frame.delivered(ChannelKey.from(receiver.frequency).toString(), receiver.reference)) { ci.cancel(); return; }
        if (receiver.getLocation().level instanceof ServerLevel level) {
            TowerManager manager = TowerManager.get(level.getServer()); if (manager != null) manager.receiverDeliveries++;
        }
    }
}
