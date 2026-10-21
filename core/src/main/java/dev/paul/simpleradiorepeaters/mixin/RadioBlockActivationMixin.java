package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.AuditoryBlockEntity;
import com.codinglitch.simpleradio.core.registry.blocks.*;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Deserialization can precede ticking long enough for native GC to evict the saved frequency object. */
@Mixin(value = {RadioBlockEntity.class, ReceiverBlockEntity.class, TransmitterBlockEntity.class}, remap = false)
public abstract class RadioBlockActivationMixin {
    @Inject(method = "activate()V", at = @At("HEAD"))
    private void repeaters$bindLiveFrequency(CallbackInfo ci) {
        AuditoryBlockEntity device = (AuditoryBlockEntity) (Object) this;
        if (!(device.getLevel() instanceof ServerLevel level) || device.frequency == null) return;
        var stored = device.frequency;
        var live = ServerSimpleRadioApi.getInstance().frequencies()
                .getOrCreate(stored.getFrequency(), stored.getModulation());
        if (stored == live) return;
        device.frequency = live;
        SimpleRadioRepeaters.LOGGER.warn("[radio-reload] Rebound {} at {} {} to live frequency {} {} before activation; the loaded frequency object was evicted or replaced",
                device.getClass().getSimpleName(), level.dimension().location(), device.getBlockPos().toShortString(),
                live.getFrequency(), live.getModulation().shorthand);
    }
}
