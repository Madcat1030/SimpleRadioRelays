package dev.paul.simpleradiodirectional.preview;

import com.codinglitch.simpleradio.central.Antennal;
import com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks;
import com.codinglitch.simpleradio.core.registry.blocks.AntennaBlock;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import dev.paul.simpleradiodirectional.DirectionalAntenna;
import dev.paul.simpleradiodirectional.block.ParabolicReceiverBlock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;
import java.io.File;
import java.util.HashSet;

/** Capture only: uses the real client texture atlas, baked block models, item lighting and framebuffer. */
public final class DishPreviewClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() == null && !(client.screen instanceof Preview)) {
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1600, 1000);
                client.options.guiScale().set(1); client.resizeDisplay(); client.setScreen(new Preview());
            }
        });
    }
    private static final class Preview extends Screen {
        private int frames;
        private boolean checked;
        Preview() { super(Component.literal("Parabolic receiver / native model capture")); }
        private BlockState dish(int aim) {
            return DirectionalAntenna.DISH.defaultBlockState().setValue(ParabolicReceiverBlock.FACING, Direction.NORTH)
                    .setValue(ParabolicReceiverBlock.AIM, aim);
        }
        private void checkModel() {
            var textures = new HashSet<String>();
            for (int aim = 0; aim <= 6; aim++) {
                var model = minecraft.getBlockRenderer().getBlockModel(dish(aim));
                var random = RandomSource.create(0);
                for (var quad : model.getQuads(dish(aim), null, random)) textures.add(quad.getSprite().contents().name().toString());
            }
            for (String material : new String[]{"array_copper", "array_iron", "array_ceramic", "array_cable", "dish_reflector"})
                if (!textures.contains("simpleradio_directional:block/" + material))
                    throw new IllegalStateException("Native block model missing texture: " + material + "; " + textures);
            if (textures.stream().anyMatch(s -> s.contains("missingno"))) throw new IllegalStateException("Missing model texture");
            System.out.println("DISH_NATIVE_PREVIEW verified seven baked aims and all five pixel materials: " + textures);
        }
        private void block(GuiGraphics graphics, BlockState state, double x, double y, double z) {
            graphics.pose().pushPose(); graphics.pose().translate(x, y, z);
            minecraft.getBlockRenderer().renderSingleBlock(state, graphics.pose(), graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            graphics.pose().popPose();
        }
        private void view(GuiGraphics graphics, double x, double y, float scale, float tilt, float yaw, int aim, boolean mounted) {
            var pose = graphics.pose(); pose.pushPose(); pose.translate(x, y, 250);
            pose.scale(scale, -scale, scale); pose.mulPose(Axis.XP.rotationDegrees(tilt));
            pose.mulPose(Axis.YP.rotationDegrees(180 + yaw)); pose.translate(-.5, -.5, -.5);
            Lighting.setupFor3DItems();
            if (mounted) {
                block(graphics, Blocks.IRON_BLOCK.defaultBlockState(), 0, -3, 1);
                for (int height = -2; height <= 2; height++) {
                    var mast = SimpleRadioBlocks.ANTENNA.defaultBlockState().setValue(Antennal.UP, height < 2)
                            .setValue(Antennal.DOWN, true).setValue(Antennal.ATTACHED, height == -2)
                            .setValue(AntennaBlock.COLUMN, true).setValue(AntennaBlock.AXIS, Direction.Axis.Y);
                    block(graphics, mast, 0, height, 1);
                }
            }
            block(graphics, dish(aim), 0, 0, 0);
            graphics.flush(); pose.popPose();
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (!checked) { checkModel(); checked = true; }
            graphics.fill(0, 0, width, height, 0xff161e21);
            graphics.drawString(font, "PARABOLIC RECEIVER / 0.2.1", 40, 26, 0xffd8d2b6, false);
            graphics.drawString(font, "Native Minecraft capture / 16 texture pixels per block / shared mast clamp", 40, 46, 0xffa9b8ae, false);
            if (frames < 60) {
                view(graphics, width * .29, height * .47, 133, 12, -24, 3, true);
                view(graphics, width * .76, height * .37, 151, 12, -5, 3, false);
                view(graphics, width * .77, height * .78, 125, 20, 150, 3, false);
                graphics.drawCenteredString(font, "Mounted on the native mast", (int) (width * .29), (int) (height * .96), 0xffb4c8bd);
                graphics.drawCenteredString(font, "Front / faceted bowl and compact feed", (int) (width * .76), (int) (height * .57), 0xffb4c8bd);
                graphics.drawCenteredString(font, "Rear / saddle, swivel and twin clamps", (int) (width * .77), (int) (height * .96), 0xffb4c8bd);
            } else {
                for (int i = 0; i < 7; i++) {
                    int column = i % 4, row = i / 4;
                    double x = width * (.135 + column * .245), y = height * (.34 + row * .45);
                    view(graphics, x, y, 100, 14, -22, i, false);
                    graphics.drawCenteredString(font, String.format("%+d degrees", -45 + 15 * i), (int) x,
                            (int) (y + 185), 0xffb4c8bd);
                }
            }
            graphics.flush();
            if (++frames == 60) capture("Parabolic-Receiver-0.2.1-Native-Preview.png", false);
            if (frames == 120) capture("Parabolic-Receiver-0.2.1-Aiming.png", true);
        }
        private void capture(String name, boolean stop) {
            Screenshot.grab(new File("."), name, minecraft.getMainRenderTarget(), message -> {
                System.out.println("DISH_NATIVE_PREVIEW " + message.getString());
                if (stop) minecraft.stop();
            });
        }
    }
}
