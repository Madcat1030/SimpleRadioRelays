package dev.paul.simpleradiorepeaters.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import dev.paul.simpleradiorepeaters.radio.RelayCatalysts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.io.File;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/** Standalone QA screen: actual Minecraft block textures, models, fonts and production renderers. */
final class CatalystPreview extends Screen {
    private int frames;
    private final Item[] catalysts = {Items.GILDED_BLACKSTONE, Items.AMETHYST_BLOCK};
    private CatalystPreview() { super(Component.literal("Relay catalysts")); }
    static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() == null && !(client.screen instanceof CatalystPreview)) {
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 900);
                client.options.guiScale().set(1); client.resizeDisplay();
                RelayCatalysts.updateClient(List.of(BuiltInRegistries.ITEM.getKey(Items.GILDED_BLACKSTONE), BuiltInRegistries.ITEM.getKey(Items.AMETHYST_BLOCK)));
                if (!RelayCatalysts.acceptsClient(new ItemStack(Items.AMETHYST_BLOCK)) || RelayCatalysts.acceptsClient(new ItemStack(Items.DIRT)))
                    throw new IllegalStateException("Client catalyst catalog is inconsistent");
                client.setScreen(new CatalystPreview());
            }
        });
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xff161e21);
        g.drawString(font, "RADIO RELAY / 0.3.0 / CATALYSTS", 40, 26, 0xffd8d2b6, false);
        g.drawString(font, "Actual native block textures / same socket geometry / first relay already applies efficiency", 40, 45, 0xffa9b8ae, false);
        for (int i = 0; i < 2; i++) {
            double cx = width * (.27 + .46 * i);
            draw(g, catalysts[i], cx, height * .55, 365, false);
            g.drawCenteredString(font, i == 0 ? "GILDED BLACKSTONE / 90%" : "AMETHYST BLOCK / 60%", (int)cx, (int)(height * .61), 0xffd8d2b6);
            g.drawCenteredString(font, i == 0 ? "Relay 1: 90%   Relay 2: 81%   Relay 3: 72.9%" : "Relay 1: 60%   Relay 2: 36%   Relay 3: 21.6%", (int)cx, (int)(height * .64), 0xffa9b8ae);
            draw(g, catalysts[i], cx, height * .96, 125, true);
        }
        g.flush();
        if (++frames == 40) Screenshot.grab(new File("."), "Relay-Catalysts-0.3.0.png", minecraft.getMainRenderTarget(), message -> {
            SimpleRadioRepeaters.LOGGER.info("CATALYST_QA native gilded and amethyst textures and catalog rendered: {}", message.getString()); minecraft.stop();
        });
    }
    private void draw(GuiGraphics g, Item catalyst, double cx, double cy, float scale, boolean bank) {
        var pose = g.pose(); pose.pushPose(); pose.translate(cx, cy, 250); pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(12)); pose.mulPose(Axis.YP.rotationDegrees(198)); pose.translate(-.5, 0, -.5); Lighting.setupFor3DItems();
        var state = SimpleRadioRepeaters.RELAY.defaultBlockState().setValue(RelayBlock.ACTIVE, true);
        RelayBlockEntity relay = new RelayBlockEntity(BlockPos.ZERO, state); CompoundTag tag = new CompoundTag();
        tag.putInt("AntennaScore", 32); tag.putInt("DisplayStatus", 0); tag.putInt("DisplayPeers", 2); tag.putBoolean("Enabled", true);
        tag.put("Catalyst", new ItemStack(catalyst).save(new CompoundTag())); relay.load(tag);
        minecraft.getBlockRenderer().renderSingleBlock(state, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        minecraft.getBlockEntityRenderDispatcher().getRenderer(relay).render(relay, 0, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        if (bank) {
            pose.translate(0, 1, 0); var bankState = SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState().setValue(ChannelBankBlock.CONNECTED, true);
            ChannelBankBlockEntity cards = new ChannelBankBlockEntity(BlockPos.ZERO, bankState);
            for (int slot = 0; slot < 6; slot++) {
                ItemStack card = new ItemStack(SimpleRadioRepeaters.CARD); card.getOrCreateTag().putString("frequency", "000.0" + (slot + 1));
                card.getTag().putString("modulation", "AM"); cards.setItem(slot, card);
            }
            minecraft.getBlockRenderer().renderSingleBlock(bankState, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            minecraft.getBlockEntityRenderDispatcher().getRenderer(cards).render(cards, 0, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        g.flush(); pose.popPose();
    }
}
