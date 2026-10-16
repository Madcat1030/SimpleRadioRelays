package dev.paul.simpleradiorepeaters.radio;

import dev.paul.simpleradiorepeaters.item.RelayAttachmentItem;
import net.minecraft.world.item.ItemStack;

/** The mutually exclusive components in the Relay's second socket. */
public enum RelayAttachment {
    NONE, NETHER_FOCUS, END_FOCUS, AMPLIFIER;
    public static RelayAttachment of(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof RelayAttachmentItem item ? item.attachment() : NONE;
    }
    public static RelayAttachment stored(String name) {
        try { return valueOf(name); } catch (IllegalArgumentException e) { return NONE; }
    }
}
