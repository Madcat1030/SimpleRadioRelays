package dev.paul.simpleradiorepeaters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.item.CardState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import java.util.stream.IntStream;

public final class ChannelBankRenderer implements BlockEntityRenderer<ChannelBankBlockEntity> {
    public static final ResourceLocation[] CARDS = IntStream.range(0, 6)
            .mapToObj(slot -> SimpleRadioRepeaters.id("block/bank_card_" + slot)).toArray(ResourceLocation[]::new);
    private final Font font;
    public ChannelBankRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }
    @Override public void render(ChannelBankBlockEntity bank, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose(); DeviceRendering.face(pose, bank.getBlockState().getValue(ChannelBankBlock.FACING));
        var cards = bank.cardStates();
        for (int slot = 0; slot < 6; slot++) {
            CardState card = cards.get(slot); if (!card.present()) continue;
            DeviceRendering.part(CARDS[slot], bank.getBlockState(), pose, buffers, light, overlay);
            double x = BankConnection.slotX(slot) + 3, y = BankConnection.slotY(slot);
            boolean tuned = card.channel() != null;
            DeviceRendering.text(font, tuned ? card.frequency() : "BLANK", x, y + 2.75, 0, 5.5, .0065f, 0xff172522, pose, buffers);
            DeviceRendering.text(font, tuned ? card.modulation() : "--", x, y + 1.3, 0, 5.5, .0065f, 0xff172522, pose, buffers);
        }
        pose.popPose();
    }
}
