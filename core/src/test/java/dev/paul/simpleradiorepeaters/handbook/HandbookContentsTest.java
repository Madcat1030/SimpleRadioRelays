package dev.paul.simpleradiorepeaters.handbook;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class HandbookContentsTest {
    @Test void optionalChaptersAppearOnlyForTheirOwnInstalledAddon() {
        var core = HandbookContents.chapters(false, false);
        for (boolean foci : new boolean[]{false, true}) for (boolean extenders : new boolean[]{false, true}) {
            var chapters = HandbookContents.chapters(foci, extenders);
            assertEquals(core, chapters.subList(0, core.size()));
            assertEquals(foci, chapters.stream().anyMatch(c -> c.id().equals("foci")));
            assertEquals(extenders, chapters.stream().anyMatch(c -> c.id().equals("extenders")));
            assertEquals(chapters.size(), chapters.stream().map(HandbookContents.Chapter::id).distinct().count());
        }
        assertEquals(core, HandbookContents.chapters(false, false));
        assertThrows(UnsupportedOperationException.class, () -> core.clear());
    }
    @Test void everyAvailableChapterHasTranslatedReadableContent() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                "/assets/simpleradio_repeaters/lang/en_us.json"), StandardCharsets.UTF_8)) {
            var language = JsonParser.parseReader(reader).getAsJsonObject();
            for (var chapter : HandbookContents.chapters(true, true)) {
                assertFalse(language.get(chapter.titleKey()).getAsString().isBlank());
                assertFalse(language.get(chapter.bodyKey()).getAsString().isBlank());
            }
        }
    }
}
