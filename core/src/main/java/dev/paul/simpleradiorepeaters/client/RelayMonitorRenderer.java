package dev.paul.simpleradiorepeaters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;

public final class RelayMonitorRenderer implements BlockEntityRenderer<RelayMonitorBlockEntity> {
    public static final ResourceLocation CARD = SimpleRadioRepeaters.id("block/monitor_card");
    private final Font font;
    public RelayMonitorRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }
    @Override public void render(RelayMonitorBlockEntity monitor, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose(); DeviceRendering.face(pose, monitor.getBlockState().getValue(ChannelBankBlock.FACING));
        var card = monitor.displayCards().get(0);
        if (card.present()) {
            DeviceRendering.part(CARD, monitor.getBlockState(), pose, buffers, light, overlay);
            DeviceRendering.centeredText(font, card.label(), 8, 2.5, 0, 4, .8, .0065f, 0xffe0d8b8, pose, buffers);
        }
        var lines = monitor.lines(); double spacing = Math.min(1.05, 7.25 / Math.max(1, lines.size()));
        double top = 10 + (lines.size() - 1) * spacing / 2;
        for (int i = 0; i < lines.size(); i++)
            DeviceRendering.centeredText(font, lines.get(i), 8, top - i * spacing, 0, 11.6, .85, .0065f,
                    i == 0 ? 0xffc6e18a : 0xffa4cfad, pose, buffers);
        pose.popPose();
    }
}
