// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

final class OfflineLicenseControllerTest {
    @Test
    void keepsAHighResolutionQrSourceInACompactDialog() {
        assertEquals(1040, OfflineLicenseController.QR_RENDER_SIZE);
        assertEquals(420, OfflineLicenseController.QR_DISPLAY_SIZE);
        assertEquals(420.0, OfflineLicenseController.QR_DIALOG_WIDTH);
        assertEquals(500.0, OfflineLicenseController.QR_DIALOG_HEIGHT);
        assertFalse(OfflineLicenseController.QR_DIALOG_RESIZABLE);
    }

    @Test
    void validatesTheSameLicenseChecksumAsTheServer() {
        String body = "0123456789ABCDEFGHJKMNPQRS";
        CRC32 crc = new CRC32();
        crc.update(body.getBytes(StandardCharsets.US_ASCII));
        long checksum = crc.getValue() % 1024;
        String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        String raw = body + alphabet.charAt((int) ((checksum >> 5) & 31)) + alphabet.charAt((int) (checksum & 31));
        String formatted = "FRM-" + raw.substring(0, 5) + "-" + raw.substring(5, 10) + "-" + raw.substring(10, 15)
                + "-" + raw.substring(15, 20) + "-" + raw.substring(20, 25) + "-" + raw.substring(25);

        assertTrue(OfflineLicenseController.isValidLicenseCode(formatted));
        assertTrue(OfflineLicenseController.isValidLicenseCode(formatted.toLowerCase()));
        assertFalse(OfflineLicenseController.isValidLicenseCode(formatted.substring(0, formatted.length() - 1) + "0"));
        assertFalse(OfflineLicenseController.isValidLicenseCode("FRM-AAAAA-AAAAA-AAAAA-AAAAA-AAAAA-AAA"));
    }
}
