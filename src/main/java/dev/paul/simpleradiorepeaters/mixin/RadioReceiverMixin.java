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
    @Inject(method = "take(Lcom/codinglitch/simpleradio/radio/Source;)V", at = @At("HEAD"), cancellable = true)
    private void repeaters$oneCopy(Source source, CallbackInfo ci) {
        RadioReceiver receiver = (RadioReceiver) (Object) this;
        if (!receiver.active || source.getPower() <= 0 || !(source instanceof RelaySourceState state) || receiver.frequency == null) return;
        RelayFrame frame = state.repeaters$getFrame(); if (frame == null || !frame.managed) return;
        if (!frame.delivered(ChannelKey.from(receiver.frequency).toString(), receiver.reference)) { ci.cancel(); return; }
        if (frame.managed && receiver.getLocation().level instanceof ServerLevel level) {
            TowerManager manager = TowerManager.get(level.getServer()); if (manager != null) manager.receiverDeliveries++;
        }
    }
}
