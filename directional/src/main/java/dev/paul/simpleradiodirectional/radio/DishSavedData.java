package dev.paul.simpleradiodirectional.radio;

import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class DishSavedData extends SavedData {
    public static final String NAME = "simpleradio_directional_dishes";
    final Map<Key, Dish> dishes = new HashMap<>();
    final Map<Key, Set<Long>> relays = new HashMap<>();
    record Key(String dimension, long position) {
        static Key of(Dish dish) { return new Key(dish.dimension().toString(), dish.position().asLong()); }
    }
    public static DishSavedData load(CompoundTag tag) {
        var data = new DishSavedData(); var entries = tag.getList("Dishes", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var dish = Dish.load(entries.getCompound(i));
            if (dish != null) data.dishes.put(Key.of(dish), dish);
        }
        var bindings = tag.getList("Relays", Tag.TAG_COMPOUND);
        for (int i = 0; i < bindings.size(); i++) {
            var entry = bindings.getCompound(i);
            var hosts = new HashSet<Long>(); for (long host : entry.getLongArray("Masts")) hosts.add(host);
            data.relays.put(new Key(entry.getString("Dimension"), entry.getLong("Position")), Set.copyOf(hosts));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var list = new ListTag(); dishes.values().stream()
                .sorted(Comparator.comparing(d -> d.dimension() + ":" + d.position().asLong()))
                .forEach(d -> list.add(d.save()));
        tag.put("Dishes", list);
        var bindings = new ListTag();
        dataBindings(bindings); tag.put("Relays", bindings); return tag;
    }
    private void dataBindings(ListTag list) {
        relays.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().dimension() + ":" + e.getKey().position())).forEach(e -> {
            var entry = new CompoundTag(); entry.putString("Dimension", e.getKey().dimension());
            entry.putLong("Position", e.getKey().position()); entry.putLongArray("Masts", e.getValue().stream().sorted().mapToLong(Long::longValue).toArray());
            list.add(entry);
        });
    }
}
