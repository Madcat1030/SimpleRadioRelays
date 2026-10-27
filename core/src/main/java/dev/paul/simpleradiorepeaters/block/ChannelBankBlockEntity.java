package dev.paul.simpleradiorepeaters.block;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.item.CardState;
import dev.paul.simpleradiorepeaters.network.TowerManager;
import dev.paul.simpleradiorepeaters.network.TransponderChannels;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** The visible six-card module; the Relay owns the catalyst, antenna, and persistent RF identity. */
public class ChannelBankBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int[] NO_AUTOMATION_SLOTS = new int[0];
    private final int slots;
    private NonNullList<ItemStack> items;
    private UUID bankId = UUID.randomUUID();
    private List<CardState> lastCards;
    private List<CardState> displaySource = List.of();
    @Nullable private List<CardDisplay> displayCards;
    private boolean connectionDirty = true;
    public ChannelBankBlockEntity(BlockPos pos, BlockState state) { this(SimpleRadioRepeaters.CHANNEL_BANK_ENTITY, 6, pos, state); }
    protected ChannelBankBlockEntity(BlockEntityType<?> type, int slots, BlockPos pos, BlockState state) {
        super(type, pos, state); this.slots = slots; items = NonNullList.withSize(slots, ItemStack.EMPTY); lastCards = cardStates();
    }
    @Nullable public TransponderChannels transponderChannels() { return null; }
    public UUID bankId() { return bankId; }
    public List<CardState> cardStates() { return items.stream().map(CardState::of).toList(); }
    /** Passive diagnostic attachments own a card but never contribute forwarding channels. */
    public List<CardState> routingCards() { return cardStates(); }
    /** Lazily derived client display data; no ItemStack or tuning parsing on subsequent frames. */
    public List<CardDisplay> displayCards() {
        if (displayCards == null) {
            displaySource = cardStates();
            displayCards = displaySource.stream().map(CardDisplay::of).toList();
        }
        return displayCards;
    }
    public record CardDisplay(boolean present, String label) {
        private static CardDisplay of(CardState card) {
            boolean tuned = card.channel() != null;
            String frequency = tuned ? card.frequency() : "BLANK", modulation = tuned ? card.modulation() : "--";
            return new CardDisplay(card.present(), frequency + " " + modulation);
        }
    }
    @Nullable public BlockPos primaryRelayPosition() {
        if (level == null) return null;
        BlockPos pos = worldPosition.below();
        BlockState state = BankConnection.state(level, pos);
        return state != null && state.is(SimpleRadioRepeaters.RELAY) ? pos : null;
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
        boolean connected = relay != null;
        if (state.getValue(ChannelBankBlock.CONNECTED) != connected)
            level.setBlock(pos, state.setValue(ChannelBankBlock.CONNECTED, connected), Block.UPDATE_CLIENTS);
    }
    public int firstEmptySlot() { for (int slot = 0; slot < slots; slot++) if (items.get(slot).isEmpty()) return slot; return -1; }
    public boolean insert(int slot, ItemStack held, boolean creative) {
        if (slot < 0 || slot >= items.size() || !items.get(slot).isEmpty() || !held.is(SimpleRadioRepeaters.CARD)) return false;
        ItemStack card = held.copy(); card.setCount(1); items.set(slot, card);
        if (!creative) held.shrink(1); setChanged(); return true;
    }
    public static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
    }
    @Override public int getContainerSize() { return slots; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count); if (!result.isEmpty()) setChanged(); return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); stack.setCount(Math.min(1, stack.getCount())); setChanged(); }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return stack.is(SimpleRadioRepeaters.CARD); }
    // Keep direct Container access for integrations; expose no slots to sided automation such as hoppers.
    @Override public int[] getSlotsForFace(Direction side) { return NO_AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public void setChanged() {
        super.setChanged(); if (items == null) return;
        List<CardState> current = cardStates();
        if (!current.equals(displaySource)) displayCards = null;
        if (current.equals(lastCards)) return;
        lastCards = current; configurationChanged();
    }
    protected final void configurationChanged() {
        savedAndSynced();
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
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); items = NonNullList.withSize(slots, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items);
        if (tag.hasUUID("CardBankId")) bankId = tag.getUUID("CardBankId");
        lastCards = cardStates(); displayCards = null; connectionDirty = true;
    }
}
