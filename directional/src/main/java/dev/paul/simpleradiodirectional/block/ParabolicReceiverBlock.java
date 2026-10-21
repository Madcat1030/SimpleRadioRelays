package dev.paul.simpleradiodirectional.block;

import dev.paul.simpleradiodirectional.DirectionalAntenna;
import dev.paul.simpleradiodirectional.radio.DishManager;
import dev.paul.simpleradiodirectional.radio.Dish;
import dev.paul.simpleradiodirectional.radio.DishAim;
import dev.paul.simpleradiodirectional.radio.DishPattern;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class ParabolicReceiverBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty AIM = IntegerProperty.create("aim", DishAim.MIN, DishAim.MAX);
    private final DishPattern pattern;
    public ParabolicReceiverBlock(Properties properties) { this(properties, DishPattern.PARABOLIC); }
    public ParabolicReceiverBlock(Properties properties, DishPattern pattern) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(AIM, DishAim.STRAIGHT));
        this.pattern = pattern;
    }
    public DishPattern pattern() { return pattern; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, AIM); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        if (facing.getAxis().isVertical() || !DishGeometry.room(context.getLevel(), context.getClickedPos(), facing)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return DishGeometry.validMast(level, pos.relative(state.getValue(FACING).getOpposite()));
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack stack) {
        if (!level.isClientSide) DishGeometry.build(level, pos, state.getValue(FACING));
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            int aim = DishAim.adjust(state.getValue(AIM), player.isShiftKeyDown());
            if (aim != state.getValue(AIM)) {
                level.setBlock(pos, state.setValue(AIM, aim), Block.UPDATE_CLIENTS);
                // Publish immediately: audio threads and cached relay links must see the new bearing together.
                DishManager.verify(server, new Dish(level.dimension().location(), pos, state.getValue(FACING), aim, pattern));
            }
            int degrees = DishAim.degrees(aim);
            player.displayClientMessage(Component.translatable("message.simpleradio_directional.aim",
                    degrees > 0 ? "+" + degrees : Integer.toString(degrees)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            if (!level.isClientSide) DishManager.remove(level, pos);
            DishGeometry.remove(level, pos, state.getValue(FACING), pos, pattern);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DishPartBlock.shape(state.getValue(FACING));
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(AIM, mirror == Mirror.NONE ? state.getValue(AIM) : DishAim.mirror(state.getValue(AIM)));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DishBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, DirectionalAntenna.DISH_ENTITY, DishBlockEntity::tick);
    }
}
