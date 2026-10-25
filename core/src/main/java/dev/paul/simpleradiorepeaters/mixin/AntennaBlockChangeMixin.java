package dev.paul.simpleradiorepeaters.mixin;

import dev.paul.simpleradiorepeaters.network.TowerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Actual block replacement only; unloading a chunk does not call setBlockState. */
@Mixin(LevelChunk.class)
public abstract class AntennaBlockChangeMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void repeaters$blockReplaced(BlockPos pos, BlockState state, boolean moved,
                                         CallbackInfoReturnable<BlockState> result) {
        BlockState previous = result.getReturnValue();
        if (previous != null && previous.getBlock() != state.getBlock()
                && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel server
                && ((LevelChunk) (Object) this).getBlockState(pos).getBlock() != previous.getBlock())
            TowerManager.blockChanged(server, pos, previous);
    }
}
