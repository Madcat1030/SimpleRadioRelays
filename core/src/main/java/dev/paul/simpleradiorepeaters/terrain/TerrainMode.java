package dev.paul.simpleradiorepeaters.terrain;

import com.google.gson.annotations.SerializedName;

/** One switch controls the complete environmental model; OFF restores pre-terrain propagation. */
public enum TerrainMode {
    @SerializedName(value = "off", alternate = {"OFF"}) OFF("off"),
    @SerializedName(value = "relay_mapping", alternate = {"RELAY_MAPPING", "Relay Mapping"}) RELAY_MAPPING("relay_mapping"),
    @SerializedName(value = "full", alternate = {"FULL"}) FULL("full");

    private final String id;
    TerrainMode(String id) { this.id = id; }
    public String id() { return id; }
    public boolean relayLinks() { return this != OFF; }
    public boolean pointToPoint() { return this == FULL; }
}
