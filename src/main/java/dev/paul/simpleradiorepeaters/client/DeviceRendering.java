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
        pose.pushPose(); pose.translate(x / 16, y / 16, z / 16 - .001);
        pose.mulPose(Axis.YP.rotationDegrees(180)); pose.scale(scale, -scale, scale);
        font.drawInBatch(label, -labelWidth / 2f, 0, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
