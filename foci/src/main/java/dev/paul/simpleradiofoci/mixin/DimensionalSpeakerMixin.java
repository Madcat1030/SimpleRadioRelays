package dev.paul.simpleradiofoci.mixin;
import com.codinglitch.simpleradio.radio.RadioSpeaker;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** A focus exit has a local carrier origin; foreign native carriers remain disallowed. */
@Mixin(value = RadioSpeaker.class, remap = false)
public abstract class DimensionalSpeakerMixin {
    @ModifyExpressionValue(method = "speak", at = @At(value = "FIELD", target =
            "Lcom/codinglitch/simpleradio/SimpleRadioServerConfig$Frequency;crossDimensional:Ljava/lang/Boolean;"))
    private Boolean foci$dimensionalPolicy(Boolean original) { return false; }
}
