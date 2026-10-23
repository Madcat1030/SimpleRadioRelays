package dev.paul.simpleradiorepeaters.handbook;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;

/** Resource-defined chapters. Addons own their content; their code and IDs are not required here. */
public final class HandbookContents {
    public static final String DIRECTORY = "radio_handbook/chapters";
    public record Entry(String textKey, String recipeId) {
        public Entry {
            if ((textKey == null) == (recipeId == null)) throw new IllegalArgumentException("Entry requires text or recipe");
            if (textKey != null && textKey.isBlank()) throw new IllegalArgumentException("Empty text key");
            if (recipeId != null && !recipeId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid recipe ID");
        }
    }
    public record Chapter(String id, int order, String titleKey, List<Entry> entries) {
        public Chapter {
            if (id.isBlank() || titleKey.isBlank() || entries.isEmpty()) throw new IllegalArgumentException("Incomplete chapter");
            entries = List.copyOf(entries);
        }
    }
    private HandbookContents() {}
    public static Chapter read(String id, Reader reader) {
        var json = JsonParser.parseReader(reader).getAsJsonObject();
        List<Entry> entries = new ArrayList<>();
        for (JsonElement value : json.getAsJsonArray("entries")) {
            var entry = value.getAsJsonObject();
            entries.add(new Entry(entry.has("text") ? entry.get("text").getAsString() : null,
                    entry.has("recipe") ? entry.get("recipe").getAsString() : null));
        }
        return new Chapter(id, json.has("order") ? json.get("order").getAsInt() : 100,
                json.get("title").getAsString(), entries);
    }
    public static List<Chapter> sorted(Collection<Chapter> chapters) {
        return chapters.stream().sorted(Comparator.comparingInt(Chapter::order).thenComparing(Chapter::id)).toList();
    }
}
