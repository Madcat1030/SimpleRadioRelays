package dev.paul.simpleradiorepeaters.network;

/** Separates a settled validation from an installation that is still restoring after load. */
public final class AntennaVerification {
    public static final int SETTLE_TICKS = 20;
    public static final int RETRY_TICKS = 200;
    private long readySince = -1;
    private long missingSince = -1;

    public void reset() { readySince = -1; missingSince = -1; }

    public boolean canScan(long now, boolean dependenciesReady) {
        if (!dependenciesReady) { reset(); return false; }
        if (readySince < 0) readySince = now;
        return now - readySince >= SETTLE_TICKS;
    }

    public boolean accept(long now, boolean complete, int score) {
        if (!complete) { reset(); return false; }
        if (score > 0) { missingSince = -1; return true; }
        if (missingSince < 0) { missingSince = now; return false; }
        return now - missingSince >= RETRY_TICKS;
    }

    public long nextAttempt(long now) {
        if (readySince >= 0 && now - readySince < SETTLE_TICKS) return readySince + SETTLE_TICKS;
        return now + RETRY_TICKS;
    }
}
