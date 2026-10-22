package dev.paul.paritychecks;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;
import java.util.Set;
@Mixin(value=GameTestRegistry.class,remap=false)
public abstract class ParityTestFilter {
    private static final Set<String> CLASSES = Set.of("repeatergametests", "relaydiagnosticsgametests", "catalystgametests",
        "splitboundarygametests", "transponderroutinggametests", "handbookgametests", "nativemathgametests",
        "antennaorigingametests", "expansiongametests", "expansionhardwaregametests", "directionalgametests", "widearraygametests");
    @Inject(method="getAllTestFunctions",at=@At("RETURN"),cancellable=true,remap=false)
    private static void parityChecks(CallbackInfoReturnable<Collection<TestFunction>> result) {
        result.setReturnValue(result.getReturnValue().stream().filter(test -> {
            String name=test.getTestName(); return CLASSES.contains(name.substring(0, name.indexOf('.')));
        }).toList());
    }
}
