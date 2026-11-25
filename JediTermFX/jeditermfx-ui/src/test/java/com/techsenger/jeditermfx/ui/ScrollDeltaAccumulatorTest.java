package com.techsenger.jeditermfx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScrollDeltaAccumulatorTest {
    private static final long FRAME = 16_000_000L;

    @Test
    void smallTrackpadDeltasAddUpInsteadOfBeingDropped() {
        ScrollDeltaAccumulator accumulator = new ScrollDeltaAccumulator();
        int total = 0;
        for (int i = 0; i < 20; i++) {
            total += accumulator.accumulate(8.0, i * FRAME);
        }
        assertEquals(-4, total);
    }

    @Test
    void eachWheelNotchScrollsOneUnitAndDownIsPositive() {
        ScrollDeltaAccumulator accumulator = new ScrollDeltaAccumulator();
        assertEquals(1, accumulator.accumulate(-40.0, 0));
        assertEquals(3, accumulator.accumulate(-120.0, FRAME));
    }

    @Test
    void directionChangeDiscardsTheOppositeRemainder() {
        ScrollDeltaAccumulator accumulator = new ScrollDeltaAccumulator();
        assertEquals(0, accumulator.accumulate(30.0, 0));
        assertEquals(0, accumulator.accumulate(-30.0, FRAME));
        assertEquals(1, accumulator.accumulate(-10.0, 2 * FRAME));
    }

    @Test
    void pauseStartsANewGesture() {
        ScrollDeltaAccumulator accumulator = new ScrollDeltaAccumulator();
        assertEquals(0, accumulator.accumulate(30.0, 0));
        assertEquals(0, accumulator.accumulate(30.0, 1_000_000_000L));
        assertEquals(-1, accumulator.accumulate(10.0, 1_000_000_000L + FRAME));
    }
}
