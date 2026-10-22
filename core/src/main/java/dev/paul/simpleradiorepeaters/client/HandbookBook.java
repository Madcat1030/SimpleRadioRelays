package dev.paul.simpleradiorepeaters.client;

import dev.paul.simpleradiorepeaters.handbook.HandbookContents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.*;
import java.util.ArrayList;
import java.util.List;

/** Native book pages, rebuilt with the current language, font and installed addons on each opening. */
public final class HandbookBook implements BookViewScreen.BookAccess {
    private static final int WIDTH = 114, LINES = 14;
    private final List<FormattedText> pages;
    private HandbookBook(List<FormattedText> pages) { this.pages = List.copyOf(pages); }
    public static void open() {
        var client = Minecraft.getInstance(); var mods = FabricLoader.getInstance();
        client.setScreen(new BookViewScreen(create(client.font, HandbookContents.chapters(
                mods.isModLoaded("simpleradio_repeaters_foci"), mods.isModLoaded("simpleradio_directional")))));
    }
    public static HandbookBook create(Font font, List<HandbookContents.Chapter> chapters) {
        List<FormattedText> pages = new ArrayList<>();
        pages.add(FormattedText.EMPTY); pages.add(FormattedText.EMPTY);
        var contents = Component.translatable("handbook.simpleradio_repeaters.contents").withStyle(ChatFormatting.BOLD);
        contents.append("\n\n");
        for (var chapter : chapters) {
            contents.append(link(chapter.titleKey(), pages.size() + 1)).append("\n");
            var title = lines(font, Component.translatable(chapter.titleKey()).withStyle(ChatFormatting.BOLD));
            var body = lines(font, Component.translatable(chapter.bodyKey()));
            int capacity = LINES - title.size() - 3;
            if (capacity < 1) throw new IllegalArgumentException("Handbook title is too tall: " + chapter.id());
            for (int start = 0; start < body.size(); start += capacity) {
                var page = new ArrayList<FormattedText>(title); page.add(FormattedText.EMPTY);
                page.addAll(body.subList(start, Math.min(body.size(), start + capacity)));
                while (page.size() < LINES - 1) page.add(FormattedText.EMPTY);
                page.add(link("handbook.simpleradio_repeaters.contents", 2));
                pages.add(join(page));
            }
        }
        var cover = Component.empty().append(Component.translatable("item.simpleradio_repeaters.handbook").withStyle(ChatFormatting.BOLD))
                .append("\n\n")
                .append(Component.translatable("handbook.simpleradio_repeaters.intro"))
                .append("\n\n").append(link("handbook.simpleradio_repeaters.contents", 2));
        pages.set(0, cover); pages.set(1, contents);
        return new HandbookBook(pages);
    }
    private static List<FormattedText> lines(Font font, FormattedText text) {
        return font.getSplitter().splitLines(text, WIDTH, Style.EMPTY);
    }
    private static MutableComponent link(String translation, int page) {
        return Component.translatable(translation).withStyle(style -> style.withColor(ChatFormatting.DARK_BLUE)
                .withBold(false).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, Integer.toString(page))));
    }
    private static FormattedText join(List<FormattedText> lines) {
        var pieces = new ArrayList<FormattedText>();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) pieces.add(FormattedText.of("\n"));
            pieces.add(lines.get(i));
        }
        return FormattedText.composite(pieces);
    }
    @Override public int getPageCount() { return pages.size(); }
    @Override public FormattedText getPageRaw(int page) {
        return page >= 0 && page < pages.size() ? pages.get(page) : FormattedText.EMPTY;
    }
}
