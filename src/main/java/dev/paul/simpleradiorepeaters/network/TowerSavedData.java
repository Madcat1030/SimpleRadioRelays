package dev.paul.simpleradiorepeaters.network;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class TowerSavedData extends SavedData {
    public static final String NAME = "simpleradio_repeaters_towers";
    public final Map<UUID, TowerRecord> towers = new HashMap<>();
    public static TowerSavedData load(CompoundTag tag) {
        TowerSavedData data = new TowerSavedData(); ListTag nodes = tag.getList("Towers", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodes.size(); i++) try { TowerRecord record = TowerRecord.load(nodes.getCompound(i)); data.towers.put(record.id(), record); }
        catch (RuntimeException e) { SimpleRadioRepeaters.LOGGER.warn("Skipping damaged repeater record", e); }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Schema", 1); ListTag nodes = new ListTag();
        towers.values().stream().sorted(Comparator.comparing(t -> t.id().toString())).forEach(t -> nodes.add(t.save())); tag.put("Towers", nodes); return tag;
    }
}
