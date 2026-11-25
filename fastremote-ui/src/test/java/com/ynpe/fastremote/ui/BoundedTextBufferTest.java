// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoundedTextBufferTest {
    @Test
    void evictsHalfOfTheHeadAtALineBoundaryWhenCapacityIsExceeded() {
        BoundedTextBuffer buffer = new BoundedTextBuffer(20, 0.5);
        buffer.append("1111\n2222\n3333\n4444\n5555\n");

        assertEquals("4444\n5555\n", buffer.snapshot());
        assertTrue(buffer.length() <= 20);
    }

    @Test
    void remainsBoundedWhenOneAppendIsMuchLargerThanCapacity() {
        BoundedTextBuffer buffer = new BoundedTextBuffer(16, 0.5);
        buffer.append("old\n" + "x".repeat(40));

        assertTrue(buffer.length() <= 16);
        assertEquals("x".repeat(16), buffer.snapshot());
    }
}
