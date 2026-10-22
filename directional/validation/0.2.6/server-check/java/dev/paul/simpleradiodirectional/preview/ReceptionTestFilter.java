package dev.paul.simpleradiodirectional.preview;

import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;
import java.util.Set;

/** QA-only reception and aiming regression selection; never included in the runtime JAR. */
@Mixin(value=GameTestRegistry.class,remap=false)
public abstract class ReceptionTestFilter {
    private static final Set<String> SELECTED=Set.of(
            "full_wedge_extends_native_reach_and_preserves_audio_power",
            "angular_shoulder_is_weaker_and_side_has_no_bonus",
            "inherited_weak_power_is_not_repaired",
            "receiver_bonus_does_not_change_transmitters_or_untracked_devices",
            "seven_aims_rotate_native_reception_and_keep_the_mast_binding",
            "optional_repeaters_use_directional_gain_and_invalidate_cached_links",
            "placed_array_applies_shared_scale_and_falloff_to_native_reception");
    @Inject(method="getAllTestFunctions",at=@At("RETURN"),cancellable=true,remap=false)
    private static void receptionChecks(CallbackInfoReturnable<Collection<TestFunction>> result) {
        result.setReturnValue(result.getReturnValue().stream().filter(test->{
            String name=test.getTestName();
            return SELECTED.contains(name.substring(name.lastIndexOf('.')+1));
        }).toList());
    }
}
