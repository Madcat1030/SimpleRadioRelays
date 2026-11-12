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
    private static final int ORIGIN_COLOR = 0xff79d9ff;
    private static final int LINK_COLOR = 0xff527880;
    private static final int ESTIMATE_LINK_COLOR = 0xffc68348;
    private record MapPoint(float x, float y) {}
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
        var vertices = buffers.getBuffer(RenderType.debugQuads());
        var transform = pose.last().pose();
        float radius = (float) MonitorMap.ringRadius(density);
        for (var link : map.links())
            drawLink(vertices, transform, mapPoint(link.fromCell(), density), mapPoint(link.toCell(), density),
                    radius, link.estimated());
        drawRing(vertices, transform, mapPoint(map.centerCell(), density), radius, markerColor(map.centerKind()));
        for (var marker : map.markers())
            drawRing(vertices, transform, mapPoint(marker.cell(), density), radius, markerColor(marker.kind()));
        DeviceRendering.centeredText(font, map.location(), 8, 6.47, 0, 11.5, .61, .0065f,
                map.centerKind() == MonitorMap.Kind.ESTIMATE ? markerColor(map.centerKind()) : 0xffa4cfad, pose, buffers);
        if (map.pending())
            DeviceRendering.centeredText(font, "Surveying", 11.5, 13.68, 0, 4, .43, .0065f, 0xffa4cfad, pose, buffers);
    }

    private static int markerColor(MonitorMap.Kind kind) {
        return switch (kind) {
            case HOME -> ORIGIN_COLOR;
            case TX_RX -> 0xff75ed82;
            case RX_ONLY -> 0xffffffff;
            case ESTIMATE -> 0xffffa24a;
        };
    }

    private static MapPoint mapPoint(int cell, int density) {
        // A north-facing block's model X runs leftward when viewed from the front.
        return new MapPoint((float) MonitorMap.modelX(cell, density), (float) MonitorMap.modelY(cell, density));
    }

    private static void drawLink(VertexConsumer vertices, Matrix4f transform, MapPoint from, MapPoint to,
                                 float radius, boolean estimated) {
        float dx = to.x() - from.x(), dy = to.y() - from.y();
        float length = (float) Math.hypot(dx, dy);
        float inset = radius * 1.15f;
        if (length <= 2 * inset) return;
        float unitX = dx / length, unitY = dy / length;
        if (estimated) {
            int count = Math.max(1, Math.min(32, Math.round((length - 2 * inset) / (radius * 1.1f))));
            for (int i = 0; i < count; i++) {
                float distance = inset + (i + .5f) * (length - 2 * inset) / count;
                float x = from.x() + distance * unitX, y = from.y() + distance * unitY;
                float half = radius * .23f;
                linkQuad(vertices, transform, x - half, y, x + half, y, 0, half, ESTIMATE_LINK_COLOR);
            }
        } else {
            float half = radius * .15f;
            linkQuad(vertices, transform, from.x() + inset * unitX, from.y() + inset * unitY,
                    to.x() - inset * unitX, to.y() - inset * unitY, -half * unitY, half * unitX, LINK_COLOR);
        }
    }

    private static void linkQuad(VertexConsumer vertices, Matrix4f transform, float x1, float y1,
                                 float x2, float y2, float normalX, float normalY, int color) {
        vertices.vertex(transform, x1 + normalX, y1 + normalY, -.0007f).color(color).endVertex();
        vertices.vertex(transform, x2 + normalX, y2 + normalY, -.0007f).color(color).endVertex();
        vertices.vertex(transform, x2 - normalX, y2 - normalY, -.0007f).color(color).endVertex();
        vertices.vertex(transform, x1 - normalX, y1 - normalY, -.0007f).color(color).endVertex();
    }

    // Solid strokes preserve the o shape when the font's thin bitmap strokes would undersample.
    private static void drawRing(VertexConsumer vertices, Matrix4f transform, MapPoint center, float radius, int color) {
        for (int i = 0; i < RING.length; i++) {
            float[] start = RING[i], end = RING[(i + 1) % RING.length];
            ringVertex(vertices, transform, center, start, radius, color);
            ringVertex(vertices, transform, center, end, radius, color);
            ringVertex(vertices, transform, center, end, radius * INNER_RADIUS_RATIO, color);
            ringVertex(vertices, transform, center, start, radius * INNER_RADIUS_RATIO, color);
        }
    }

    private static void ringVertex(VertexConsumer vertices, Matrix4f transform, MapPoint center,
                                   float[] offset, float radius, int color) {
        vertices.vertex(transform, center.x() + offset[0] * radius, center.y() + offset[1] * radius, -.001f).color(color).endVertex();
    }
}
