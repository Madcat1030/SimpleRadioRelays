package dev.paul.simpleradiodirectional.block;

import dev.paul.simpleradiodirectional.DirectionalAntenna;
import dev.paul.simpleradiodirectional.radio.DishManager;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.phys.BlockHitResult;

/** Invisible occupied cells retain selection, collision and a single whole-dish drop. */
public final class DishPartBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    public DishPartBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING).add(PART); }
    public static VoxelShape shape(Direction facing) {
        return switch (facing) {
            case NORTH -> Block.box(0,0,4,16,16,14);
            case SOUTH -> Block.box(0,0,2,16,16,12);
            case WEST -> Block.box(4,0,0,14,16,16);
            default -> Block.box(2,0,0,12,16,16);
        };
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state.getValue(FACING)); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        BlockPos center = DishGeometry.center(pos, state.getValue(FACING), state.getValue(PART));
        if (!level.hasChunkAt(center)) return InteractionResult.PASS;
        var dish = level.getBlockState(center);
        return dish.is(DirectionalAntenna.DISH) ? DirectionalAntenna.DISH.use(dish, level, center, player, hand,
                new BlockHitResult(hit.getLocation(), hit.getDirection(), center, hit.isInside())) : InteractionResult.PASS;
    }
    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) { return new ItemStack(DirectionalAntenna.DISH); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING), mirrored = mirror.mirror(facing);
        int part = state.getValue(PART);
        if (mirror.mirror(facing.getClockWise()) != mirrored.getClockWise()) part = (part / 3) * 3 + 2 - part % 3;
        return state.setValue(FACING, mirrored).setValue(PART, part);
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
        return state;
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos center = DishGeometry.center(pos, state.getValue(FACING), state.getValue(PART));
        if (level.hasChunkAt(center) && !level.getBlockState(center).is(DirectionalAntenna.DISH)) level.removeBlock(pos, false);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            Direction facing = state.getValue(FACING);
            BlockPos center = DishGeometry.center(pos, facing, state.getValue(PART));
            if (!level.isClientSide) DishManager.remove(level, center);
            DishGeometry.remove(level, center, facing, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
