package dev.paul.simpleradiorepeaters.mixin;

import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.radio.*;
import com.codinglitch.simpleradio.routers.Router;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.paul.simpleradiorepeaters.terrain.TerrainManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RadioSource.class, remap = false)
public abstract class TerrainTravelMixin {
    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target =
            "Lcom/codinglitch/simpleradio/central/WorldlyPosition;distance(Lcom/codinglitch/simpleradio/central/WorldlyPosition;)F"))
    private float repeaters$terrainTravel(float distance, Router from, Router to, Medium medium) {
        return medium instanceof Frequency && from instanceof RadioTransmitter transmitter && to instanceof RadioReceiver receiver
                ? (float) (distance + TerrainManager.nativeLoss(transmitter, receiver)) : distance;
    }
}
