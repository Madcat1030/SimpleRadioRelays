package dev.paul.simpleradiorepeaters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import java.util.stream.IntStream;

public final class TransponderRenderer implements BlockEntityRenderer<TransponderBlockEntity> {
    public static final ResourceLocation[] CARDS = IntStream.range(0, 3)
            .mapToObj(slot -> SimpleRadioRepeaters.id("block/transponder_card_" + slot)).toArray(ResourceLocation[]::new);
    private final Font font;
    public TransponderRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }
    @Override public void render(TransponderBlockEntity transponder, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose(); DeviceRendering.face(pose, transponder.getBlockState().getValue(ChannelBankBlock.FACING));
        var cards = transponder.displayCards();
        for (int slot = 0; slot < 3; slot++) {
            var card = cards.get(slot); if (!card.present()) continue;
            DeviceRendering.part(CARDS[slot], transponder.getBlockState(), pose, buffers, light, overlay);
            DeviceRendering.centeredText(font, card.label(), TransponderBlock.slotX(slot) + 2.5, TransponderBlock.slotY(slot) + 1.5,
                    0, 3, .8, .0065f, 0xffe0d8b8, pose, buffers);
        }
        switch (transponder.mode()) {
            case SIMPLEX -> arrow("\u2192", 8, pose, buffers);
            case DUPLEX -> { arrow("\u2192", 8.8, pose, buffers); arrow("\u2190", 7.2, pose, buffers); }
            case MULTICAST -> arrow("*", 8, pose, buffers);
        }
        pose.popPose();
    }
    private void arrow(String arrow, double y, PoseStack pose, MultiBufferSource buffers) {
        DeviceRendering.centeredText(font, arrow, 8, y, -.02, 3.25, 1.3, .012f, 0xff91d25f, pose, buffers);
    }
}
