// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LegalResourcesTest {
    @Test
    void packagedAboutResourcesContainTheEditionLicenseAndRequiredNotices() {
        assertTrue(LegalResources.productVersion().matches("[1-9][0-9]*(\\.[0-9]+){0,2}"));
        assertTrue(!LegalResources.legalEntity().isBlank());
        assertTrue(LegalResources.copyrightYear().matches("[0-9]{4}"));
        boolean commercial = LegalResources.edition().equals("commercial");
        assertTrue(LegalResources.text("LICENSE.txt").contains(commercial
                ? "Proprietary Software License" : "Mozilla Public License"));
        String thirdPartyNotices = LegalResources.text("THIRD_PARTY_NOTICES.txt");
        String sourceOffer = LegalResources.text("SOURCE_OFFER.txt");
        assertTrue(thirdPartyNotices.contains("Pty4J 0.13.9"));
        assertTrue(thirdPartyNotices.contains("BellSoft Liberica JDK 21 Full"));
        assertTrue(thirdPartyNotices.contains("JavaFX 21.0.12.1"));
        assertFalse(thirdPartyNotices.contains("Microsoft Build of OpenJDK"));
        if (commercial) {
            assertTrue(sourceOffer.contains("three years"));
            assertTrue(sourceOffer.contains("BellSoft Liberica JDK 21 Full"));
            assertTrue(sourceOffer.contains("JavaFX 21.0.12.1"));
            assertTrue(LegalResources.text("EULA.md").contains("Commercial entitlement"));
        } else {
            assertTrue(sourceOffer.contains("https://github.com/feinir/FastRemote"));
            assertThrows(IllegalStateException.class, () -> LegalResources.text("EULA.md"));
        }
        assertFalse(sourceOffer.contains("Microsoft Build of OpenJDK"));
        assertTrue(LegalResources.text("PRIVACY.md").contains("AI providers and MCP servers"));
        assertTrue(LegalResources.text("TRADEMARKS.md").contains("affiliation, sponsorship"));
    }
}
