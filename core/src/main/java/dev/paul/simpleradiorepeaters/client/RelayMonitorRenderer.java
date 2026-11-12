package dev.paul.simpleradiorepeaters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.monitor.MonitorMap;
import dev.paul.simpleradiorepeaters.monitor.MonitorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4f;

public final class RelayMonitorRenderer implements BlockEntityRenderer<RelayMonitorBlockEntity> {
    public static final ResourceLocation CARD = SimpleRadioRepeaters.id("block/monitor_card");
    private static final float[][] RING = {
            {-.55f, 1}, {.55f, 1}, {1, .55f}, {1, -.55f},
            {.55f, -1}, {-.55f, -1}, {-1, -.55f}, {-1, .55f}
    };
    private static final int LABEL_COLOR = 0xffa4cfad;
    private static final int CONTROL_COLOR = 0xffc6e18a;
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
        if (map != null) renderMap(map, hoveredCell(monitor, map, tick), monitor.showHome(), pose, buffers);
        else renderText(monitor, pose, buffers);
        pose.popPose();
    }

    private static int hoveredCell(RelayMonitorBlockEntity monitor, MonitorMap map, float tick) {
        var client = Minecraft.getInstance();
        if (client.screen != null || client.player == null || client.player.isSpectator() || client.gameMode == null
                || !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(monitor.getBlockPos())) return -1;
        var facing = monitor.getBlockState().getValue(ChannelBankBlock.FACING);
        if (hit.getDirection() != facing) return -1;
        double range = client.gameMode.getPickRange();
        if (client.player.getEyePosition(tick).distanceToSqr(hit.getLocation()) > range * range) return -1;
        var local = BankConnection.localHit(monitor.getBlockPos(), facing, hit.getLocation());
        return MonitorScreen.contains(local.x, local.y) ? map.cellAt(local.x, local.y) : -1;
    }

    private void renderText(RelayMonitorBlockEntity monitor, PoseStack pose, MultiBufferSource buffers) {
        var lines = monitor.lines();
        for (int i = 0; i < lines.size(); i++) {
            double y = monitor.showBack() ? MonitorScreen.lineY(i) : 10 + (lines.size() - 1) / 2.0 - i;
            DeviceRendering.centeredText(font, lines.get(i), 8, y, 0, 11.6, .72, .0065f,
                    i == 0 ? CONTROL_COLOR : LABEL_COLOR, pose, buffers);
        }
        if (monitor.showBack())
            DeviceRendering.centeredText(font, "[ Back ]", MonitorScreen.BACK_X, MonitorScreen.FOOTER_Y, 0, 3.6, .62,
                    .0065f, CONTROL_COLOR, pose, buffers);
    }

    private void renderMap(MonitorMap map, int hovered, boolean showHome, PoseStack pose, MultiBufferSource buffers) {
        DeviceRendering.centeredText(font, "N", 8, MonitorScreen.HOME_Y, 0, 1, .46, .0065f, LABEL_COLOR, pose, buffers);
        if (showHome)
            DeviceRendering.centeredText(font, "[ Home ]", MonitorScreen.HOME_X, MonitorScreen.HOME_Y, 0, 3.6, .46,
                    .0065f, CONTROL_COLOR, pose, buffers);
        int density = map.density();
        var vertices = buffers.getBuffer(RenderType.debugQuads());
        var transform = pose.last().pose();
        float radius = (float) MonitorMap.ringRadius(density);
        for (var link : map.links())
            drawLink(vertices, transform, mapPoint(link.fromCell(), density), mapPoint(link.toCell(), density),
                    radius, link.estimated());
        drawRing(vertices, transform, mapPoint(map.centerCell(), density), radius, markerColor(map.centerKind()),
                hovered == map.centerCell());
        for (var marker : map.markers())
            drawRing(vertices, transform, mapPoint(marker.cell(), density), radius, markerColor(marker.kind()),
                    hovered == marker.cell());
        DeviceRendering.centeredText(font, map.location(), 8, MonitorScreen.FOOTER_Y, 0, 11.5, .61, .0065f,
                map.centerKind() == MonitorMap.Kind.ESTIMATE ? markerColor(map.centerKind()) : LABEL_COLOR, pose, buffers);
        if (map.pending())
            DeviceRendering.centeredText(font, "Surveying", showHome ? 4.5 : 11.5, MonitorScreen.HOME_Y, 0, 4, .43, .0065f, LABEL_COLOR, pose, buffers);
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
    private static void drawRing(VertexConsumer vertices, Matrix4f transform, MapPoint center, float radius, int color, boolean hovered) {
        float outer = hovered ? radius * 1.18f : radius;
        float inner = hovered ? radius * .43f : radius * .55f;
        for (int i = 0; i < RING.length; i++) {
            float[] start = RING[i], end = RING[(i + 1) % RING.length];
            ringVertex(vertices, transform, center, start, outer, color);
            ringVertex(vertices, transform, center, end, outer, color);
            ringVertex(vertices, transform, center, end, inner, color);
            ringVertex(vertices, transform, center, start, inner, color);
        }
    }

    private static void ringVertex(VertexConsumer vertices, Matrix4f transform, MapPoint center,
                                   float[] offset, float radius, int color) {
        vertices.vertex(transform, center.x() + offset[0] * radius, center.y() + offset[1] * radius, -.001f).color(color).endVertex();
    }
}
