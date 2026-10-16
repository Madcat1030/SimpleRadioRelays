package dev.paul.simpleradiorepeaters.network;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class TowerSavedData extends SavedData {
    // The split hardware intentionally starts a fresh network; 0.1.x records are not imported.
    public static final String NAME = "simpleradio_repeaters_relays";
    public final Map<UUID, TowerRecord> towers = new HashMap<>();
    public static TowerSavedData load(CompoundTag tag) {
        TowerSavedData data = new TowerSavedData(); ListTag nodes = tag.getList("Towers", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodes.size(); i++) try {
            CompoundTag node = nodes.getCompound(i); TowerRecord record = TowerRecord.load(node); data.towers.put(record.id(), record);
            if (node.contains("CardBankPosition") && record.cardBank() == null) data.setDirty();
        }
        catch (RuntimeException e) { SimpleRadioRepeaters.LOGGER.warn("Skipping damaged repeater record", e); }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Schema", 1); ListTag nodes = new ListTag();
        towers.values().stream().sorted(Comparator.comparing(t -> t.id().toString())).forEach(t -> nodes.add(t.save())); tag.put("Towers", nodes); return tag;
    }
}
