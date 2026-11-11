package dev.paul.simpleradiorepeaters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.monitor.MonitorMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class RelayMonitorRenderer implements BlockEntityRenderer<RelayMonitorBlockEntity> {
    public static final ResourceLocation CARD = SimpleRadioRepeaters.id("block/monitor_card");
    private static final float[][] RING = {
            {-.55f, 1}, {.55f, 1}, {1, .55f}, {1, -.55f},
            {.55f, -1}, {-.55f, -1}, {-1, -.55f}, {-1, .55f}
    };
    private static final float INNER_RADIUS_RATIO = .55f;
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
        int density = map.density();
        double width = MonitorMap.columnSpacing(density) * .9, height = MonitorMap.rowSpacing(density) * .82;
        if (map.wide()) {
            DeviceRendering.centeredText(font, "NETWORK", 11.6, 13.68, 0, 4, .46, .0065f, 0xffa4cfad, pose, buffers);
            DeviceRendering.centeredText(font, "0", 8, 10, 0, width, height, .0065f, 0xff79d9ff, pose, buffers);
        } else {
            DeviceRendering.centeredText(font, "O", 8, 10, 0, 1, .82, .0065f, 0xff79d9ff, pose, buffers);
        }
        if (map.wide() && !map.markers().isEmpty()) {
            var vertices = buffers.getBuffer(RenderType.debugQuads());
            var transform = pose.last().pose();
            float radius = (float) (height * .45 / 16);
            for (var marker : map.markers()) {
                // A north-facing block's model X runs leftward when viewed from the front.
                float x = (float) ((8 - MonitorMap.east(marker.cell(), density)) / 16);
                float y = (float) ((10 - MonitorMap.south(marker.cell(), density)) / 16);
                drawRing(vertices, transform, x, y, radius, markerColor(marker.kind()));
            }
        } else {
            for (var marker : map.markers())
                DeviceRendering.centeredText(font, marker.label(), 8 - MonitorMap.east(marker.cell(), density),
                        10 - MonitorMap.south(marker.cell(), density), 0, width, height, .0065f,
                        markerColor(marker.kind()), pose, buffers);
        }
        if (map.pending())
            DeviceRendering.centeredText(font, "Surveying", 8, 6.3, 0, 5, .4, .0065f, 0xffa4cfad, pose, buffers);
    }

    private static int markerColor(MonitorMap.Kind kind) {
        return switch (kind) {
            case TX_RX -> 0xff75ed82;
            case RX_ONLY -> 0xffffffff;
            case ESTIMATE -> 0xffffa24a;
        };
    }

    // Solid strokes preserve the o shape when the font's thin bitmap strokes would undersample.
    private static void drawRing(VertexConsumer vertices, Matrix4f transform, float x, float y, float radius, int color) {
        for (int i = 0; i < RING.length; i++) {
            float[] start = RING[i], end = RING[(i + 1) % RING.length];
            ringVertex(vertices, transform, x, y, start, radius, color);
            ringVertex(vertices, transform, x, y, end, radius, color);
            ringVertex(vertices, transform, x, y, end, radius * INNER_RADIUS_RATIO, color);
            ringVertex(vertices, transform, x, y, start, radius * INNER_RADIUS_RATIO, color);
        }
    }

    private static void ringVertex(VertexConsumer vertices, Matrix4f transform, float x, float y,
                                   float[] offset, float radius, int color) {
        vertices.vertex(transform, x + offset[0] * radius, y + offset[1] * radius, -.001f).color(color).endVertex();
    }
}
