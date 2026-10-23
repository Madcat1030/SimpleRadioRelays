package dev.paul.simpleradiorepeaters.network;

import dev.paul.simpleradiorepeaters.item.CardState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Ordered left/right card roles; repeated frequencies are allowed and collapse to one directed port. */
public record TransponderChannels(@Nullable ChannelKey left, @Nullable ChannelKey rightTop,
                                  @Nullable ChannelKey rightBottom, Mode mode) {
    public enum Mode {
        SIMPLEX, DUPLEX, MULTICAST;
        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
        public static Mode load(CompoundTag tag) {
            if (!tag.contains("Mode", Tag.TAG_STRING)) return tag.getBoolean("Duplex") ? DUPLEX : SIMPLEX;
            try { return valueOf(tag.getString("Mode")); }
            catch (IllegalArgumentException ignored) { return SIMPLEX; }
        }
    }
    public TransponderChannels { Objects.requireNonNull(mode); }
    public TransponderChannels(ChannelKey left, ChannelKey rightTop, ChannelKey rightBottom, boolean duplex) {
        this(left, rightTop, rightBottom, duplex ? Mode.DUPLEX : Mode.SIMPLEX);
    }
    public record Port(ChannelKey input, ChannelKey output) {}
    public static TransponderChannels of(List<CardState> cards, Mode mode) {
        return new TransponderChannels(cards.get(0).channel(), cards.get(1).channel(), cards.get(2).channel(), mode);
    }
    public boolean ready() { return left != null && (rightTop != null || rightBottom != null); }
    public Set<ChannelKey> channels() {
        Set<ChannelKey> result = new TreeSet<>();
        for (ChannelKey key : Arrays.asList(left, rightTop, rightBottom)) if (key != null) result.add(key);
        return Set.copyOf(result);
    }
    public List<Port> ports() {
        if (!ready()) return List.of();
        Set<Port> result = new LinkedHashSet<>();
        if (mode == Mode.MULTICAST) {
            var channels = channels().stream().sorted().toList();
            for (ChannelKey input : channels) for (ChannelKey output : channels) result.add(new Port(input, output));
            return List.copyOf(result);
        }
        for (ChannelKey right : Arrays.asList(rightTop, rightBottom)) if (right != null) {
            result.add(new Port(left, right)); if (mode == Mode.DUPLEX) result.add(new Port(right, left));
        }
        return List.copyOf(result);
    }
    public List<CardState> cards() {
        return Arrays.asList(left, rightTop, rightBottom).stream()
                .map(key -> key == null ? CardState.EMPTY : new CardState(true, key.frequency(), key.modulation())).toList();
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putString("Mode", mode.name());
        if (left != null) tag.put("Left", left.save());
        if (rightTop != null) tag.put("RightTop", rightTop.save());
        if (rightBottom != null) tag.put("RightBottom", rightBottom.save());
        return tag;
    }
    public static TransponderChannels load(CompoundTag tag) {
        return new TransponderChannels(ChannelKey.from(tag.getCompound("Left")), ChannelKey.from(tag.getCompound("RightTop")),
                ChannelKey.from(tag.getCompound("RightBottom")), Mode.load(tag));
    }
}
