package dev.paul.simpleradiorepeaters.handbook;

import java.util.ArrayList;
import java.util.List;

/** Chapter selection has no dependency on optional addons or client classes. */
public final class HandbookContents {
    public record Chapter(String id) {
        public String titleKey() { return "handbook.simpleradio_repeaters." + id + ".title"; }
        public String bodyKey() { return "handbook.simpleradio_repeaters." + id + ".body"; }
    }
    private static final List<Chapter> CORE = List.of(new Chapter("start"), new Chapter("cards"),
            new Chapter("signals"), new Chapter("catalysts"), new Chapter("amplifiers"),
            new Chapter("transponders"), new Chapter("troubleshooting"));
    private HandbookContents() {}
    public static List<Chapter> chapters(boolean foci, boolean extenders) {
        var chapters = new ArrayList<>(CORE);
        if (foci) chapters.add(new Chapter("foci"));
        if (extenders) chapters.add(new Chapter("extenders"));
        return List.copyOf(chapters);
    }
}
