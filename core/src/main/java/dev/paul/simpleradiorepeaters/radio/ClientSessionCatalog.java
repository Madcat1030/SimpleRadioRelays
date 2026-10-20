package dev.paul.simpleradiorepeaters.radio;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/** One connection's immutable catalog; delayed packets and disconnects cannot alter a newer session. */
public final class ClientSessionCatalog<T> {
    private record State<T>(Object connection, Set<T> items, boolean ready) {}
    private volatile State<T> state = new State<>(null, Set.of(), false);
    public synchronized void begin(Object connection) {
        state = new State<>(Objects.requireNonNull(connection), Set.of(), false);
    }
    public synchronized boolean update(Object connection, Collection<T> items) {
        if (connection == null || state.connection() != connection) return false;
        state = new State<>(connection, Set.copyOf(items), true); return true;
    }
    public synchronized void end(Object connection) {
        if (state.connection() == connection) state = new State<>(null, Set.of(), false);
    }
    public boolean ready() { return state.ready(); }
    public boolean contains(T item) { return state.items().contains(item); }
}
