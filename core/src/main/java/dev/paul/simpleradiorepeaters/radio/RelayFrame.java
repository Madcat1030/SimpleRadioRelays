package dev.paul.simpleradiorepeaters.radio;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One emission, shared by source copies. Never uses audio bytes as packet identity. */
public final class RelayFrame {
    public boolean managed;
    public final Set<String> emitters = new HashSet<>();
    public final Set<String> receivers = new HashSet<>();
    public boolean delivered(String channel, UUID receiver) { return receivers.add(channel + '/' + receiver); }
}
