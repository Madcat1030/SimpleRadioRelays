package dev.paul.handbookchecks;

import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;

/** QA-only dedicated-server recipe/use check; excluded from all distributed JARs. */
@Mixin(value=GameTestRegistry.class,remap=false)
public abstract class HandbookTestFilter {
    @Inject(method="getAllTestFunctions",at=@At("RETURN"),cancellable=true,remap=false)
    private static void handbookChecks(CallbackInfoReturnable<Collection<TestFunction>> result) {
        result.setReturnValue(result.getReturnValue().stream()
                .filter(test->test.getTestName().startsWith("handbookgametests.")).toList());
    }
}
