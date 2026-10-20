package dev.paul.simpleradiodirectional.mixin;

import com.codinglitch.simpleradio.core.registry.blocks.ReceiverBlockEntity;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import dev.paul.simpleradiodirectional.radio.DishManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ReceiverBlockEntity.class, remap = false)
public abstract class ReceiverBlockEntityMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private static void directional$refresh(Level level, BlockPos pos, BlockState state, ReceiverBlockEntity entity, CallbackInfo ci) {
        if (level instanceof ServerLevel server) DishManager.refreshReceiver(server, entity);
    }
    @Inject(method = "markDirty", at = @At("HEAD"))
    private void directional$dirty(CallbackInfo ci) {
        var entity = (ReceiverBlockEntity) (Object) this;
        if (entity.getLevel() instanceof ServerLevel && entity.receiver instanceof RadioReceiver receiver) DishManager.dirtyReceiver(receiver);
    }
}
