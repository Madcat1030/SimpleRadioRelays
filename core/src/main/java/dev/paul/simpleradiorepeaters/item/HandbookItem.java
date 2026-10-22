package dev.paul.simpleradiorepeaters.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Objects;

/** The client binds its reader once. Dedicated servers never load the book screen. */
public final class HandbookItem extends Item {
    private Runnable reader = () -> {};
    public HandbookItem() { super(new Item.Properties().stacksTo(1)); }
    public void setReader(Runnable reader) { this.reader = Objects.requireNonNull(reader); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) reader.run();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.simpleradio_repeaters.handbook").withStyle(ChatFormatting.GRAY));
    }
}
