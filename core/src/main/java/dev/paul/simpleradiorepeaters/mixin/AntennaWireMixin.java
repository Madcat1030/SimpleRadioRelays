package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.core.registry.entities.Wire;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Protect verified antenna extensions from native missing-router expiry during partial unloads. */
@Mixin(value = Wire.class, remap = false)
public abstract class AntennaWireMixin {
    @Shadow private int killTime;

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void repeaters$waitForEndpointChunks(CallbackInfo ci) {
        if (TowerManager.waitingForWireDependencies((Wire) (Object) this)) {
            killTime = -1; // Allow the full native settling interval after the chunks return.
            ci.cancel();
        }
    }
}
