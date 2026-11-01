package com.dipak.hearclear;

/** In-memory monotonic lease: never persist a wall-clock based unlock. */
public final class AccessLease {
    private long until;
    public synchronized void grant(long requestStartedMs, long nowMs, int seconds) {
        if (seconds < 0 || seconds > 75 || requestStartedMs > nowMs) { until = 0; return; }
        until = requestStartedMs + seconds * 1000L;
    }
    public synchronized boolean valid(long elapsedMs) { return until > elapsedMs; }
    public synchronized void clear() { until = 0; }
}
