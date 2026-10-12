package dev.paul.simpleradiorepeaters.client;

import dev.paul.simpleradiorepeaters.screen.RepeaterBankMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class RepeaterBankScreen extends AbstractContainerScreen<RepeaterBankMenu> {
    private Button toggle;
    public RepeaterBankScreen(RepeaterBankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageHeight = 228; inventoryLabelY = 132;
    }
    @Override protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 103, topPos + 76, 65, 18).build());
        updateToggle();
    }
    private void updateToggle() { if (toggle != null) toggle.setMessage(Component.translatable(menu.enabled() ? "screen.simpleradio_repeaters.enabled" : "screen.simpleradio_repeaters.disabled")); }
    @Override protected void containerTick() { super.containerTick(); updateToggle(); }
    @Override protected void renderBg(GuiGraphics g, float delta, int mouseX, int mouseY) {
        g.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, 0xff181c1d);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffc8c4b9);
        g.fill(leftPos + 5, topPos + 19, leftPos + 171, topPos + 128, 0xff333c3d);
        for (SlotPosition p : bankSlots()) slot(g, leftPos + p.x, topPos + p.y);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(g, leftPos + 8 + col * 18, topPos + 144 + row * 18);
        for (int col = 0; col < 9; col++) slot(g, leftPos + 8 + col * 18, topPos + 202);
    }
    private void slot(GuiGraphics g, int x, int y) { g.fill(x - 1, y - 1, x + 17, y + 17, 0xff191f20); g.fill(x, y, x + 16, y + 16, 0xff87918d); }
    private record SlotPosition(int x, int y) {}
    private SlotPosition[] bankSlots() {
        SlotPosition[] p = new SlotPosition[10];
        for (int i = 0; i < 9; i++) p[i] = new SlotPosition(26 + (i % 3) * 18, 34 + (i / 3) * 18);
        p[9] = new SlotPosition(130, 52); return p;
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, Component.translatable("screen.simpleradio_repeaters.channels"), 24, 23, 0xffe2e6db, false);
        g.drawString(font, Component.translatable("screen.simpleradio_repeaters.catalyst"), 110, 34, 0xffe2e6db, false);
        g.drawString(font, Component.translatable("screen.simpleradio_repeaters.status." + menu.status()), 10, 100, menu.status() == 0 ? 0xff8edf9b : 0xffe9c789, false);
        g.drawString(font, Component.translatable("screen.simpleradio_repeaters.strength", menu.score(), menu.channels()), 10, 110, 0xffe2e6db, false);
        g.drawString(font, Component.translatable("screen.simpleradio_repeaters.peers", menu.peers()), 10, 120, 0xffbfcac6, false);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g); super.render(g, mouseX, mouseY, delta); renderTooltip(g, mouseX, mouseY);
    }
}
