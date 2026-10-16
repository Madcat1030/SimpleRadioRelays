package dev.paul.simpleradiorepeaters.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.io.File;
import org.lwjgl.glfw.GLFW;

/** Native Minecraft meshes, textures, fonts and production block-entity renderers. No world or audio recording. */
final class ExpansionPreview extends Screen {
    private int frames;
    private ExpansionPreview() { super(Component.literal("Relay expansion")); }
    static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() == null && !(client.screen instanceof ExpansionPreview)) {
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1600, 1200);
                client.options.guiScale().set(1); client.resizeDisplay(); client.setScreen(new ExpansionPreview());
            }
        });
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xff161e21);
        g.drawString(font, "SIMPLE RADIO REPEATERS / 0.4.0", 45, 30, 0xffd8d2b6, false);
        g.drawString(font, "Native Minecraft render / existing 16px texture palette / separate catalyst and attachment sockets", 45, 50, 0xffa9b8ae, false);
        Item[] attachments = {SimpleRadioRepeaters.NETHER_FOCUS, SimpleRadioRepeaters.END_FOCUS, SimpleRadioRepeaters.RELAY_AMPLIFIER};
        String[] names = {"NETHER FOCUS", "END FOCUS", "RELAY AMPLIFIER"};
        String[] notes = {"Mapped X/Z / Nether roof / native attenuation", "Mapped X/Z / End bottom / native attenuation", "Reset carrier decay / retain worst audio"};
        for (int i = 0; i < 3; i++) {
            int x = 280 + i * 520;
            draw(g, attachments[i], i == 1 ? Items.AMETHYST_BLOCK : Items.GILDED_BLACKSTONE, x, 470, 320, -1);
            g.drawCenteredString(font, names[i], x, 92, 0xffd8d2b6);
            g.drawCenteredString(font, notes[i], x, 510, 0xffa9b8ae);
            g.pose().pushPose(); g.pose().translate(x - 16, 528, 1000); g.pose().scale(2, 2, 2);
            g.renderItem(new ItemStack(attachments[i]), 0, 0); g.flush(); g.pose().popPose();
        }
        String[] modes = {"SIMPLEX / LEFT TO RIGHT", "DUPLEX / BOTH DIRECTIONS", "EXISTING SIX-CARD BANK"};
        for (int i = 0; i < 3; i++) {
            int x = 280 + i * 520;
            draw(g, attachments[i], Items.GILDED_BLACKSTONE, x, 1100, 250, i);
            g.drawCenteredString(font, modes[i], x, 570, 0xffd8d2b6);
        }
        g.drawString(font, "Focus projections are ignored when native cross-dimensional radio is enabled. The amplifier still works.", 45, 1160, 0xffa9b8ae, false);
        g.flush();
        if (++frames == 40) Screenshot.grab(new File("."), "Relay-Expansion-0.4.0.png", minecraft.getMainRenderTarget(), message -> {
            SimpleRadioRepeaters.LOGGER.info("EXPANSION_QA native expansion rendered: {}", message.getString()); minecraft.stop();
        });
    }
    private void draw(GuiGraphics g, Item attachment, Item catalyst, double cx, double cy, float scale, int module) {
        var pose = g.pose(); pose.pushPose(); pose.translate(cx, cy, 250); pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(12)); pose.mulPose(Axis.YP.rotationDegrees(190)); pose.translate(-.5, 0, -.5); Lighting.setupFor3DItems();
        var state = SimpleRadioRepeaters.RELAY.defaultBlockState().setValue(RelayBlock.ACTIVE, true);
        RelayBlockEntity relay = new RelayBlockEntity(BlockPos.ZERO, state); CompoundTag tag = new CompoundTag();
        tag.putInt("AntennaScore", 32); tag.putInt("DisplayStatus", 0); tag.putInt("DisplayPeers", 2); tag.putBoolean("Enabled", true);
        tag.put("Catalyst", new ItemStack(catalyst).save(new CompoundTag()));
        tag.put("Attachment", new ItemStack(attachment).save(new CompoundTag())); relay.load(tag);
        minecraft.getBlockRenderer().renderSingleBlock(state, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        minecraft.getBlockEntityRenderDispatcher().getRenderer(relay).render(relay, 0, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        if (module >= 0) {
            pose.translate(0, 1, 0);
            var block = module == 2 ? SimpleRadioRepeaters.CHANNEL_BANK : SimpleRadioRepeaters.TRANSPONDER;
            var bankState = block.defaultBlockState().setValue(ChannelBankBlock.CONNECTED, true);
            ChannelBankBlockEntity cards = module == 2 ? new ChannelBankBlockEntity(BlockPos.ZERO, bankState) : new TransponderBlockEntity(BlockPos.ZERO, bankState);
            for (int slot = 0; slot < cards.getContainerSize(); slot++) {
                ItemStack card = new ItemStack(SimpleRadioRepeaters.CARD); card.getOrCreateTag().putString("frequency", "000.0" + (slot + 1));
                card.getTag().putString("modulation", slot == 2 && module < 2 ? "FM" : "AM"); cards.setItem(slot, card);
            }
            if (cards instanceof TransponderBlockEntity transponder) transponder.setDuplex(module == 1);
            minecraft.getBlockRenderer().renderSingleBlock(bankState, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            minecraft.getBlockEntityRenderDispatcher().getRenderer(cards).render(cards, 0, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        g.flush(); pose.popPose();
    }
}
