package dev.paul.simpleradiorepeaters.radio;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClientSessionCatalogTest {
    @Test void pendingAndAuthoritativeEmptyAreDifferent() {
        var catalog = new ClientSessionCatalog<String>(); Object session = new Object();
        assertFalse(catalog.ready()); catalog.begin(session); assertFalse(catalog.ready());
        assertTrue(catalog.update(session, List.of())); assertTrue(catalog.ready());
        assertFalse(catalog.contains("amethyst"));
    }
    @Test void updatesUseImmutableCopiesAndReplaceTheWholeCatalog() {
        var catalog = new ClientSessionCatalog<String>(); Object session = new Object(); catalog.begin(session);
        var supplied = new ArrayList<>(List.of("amethyst")); catalog.update(session, supplied); supplied.clear();
        assertTrue(catalog.contains("amethyst"));
        catalog.update(session, List.of("blackstone"));
        assertTrue(catalog.ready()); assertFalse(catalog.contains("amethyst")); assertTrue(catalog.contains("blackstone"));
    }
    @Test void disconnectedPacketsCannotRestoreThePreviousCatalog() {
        var catalog = new ClientSessionCatalog<String>(); Object session = new Object(); catalog.begin(session);
        catalog.update(session, List.of("amethyst")); catalog.end(session);
        assertFalse(catalog.ready()); assertFalse(catalog.contains("amethyst"));
        assertFalse(catalog.update(session, List.of("blackstone"))); assertFalse(catalog.ready());
        assertFalse(catalog.update(null, List.of("blackstone")));
    }
    @Test void stalePacketsAndDisconnectsCannotAlterTheNewConnection() {
        var catalog = new ClientSessionCatalog<String>(); Object old = new Object(), current = new Object();
        catalog.begin(old); catalog.update(old, List.of("amethyst")); catalog.begin(current);
        assertFalse(catalog.contains("amethyst")); assertFalse(catalog.ready());
        assertFalse(catalog.update(old, List.of("blackstone"))); catalog.end(old); assertFalse(catalog.ready());
        catalog.update(current, List.of("custom")); catalog.end(old);
        assertTrue(catalog.ready()); assertTrue(catalog.contains("custom"));
        assertFalse(catalog.update(old, List.of("blackstone"))); assertFalse(catalog.contains("blackstone"));
    }
    @Test void connectionIdentityIsUsedRatherThanEquality() {
        var catalog = new ClientSessionCatalog<String>(); Object first = new String("same"), other = new String("same");
        catalog.begin(first); assertFalse(catalog.update(other, List.of("wrong")));
        catalog.end(other); assertTrue(catalog.update(first, List.of("right")));
    }
}
