package dev.paul.simpleradiorepeaters.handbook;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HandbookContentsTest {
    private HandbookContents.Chapter read(String id, String json) {
        return HandbookContents.read(id, new StringReader(json));
    }
    @Test void onlySuppliedChaptersAppearAndOrderingIsStable() {
        var core = read("core:relay", "{\"order\":20,\"title\":\"core.title\",\"entries\":[{\"text\":\"core.body\"}]}");
        var addon = read("addon:dish", "{\"order\":90,\"title\":\"addon.title\",\"entries\":[{\"recipe\":\"addon:dish\"},{\"text\":\"addon.body\"}]}");
        var peer = read("aaa:other", "{\"order\":90,\"title\":\"other.title\",\"entries\":[{\"text\":\"other.body\"}]}");
        assertEquals(List.of(core), HandbookContents.sorted(List.of(core)));
        assertEquals(List.of(core, peer, addon), HandbookContents.sorted(List.of(addon, core, peer)));
        assertThrows(UnsupportedOperationException.class, () -> core.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> HandbookContents.sorted(List.of(core)).clear());
    }
    @Test void malformedEntriesCannotPretendToBeBothTextAndRecipes() {
        for (String entry : List.of("{}", "{\"text\":\"\"}", "{\"recipe\":\"missing_namespace\"}",
                "{\"text\":\"body\",\"recipe\":\"mod:item\"}"))
            assertThrows(RuntimeException.class, () -> read("mod:bad", "{\"title\":\"title\",\"entries\":[" + entry + "]}"));
        assertThrows(RuntimeException.class, () -> read("mod:empty", "{\"title\":\"title\",\"entries\":[]}"));
    }
    @Test void coreResourcesHaveCompleteTextAndOnlyCoreRecipes() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                "/assets/simpleradio_repeaters/lang/en_us.json"), StandardCharsets.UTF_8)) {
            var language = JsonParser.parseReader(reader).getAsJsonObject();
            for (String id : List.of("start", "relay", "bank", "transponder", "monitor", "card", "amplifiers", "terrain", "troubleshooting")) {
                var resource = "/assets/simpleradio_repeaters/radio_handbook/chapters/" + id + ".json";
                try (var chapterReader = new InputStreamReader(getClass().getResourceAsStream(resource), StandardCharsets.UTF_8)) {
                    var chapter = HandbookContents.read("simpleradio_repeaters:" + id, chapterReader);
                    assertFalse(language.get(chapter.titleKey()).getAsString().isBlank());
                    for (var entry : chapter.entries()) {
                        if (entry.textKey() != null) assertFalse(language.get(entry.textKey()).getAsString().isBlank());
                        else assertNotNull(getClass().getResource("/data/simpleradio_repeaters/recipes/" + entry.recipeId().split(":")[1] + ".json"));
                    }
                }
            }
            assertFalse(language.keySet().stream().anyMatch(k -> k.contains(".foci.") || k.contains(".extenders.") || k.contains(".directional.")));
        }
    }
}
