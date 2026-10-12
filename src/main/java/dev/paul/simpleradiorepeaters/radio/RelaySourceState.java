package dev.paul.simpleradiorepeaters.radio;

public interface RelaySourceState {
    RelayFrame repeaters$getFrame();
    void repeaters$setFrame(RelayFrame frame);
    double repeaters$getInheritedSeverity();
    void repeaters$setInheritedSeverity(double severity);
}
