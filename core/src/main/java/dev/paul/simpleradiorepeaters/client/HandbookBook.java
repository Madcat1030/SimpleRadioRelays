package dev.paul.simpleradiorepeaters.client;

import dev.paul.simpleradiorepeaters.handbook.HandbookContents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Current-language pagination and recipe pages; books never store a stale copy of the guide. */
public final class HandbookBook implements BookViewScreen.BookAccess {
    private static final int WIDTH = 114, LINES = 14;
    public record Page(FormattedText text, @Nullable ResourceLocation recipe) {}
    private final List<Page> pages;
    private HandbookBook(List<Page> pages) { this.pages = List.copyOf(pages); }
    public static void open() {
        var client = Minecraft.getInstance();
        client.setScreen(new HandbookScreen(create(client.font, HandbookChapters.load(client.getResourceManager()))));
    }
    public static HandbookBook create(Font font, List<HandbookContents.Chapter> chapters) {
        var coverTitle = title(font, "item.simpleradio_repeaters.handbook");
        var intro = lines(font, Component.translatable("handbook.simpleradio_repeaters.intro"));
        var tocTitle = title(font, "handbook.simpleradio_repeaters.contents");
        int covers = pageCount(intro, coverTitle), contents = pageCount(lines(font, contents(chapters, Map.of())), tocTitle);
        int contentsPage = covers + 1;
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < covers + contents; i++) pages.add(new Page(FormattedText.EMPTY, null));
        Map<String, Integer> destinations = new HashMap<>();
        for (var chapter : chapters) {
            destinations.put(chapter.id(), pages.size() + 1);
            var heading = title(font, chapter.titleKey());
            for (var entry : chapter.entries()) {
                if (entry.recipeId() != null) {
                    pages.add(new Page(page(heading, List.of(), link("handbook.simpleradio_repeaters.contents", contentsPage)),
                            new ResourceLocation(entry.recipeId())));
                } else pages.addAll(textPages(heading, lines(font, Component.translatable(entry.textKey())),
                        link("handbook.simpleradio_repeaters.contents", contentsPage)));
            }
        }
        var cover = textPages(coverTitle, intro, link("handbook.simpleradio_repeaters.contents", contentsPage));
        for (int i = 0; i < covers; i++) pages.set(i, cover.get(i));
        var toc = textPages(tocTitle, lines(font, contents(chapters, destinations)),
                link("handbook.simpleradio_repeaters.introduction", 1));
        for (int i = 0; i < contents; i++) pages.set(covers + i, toc.get(i));
        return new HandbookBook(pages);
    }
    private static MutableComponent contents(List<HandbookContents.Chapter> chapters, Map<String, Integer> destinations) {
        var text = Component.empty();
        for (var chapter : chapters) {
            if (!text.getSiblings().isEmpty()) text.append("\n");
            text.append(link(chapter.titleKey(), destinations.getOrDefault(chapter.id(), 1)));
        }
        return text;
    }
    private static List<FormattedText> title(Font font, String key) {
        return lines(font, Component.translatable(key).withStyle(ChatFormatting.BOLD));
    }
    private static int capacity(List<FormattedText> title) {
        int capacity = LINES - title.size() - 2;
        if (capacity < 1) throw new IllegalArgumentException("Handbook title is too tall");
        return capacity;
    }
    private static int pageCount(List<FormattedText> text, List<FormattedText> title) {
        return Math.max(1, (text.size() + capacity(title) - 1) / capacity(title));
    }
    private static List<Page> textPages(List<FormattedText> title, List<FormattedText> body, FormattedText footer) {
        List<Page> result = new ArrayList<>(); int capacity = capacity(title);
        for (int start = 0; start < Math.max(1, body.size()); start += capacity)
            result.add(new Page(page(title, body.subList(start, Math.min(body.size(), start + capacity)), footer), null));
        return result;
    }
    private static FormattedText page(List<FormattedText> title, List<FormattedText> body, FormattedText footer) {
        var text = new ArrayList<>(title); text.add(FormattedText.EMPTY); text.addAll(body);
        while (text.size() < LINES - 1) text.add(FormattedText.EMPTY);
        text.add(footer); return join(text);
    }
    private static List<FormattedText> lines(Font font, FormattedText text) { return font.getSplitter().splitLines(text, WIDTH, Style.EMPTY); }
    private static MutableComponent link(String translation, int page) {
        return Component.translatable(translation).withStyle(style -> style.withColor(ChatFormatting.DARK_BLUE)
                .withBold(false).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, Integer.toString(page))));
    }
    private static FormattedText join(List<FormattedText> lines) {
        var pieces = new ArrayList<FormattedText>();
        for (int i = 0; i < lines.size(); i++) { if (i > 0) pieces.add(FormattedText.of("\n")); pieces.add(lines.get(i)); }
        return FormattedText.composite(pieces);
    }
    @Nullable public ResourceLocation recipe(int page) { return page >= 0 && page < pages.size() ? pages.get(page).recipe() : null; }
    @Override public int getPageCount() { return pages.size(); }
    @Override public FormattedText getPageRaw(int page) { return page >= 0 && page < pages.size() ? pages.get(page).text() : FormattedText.EMPTY; }
}
