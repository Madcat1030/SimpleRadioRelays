package dev.paul.simpleradiorepeaters.block;

import com.codinglitch.simpleradio.central.Frequencing;
import dev.paul.simpleradiorepeaters.*;
import dev.paul.simpleradiorepeaters.item.CardState;
import dev.paul.simpleradiorepeaters.network.*;
import dev.paul.simpleradiorepeaters.radio.RelayCatalysts;
import dev.paul.simpleradiorepeaters.radio.RelayAttachment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Antenna/network owner with a direct-insertion catalyst and no container or inventory screen. */
public final class RelayBlockEntity extends BlockEntity implements Frequencing {
    private UUID relayId = UUID.randomUUID();
    private ItemStack catalyst = ItemStack.EMPTY;
    private ItemStack attachment = ItemStack.EMPTY;
    private boolean enabled = true, dirty = true, firstTick = true, updatingBank, bankPending;
    private int antennaPower, status = 2, displayPeers;
    private long nextRefresh;
    @Nullable private CardBankRef cardBank;
    private List<CardState> cachedCards = emptyCards();
    @Nullable private TransponderChannels transponder;
    private final AntennaVerification verification = new AntennaVerification();
    private record Settings(List<CardState> cards, ResourceLocation catalyst, boolean enabled, CardBankRef controller,
                            ResourceLocation attachment, TransponderChannels transponder) {}
    private Settings lastSettings = settingsSnapshot();
    private record Display(int score, int status, int peers, int channels, boolean enabled) {}
    @Nullable private Display lastDisplay;
    public RelayBlockEntity(BlockPos pos, BlockState state) { super(SimpleRadioRepeaters.RELAY_ENTITY, pos, state); }
    private static List<CardState> emptyCards() { return Collections.nCopies(6, CardState.EMPTY); }
    public UUID relayId() { return relayId; }
    public void regenerateId() { relayId = UUID.randomUUID(); super.setChanged(); syncClient(); }
    public boolean enabled() { return enabled; }
    public int displayStatus() { return status; }
    public int displayPeers() { return displayPeers; }
    public ItemStack catalyst() { return catalyst; }
    public ItemStack attachment() { return attachment; }
    public RelayAttachment attachmentType() { return RelayAttachment.of(attachment); }
    @Nullable public TransponderChannels transponderChannels() { return transponder; }
    @Nullable public CardBankRef cardBank() { return cardBank; }
    public void setEnabled(boolean value) { if (enabled != value) { enabled = value; setChanged(); } }
    @Override public int getAntennaPower() { return antennaPower; }
    @Override public void markDirty() {
        // Coalesce a burst of mast/block changes; restarting every tick can postpone settling forever.
        if (dirty) return;
        dirty = true; nextRefresh = 0; verification.reset();
    }
    public boolean hasCatalyst() { return RelayCatalysts.accepts(catalyst); }
    @Nullable public ResourceLocation catalystItem() { return RelayCatalysts.item(catalyst); }
    public boolean installCatalyst(ItemStack held, boolean creative) {
        if (!catalyst.isEmpty() || !RelayCatalysts.accepts(held)) return false;
        catalyst = held.copy(); catalyst.setCount(1);
        if (!creative) held.shrink(1); setChanged(); return true;
    }
    public ItemStack removeCatalyst() {
        if (catalyst.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = catalyst; catalyst = ItemStack.EMPTY; setChanged(); return result;
    }
    public boolean installAttachment(ItemStack held, boolean creative) {
        if (!attachment.isEmpty() || RelayAttachment.of(held) == RelayAttachment.NONE) return false;
        attachment = held.copy(); attachment.setCount(1);
        if (!creative) held.shrink(1); setChanged(); return true;
    }
    public ItemStack removeAttachment() {
        if (attachment.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = attachment; attachment = ItemStack.EMPTY; setChanged(); return result;
    }
    public BlockPos corePos() { return worldPosition.relative(getBlockState().getValue(RelayBlock.FACING).getOpposite()); }
    public Set<ChannelKey> channels() {
        Set<ChannelKey> result = new TreeSet<>();
        for (CardState state : cachedCards) { ChannelKey channel = state.channel(); if (channel != null) result.add(channel); }
        return result;
    }
    @Nullable public ChannelBankBlockEntity findAssignedBank() {
        if (level == null) return null;
        if (BankConnection.entity(level, worldPosition.above()) instanceof ChannelBankBlockEntity bank
                && !bank.isRemoved() && worldPosition.equals(bank.primaryRelayPosition())) return bank;
        return null;
    }
    public void bankChanged() { if (!updatingBank) refreshBank(); }
    private void refreshBank() {
        if (level == null || level.isClientSide || updatingBank) return;
        updatingBank = true;
        try {
            if (cardBank != null && !BankConnection.isMounted(worldPosition, cardBank.position()))
                controllerRemoved(cardBank.position());
            if (cardBank != null) {
                BlockState state = BankConnection.state(level, cardBank.position());
                if (state == null || (state.getBlock() instanceof ChannelBankBlock
                        && !(BankConnection.entity(level, cardBank.position()) instanceof ChannelBankBlockEntity bank && !bank.isRemoved()))) {
                    bankPending = true; return;
                }
            }
            bankPending = false;
            ChannelBankBlockEntity bank = findAssignedBank();
            if (bank == null) {
                if (cardBank != null) { cardBank = null; cachedCards = emptyCards(); transponder = null; setChanged(); }
                return;
            }
            CardBankRef current = new CardBankRef(bank.getBlockPos(), bank.bankId());
            List<CardState> cards = bank.cardStates();
            TransponderChannels routing = bank.transponderChannels();
            if (!current.equals(cardBank) || !cards.equals(cachedCards) || !Objects.equals(routing, transponder)) {
                cardBank = current; cachedCards = cards; transponder = routing; setChanged();
            }
        } finally { updatingBank = false; }
    }
    public void controllerRemoved(BlockPos pos) {
        if (cardBank != null && cardBank.position().equals(pos)) {
            cardBank = null; cachedCards = emptyCards(); transponder = null; bankPending = false; setChanged();
        }
    }
    private void adoptSavedController(TowerRecord saved) {
        if (saved == null || (Objects.equals(saved.cardBank(), cardBank) && saved.channels().equals(channels())
                && Objects.equals(saved.transponder(), transponder))) return;
        // Changes observed in the Bank's loaded chunk override an older Relay NBT cache.
        cardBank = saved.cardBank();
        transponder = saved.transponder();
        List<CardState> cards = new ArrayList<>();
        if (transponder != null) cards.addAll(transponder.cards());
        else if (cardBank != null) saved.channels().stream().sorted().limit(6)
                .forEach(channel -> cards.add(new CardState(true, channel.frequency(), channel.modulation())));
        while (cards.size() < (transponder == null ? 6 : 3)) cards.add(CardState.EMPTY);
        cachedCards = List.copyOf(cards);
        lastSettings = settingsSnapshot();
        super.setChanged();
    }
    @Override public void setChanged() {
        super.setChanged(); if (cachedCards == null) return;
        Settings current = settingsSnapshot();
        if (current.equals(lastSettings)) return;
        Settings previous = lastSettings; lastSettings = current;
        if (!current.cards().equals(previous.cards()) || !Objects.equals(current.controller(), previous.controller())
                || (previous.catalyst() == null && current.catalyst() != null) || (!previous.enabled() && current.enabled())) markDirty();
        if (level instanceof ServerLevel server) {
            TowerManager manager = TowerManager.get(server.getServer());
            if (manager != null) { manager.settingsChanged(this); updateDisplayedState(manager); }
            syncClient();
        }
    }
    private Settings settingsSnapshot() { return new Settings(cachedCards, catalystItem(), enabled, cardBank,
            RelayCatalysts.item(attachment), transponder); }
    public static void tick(Level level, BlockPos pos, BlockState state, RelayBlockEntity relay) {
        TowerManager manager = TowerManager.get(((ServerLevel) level).getServer()); if (manager == null) return;
        // Resolve copied identities before adopting or publishing any controller settings.
        boolean newlyLoaded = manager.loaded(relay);
        if (relay.firstTick) { relay.adoptSavedController(manager.snapshot(relay.relayId)); relay.firstTick = false; }
        relay.refreshBank();
        if (newlyLoaded) {
            manager.settingsChanged(relay);
            TowerRecord saved = manager.snapshot(relay.relayId);
            if (saved != null && saved.verified() && saved.antennaScore() > 0) relay.antennaPower = saved.antennaScore();
            relay.markDirty();
        }
        long now = level.getGameTime();
        // Native RX/TX antenna calculations use this same 200-tick world-time boundary.
        // Also recover changes whose native dirty notification never reached the core.
        if (!relay.dirty && now % AntennaVerification.RETRY_TICKS == 0) {
            relay.dirty = true; relay.nextRefresh = now;
        }
        if (relay.dirty && now >= relay.nextRefresh) relay.refresh(manager);
        if (level.getGameTime() % 20 == 0) relay.updateDisplayedState(manager);
    }
    public void refresh(TowerManager manager) {
        if (!(level instanceof ServerLevel server)) return;
        long now = level.getGameTime(); TowerRecord previous = manager.snapshot(relayId);
        boolean available = previous == null || AntennaScanner.dependenciesAvailable(server, previous.footprint());
        boolean scanReady = verification.canScan(now, available);
        AntennaScanner.Result antenna = scanReady ? AntennaScanner.scan(server, worldPosition, corePos())
                : new AntennaScanner.Result(false, 0, Set.of(), corePos());
        boolean accepted = scanReady && verification.accept(now, antenna.complete(), antenna.score());
        if (accepted) { antennaPower = antenna.score(); manager.publish(this, antenna); super.setChanged(); dirty = false; }
        nextRefresh = verification.nextAttempt(now); updateDisplayedState(manager);
    }
    private void updateDisplayedState(TowerManager manager) {
        status = !enabled ? 4 : !hasCatalyst() ? 2
                : cardBank == null ? 7 : channels().isEmpty() || (transponder != null && !transponder.ready()) ? 3 : bankPending ? 8
                : dirty ? (manager.isEligible(relayId) ? 6 : 5) : antennaPower <= 0 ? 1 : 0;
        boolean ready = manager.isEligible(relayId);
        BlockState state = getBlockState();
        if (state.getValue(RelayBlock.ACTIVE) != ready)
            level.setBlock(worldPosition, state.setValue(RelayBlock.ACTIVE, ready), Block.UPDATE_CLIENTS);
        displayPeers = manager.peers(relayId);
        Display current = new Display(antennaPower, status, displayPeers, channels().size(), enabled);
        if (!current.equals(lastDisplay)) { lastDisplay = current; syncClient(); }
    }
    private void syncClient() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) { TowerManager manager = TowerManager.get(server.getServer()); if (manager != null) manager.unloaded(this); }
        super.setRemoved();
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("RelayId")) relayId = tag.getUUID("RelayId");
        catalyst = tag.contains("Catalyst", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Catalyst")) : ItemStack.EMPTY;
        if (!catalyst.isEmpty()) catalyst.setCount(1);
        attachment = tag.contains("Attachment", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Attachment")) : ItemStack.EMPTY;
        if (!attachment.isEmpty()) attachment.setCount(1);
        transponder = tag.contains("Transponder", Tag.TAG_COMPOUND) ? TransponderChannels.load(tag.getCompound("Transponder")) : null;
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled"); antennaPower = tag.getInt("AntennaScore");
        cardBank = tag.hasUUID("CardBankId") && tag.contains("CardBankPosition")
                ? new CardBankRef(BlockPos.of(tag.getLong("CardBankPosition")), tag.getUUID("CardBankId")) : null;
        List<CardState> cards = new ArrayList<>(); ListTag selected = tag.getList("CachedCards", Tag.TAG_COMPOUND);
        for (int i = 0; i < selected.size(); i++) cards.add(CardState.load(selected.getCompound(i)));
        cachedCards = cards.isEmpty() ? emptyCards() : List.copyOf(cards);
        if (cardBank == null || !BankConnection.isMounted(worldPosition, cardBank.position())) {
            cardBank = null; cachedCards = emptyCards(); transponder = null;
        }
        status = tag.contains("DisplayStatus") ? tag.getInt("DisplayStatus") : 2; displayPeers = tag.getInt("DisplayPeers");
        lastSettings = settingsSnapshot(); firstTick = true; lastDisplay = null;
        dirty = false; markDirty();
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putUUID("RelayId", relayId);
        if (!catalyst.isEmpty()) tag.put("Catalyst", catalyst.save(new CompoundTag()));
        if (!attachment.isEmpty()) tag.put("Attachment", attachment.save(new CompoundTag()));
        if (transponder != null) tag.put("Transponder", transponder.save());
        tag.putBoolean("Enabled", enabled); tag.putInt("AntennaScore", antennaPower);
        if (cardBank != null) { tag.putLong("CardBankPosition", cardBank.position().asLong()); tag.putUUID("CardBankId", cardBank.id()); }
        ListTag cards = new ListTag(); cachedCards.forEach(card -> cards.add(card.save())); tag.put("CachedCards", cards);
        tag.putInt("DisplayStatus", status); tag.putInt("DisplayPeers", displayPeers);
    }
}

