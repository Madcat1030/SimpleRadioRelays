package dev.paul.simpleradiorepeaters.block;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class ChannelBankBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public ChannelBankBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CONNECTED, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, CONNECTED); }
    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos relay = context.getClickedPos().below();
        if (!context.getLevel().hasChunkAt(relay)) return null;
        BlockState state = context.getLevel().getBlockState(relay);
        return state.is(SimpleRadioRepeaters.RELAY) ? defaultBlockState().setValue(FACING, state.getValue(RelayBlock.FACING)) : null;
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable(tooltip()).withStyle(ChatFormatting.GRAY));
    }
    protected String tooltip() { return "tooltip.simpleradio_repeaters.bank"; }
    protected int cardAt(net.minecraft.world.phys.Vec3 local) { return BankConnection.cardAt(local); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> Block.box(6, 0, 1, 16, 16, 15);
            case SOUTH -> Block.box(1, 0, 6, 15, 16, 16);
            case WEST -> Block.box(0, 0, 1, 10, 16, 15);
            default -> Block.box(1, 0, 0, 15, 16, 10);
        };
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ChannelBankBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SimpleRadioRepeaters.CHANNEL_BANK_ENTITY, ChannelBankBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty() && !held.is(SimpleRadioRepeaters.CARD)) return InteractionResult.PASS;
        if (hit.getDirection() != state.getValue(FACING)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof ChannelBankBlockEntity bank)) return InteractionResult.PASS;
        int slot = cardAt(BankConnection.localHit(pos, state.getValue(FACING), hit.getLocation()));
        if (held.is(SimpleRadioRepeaters.CARD)) {
            if (slot < 0) slot = bank.firstEmptySlot();
            if (slot < 0 || !bank.getItem(slot).isEmpty()) return InteractionResult.FAIL;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            return bank.insert(slot, held, player.getAbilities().instabuild) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if (slot < 0 || bank.getItem(slot).isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ChannelBankBlockEntity.give(player, bank.removeItem(slot, 1)); return InteractionResult.CONSUME;
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbour, boolean moved) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ChannelBankBlockEntity bank) bank.connectionChanged();
    }
    @Override public void onRemove(BlockState old, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!old.is(next.getBlock())) {
            if (level instanceof ServerLevel server) { TowerManager manager = TowerManager.get(server.getServer()); if (manager != null) manager.controllerRemovedAt(server, pos); }
            if (level.getBlockEntity(pos) instanceof ChannelBankBlockEntity bank) Containers.dropContents(level, pos, bank);
        }
        super.onRemove(old, level, pos, next, moved);
    }
}
