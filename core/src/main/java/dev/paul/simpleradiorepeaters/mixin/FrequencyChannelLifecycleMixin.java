package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.Frequencies;
import com.codinglitch.simpleradio.core.central.FrequencyChannel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native empty-channel cleanup always targets the server, even for client-owned channels. */
@Mixin(value = FrequencyChannel.class, remap = false)
public abstract class FrequencyChannelLifecycleMixin {
    @Unique private Frequencies repeaters$owner;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void repeaters$rememberOwner(Frequencies owner, String number, Frequency.Modulation modulation, CallbackInfo ci) {
        repeaters$owner = owner;
    }

    @WrapOperation(method = {
            "removeReceiver(Ljava/util/function/Predicate;)Lcom/codinglitch/simpleradio/routers/Receiver;",
            "removeTransmitter(Ljava/util/function/Predicate;)Lcom/codinglitch/simpleradio/routers/Transmitter;"
    }, at = @At(value = "INVOKE", target =
            "Lcom/codinglitch/simpleradio/core/Frequencies;remove(Lcom/codinglitch/simpleradio/central/Frequency;)V"))
    private void repeaters$removeOnlyOwnedChannel(Frequencies requested, Frequency channel, Operation<Void> original) {
        // Keep client cleanup on the client and avoid deleting a newer generation of the same channel.
        if (repeaters$owner.get(channel.getFrequency(), channel.getModulation()) == channel)
            original.call(repeaters$owner, channel);
    }
}
