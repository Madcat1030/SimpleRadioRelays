package dev.paul.simpleradiorepeaters.block;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import dev.paul.simpleradiorepeaters.radio.RelayCatalysts;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class RelayBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public RelayBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ACTIVE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RelayBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SimpleRadioRepeaters.RELAY_ENTITY, RelayBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty() && !(level.isClientSide ? RelayCatalysts.acceptsClient(held) : RelayCatalysts.accepts(held))) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof RelayBlockEntity relay)) return InteractionResult.PASS;
        if (!held.isEmpty()) {
            if (!relay.catalyst().isEmpty()) return InteractionResult.FAIL;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            return relay.installCatalyst(held, player.getAbilities().instabuild) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            relay.setEnabled(!relay.enabled()); return InteractionResult.CONSUME;
        }
        if (relay.catalyst().isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack removed = relay.removeCatalyst();
        if (removed.isEmpty()) return InteractionResult.PASS;
        ChannelBankBlockEntity.give(player, removed); return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState old, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!old.is(next.getBlock())) {
            if (level instanceof ServerLevel server) {
                TowerManager manager = TowerManager.get(server.getServer());
                if (manager != null) manager.removeAt(server.dimension().location().toString(), pos);
            }
            if (level.getBlockEntity(pos) instanceof RelayBlockEntity relay && !relay.catalyst().isEmpty())
                Block.popResource(level, pos, relay.catalyst().copy());
        }
        super.onRemove(old, level, pos, next, moved);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbour, boolean moved) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RelayBlockEntity relay) { relay.bankChanged(); relay.markDirty(); }
    }
}
