package dev.paul.simpleradiorepeaters.block;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.item.CardState;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** The visible six-card module; the Relay owns the catalyst, antenna, and persistent RF identity. */
public final class ChannelBankBlockEntity extends BlockEntity implements Container {
    private NonNullList<ItemStack> items = NonNullList.withSize(6, ItemStack.EMPTY);
    private UUID bankId = UUID.randomUUID();
    private List<CardState> lastCards = cardStates();
    @Nullable private BlockPos previousRelay;
    private boolean connectionDirty = true;
    public ChannelBankBlockEntity(BlockPos pos, BlockState state) { super(SimpleRadioRepeaters.CHANNEL_BANK_ENTITY, pos, state); }
    public UUID bankId() { return bankId; }
    public List<CardState> cardStates() { return items.stream().map(CardState::of).toList(); }
    @Nullable public BlockPos primaryRelayPosition() {
        if (level == null) return null;
        if (previousRelay != null && BankConnection.state(level, previousRelay) == null) return previousRelay;
        for (Direction direction : BankConnection.RELAY_PRIORITY) {
            BlockPos pos = worldPosition.relative(direction);
            BlockState state = BankConnection.state(level, pos);
            if (state != null && state.is(SimpleRadioRepeaters.RELAY)) return pos;
        }
        return null;
    }
    @Nullable public RelayBlockEntity relay() {
        BlockPos pos = primaryRelayPosition();
        if (pos != null && BankConnection.entity(level, pos) instanceof RelayBlockEntity relay
                && relay.findAssignedBank() == this) return relay;
        return null;
    }
    public void connectionChanged() { connectionDirty = true; RelayBlockEntity relay = relay(); if (relay != null) relay.bankChanged(); }
    public static void tick(Level level, BlockPos pos, BlockState state, ChannelBankBlockEntity bank) {
        if (!bank.connectionDirty && level.getGameTime() % 10 != 0) return;
        bank.connectionDirty = false; RelayBlockEntity relay = bank.relay();
        BlockPos owner = relay == null ? bank.previousRelay : relay.getBlockPos();
        if (relay == null && owner != null) {
            BlockState ownerState = BankConnection.state(level, owner);
            if (ownerState != null && !ownerState.is(SimpleRadioRepeaters.RELAY)) owner = null;
        }
        if (!Objects.equals(owner, bank.previousRelay)) { bank.previousRelay = owner; bank.savedAndSynced(); }
        boolean connected = relay != null;
        if (state.getValue(ChannelBankBlock.CONNECTED) != connected)
            level.setBlock(pos, state.setValue(ChannelBankBlock.CONNECTED, connected), Block.UPDATE_CLIENTS);
    }
    public int firstEmptySlot() { for (int slot = 0; slot < 6; slot++) if (items.get(slot).isEmpty()) return slot; return -1; }
    public int firstOccupiedSlot() { for (int slot = 0; slot < 6; slot++) if (!items.get(slot).isEmpty()) return slot; return -1; }
    public boolean insert(ItemStack held, boolean creative) {
        int slot = firstEmptySlot(); if (slot < 0 || !held.is(SimpleRadioRepeaters.CARD)) return false;
        ItemStack card = held.copy(); card.setCount(1); items.set(slot, card);
        if (!creative) held.shrink(1); setChanged(); return true;
    }
    public static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
    }
    @Override public int getContainerSize() { return 6; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count); if (!result.isEmpty()) setChanged(); return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); stack.setCount(Math.min(1, stack.getCount())); setChanged(); }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return stack.is(SimpleRadioRepeaters.CARD); }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public void setChanged() {
        super.setChanged(); if (items == null) return;
        List<CardState> current = cardStates();
        if (current.equals(lastCards)) return;
        lastCards = current; savedAndSynced();
        if (level instanceof ServerLevel server) {
            RelayBlockEntity relay = relay();
            if (relay != null) relay.bankChanged();
            else { TowerManager manager = TowerManager.get(server.getServer()); if (manager != null) manager.controllerChanged(server, this); }
        }
    }
    private void savedAndSynced() {
        super.setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); ContainerHelper.saveAllItems(tag, items); tag.putUUID("CardBankId", bankId);
        if (previousRelay != null) tag.putLong("RelayPosition", previousRelay.asLong());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); items = NonNullList.withSize(6, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items);
        if (tag.hasUUID("CardBankId")) bankId = tag.getUUID("CardBankId");
        previousRelay = tag.contains("RelayPosition") ? BlockPos.of(tag.getLong("RelayPosition")) : null;
        lastCards = cardStates(); connectionDirty = true;
    }
}
