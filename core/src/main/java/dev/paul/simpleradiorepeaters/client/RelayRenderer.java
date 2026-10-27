package dev.paul.simpleradiorepeaters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.paul.simpleradiorepeaters.block.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;

public final class RelayRenderer implements BlockEntityRenderer<RelayBlockEntity> {
    private static final String[] STATUS = {"READY", "NO ANT", "NO CAT", "NO CARD", "OFF", "SCAN", "CHECK", "NO BANK", "WAIT BANK", "MONITOR"};
    private final Font font;
    public RelayRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }
    @Override public void render(RelayBlockEntity relay, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        // Both inserts protrude into the front cell. The opaque housing's own cell can be unlit.
        int insertLight = relay.getLevel() == null ? light : LevelRenderer.getLightColor(relay.getLevel(),
                relay.getBlockPos().relative(relay.getBlockState().getValue(RelayBlock.FACING)));
        pose.pushPose(); DeviceRendering.face(pose, relay.getBlockState().getValue(RelayBlock.FACING));
        if (!relay.attachment().isEmpty()) {
            pose.pushPose(); pose.translate(12d / 16, 4d / 16, -1d / 16); pose.scale(.25f, .25f, .25f);
            Minecraft.getInstance().getItemRenderer().renderStatic(relay.attachment(), ItemDisplayContext.FIXED,
                    insertLight, overlay, pose, buffers, relay.getLevel(), 0); pose.popPose();
        }
        if (!relay.catalyst().isEmpty()) {
            pose.pushPose();
            if (relay.catalyst().getItem() instanceof BlockItem block) {
                pose.translate(2d / 16, 2d / 16, -2d / 16); pose.scale(.25f, .25f, .1875f);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.getBlock().defaultBlockState(), pose, buffers, insertLight, overlay);
            } else {
                pose.translate(4d / 16, 4d / 16, -.5d / 16); pose.scale(.25f, .25f, .25f);
                Minecraft.getInstance().getItemRenderer().renderStatic(relay.catalyst(), ItemDisplayContext.FIXED,
                        insertLight, overlay, pose, buffers, relay.getLevel(), 0);
            }
            pose.popPose();
        }
        int status = relay.displayStatus();
        String label = status >= 0 && status < STATUS.length ? STATUS[status] : "WAIT";
        long antenna = Math.round(relay.getAntennaPower());
        DeviceRendering.text(font, "ANT " + antenna, 8, 11.8, -1, 8, .008f, 0xffb5d994, pose, buffers);
        DeviceRendering.text(font, label, 8, 10.6, -1, 8, .008f, status == RelayBlockEntity.READY ? 0xff91d25f : 0xffd7b766, pose, buffers);
        DeviceRendering.text(font, "LINK " + relay.displayPeers(), 8, 9.4, -1, 8, .008f, 0xffb5d994, pose, buffers);
        pose.popPose();
    }
}
