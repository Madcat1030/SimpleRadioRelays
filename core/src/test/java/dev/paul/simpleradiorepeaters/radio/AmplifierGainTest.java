package dev.paul.simpleradiorepeaters.radio;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AmplifierGainTest {
    @Test void tiersBoostTheAntennaWithoutRoundingOrCreatingOne() {
        assertEquals(40,RelayPower.antennaScore(32,1.25));
        assertEquals(48,RelayPower.antennaScore(32,1.5));
        assertEquals(56,RelayPower.antennaScore(32,1.75));
        assertEquals(5.25,RelayPower.antennaScore(3,1.75));
        assertEquals(0,RelayPower.antennaScore(0,1.75));
        assertEquals(32,RelayPower.antennaScore(32,1));
    }
}
