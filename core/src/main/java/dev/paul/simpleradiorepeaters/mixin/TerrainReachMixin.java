package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.radio.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.paul.simpleradiorepeaters.terrain.TerrainManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RadioTransmitter.class, remap = false)
public abstract class TerrainReachMixin {
    @ModifyExpressionValue(method = "shouldRouteTo", at = @At(value = "INVOKE", target =
            "Lcom/codinglitch/simpleradio/central/WorldlyPosition;distance(Lcom/codinglitch/simpleradio/central/WorldlyPosition;)F"))
    private float repeaters$terrainReach(float distance, RadioSource source, RadioRouter destination) {
        return destination instanceof RadioReceiver receiver
                ? (float) (distance + TerrainManager.nativeLoss((RadioTransmitter) (Object) this, receiver, source, distance)) : distance;
    }
}
