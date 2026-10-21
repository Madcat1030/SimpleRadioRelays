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
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** QA only: real Minecraft baked models, real resource-pack reload, raw framebuffer captures. */
public final class DishAlternatesPreviewClient implements ClientModInitializer {
    private static final ModelResourceLocation REFINED = new ModelResourceLocation(DirectionalAntenna.ID, "refined_preview", "inventory");
    @Override public void onInitializeClient() {
        ModelLoadingPlugin.register(context -> context.addModels(REFINED));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() == null && !(client.screen instanceof Preview)) {
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1600, 1000);
                client.options.guiScale().set(1); client.resizeDisplay(); client.setScreen(new Preview());
            }
        });
    }
    private static final class Preview extends Screen {
        private int frames;
        private boolean checked, waiting, refinedActive;
        private List<String> originalPacks;
        Preview() { super(Component.literal("Parabolic alternates / native Minecraft capture")); }
        private BlockState dish(int aim) {
            return DirectionalAntenna.DISH.defaultBlockState().setValue(ParabolicReceiverBlock.FACING, Direction.NORTH)
                    .setValue(ParabolicReceiverBlock.AIM, aim);
        }
        private BakedModel refined() { return minecraft.getModelManager().getModel(REFINED); }
        private int quads(BakedModel model) { return model.getQuads(dish(3), null, RandomSource.create(0)).size(); }
        private void checkDefault() {
            var textures = new HashSet<String>();
            for (int aim = 0; aim <= 6; aim++) {
                var model = minecraft.getBlockRenderer().getBlockModel(dish(aim));
                for (var quad : model.getQuads(dish(aim), null, RandomSource.create(0)))
                    textures.add(quad.getSprite().contents().name().toString());
            }
            for (String material : new String[]{"array_copper", "array_iron", "array_ceramic", "array_cable", "dish_reflector"})
                if (!textures.contains("simpleradio_directional:block/" + material)) throw new IllegalStateException("Missing native material: " + material);
            if (textures.stream().anyMatch(s -> s.contains("missingno")) || quads(refined()) != 1500)
                throw new IllegalStateException("Refined alternate or texture is missing");
            if (quads(minecraft.getBlockRenderer().getBlockModel(dish(3))) >= quads(refined()))
                throw new IllegalStateException("Chunky default did not bake separately");
            System.out.println("CHUNKY_NATIVE_PREVIEW verified seven aimed models, five materials and the preserved refined alternate");
        }
        private void block(GuiGraphics graphics, BlockState state, double x, double y, double z) {
            graphics.pose().pushPose(); graphics.pose().translate(x, y, z);
            minecraft.getBlockRenderer().renderSingleBlock(state, graphics.pose(), graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            graphics.pose().popPose();
        }
        private void view(GuiGraphics graphics, double x, double y, float scale, float tilt, float yaw, int aim, boolean mounted, boolean old) {
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
            if (old) {
                minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                        graphics.bufferSource().getBuffer(ItemBlockRenderTypes.getRenderType(dish(3), false)),
                        dish(3), refined(), 1, 1, 1, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            } else block(graphics, dish(aim), 0, 0, 0);
            graphics.flush(); pose.popPose();
        }
        private void details(GuiGraphics g) {
            view(g, width * .29, height * .47, 133, 12, -24, 3, true, false);
            view(g, width * .76, height * .37, 151, 12, -5, 3, false, false);
            view(g, width * .77, height * .78, 125, 20, 150, 3, false, false);
            g.drawCenteredString(font, "Mounted on the native mast", (int)(width * .29), (int)(height * .96), 0xffb4c8bd);
            g.drawCenteredString(font, "Front / broad rim and three bowl depths", (int)(width * .76), (int)(height * .57), 0xffb4c8bd);
            g.drawCenteredString(font, "Rear / paired wrap-around mast clamps", (int)(width * .77), (int)(height * .96), 0xffb4c8bd);
        }
        private void enableRefined() {
            var repository = minecraft.getResourcePackRepository();
            originalPacks = new ArrayList<>(repository.getSelectedIds());
            String id = repository.getAvailableIds().stream().filter(s -> s.contains("refined_parabolic")).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Built-in refined resource pack is not available"));
            var selected = new ArrayList<>(originalPacks); selected.add(id); repository.setSelected(selected);
            waiting = true;
            minecraft.reloadResourcePacks().thenRun(() -> minecraft.execute(() -> {
                if (quads(minecraft.getBlockRenderer().getBlockModel(dish(3))) != quads(refined()))
                    throw new IllegalStateException("Enabling the refined pack did not replace the actual dish model");
                System.out.println("REFINED_PACK verified native resource-pack reload replaces the actual block model: " + id);
                waiting = false; refinedActive = true; frames = 180;
            }));
        }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            if (!checked) { checkDefault(); checked = true; }
            g.fill(0, 0, width, height, 0xff161e21);
            g.drawString(font, refinedActive ? "REFINED ALTERNATE / BUILT-IN PACK ENABLED / 0.2.2" : "PARABOLIC RECEIVER ALTERNATES / 0.2.2", 40, 26, 0xffd8d2b6, false);
            g.drawString(font, "Native Minecraft client capture / 16 texture pixels per block", 40, 46, 0xffa9b8ae, false);
            if (waiting) return;
            if (refinedActive || frames < 60) details(g);
            else if (frames < 120) {
                view(g, width * .28, height * .47, 133, 12, -24, 3, true, false);
                view(g, width * .73, height * .47, 133, 12, -24, 3, true, true);
                g.drawCenteredString(font, "CHUNKY / default", (int)(width * .28), (int)(height * .96), 0xffd8d2b6);
                g.drawCenteredString(font, "REFINED / optional built-in resource pack", (int)(width * .73), (int)(height * .96), 0xffd8d2b6);
            } else {
                for (int i = 0; i < 7; i++) {
                    int column = i % 4, row = i / 4;
                    double x = width * (.135 + column * .245), y = height * (.34 + row * .45);
                    view(g, x, y, 100, 14, -22, i, false, false);
                    g.drawCenteredString(font, String.format("%+d degrees", -45 + 15 * i), (int)x, (int)(y + 185), 0xffb4c8bd);
                }
            }
            g.flush(); frames++;
            if (frames == 60) capture("Parabolic-Receiver-0.2.2-Native-Preview.png", false);
            if (frames == 120) capture("Parabolic-Receiver-0.2.2-Comparison.png", false);
            if (frames == 180 && !refinedActive) {
                capture("Parabolic-Receiver-0.2.2-Aiming.png", false);
                enableRefined();
            }
            if (frames == 240 && refinedActive) capture("Parabolic-Receiver-0.2.2-Refined-Alternate.png", true);
        }
        private void capture(String name, boolean stop) {
            Screenshot.grab(new File("."), name, minecraft.getMainRenderTarget(), message -> {
                System.out.println("CHUNKY_NATIVE_PREVIEW " + message.getString());
                if (stop) {
                    if (originalPacks != null) minecraft.getResourcePackRepository().setSelected(originalPacks);
                    minecraft.stop();
                }
            });
        }
    }
}
