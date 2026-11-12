package dev.paul.simpleradiorepeaters.block;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.monitor.MonitorScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class RelayMonitorBlock extends ChannelBankBlock {
    public RelayMonitorBlock(Properties properties) { super(properties); }
    @Override protected String tooltip() { return "tooltip.simpleradio_repeaters.monitor"; }
    @Override protected int cardAt(Vec3 local) {
        return local.x * 16 >= 5 && local.x * 16 <= 11 && local.y * 16 >= 1 && local.y * 16 <= 5 ? 0 : -1;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RelayMonitorBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SimpleRadioRepeaters.RELAY_MONITOR_ENTITY, RelayMonitorBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hit.getDirection() == state.getValue(FACING) && level.getBlockEntity(pos) instanceof RelayMonitorBlockEntity monitor) {
            Vec3 local = BankConnection.localHit(pos, state.getValue(FACING), hit.getLocation());
            if (MonitorScreen.contains(local.x, local.y)) {
                InteractionHand compassHand = player.getItemInHand(hand).is(Items.COMPASS) ? hand
                        : hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty() && player.getOffhandItem().is(Items.COMPASS)
                        ? InteractionHand.OFF_HAND : null;
                if (compassHand != null) {
                    if (level.isClientSide) return InteractionResult.SUCCESS;
                    return monitor.bindCompass(player, compassHand) ? InteractionResult.CONSUME : InteractionResult.FAIL;
                }
                if (player.getItemInHand(hand).isEmpty()) {
                    if (!level.isClientSide) monitor.clickScreen(local.x, local.y);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }
        InteractionResult result = super.use(state, level, pos, player, hand, hit);
        if (!level.isClientSide && result.consumesAction() && level.getBlockEntity(pos) instanceof RelayMonitorBlockEntity monitor) monitor.refresh();
        return result;
    }
}
