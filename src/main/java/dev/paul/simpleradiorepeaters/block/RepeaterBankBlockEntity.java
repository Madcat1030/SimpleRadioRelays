package dev.paul.simpleradiorepeaters.block;

import com.codinglitch.simpleradio.central.Frequencing;
import dev.paul.simpleradiorepeaters.*;
import dev.paul.simpleradiorepeaters.item.FrequencyCardItem;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.screen.RepeaterBankMenu;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class RepeaterBankBlockEntity extends BaseContainerBlockEntity implements Frequencing {
    private NonNullList<ItemStack> items = NonNullList.withSize(10, ItemStack.EMPTY);
    private UUID bankId = UUID.randomUUID();
    private boolean enabled = true;
    private boolean dirty = true;
    private int antennaPower;
    private int status = 5;
    private long nextRefresh;
    private final AntennaVerification verification = new AntennaVerification();
    private record CardSetting(boolean present, String frequency, String modulation) {}
    private record Settings(List<CardSetting> cards, boolean catalyst, boolean enabled) {}
    private Settings lastSettings = settingsSnapshot();
    public RepeaterBankBlockEntity(BlockPos pos, BlockState state) { super(SimpleRadioRepeaters.BANK_ENTITY, pos, state); }
    public UUID bankId() { return bankId; }
    public void regenerateId() { bankId = UUID.randomUUID(); setChanged(); }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { if (enabled != value) { enabled = value; setChanged(); } }
    @Override public int getAntennaPower() { return antennaPower; }
    @Override public void markDirty() { dirty = true; nextRefresh = 0; verification.reset(); }
    @Override public int getContainerSize() { return 10; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) { ItemStack result = ContainerHelper.removeItem(items, slot, count); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack value) { items.set(slot, value); value.setCount(Math.min(1, value.getCount())); setChanged(); }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean canPlaceItem(int slot, ItemStack value) { return slot < 9 ? value.is(SimpleRadioRepeaters.CARD) : value.is(Items.GILDED_BLACKSTONE); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override protected Component getDefaultName() { return Component.translatable("block.simpleradio_repeaters.repeater_bank"); }
    @Override protected AbstractContainerMenu createMenu(int sync, Inventory inventory) { return new RepeaterBankMenu(sync, inventory, this, data); }
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return switch (index) {
            case 0 -> antennaPower; case 1 -> enabled ? 1 : 0; case 2 -> status;
            case 3 -> channels().size(); case 4 -> peers(); default -> 0;
        }; }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 5; }
    };
    private int peers() {
        if (!(level instanceof ServerLevel serverLevel)) return 0;
        TowerManager manager = TowerManager.get(serverLevel.getServer());
        return manager == null ? 0 : manager.peers(bankId);
    }
    public Set<ChannelKey> channels() {
        Set<ChannelKey> result = new TreeSet<>();
        for (int i = 0; i < 9; i++) if (items.get(i).is(SimpleRadioRepeaters.CARD) && FrequencyCardItem.isTuned(items.get(i))) {
            ChannelKey channel = ChannelKey.from(items.get(i).getTag());
            if (channel != null) result.add(channel);
        }
        return result;
    }
    public boolean hasCatalyst() { return items.get(9).is(Items.GILDED_BLACKSTONE); }
    public BlockPos corePos() { return worldPosition.relative(getBlockState().getValue(RepeaterBankBlock.FACING).getOpposite()); }
    @Override public void setChanged() {
        super.setChanged();
        if (items == null) return;
        Settings current = settingsSnapshot();
        if (current.equals(lastSettings)) return;
        Settings previous = lastSettings; lastSettings = current;
        // Menu clicks call setChanged even if no item moved. Only real RF configuration changes
        // require verification; disabling/removing a catalyst updates eligibility immediately.
        if (!current.cards().equals(previous.cards()) || (!previous.catalyst() && current.catalyst())
                || (!previous.enabled() && current.enabled())) markDirty();
        if (level instanceof ServerLevel serverLevel) {
            publishCurrent();
            TowerManager manager = TowerManager.get(serverLevel.getServer());
            if (manager != null) updateDisplayedState(manager);
        }
    }
    private Settings settingsSnapshot() {
        List<CardSetting> cards = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = items.get(slot);
            CompoundTag tag = stack.is(SimpleRadioRepeaters.CARD) ? stack.getTag() : null;
            cards.add(new CardSetting(stack.is(SimpleRadioRepeaters.CARD),
                    tag == null ? "" : tag.getString("frequency"), tag == null ? "" : tag.getString("modulation")));
        }
        return new Settings(List.copyOf(cards), hasCatalyst(), enabled);
    }
    private void publishCurrent() {
        if (!(level instanceof ServerLevel serverLevel) || items == null) return;
        TowerManager manager = TowerManager.get(serverLevel.getServer());
        if (manager != null) manager.settingsChanged(this);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, RepeaterBankBlockEntity bank) {
        TowerManager manager = TowerManager.get(((ServerLevel) level).getServer());
        if (manager == null) return;
        if (manager.loaded(bank)) {
            TowerRecord saved = manager.snapshot(bank.bankId);
            if (saved != null && saved.verified() && saved.antennaScore() > 0) bank.antennaPower = saved.antennaScore();
            bank.markDirty();
        }
        if (bank.dirty && level.getGameTime() >= bank.nextRefresh) bank.refresh(manager);
    }
    public void refresh(TowerManager manager) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        long now = level.getGameTime();
        TowerRecord previous = manager.snapshot(bankId);
        boolean available = previous == null || AntennaScanner.dependenciesAvailable(serverLevel, previous.footprint());
        boolean scanReady = verification.canScan(now, available);
        AntennaScanner.Result antenna = scanReady
                ? AntennaScanner.scan(serverLevel, worldPosition, corePos())
                : new AntennaScanner.Result(false, 0, Set.of());
        boolean accepted = false;
        // A warm-up is not an unsuccessful scan; preserve its settling deadline.
        if (scanReady) accepted = verification.accept(now, antenna.complete(), antenna.score());
        if (accepted) {
            antennaPower = antenna.score();
            manager.publish(this, antenna);
            super.setChanged();
            dirty = false;
        }
        nextRefresh = verification.nextAttempt(now);
        updateDisplayedState(manager);
    }
    private void updateDisplayedState(TowerManager manager) {
        status = !enabled ? 4 : RepeatersConfig.INSTANCE.requireCatalyst && !hasCatalyst() ? 2 : channels().isEmpty() ? 3
                : dirty ? (manager.isEligible(bankId) ? 6 : 5) : antennaPower <= 0 ? 1 : 0;
        boolean ready = manager.isEligible(bankId);
        BlockState state = getBlockState();
        if (state.getValue(RepeaterBankBlock.ACTIVE) != ready)
            level.setBlock(worldPosition, state.setValue(RepeaterBankBlock.ACTIVE, ready), Block.UPDATE_CLIENTS);
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            TowerManager manager = TowerManager.get(serverLevel.getServer());
            if (manager != null) manager.unloaded(this);
        }
        super.setRemoved();
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); items = NonNullList.withSize(10, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items);
        if (tag.hasUUID("BankId")) bankId = tag.getUUID("BankId");
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled"); antennaPower = tag.getInt("AntennaScore");
        lastSettings = settingsSnapshot(); markDirty();
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); ContainerHelper.saveAllItems(tag, items);
        tag.putUUID("BankId", bankId); tag.putBoolean("Enabled", enabled); tag.putInt("AntennaScore", antennaPower);
    }
}
