package dev.paul.simpleradiorepeaters.radio;

import dev.paul.simpleradiorepeaters.item.RelayAttachmentItem;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registered socket effects; optional addons own their items and behavior. */
public final class RelayAttachment {
    private static final Map<String, RelayAttachment> TYPES = new ConcurrentHashMap<>();
    public static final RelayAttachment NONE = register("NONE", 1, false);
    public static final RelayAttachment COPPER_AMPLIFIER = register("COPPER_AMPLIFIER", 1.25, false);
    public static final RelayAttachment REDSTONE_AMPLIFIER = register("REDSTONE_AMPLIFIER", 1.5, false);
    public static final RelayAttachment AMPLIFIER = register("AMPLIFIER", 1.75, true);
    private final String name;
    private final double antennaMultiplier;
    private final boolean resetsDecay;
    private RelayAttachment(String name, double multiplier, boolean reset) {
        this.name = name; antennaMultiplier = multiplier; resetsDecay = reset;
    }
    public static RelayAttachment register(String name, double multiplier, boolean reset) {
        if (name == null || name.isBlank() || !Double.isFinite(multiplier) || multiplier <= 0)
            throw new IllegalArgumentException("Invalid relay attachment");
        RelayAttachment value = new RelayAttachment(name, multiplier, reset);
        if (TYPES.putIfAbsent(name, value) != null) throw new IllegalStateException("Duplicate relay attachment: " + name);
        return value;
    }
    public String name() { return name; }
    public double antennaMultiplier() { return antennaMultiplier; }
    public boolean resetsDecay() { return resetsDecay; }
    public static RelayAttachment of(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof RelayAttachmentItem item ? item.attachment() : NONE;
    }
    public static RelayAttachment stored(String name) { return TYPES.getOrDefault(name, NONE); }
    @Override public String toString() { return name; }
}
