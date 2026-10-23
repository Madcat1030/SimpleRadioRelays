package dev.paul.simpleradiorepeaters.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Native book navigation with crafting illustrations from the server's synchronized recipe manager. */
public class HandbookScreen extends BookViewScreen {
    private final HandbookBook book;
    private final Map<ResourceLocation, Optional<Illustration>> illustrations = new HashMap<>();
    private int page;
    public HandbookScreen(HandbookBook book) { super(book); this.book = book; }
    @Override public boolean setPage(int requested) {
        int target = book == null ? requested : Math.max(0, Math.min(book.getPageCount() - 1, requested));
        boolean changed = super.setPage(target);
        if (changed) page = target;
        return changed;
    }
    @Override protected void pageBack() { setPage(page - 1); }
    @Override protected void pageForward() { setPage(page + 1); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ResourceLocation recipe = book.recipe(page); if (recipe == null) return;
        var illustration = illustrations.computeIfAbsent(recipe, this::resolve).orElse(null);
        int left = (width - 192) / 2 + 36;
        if (illustration == null) {
            graphics.drawWordWrap(font, Component.translatable("handbook.simpleradio_repeaters.recipe_missing"), left, 64, 114, 0x333333);
            return;
        }
        var name = font.split(illustration.result().getHoverName(), 114);
        for (int i = 0; i < Math.min(2, name.size()); i++) graphics.drawString(font, name.get(i), left, 55 + i * 9, 0x333333, false);
        ItemStack hovered = ItemStack.EMPTY;
        long cycle = System.currentTimeMillis() / 1000;
        for (int slot = 0; slot < 9; slot++) {
            var choices = illustration.ingredients().get(slot);
            ItemStack item = choices.length == 0 ? ItemStack.EMPTY : choices[(int) (cycle % choices.length)];
            int x = left + slot % 3 * 18, y = 82 + slot / 3 * 18;
            drawSlot(graphics, item, x, y);
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) hovered = item;
        }
        graphics.drawString(font, "\u2192", left + 65, 105, 0x555555, false);
        drawSlot(graphics, illustration.result(), left + 94, 100);
        if (mouseX >= left + 94 && mouseX < left + 112 && mouseY >= 100 && mouseY < 118) hovered = illustration.result();
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }
    private void drawSlot(GuiGraphics graphics, ItemStack item, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xff6f6556);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xffb9ab8e);
        if (!item.isEmpty()) { graphics.renderItem(item, x + 1, y + 1); graphics.renderItemDecorations(font, item, x + 1, y + 1); }
    }
    protected record Illustration(List<ItemStack[]> ingredients, ItemStack result) {}
    protected Optional<Illustration> resolve(ResourceLocation id) {
        var client = Minecraft.getInstance(); if (client.level == null) return Optional.empty();
        return illustrate(client.level.getRecipeManager().byKey(id).orElse(null), client.level.registryAccess());
    }
    protected static Optional<Illustration> illustrate(Recipe<?> recipe, net.minecraft.core.RegistryAccess registries) {
        if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe)) return Optional.empty();
        List<ItemStack[]> ingredients = new ArrayList<>(Collections.nCopies(9, new ItemStack[0]));
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        var source = recipe.getIngredients();
        for (int i = 0; i < source.size() && i / width < 3; i++) ingredients.set(i / width * 3 + i % width, source.get(i).getItems());
        return Optional.of(new Illustration(List.copyOf(ingredients), recipe.getResultItem(registries)));
    }
}
