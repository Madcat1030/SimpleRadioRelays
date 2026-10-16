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
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.io.File;
import org.lwjgl.glfw.GLFW;

/** Separate temporary QA mod, never included in the released addon. */
final class BankModelPreview extends Screen {
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private final ChannelBankBlockEntity[] banks = new ChannelBankBlockEntity[4];
    private final ChannelBankBlockEntity empty = bank(Direction.NORTH);
    private final ChannelBankBlockEntity updates = bank(Direction.NORTH);
    private int frames;
    private boolean closeup;
    private BankModelPreview() {
        super(Component.literal("Repeater Bank preview"));
        for (int i = 0; i < 4; i++) { banks[i] = bank(FACINGS[i]); fill(banks[i]); }
        updates.displayCards();
    }
    static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() == null && !(client.screen instanceof BankModelPreview)) {
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 900);
                client.options.guiScale().set(1); client.resizeDisplay();
                client.setScreen(new BankModelPreview());
            }
        });
    }
    private static ChannelBankBlockEntity bank(Direction facing) {
        return new ChannelBankBlockEntity(BlockPos.ZERO, SimpleRadioRepeaters.CHANNEL_BANK.defaultBlockState().setValue(ChannelBankBlock.FACING, facing));
    }
    private static void fill(ChannelBankBlockEntity bank) {
        for (int slot = 0; slot < 6; slot++) {
            ItemStack card = new ItemStack(SimpleRadioRepeaters.CARD);
            card.getOrCreateTag().putString("frequency", "000.0" + (slot + 1));
            card.getTag().putString("modulation", slot % 2 == 0 ? "AM" : "FM"); bank.setItem(slot, card);
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (closeup) { renderCloseup(graphics); return; }
        if (frames == 10) { updates.load(banks[0].getUpdateTag()); if (!updates.displayCards().get(3).present()) throw new IllegalStateException("Client insertion cache stale"); }
        if (frames == 20) {
            ChannelBankBlockEntity changed = bank(Direction.NORTH); changed.load(updates.getUpdateTag()); changed.removeItem(3, 1);
            updates.load(changed.getUpdateTag()); if (updates.displayCards().get(3).present()) throw new IllegalStateException("Client removal cache stale");
        }
        if (frames == 25) {
            ChannelBankBlockEntity changed = bank(Direction.NORTH); changed.load(banks[0].getUpdateTag());
            changed.setItem(0, new ItemStack(SimpleRadioRepeaters.CARD));
            changed.getItem(3).getOrCreateTag().putString("frequency", "123.456.789"); changed.setChanged(); updates.load(changed.getUpdateTag());
            if (!updates.displayCards().get(0).label().equals("BLANK --") || !updates.displayCards().get(3).label().equals("123.456.789 FM"))
                throw new IllegalStateException("Client tuning cache stale");
        }
        graphics.fill(0, 0, width, height, 0xff161e21);
        graphics.drawString(font, "REPEATER BANK / 0.2.7", 40, 26, 0xffd8d2b6, false);
        graphics.drawString(font, "Native Minecraft models and font / compact cards / centered single-line labels", 40, 45, 0xffa9b8ae, false);
        for (int i = 0; i < 4; i++) {
            double x = width * (.14 + i * .24);
            drawBank(graphics, banks[i], x, height * .44, 225, 0, 0);
            graphics.drawCenteredString(font, FACINGS[i].getName().toUpperCase(), (int)x, (int)(height * .47), 0xffb4c8bd);
        }
        drawAssembly(graphics, width * .23, height * .90, 165);
        drawBank(graphics, empty, width * .56, height * .90, 230, 18, 28);
        drawBank(graphics, updates, width * .84, height * .90, 230, 0, 0);
        graphics.drawCenteredString(font, "Relay + Bank", (int)(width * .23), (int)(height * .96), 0xffb4c8bd);
        graphics.drawCenteredString(font, "Empty housing", (int)(width * .56), (int)(height * .94), 0xffb4c8bd);
        graphics.drawCenteredString(font, "Blank card + long frequency", (int)(width * .84), (int)(height * .94), 0xffb4c8bd);
        graphics.flush();
        if (++frames == 35) Screenshot.grab(new File("."), "Repeater-Bank-0.2.7-Native-Preview.png", minecraft.getMainRenderTarget(), message -> {
            SimpleRadioRepeaters.LOGGER.info("BANK_MODEL_QA cache updates and all four facings rendered: {}", message.getString());
        });
        if (frames == 35) closeup = true;
    }
    private void renderCloseup(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xff161e21);
        graphics.drawString(font, "REPEATER BANK / 0.2.7 / CARD DETAIL", 40, 26, 0xffd8d2b6, false);
        graphics.drawString(font, "Three-unit cards / dark strip and text centered in the middle row", 40, 45, 0xffa9b8ae, false);
        drawBank(graphics, banks[0], width * .30, height * .86, 550, 0, 0);
        drawBank(graphics, updates, width * .75, height * .86, 500, 0, 0);
        graphics.drawCenteredString(font, "Six tuned cards", (int)(width * .30), (int)(height * .94), 0xffb4c8bd);
        graphics.drawCenteredString(font, "Blank card + long frequency", (int)(width * .75), (int)(height * .94), 0xffb4c8bd);
        graphics.flush();
        if (++frames == 70) Screenshot.grab(new File("."), "Repeater-Bank-0.2.7-Card-Detail.png", minecraft.getMainRenderTarget(), message -> {
            SimpleRadioRepeaters.LOGGER.info("BANK_MODEL_QA three-unit card closeup rendered: {}", message.getString()); minecraft.stop();
        });
    }
    private void pose(GuiGraphics graphics, Direction facing, double cx, double cy, float scale, float tilt, float yaw) {
        var pose = graphics.pose(); pose.pushPose(); pose.translate(cx, cy, 250); pose.scale(scale, -scale, scale);
        float facingView = switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> -90; default -> 0; };
        pose.mulPose(Axis.XP.rotationDegrees(tilt)); pose.mulPose(Axis.YP.rotationDegrees(180 + facingView + yaw));
        pose.translate(-.5, 0, -.5); Lighting.setupFor3DItems();
    }
    private void renderBank(GuiGraphics graphics, ChannelBankBlockEntity bank) {
        minecraft.getBlockRenderer().renderSingleBlock(bank.getBlockState(), graphics.pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        minecraft.getBlockEntityRenderDispatcher().getRenderer(bank).render(bank, 0, graphics.pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }
    private void drawBank(GuiGraphics graphics, ChannelBankBlockEntity bank, double cx, double cy, float scale, float tilt, float yaw) {
        pose(graphics, bank.getBlockState().getValue(ChannelBankBlock.FACING), cx, cy, scale, tilt, yaw);
        renderBank(graphics, bank); graphics.flush(); graphics.pose().popPose();
    }
    private void drawAssembly(GuiGraphics graphics, double cx, double cy, float scale) {
        pose(graphics, Direction.NORTH, cx, cy, scale, 18, 28);
        var state = SimpleRadioRepeaters.RELAY.defaultBlockState().setValue(RelayBlock.ACTIVE, true);
        RelayBlockEntity relay = new RelayBlockEntity(BlockPos.ZERO, state); CompoundTag tag = new CompoundTag();
        tag.putInt("AntennaScore", 32); tag.putInt("DisplayStatus", 0); tag.putInt("DisplayPeers", 3); tag.putBoolean("Enabled", true);
        tag.put("Catalyst", new ItemStack(Items.GILDED_BLACKSTONE).save(new CompoundTag())); relay.load(tag);
        minecraft.getBlockRenderer().renderSingleBlock(state, graphics.pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        minecraft.getBlockEntityRenderDispatcher().getRenderer(relay).render(relay, 0, graphics.pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        graphics.pose().translate(0, 1, 0); renderBank(graphics, banks[0]); graphics.flush(); graphics.pose().popPose();
    }
}
