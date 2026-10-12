package dev.paul.simpleradiorepeaters.item;

import com.codinglitch.simpleradio.SimpleRadioApi;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.Frequencing;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class FrequencyCardItem extends Item implements Frequencing {
    public FrequencyCardItem(Properties properties) { super(properties); }
    public static boolean isTuned(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("frequency") && !tag.getString("frequency").isBlank()
                && Frequency.modulationOf(tag.getString("modulation")) != null;
    }
    @Override public Frequency getFrequency(ItemStack stack) {
        // The native bench dereferences this value even for an untuned item.
        var api = SimpleRadioApi.getInstance();
        if (isTuned(stack)) {
            CompoundTag tag = stack.getTag();
            if (api.frequencies().check(tag.getString("frequency")))
                return api.frequencies().getOrCreate(tag.getString("frequency"), Frequency.modulationOf(tag.getString("modulation")));
        }
        return api.frequencies().getOrCreate(api.frequencies().defaultFrequency(), api.frequencies().defaultModulation());
    }
    @Override public CompoundTag setFrequency(ItemStack stack, String frequency, Frequency.Modulation modulation) {
        if (modulation == null || !SimpleRadioApi.getInstance().frequencies().check(frequency)) return stack.getOrCreateTag();
        return Frequencing.super.setFrequency(stack, frequency, modulation);
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        if (isTuned(stack)) lines.add(Component.literal(stack.getTag().getString("frequency") + " "
                + stack.getTag().getString("modulation")).withStyle(ChatFormatting.AQUA));
        else lines.add(Component.translatable("tooltip.simpleradio_repeaters.untuned").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.simpleradio_repeaters.card").withStyle(ChatFormatting.DARK_GRAY));
    }
}
