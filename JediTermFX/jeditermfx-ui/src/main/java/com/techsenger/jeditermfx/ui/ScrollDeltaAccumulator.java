package com.techsenger.jeditermfx.ui;

/**
 * Converts pixel scroll deltas into whole terminal scroll units.
 *
 * <p>Trackpads deliver many small deltas per gesture. Rounding each event on its own drops most of them and
 * makes scrolling stall; forwarding each event as a wheel code floods the remote side. The accumulator keeps
 * the fractional remainder so every pixel counts exactly once.</p>
 */
final class ScrollDeltaAccumulator {

    /** Pixels per scroll unit; a typical mouse wheel notch. */
    static final double PIXELS_PER_UNIT = 40.0;

    /** A pause longer than this starts a new gesture and discards the stale remainder. */
    private static final long GESTURE_GAP_NANOS = 300_000_000L;

    private double remainder;

    private long lastEventNanos;

    private boolean hasLastEvent;

    /**
     * Adds a JavaFX {@code deltaY} and returns the units to scroll: negative scrolls up (towards history),
     * positive scrolls down.
     */
    int accumulate(double deltaY, long nowNanos) {
        boolean gap = !hasLastEvent || nowNanos - lastEventNanos > GESTURE_GAP_NANOS;
        if (gap || Math.signum(deltaY) != Math.signum(remainder)) {
            remainder = 0.0;
        }
        hasLastEvent = true;
        lastEventNanos = nowNanos;
        remainder += deltaY;
        int units = (int) (remainder / PIXELS_PER_UNIT);
        remainder -= units * PIXELS_PER_UNIT;
        return -units;
    }
}
