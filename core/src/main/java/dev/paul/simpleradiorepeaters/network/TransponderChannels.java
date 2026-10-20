package dev.paul.simpleradiorepeaters.network;

import dev.paul.simpleradiorepeaters.item.CardState;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Ordered left/right card roles; repeated frequencies are allowed and collapse to one directed port. */
public record TransponderChannels(@Nullable ChannelKey left, @Nullable ChannelKey rightTop,
                                  @Nullable ChannelKey rightBottom, boolean duplex) {
    public record Port(ChannelKey input, ChannelKey output) {}
    public static TransponderChannels of(List<CardState> cards, boolean duplex) {
        return new TransponderChannels(cards.get(0).channel(), cards.get(1).channel(), cards.get(2).channel(), duplex);
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
        for (ChannelKey right : Arrays.asList(rightTop, rightBottom)) if (right != null) {
            result.add(new Port(left, right)); if (duplex) result.add(new Port(right, left));
        }
        return List.copyOf(result);
    }
    public List<CardState> cards() {
        return Arrays.asList(left, rightTop, rightBottom).stream()
                .map(key -> key == null ? CardState.EMPTY : new CardState(true, key.frequency(), key.modulation())).toList();
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putBoolean("Duplex", duplex);
        if (left != null) tag.put("Left", left.save());
        if (rightTop != null) tag.put("RightTop", rightTop.save());
        if (rightBottom != null) tag.put("RightBottom", rightBottom.save());
        return tag;
    }
    public static TransponderChannels load(CompoundTag tag) {
        return new TransponderChannels(ChannelKey.from(tag.getCompound("Left")), ChannelKey.from(tag.getCompound("RightTop")),
                ChannelKey.from(tag.getCompound("RightBottom")), tag.getBoolean("Duplex"));
    }
}
