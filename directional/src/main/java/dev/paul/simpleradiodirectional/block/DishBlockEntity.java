package dev.paul.simpleradiodirectional.block;

import dev.paul.simpleradiodirectional.DirectionalAntenna;
import dev.paul.simpleradiodirectional.radio.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class DishBlockEntity extends BlockEntity {
    private boolean checked;
    public DishBlockEntity(BlockPos pos, BlockState state) { super(DirectionalAntenna.DISH_ENTITY, pos, state); }
    public static void tick(Level world, BlockPos pos, BlockState state, DishBlockEntity entity) {
        if (!(world instanceof ServerLevel level) || (entity.checked && level.getGameTime() % 20 != 0)) return;
        entity.checked = true;
        var facing = state.getValue(ParabolicReceiverBlock.FACING);
        switch (DishGeometry.validate(level, pos, facing)) {
            case VALID -> DishManager.verify(level, new Dish(level.dimension().location(), pos, facing,
                    state.getValue(ParabolicReceiverBlock.AIM), ((ParabolicReceiverBlock) state.getBlock()).pattern()));
            // A missing panel may already have dropped the item while this center's chunk was absent.
            case INVALID -> level.destroyBlock(pos, !DishGeometry.validMast(level, pos.relative(facing.getOpposite())));
            case INCOMPLETE -> { /* Preserve last verified state until adjacent chunks restore. */ }
        }
    }
}
