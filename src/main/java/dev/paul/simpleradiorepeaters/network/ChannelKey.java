package dev.paul.simpleradiorepeaters.network;

import com.codinglitch.simpleradio.central.Frequency;
import net.minecraft.nbt.CompoundTag;

public record ChannelKey(String frequency, String modulation) implements Comparable<ChannelKey> {
    public static ChannelKey from(CompoundTag tag) {
        if (tag == null || tag.getString("frequency").isBlank()
                || Frequency.modulationOf(tag.getString("modulation")) == null) return null;
        return new ChannelKey(tag.getString("frequency"), tag.getString("modulation"));
    }
    public static ChannelKey from(Frequency channel) { return new ChannelKey(channel.getFrequency(), channel.getModulation().shorthand); }
    public Frequency.Modulation mode() { return Frequency.modulationOf(modulation); }
    public CompoundTag save() { CompoundTag tag = new CompoundTag(); tag.putString("frequency", frequency); tag.putString("modulation", modulation); return tag; }
    @Override public int compareTo(ChannelKey other) { int f = frequency.compareTo(other.frequency); return f == 0 ? modulation.compareTo(other.modulation) : f; }
    @Override public String toString() { return frequency + " " + modulation; }
}
