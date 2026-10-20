package dev.paul.simpleradiorepeaters.item;

import dev.paul.simpleradiorepeaters.radio.RelayAttachment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class RelayAttachmentItem extends Item {
    private final RelayAttachment attachment;
    private final String hint;
    public RelayAttachmentItem(RelayAttachment attachment) { this(attachment, null); }
    public RelayAttachmentItem(RelayAttachment attachment, String hint) { super(new Properties().stacksTo(1)); this.attachment = attachment; this.hint = hint; }
    public RelayAttachment attachment() { return attachment; }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.simpleradio_repeaters." + attachment.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
        if (hint != null) lines.add(Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY));
    }
}
