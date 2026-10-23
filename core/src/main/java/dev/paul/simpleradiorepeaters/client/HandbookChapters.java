package dev.paul.simpleradiorepeaters.client;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.handbook.HandbookContents;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads current resources on opening, including addon chapters and resource-pack overrides. */
public final class HandbookChapters {
    private HandbookChapters() {}
    public static List<HandbookContents.Chapter> load(ResourceManager resources) {
        var chapters = new ArrayList<HandbookContents.Chapter>();
        resources.listResources(HandbookContents.DIRECTORY, id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) { chapters.add(HandbookContents.read(id.toString(), reader)); }
            catch (IOException | RuntimeException error) { SimpleRadioRepeaters.LOGGER.warn("Skipping invalid handbook chapter {}", id, error); }
        });
        return HandbookContents.sorted(chapters);
    }
}
