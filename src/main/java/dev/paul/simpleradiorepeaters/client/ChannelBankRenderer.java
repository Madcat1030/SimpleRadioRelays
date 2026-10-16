package dev.paul.simpleradiorepeaters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import java.util.stream.IntStream;

public final class ChannelBankRenderer implements BlockEntityRenderer<ChannelBankBlockEntity> {
    public static final ResourceLocation[] CARDS = IntStream.range(0, 6)
            .mapToObj(slot -> SimpleRadioRepeaters.id("block/bank_card_" + slot)).toArray(ResourceLocation[]::new);
    // Model pixels: the four-pixel strip is centered in the upper half of each two-pixel card.
    private static final double LABEL_WIDTH = 3.75, LABEL_HEIGHT = .8;
    private static final float LABEL_SCALE = .0065f;
    private static final int LABEL_COLOR = 0xffe0d8b8;
    private final Font font;
    public ChannelBankRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }
    @Override public void render(ChannelBankBlockEntity bank, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose(); DeviceRendering.face(pose, bank.getBlockState().getValue(ChannelBankBlock.FACING));
        var cards = bank.displayCards();
        for (int slot = 0; slot < 6; slot++) {
            var card = cards.get(slot); if (!card.present()) continue;
            DeviceRendering.part(CARDS[slot], bank.getBlockState(), pose, buffers, light, overlay);
            double x = BankConnection.slotX(slot) + 3, y = BankConnection.slotY(slot) + 1.5;
            DeviceRendering.centeredText(font, card.label(), x, y, 0, LABEL_WIDTH, LABEL_HEIGHT, LABEL_SCALE,
                    LABEL_COLOR, pose, buffers);
        }
        pose.popPose();
    }
}
