package dev.paul.simpleradiorepeaters.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import dev.paul.simpleradiorepeaters.RepeatersConfig;
import dev.paul.simpleradiorepeaters.block.BankConnection;
import dev.paul.simpleradiorepeaters.radio.RelayCatalysts;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.*;

public record TowerRecord(UUID id, String dimension, BlockPos position, Set<ChannelKey> channels,
                          boolean enabled, @Nullable ResourceLocation catalystItem, boolean verified, int antennaScore, Set<Long> footprint, CardBankRef cardBank) {
    public TowerRecord {
        position = position.immutable(); channels = Set.copyOf(channels); footprint = Set.copyOf(footprint);
        // Apply the same mounting rule to restored records and all later manager updates.
        if (cardBank != null && !BankConnection.isMounted(position, cardBank.position())) {
            cardBank = null; channels = Set.of();
        }
    }
    public TowerRecord(UUID id, String dimension, BlockPos position, Set<ChannelKey> channels,
                       boolean enabled, @Nullable ResourceLocation catalystItem, boolean verified, int antennaScore, Set<Long> footprint) {
        this(id, dimension, position, channels, enabled, catalystItem, verified, antennaScore, footprint, null);
    }
    public boolean catalyst() { return RelayCatalysts.registered(catalystItem); }
    public double efficiency() { return catalystItem == null && !RepeatersConfig.INSTANCE.requireCatalyst ? 1 : RelayCatalysts.efficiency(catalystItem); }
    public boolean eligible() { return enabled && (!RepeatersConfig.INSTANCE.requireCatalyst || catalyst()) && verified && antennaScore > 0 && !channels.isEmpty(); }
    public TowerRecord settings(Set<ChannelKey> selected, boolean enabled, ResourceLocation catalyst, CardBankRef controller) {
        return new TowerRecord(id, dimension, position, selected, enabled, catalyst, verified, antennaScore, footprint, controller);
    }
    public TowerRecord invalidate() { return new TowerRecord(id, dimension, position, channels, enabled, catalystItem, false, antennaScore, footprint, cardBank); }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("Id", id); tag.putString("Dimension", dimension); tag.putLong("Position", position.asLong());
        tag.putBoolean("Enabled", enabled); if (catalystItem != null) tag.putString("CatalystItem", catalystItem.toString());
        tag.putBoolean("Verified", verified); tag.putInt("Score", antennaScore);
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
                tag.getBoolean("Verified"), Math.max(0, tag.getInt("Score")), footprint, controller);
    }
}
