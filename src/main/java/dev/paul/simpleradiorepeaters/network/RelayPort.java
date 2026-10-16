package dev.paul.simpleradiorepeaters.network;

import dev.paul.simpleradiorepeaters.radio.RadioSpace;
import java.util.List;

/** One directed channel conversion at one physical Relay. Ordinary Bank ports keep their frequency. */
public record RelayPort(TowerRecord tower, ChannelKey input, ChannelKey output, List<RadioSpace.Site> sites) {
    public RelayPort { sites = List.copyOf(sites); }
}
