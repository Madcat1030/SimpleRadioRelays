package dev.paul.simpleradiodirectional.mixin;

import dev.paul.simpleradiodirectional.radio.DishManager;
import dev.paul.simpleradiorepeaters.block.RelayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.paul.simpleradiorepeaters.block.RelayBlockEntity", remap = false)
public abstract class RepeatersBlockEntityMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private static void directional$refresh(Level level, BlockPos pos, BlockState state, RelayBlockEntity relay, CallbackInfo ci) {
        if (level instanceof ServerLevel server) DishManager.refreshRelay(server, pos, relay.corePos());
    }
    @Inject(method = "markDirty", at = @At("HEAD"))
    private void directional$dirty(CallbackInfo ci) {
        var relay = (RelayBlockEntity) (Object) this;
        if (relay.getLevel() instanceof ServerLevel server) DishManager.dirtyRelay(server, relay.getBlockPos());
    }
}
