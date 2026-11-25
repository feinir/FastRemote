// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

/** Loads the single legal-document set shared by the About dialog and native distribution. */
final class LegalResources {
    private static final String ROOT = "/com/ynpe/fastremote/ui/legal/";
    private static final Properties PRODUCT = loadProductProperties();

    private LegalResources() {}

    static String text(String name) {
        String resource = ROOT + Objects.requireNonNull(name, "name");
        try (InputStream input = LegalResources.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing legal resource: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read legal resource: " + resource, failure);
        }
    }

    static String productVersion() {
        return requiredProductProperty("version");
    }

    static String legalEntity() {
        return requiredProductProperty("legalEntity");
    }

    static String copyrightYear() {
        return requiredProductProperty("copyrightYear");
    }

    static String edition() {
        return requiredProductProperty("edition");
    }

    private static String requiredProductProperty(String name) {
        String value = PRODUCT.getProperty(name, "").strip();
        if (value.isEmpty()) throw new IllegalStateException("Product property is missing: " + name);
        return value;
    }

    private static Properties loadProductProperties() {
        Properties properties = new Properties();
        String resource = ROOT + "product.properties";
        try (InputStream input = LegalResources.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing legal resource: " + resource);
            properties.load(input);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read legal resource: " + resource, failure);
        }
        return properties;
    }
}
