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
    public RelayAttachmentItem(RelayAttachment attachment) { super(new Properties().stacksTo(1)); this.attachment = attachment; }
    public RelayAttachment attachment() { return attachment; }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.simpleradio_repeaters." + attachment.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
        if (attachment == RelayAttachment.NETHER_FOCUS || attachment == RelayAttachment.END_FOCUS)
            lines.add(Component.translatable("tooltip.simpleradio_repeaters.focus_policy").withStyle(ChatFormatting.DARK_GRAY));
    }
}
