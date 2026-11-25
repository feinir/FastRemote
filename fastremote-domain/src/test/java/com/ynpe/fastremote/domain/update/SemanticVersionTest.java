// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SemanticVersionTest {
    @Test void comparesStableAndPrereleaseVersions() {
        assertTrue(SemanticVersion.parse("1.1.0").compareTo(SemanticVersion.parse("1.0.9")) > 0);
        assertTrue(SemanticVersion.parse("1.1.0").compareTo(SemanticVersion.parse("1.1.0-beta.2")) > 0);
        assertTrue(SemanticVersion.parse("1.1.0-beta.10").compareTo(SemanticVersion.parse("1.1.0-beta.2")) > 0);
        assertEquals(0, SemanticVersion.parse("1.2.3+build.7").compareTo(SemanticVersion.parse("1.2.3")));
    }

    @Test void rejectsLooseOrAmbiguousVersions() {
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.2"));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("01.2.3"));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.2.3-beta.01"));
    }
}
