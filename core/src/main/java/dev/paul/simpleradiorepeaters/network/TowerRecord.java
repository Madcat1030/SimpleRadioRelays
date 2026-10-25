package dev.paul.simpleradiorepeaters.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import dev.paul.simpleradiorepeaters.block.BankConnection;
import dev.paul.simpleradiorepeaters.radio.RelayCatalysts;
import dev.paul.simpleradiorepeaters.radio.RelayAttachment;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.*;

public record TowerRecord(UUID id, String dimension, BlockPos position, Set<ChannelKey> channels,
                          boolean enabled, @Nullable ResourceLocation catalystItem, boolean verified, int antennaScore, Set<Long> footprint,
                          CardBankRef cardBank, RelayAttachment attachment, @Nullable TransponderChannels transponder,
                          BlockPos antennaBase, @Nullable BlockPos antennaTip) {
    public TowerRecord {
        position = position.immutable(); antennaBase = antennaBase.immutable();
        if (antennaTip != null) antennaTip = antennaTip.immutable();
        channels = Set.copyOf(channels); footprint = Set.copyOf(footprint);
        // Apply the same mounting rule to restored records and all later manager updates.
        if (cardBank != null && !BankConnection.isMounted(position, cardBank.position())) {
            cardBank = null; channels = Set.of(); transponder = null;
        }
        if (transponder != null) channels = transponder.channels();
    }
    public TowerRecord(UUID id, String dimension, BlockPos position, Set<ChannelKey> channels,
                       boolean enabled, @Nullable ResourceLocation catalystItem, boolean verified, int antennaScore, Set<Long> footprint,
                       CardBankRef cardBank, RelayAttachment attachment, @Nullable TransponderChannels transponder, BlockPos antennaBase) {
        this(id, dimension, position, channels, enabled, catalystItem, verified, antennaScore, footprint,
                cardBank, attachment, transponder, antennaBase, null);
    }
    /** Legacy snapshots and external adapters default to their supplied site until a scan resolves the mast. */
    public TowerRecord(UUID id, String dimension, BlockPos position, Set<ChannelKey> channels,
                       boolean enabled, @Nullable ResourceLocation catalystItem, boolean verified, int antennaScore, Set<Long> footprint,
                       CardBankRef cardBank, RelayAttachment attachment, @Nullable TransponderChannels transponder) {
        this(id, dimension, position, channels, enabled, catalystItem, verified, antennaScore, footprint,
                cardBank, attachment, transponder, position);
    }
    public TowerRecord(UUID id, String dimension, BlockPos position, Set<ChannelKey> channels,
                       boolean enabled, @Nullable ResourceLocation catalystItem, boolean verified, int antennaScore, Set<Long> footprint, CardBankRef cardBank) {
        this(id, dimension, position, channels, enabled, catalystItem, verified, antennaScore, footprint, cardBank, RelayAttachment.NONE, null);
    }
    public TowerRecord(UUID id, String dimension, BlockPos position, Set<ChannelKey> channels,
                       boolean enabled, @Nullable ResourceLocation catalystItem, boolean verified, int antennaScore, Set<Long> footprint) {
        this(id, dimension, position, channels, enabled, catalystItem, verified, antennaScore, footprint, null);
    }
    public boolean catalyst() { return RelayCatalysts.registered(catalystItem); }
    public double efficiency() { return RelayCatalysts.efficiency(catalystItem); }
    public boolean hardwareReady() { return enabled && catalyst() && verified && antennaScore > 0; }
    public boolean eligible() { return hardwareReady() && !channels.isEmpty()
            && (transponder == null || transponder.ready()); }
    public List<TransponderChannels.Port> ports() {
        return transponder == null ? channels.stream().sorted().map(channel -> new TransponderChannels.Port(channel, channel)).toList()
                : transponder.ports();
    }
    public TowerRecord settings(Set<ChannelKey> selected, boolean enabled, ResourceLocation catalyst, CardBankRef controller) {
        return settings(selected, enabled, catalyst, controller, attachment, controller == null ? null : transponder);
    }
    public TowerRecord settings(Set<ChannelKey> selected, boolean enabled, ResourceLocation catalyst, CardBankRef controller,
                                RelayAttachment attachment, @Nullable TransponderChannels transponder) {
        return new TowerRecord(id, dimension, position, selected, enabled, catalyst, verified, antennaScore, footprint,
                controller, attachment, transponder, antennaBase, antennaTip);
    }
    public TowerRecord invalidate() { return new TowerRecord(id, dimension, position, channels, enabled, catalystItem, false,
            antennaScore, footprint, cardBank, attachment, transponder, antennaBase, antennaTip); }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("Id", id); tag.putString("Dimension", dimension); tag.putLong("Position", position.asLong());
        tag.putLong("AntennaBase", antennaBase.asLong());
        if (antennaTip != null) tag.putLong("AntennaTip", antennaTip.asLong());
        tag.putBoolean("Enabled", enabled); if (catalystItem != null) tag.putString("CatalystItem", catalystItem.toString());
        tag.putBoolean("Verified", verified); tag.putInt("Score", antennaScore);
        tag.putString("Attachment", attachment.name());
        if (transponder != null) tag.put("Transponder", transponder.save());
        tag.putLongArray("Footprint", footprint.stream().mapToLong(Long::longValue).toArray());
        if (cardBank != null) { tag.putLong("CardBankPosition", cardBank.position().asLong()); tag.putUUID("CardBankId", cardBank.id()); }
        ListTag selected = new ListTag(); channels.stream().sorted().forEach(channel -> selected.add(channel.save())); tag.put("Channels", selected); return tag;
    }
    public static TowerRecord load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || tag.getString("Dimension").isBlank()) throw new IllegalArgumentException("Invalid tower record");
        Set<ChannelKey> channels = new TreeSet<>(); ListTag list = tag.getList("Channels", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) { ChannelKey key = ChannelKey.from(list.getCompound(i)); if (key != null) channels.add(key); }
        Set<Long> footprint = new HashSet<>(); for (long pos : tag.getLongArray("Footprint")) footprint.add(pos);
        CardBankRef controller = tag.hasUUID("CardBankId") && tag.contains("CardBankPosition", Tag.TAG_LONG)
                ? new CardBankRef(BlockPos.of(tag.getLong("CardBankPosition")), tag.getUUID("CardBankId")) : null;
        return new TowerRecord(tag.getUUID("Id"), tag.getString("Dimension"), BlockPos.of(tag.getLong("Position")), channels,
                tag.getBoolean("Enabled"), tag.getString("CatalystItem").isBlank() ? null : ResourceLocation.tryParse(tag.getString("CatalystItem")),
                tag.getBoolean("Verified"), Math.max(0, tag.getInt("Score")), footprint, controller,
                RelayAttachment.stored(tag.getString("Attachment")), tag.contains("Transponder", Tag.TAG_COMPOUND)
                        ? TransponderChannels.load(tag.getCompound("Transponder")) : null,
                BlockPos.of(tag.getLong(tag.contains("AntennaBase", Tag.TAG_LONG) ? "AntennaBase" : "Position")),
                tag.contains("AntennaTip", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("AntennaTip")) : null);
    }
}
