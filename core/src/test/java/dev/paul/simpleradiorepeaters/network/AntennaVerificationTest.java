package dev.paul.simpleradiorepeaters.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AntennaVerificationTest {
    @Test void newlyLoadedConnectionsGetTimeToRestore() {
        AntennaVerification verification = new AntennaVerification();
        assertFalse(verification.canScan(1000, true));
        assertEquals(1020, verification.nextAttempt(1000));
        assertFalse(verification.canScan(1019, true));
        assertTrue(verification.canScan(1020, true));
        assertTrue(verification.accept(1020, true, 114));
    }

    @Test void oneEmptyScanCannotDiscardThePreviousAntenna() {
        AntennaVerification verification = new AntennaVerification();
        verification.canScan(0, true);
        assertTrue(verification.canScan(20, true));
        assertFalse(verification.accept(20, true, 0));
        assertEquals(220, verification.nextAttempt(20));
        assertTrue(verification.accept(220, true, 114));
    }

    @Test void confirmedRemovalEventuallyStopsRelaying() {
        AntennaVerification verification = new AntennaVerification();
        verification.canScan(0, true);
        assertTrue(verification.canScan(20, true));
        assertFalse(verification.accept(20, true, 0));
        assertFalse(verification.accept(219, true, 0));
        assertTrue(verification.accept(220, true, 0));
    }

    @Test void unavailableAntennaChunksRestartConfirmation() {
        AntennaVerification verification = new AntennaVerification();
        verification.canScan(0, true);
        verification.canScan(20, true);
        assertFalse(verification.accept(20, true, 0));
        assertFalse(verification.canScan(220, false));
        assertEquals(420, verification.nextAttempt(220));
        assertFalse(verification.canScan(420, true));
        assertTrue(verification.canScan(440, true));
        assertFalse(verification.accept(440, true, 0));
        assertTrue(verification.accept(640, true, 0));
    }

    @Test void unresolvedWireEndpointsAreStillPending() {
        AntennaVerification verification = new AntennaVerification();
        verification.canScan(0, true);
        verification.canScan(20, true);
        assertFalse(verification.accept(20, false, 0));
        assertEquals(220, verification.nextAttempt(20));
        assertFalse(verification.canScan(220, true));
        assertTrue(verification.canScan(240, true));
        assertTrue(verification.accept(240, true, 114));
    }

    @Test void dirtyNotificationBeginsNewVerification() {
        AntennaVerification verification = new AntennaVerification();
        verification.canScan(0, true);
        verification.canScan(20, true);
        verification.accept(20, true, 114);
        verification.reset();
        assertFalse(verification.canScan(100, true));
        assertTrue(verification.canScan(120, true));
        assertFalse(verification.accept(120, true, 0));
    }
}
