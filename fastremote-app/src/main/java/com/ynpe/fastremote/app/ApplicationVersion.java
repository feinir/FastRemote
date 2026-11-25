// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class ApplicationVersion {
    private static final String RESOURCE = "/com/ynpe/fastremote/app/product.properties";

    private ApplicationVersion() { }

    static String current() {
        String version = productProperty("version");
        com.ynpe.fastremote.domain.update.SemanticVersion.parse(version);
        return version;
    }

    static String productProperty(String key) {
        Properties values = new Properties();
        try (InputStream input = ApplicationVersion.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing application version resource");
            values.load(input);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read application version", error);
        }
        String value = values.getProperty(key, "").strip();
        if (value.isEmpty()) throw new IllegalStateException("Missing application property: " + key);
        return value;
    }
}
