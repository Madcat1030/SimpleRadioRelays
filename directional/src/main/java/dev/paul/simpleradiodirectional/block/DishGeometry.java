package dev.paul.simpleradiodirectional.block;

import com.codinglitch.simpleradio.core.registry.blocks.AntennaBlock;
import dev.paul.simpleradiodirectional.DirectionalAntenna;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** One center and eight reservation/selection parts in a 3x3 vertical plane. */
public final class DishGeometry {
    public enum Validity { VALID, INVALID, INCOMPLETE }
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private DishGeometry() {}
    public static Direction right(Direction facing) { return facing.getClockWise(); }
    public static BlockPos cell(BlockPos center, Direction facing, int part) {
        return center.relative(right(facing), part % 3 - 1).above(part / 3 - 1);
    }
    public static BlockPos center(BlockPos partPos, Direction facing, int part) {
        return partPos.relative(right(facing), 1 - part % 3).above(1 - part / 3);
    }
    private static boolean loaded(LevelReader level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos);
    }
    public static boolean validMast(LevelReader level, BlockPos mast) {
        if (!loaded(level, mast) || !loaded(level, mast.above()) || !loaded(level, mast.below())) return false;
        var state = level.getBlockState(mast);
        // Both native vertical visual variants qualify; a horizontal-only arm and the top do not.
        return state.getBlock() instanceof AntennaBlock && state.getValue(AntennaBlock.UP)
                && state.getValue(AntennaBlock.DOWN)
                && level.getBlockState(mast.above()).getBlock() instanceof AntennaBlock
                && (level.getBlockState(mast.below()).getBlock() instanceof AntennaBlock
                    || level.getBlockState(mast.below()).isFaceSturdy(level, mast.below(), Direction.UP));
    }
    public static boolean room(LevelReader level, BlockPos center, Direction facing) {
        if (!validMast(level, center.relative(facing.getOpposite()))) return false;
        for (int i = 0; i < 9; i++) {
            BlockPos pos = cell(center, facing, i);
            if (!loaded(level, pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced() || !level.getFluidState(pos).isEmpty()) return false;
        }
        return true;
    }
    public static void build(Level level, BlockPos center, Direction facing) {
        for (int i = 0; i < 9; i++) if (i != 4) level.setBlock(cell(center, facing, i),
                DirectionalAntenna.PART.defaultBlockState().setValue(DishPartBlock.FACING, facing)
                        .setValue(DishPartBlock.PART, i), Block.UPDATE_CLIENTS);
        for (int i = 0; i < 9; i++) level.updateNeighborsAt(cell(center, facing, i), DirectionalAntenna.DISH);
    }
    public static Validity validate(ServerLevel level, BlockPos center, Direction facing) {
        BlockPos mast = center.relative(facing.getOpposite());
        for (BlockPos pos : List.of(mast, mast.above(), mast.below())) if (!loaded(level, pos)) return Validity.INCOMPLETE;
        for (int i = 0; i < 9; i++) if (!loaded(level, cell(center, facing, i))) return Validity.INCOMPLETE;
        if (!validMast(level, mast)) return Validity.INVALID;
        for (int i = 0; i < 9; i++) {
            var state = level.getBlockState(cell(center, facing, i));
            if (i == 4) {
                if (!state.is(DirectionalAntenna.DISH) || state.getValue(ParabolicReceiverBlock.FACING) != facing) return Validity.INVALID;
            } else if (!state.is(DirectionalAntenna.PART) || state.getValue(DishPartBlock.FACING) != facing
                    || state.getValue(DishPartBlock.PART) != i) return Validity.INVALID;
        }
        return Validity.VALID;
    }
    public static void remove(Level level, BlockPos center, Direction facing, BlockPos changed) {
        if (REMOVING.get()) return;
        REMOVING.set(true);
        try {
            for (int i = 0; i < 9; i++) {
                BlockPos pos = cell(center, facing, i);
                if (pos.equals(changed) || !level.hasChunkAt(pos)) continue;
                var state = level.getBlockState(pos);
                if ((i == 4 && state.is(DirectionalAntenna.DISH) && state.getValue(ParabolicReceiverBlock.FACING) == facing)
                        || (i != 4 && state.is(DirectionalAntenna.PART) && state.getValue(DishPartBlock.FACING) == facing
                        && state.getValue(DishPartBlock.PART) == i)) level.removeBlock(pos, false);
            }
        } finally { REMOVING.set(false); }
    }
}
