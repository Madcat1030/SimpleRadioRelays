package dev.paul.simpleradiorepeaters.terrain;

import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** World-local, bounded relay terrain samples. Device paths never enter this SavedData. */
public final class TerrainProfiles extends SavedData {
    public static final String NAME = "simpleradio_repeater_terrain";
    private static final int MAX_PROFILES = 1024;
    public record Key(int ax, int az, int bx, int bz, int spacing, int limit) {
        public static Key between(int ax, int az, int bx, int bz, int spacing, int limit) {
            return ax < bx || ax == bx && az <= bz ? new Key(ax, az, bx, bz, spacing, limit)
                    : new Key(bx, bz, ax, az, spacing, limit);
        }
        public boolean startsAt(int x, int z) { return ax == x && az == z; }
    }
    private final Map<Key, int[]> profiles = new LinkedHashMap<>(16, .75f, true);
    private long seed;
    private boolean initialized;
    public void initialize(long worldSeed) {
        if (!initialized || seed != worldSeed) { clear(); seed = worldSeed; initialized = true; setDirty(); }
    }
    public int size() { return profiles.size(); }
    public int[] get(Key key) { return profiles.get(key); }
    public void put(Key key, int[] heights) {
        profiles.put(key, heights.clone());
        while (profiles.size() > MAX_PROFILES) profiles.remove(profiles.keySet().iterator().next());
        setDirty();
    }
    public void clear() { profiles.clear(); setDirty(); }
    public static TerrainProfiles load(CompoundTag tag) {
        var data = new TerrainProfiles();
        if (tag.getInt("Format") != 1) return data;
        data.seed = tag.getLong("Seed"); data.initialized = true;
        var entries = tag.getList("Profiles", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, entries.size() - MAX_PROFILES); i < entries.size(); i++) {
            var entry = entries.getCompound(i); var coords = entry.getIntArray("Path");
            var heights = entry.getIntArray("Heights");
            if (coords.length != 6 || coords[4] < 16 || coords[5] < 2 || coords[5] > 4096
                    || heights.length < 2 || heights.length > coords[5]) continue;
            data.profiles.put(new Key(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]), heights);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Format", 1); tag.putLong("Seed", seed); var entries = new ListTag();
        profiles.forEach((key, heights) -> {
            var entry = new CompoundTag();
            entry.putIntArray("Path", new int[]{key.ax(), key.az(), key.bx(), key.bz(), key.spacing(), key.limit()});
            entry.putIntArray("Heights", heights); entries.add(entry);
        });
        tag.put("Profiles", entries); return tag;
    }
}
