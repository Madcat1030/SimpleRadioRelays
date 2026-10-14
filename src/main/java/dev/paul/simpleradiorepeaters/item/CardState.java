package dev.paul.simpleradiorepeaters.item;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.network.ChannelKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Immutable tuning snapshot; detects in-place NBT edits without treating empty clicks as changes. */
public record CardState(boolean present, String frequency, String modulation) {
    public static final CardState EMPTY = new CardState(false, "", "");
    public static CardState of(ItemStack stack) {
        if (!stack.is(SimpleRadioRepeaters.CARD)) return EMPTY;
        CompoundTag tag = stack.getTag();
        return new CardState(true, tag == null ? "" : tag.getString("frequency"), tag == null ? "" : tag.getString("modulation"));
    }
    public ChannelKey channel() { return present ? ChannelKey.from(save()) : null; }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putBoolean("Present", present);
        tag.putString("frequency", frequency); tag.putString("modulation", modulation); return tag;
    }
    public static CardState load(CompoundTag tag) { return new CardState(tag.getBoolean("Present"), tag.getString("frequency"), tag.getString("modulation")); }
}
