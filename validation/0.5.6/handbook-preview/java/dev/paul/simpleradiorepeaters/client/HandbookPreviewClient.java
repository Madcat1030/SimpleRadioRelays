package dev.paul.simpleradiorepeaters.client;

import com.google.gson.JsonParser;
import dev.paul.simpleradiorepeaters.handbook.HandbookContents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** QA only: native font, book links and unmodified framebuffer captures. Never part of a runtime jar. */
public final class HandbookPreviewClient implements ClientModInitializer {
    private record Capture(String name, HandbookBook book, int page) {}
    private final List<Capture> captures = new ArrayList<>();
    private int tick, step; private boolean started, requested; private PreviewScreen screen;
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static String normalized(String text) { return text.replaceAll("\\s+", " ").trim(); }
    private static final class PreviewScreen extends HandbookScreen {
        ResourceLocation resolved;
        PreviewScreen(HandbookBook book) { super(book); }
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Keep the capture pointer outside the page; native ingredient tooltips remain enabled in the real reader.
            super.render(graphics, -1000, -1000, partialTick);
        }
        @Override protected Optional<Illustration> resolve(ResourceLocation id) {
            resolved = id;
            String resource = "/data/" + id.getNamespace() + "/recipes/" + id.getPath() + ".json";
            try (var input = HandbookPreviewClient.class.getResourceAsStream(resource)) {
                check(input != null, "Recipe resource missing: " + id);
                var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                var serializer = BuiltInRegistries.RECIPE_SERIALIZER.get(new ResourceLocation(json.get("type").getAsString()));
                return illustrate(serializer.fromJson(id, json), RegistryAccess.EMPTY);
            } catch (IOException error) { throw new IllegalStateException(error); }
        }
    }
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            if (!started) {
                started = true;
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1000, 800);
                client.options.guiScale().set(3); client.resizeDisplay();
                var discovered = HandbookChapters.load(client.getResourceManager());
                check(discovered.size() == 10 && discovered.stream().anyMatch(c -> c.id().startsWith("handbook_checks:")),
                        "An independent addon must contribute a chapter without core changes");
                var production = discovered.stream().filter(c -> !c.id().startsWith("handbook_checks:")).toList();
                check(production.size() == 9, "Core and both addons must contribute exactly nine chapters");
                for (boolean foci : new boolean[]{false, true}) for (boolean directional : new boolean[]{false, true}) {
                    var chapters = production.stream().filter(c -> foci || !c.id().startsWith("simpleradio_repeaters_foci:"))
                            .filter(c -> directional || !c.id().startsWith("simpleradio_directional:")).toList();
                    var book = HandbookBook.create(client.font, chapters);
                    for (int page = 0; page < book.getPageCount(); page++) {
                        var raw = book.getPageRaw(page);
                        check(client.font.split(raw, 114).size() <= 14, "Page overflow at " + (page + 1));
                        check(!raw.getString().contains("handbook."), "Missing translation at " + (page + 1));
                        raw.visit((style, text) -> {
                            var link = style.getClickEvent();
                            if (link != null && link.getAction() == ClickEvent.Action.CHANGE_PAGE) {
                                int target = Integer.parseInt(link.getValue());
                                check(target >= 1 && target <= book.getPageCount(), "Broken page link");
                            }
                            return Optional.empty();
                        }, Style.EMPTY);
                        if (book.recipe(page) != null) check(new PreviewScreen(book).resolve(book.recipe(page)).isPresent(), "Recipe must render");
                    }
                    System.out.println("HANDBOOK_QA foci=" + foci + " directional=" + directional + " chapters=" + chapters.size()
                            + " pages=" + book.getPageCount() + " fit=true links=true recipes=true");
                }
                var full = HandbookBook.create(client.font, production);
                check(production.stream().noneMatch(c -> c.entries().stream().anyMatch(e -> e.textKey() != null
                        && e.textKey().startsWith("handbook.simpleradio_repeaters.foci"))), "Addon text must live under its own keys");
                HandbookBook.open(); check(client.screen instanceof HandbookScreen, "Runtime book opener must use the recipe reader");
                captures.add(new Capture("Simple-Radio-Relays-Cover.png", full, 0));
                captures.add(new Capture("Simple-Radio-Relays-Contents.png", full, containing(full, "Contents Your First Relay")));
                captures.add(new Capture("Simple-Radio-Relays-Relay-Recipe.png", full, recipe(full, "simpleradio_repeaters:relay")));
                captures.add(new Capture("Simple-Radio-Relays-Transponder-Recipe.png", full, recipe(full, "simpleradio_repeaters:transponder")));
                captures.add(new Capture("Simple-Radio-Relays-Multicast.png", full, containing(full, "asterisk")));
                captures.add(new Capture("Simple-Radio-Relays-Nether-Focus.png", full, recipe(full, "simpleradio_repeaters:nether_focus")));
                captures.add(new Capture("Simple-Radio-Relays-Wide-Angle.png", full, recipe(full, "simpleradio_directional:wide_angle_receiver")));
                captures.add(new Capture("Simple-Radio-Relays-Wide-Profile.png", full, containing(full, "22%")));
                show(client); return;
            }
            if (step >= captures.size()) {
                check(captures.stream().allMatch(c -> new File(client.gameDirectory, "screenshots/" + c.name()).isFile()), "Capture missing");
                System.out.println("HANDBOOK_QA native book captures complete"); client.stop(); return;
            }
            tick++;
            if (tick == 20 && !requested) {
                requested = true; var capture = captures.get(step); ResourceLocation expected = capture.book().recipe(capture.page());
                check(Objects.equals(expected, screen.resolved), "Visible recipe must follow native page navigation");
                Screenshot.grab(client.gameDirectory, capture.name(), client.getMainRenderTarget(),
                        message -> System.out.println("HANDBOOK_CAPTURE " + capture.name() + " " + message.getString()));
            }
            if (tick >= 35) { step++; if (step < captures.size()) show(client); }
        });
    }
    private void show(Minecraft client) {
        var capture = captures.get(step); screen = new PreviewScreen(capture.book()); client.setScreen(screen);
        GLFW.glfwSetCursorPos(client.getWindow().getWindow(), 10, 10);
        screen.setPage(capture.page() == 0 ? 1 : 0);
        check(screen.handleComponentClicked(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE,
                Integer.toString(capture.page() + 1)))), "Native contents links must navigate");
        tick = 0; requested = false;
    }
    private static int containing(HandbookBook book, String needle) {
        for (int page = 0; page < book.getPageCount(); page++)
            if (normalized(book.getPageRaw(page).getString()).contains(needle)) return page;
        throw new IllegalStateException("Text missing: " + needle);
    }
    private static int recipe(HandbookBook book, String id) {
        for (int page = 0; page < book.getPageCount(); page++)
            if (new ResourceLocation(id).equals(book.recipe(page))) return page;
        throw new IllegalStateException("Recipe missing: " + id);
    }
}
