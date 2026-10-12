package dev.paul.simpleradiorepeaters.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import dev.paul.simpleradiorepeaters.RepeatersConfig;
import java.util.*;

public record TowerRecord(UUID id, String dimension, BlockPos position, Set<ChannelKey> channels,
                          boolean enabled, boolean catalyst, boolean verified, int antennaScore, Set<Long> footprint) {
    public TowerRecord { position = position.immutable(); channels = Set.copyOf(channels); footprint = Set.copyOf(footprint); }
    public boolean eligible() { return enabled && (!RepeatersConfig.INSTANCE.requireCatalyst || catalyst) && verified && antennaScore > 0 && !channels.isEmpty(); }
    public TowerRecord settings(Set<ChannelKey> selected, boolean enabled, boolean catalyst) {
        return new TowerRecord(id, dimension, position, selected, enabled, catalyst, verified, antennaScore, footprint);
    }
    public TowerRecord invalidate() { return new TowerRecord(id, dimension, position, channels, enabled, catalyst, false, antennaScore, footprint); }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("Id", id); tag.putString("Dimension", dimension); tag.putLong("Position", position.asLong());
        tag.putBoolean("Enabled", enabled); tag.putBoolean("Catalyst", catalyst); tag.putBoolean("Verified", verified); tag.putInt("Score", antennaScore);
        tag.putLongArray("Footprint", footprint.stream().mapToLong(Long::longValue).toArray());
        ListTag selected = new ListTag(); channels.stream().sorted().forEach(channel -> selected.add(channel.save())); tag.put("Channels", selected); return tag;
    }
    public static TowerRecord load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || tag.getString("Dimension").isBlank()) throw new IllegalArgumentException("Invalid tower record");
        Set<ChannelKey> channels = new TreeSet<>(); ListTag list = tag.getList("Channels", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) { ChannelKey key = ChannelKey.from(list.getCompound(i)); if (key != null) channels.add(key); }
        Set<Long> footprint = new HashSet<>(); for (long pos : tag.getLongArray("Footprint")) footprint.add(pos);
        return new TowerRecord(tag.getUUID("Id"), tag.getString("Dimension"), BlockPos.of(tag.getLong("Position")), channels,
                tag.getBoolean("Enabled"), tag.getBoolean("Catalyst"), tag.getBoolean("Verified"), Math.max(0, tag.getInt("Score")), footprint);
    }
}
