package dev.paul.simpleradiorepeaters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Shared NORTH-facing model transform and surface text; all positions are in model pixels. */
final class DeviceRendering {
    private DeviceRendering() {}
    static void face(PoseStack pose, Direction facing) {
        float rotation = switch (facing) { case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0; };
        pose.translate(.5, .5, .5); pose.mulPose(Axis.YP.rotationDegrees(rotation)); pose.translate(-.5, -.5, -.5);
    }
    static void part(ResourceLocation id, BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft client = Minecraft.getInstance();
        client.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), state,
                client.getModelManager().getModel(id), 1, 1, 1, light, overlay);
    }
    static void text(Font font, String label, double x, double y, double z, double width, float preferredScale,
                     int color, PoseStack pose, MultiBufferSource buffers) {
        int labelWidth = font.width(label);
        float scale = Math.min(preferredScale, (float) (width / 16 / Math.max(1, labelWidth)));
        label(font, label, labelWidth, x, y, z, scale, 0, color, pose, buffers);
    }
    /** Fits a single line in both dimensions, centered at model-space X/Y even when it shrinks. */
    static void centeredText(Font font, String label, double x, double y, double z, double width, double height,
                             float preferredScale, int color, PoseStack pose, MultiBufferSource buffers) {
        int labelWidth = font.width(label);
        float scale = Math.min(preferredScale, (float) Math.min(width / 16 / Math.max(1, labelWidth),
                height / 16 / Math.max(1, font.lineHeight)));
        label(font, label, labelWidth, x, y, z, scale, -font.lineHeight / 2f, color, pose, buffers);
    }
    private static void label(Font font, String label, int labelWidth, double x, double y, double z, float scale,
                              float textY, int color, PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose(); pose.translate(x / 16, y / 16, z / 16 - .001);
        pose.mulPose(Axis.YP.rotationDegrees(180)); pose.scale(scale, -scale, scale);
        font.drawInBatch(label, -labelWidth / 2f, textY, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
