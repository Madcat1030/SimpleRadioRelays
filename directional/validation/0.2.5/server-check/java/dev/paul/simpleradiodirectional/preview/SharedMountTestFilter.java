package dev.paul.simpleradiodirectional.preview;

import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;

/** QA-only selection of the two shared-mount tests; excluded from every distributed JAR. */
@Mixin(value=GameTestRegistry.class,remap=false)
public abstract class SharedMountTestFilter {
    @Inject(method="getAllTestFunctions",at=@At("RETURN"),cancellable=true,remap=false)
    private static void sharedMountChecks(CallbackInfoReturnable<Collection<TestFunction>> result) {
        result.setReturnValue(result.getReturnValue().stream()
                .filter(test->test.getTestName().contains(".shared_mount_")).toList());
    }
}
