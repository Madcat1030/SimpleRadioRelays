package dev.paul.simpleradiorepeaters.block;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class TransponderBlock extends ChannelBankBlock {
    public TransponderBlock(Properties properties) { super(properties); }
    @Override protected String tooltip() { return "tooltip.simpleradio_repeaters.transponder"; }
    public static double slotX(int slot) { return slot == 0 ? 10 : 1; }
    public static double slotY(int slot) { return slot == 0 ? 6 : slot == 1 ? 10 : 2; }
    @Override protected int cardAt(Vec3 local) {
        double x = local.x * 16, y = local.y * 16;
        for (int slot = 0; slot < 3; slot++)
            if (x >= slotX(slot) && x <= slotX(slot) + 5 && y >= slotY(slot) && y <= slotY(slot) + 4) return slot;
        return -1;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TransponderBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SimpleRadioRepeaters.TRANSPONDER_ENTITY, ChannelBankBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).isEmpty() && hit.getDirection() == state.getValue(FACING)) {
            Vec3 local = BankConnection.localHit(pos, state.getValue(FACING), hit.getLocation());
            if (local.x * 16 > 6 && local.x * 16 < 10 && local.y * 16 >= 6 && local.y * 16 <= 10
                    && level.getBlockEntity(pos) instanceof TransponderBlockEntity transponder) {
                if (!level.isClientSide) transponder.setMode(transponder.mode().next());
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
