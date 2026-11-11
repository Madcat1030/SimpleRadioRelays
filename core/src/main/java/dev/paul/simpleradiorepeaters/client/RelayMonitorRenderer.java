package dev.paul.simpleradiorepeaters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.monitor.MonitorMap;
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
        var map = monitor.displayedMap();
        if (map != null) renderMap(map, pose, buffers);
        else renderText(monitor, pose, buffers);
        pose.popPose();
    }

    private void renderText(RelayMonitorBlockEntity monitor, PoseStack pose, MultiBufferSource buffers) {
        var lines = monitor.lines(); double spacing = Math.min(1.05, 7.25 / Math.max(1, lines.size()));
        double top = 10 + (lines.size() - 1) * spacing / 2;
        for (int i = 0; i < lines.size(); i++)
            DeviceRendering.centeredText(font, lines.get(i), 8, top - i * spacing, 0, 11.6, .85, .0065f,
                    i == 0 ? 0xffc6e18a : 0xffa4cfad, pose, buffers);
    }

    private void renderMap(MonitorMap map, PoseStack pose, MultiBufferSource buffers) {
        DeviceRendering.centeredText(font, "N", 8, 13.68, 0, 1, .46, .0065f, 0xffa4cfad, pose, buffers);
        DeviceRendering.centeredText(font, "0", 8, 10, 0, 1, .82, .0065f, 0xff79d9ff, pose, buffers);
        int density = map.density();
        for (var marker : map.markers()) {
            int color = switch (marker.kind()) {
                case TX_RX -> 0xff75ed82;
                case RX_ONLY -> 0xffffffff;
                case ESTIMATE -> 0xffffa24a;
            };
            // A north-facing block's model X runs leftward when viewed from the front.
            DeviceRendering.centeredText(font, marker.label(), 8 - MonitorMap.east(marker.cell(), density),
                    10 - MonitorMap.south(marker.cell(), density), 0, .95 / density, .82 / density, .0065f, color, pose, buffers);
        }
        if (map.pending())
            DeviceRendering.centeredText(font, "Surveying", 8, 6.3, 0, 5, .4, .0065f, 0xffa4cfad, pose, buffers);
    }
}
