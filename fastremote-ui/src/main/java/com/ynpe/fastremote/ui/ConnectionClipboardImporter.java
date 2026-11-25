// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;

/** Reads clipboard connection text and applies the parsed values to connection editors. */
final class ConnectionClipboardImporter {
    private ConnectionClipboardImporter() {}

    static ParsedConnection parseSystemClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (!clipboard.hasString() || clipboard.getString() == null || clipboard.getString().isBlank()) {
            throw new IllegalArgumentException(com.ynpe.fastremote.i18n.Texts.text("legacy.java.63cce312c4bc7160"));
        }
        return parse(clipboard.getString());
    }

    static ParsedConnection parse(String clipboardText) {
        Optional<QuickConnectTextParser.LabeledCredentials> credentials =
                QuickConnectTextParser.parseLabeledCredentialsOnly(clipboardText);
        if (credentials.isPresent()) {
            QuickConnectTextParser.LabeledCredentials value = credentials.orElseThrow();
            return ParsedConnection.credentialsOnly(value.username(), value.password());
        }
        return ParsedConnection.complete(QuickConnectTextParser.parse(clipboardText));
    }

    static void apply(
            ParsedConnection parsed,
            Optional<TextField> name,
            TextField host,
            TextField port,
            TextField username,
            ComboBox<AuthenticationType> authentication,
            Consumer<String> hostPassword,
            TextField privateKey,
            Consumer<String> privateKeyPassphrase) {
        Objects.requireNonNull(parsed, "parsed");
        Objects.requireNonNull(name, "name");
        parsed.host().ifPresent(value -> {
            name.ifPresent(field -> field.setText(value));
            host.setText(value);
            port.setText(Integer.toString(parsed.port().orElse(22)));
        });
        parsed.username().ifPresent(username::setText);
        privateKey.setText(parsed.privateKey().orElse(""));
        hostPassword.accept("");
        privateKeyPassphrase.accept("");
        boolean usesPrivateKey = parsed.privateKey().isPresent();
        authentication.setValue(usesPrivateKey ? AuthenticationType.PRIVATE_KEY : AuthenticationType.PASSWORD);
        parsed.password().ifPresent(password -> {
            if (usesPrivateKey) privateKeyPassphrase.accept(password);
            else hostPassword.accept(password);
        });
    }

    static void revealParsedSecret(
            ParsedConnection parsed,
            RevealablePasswordEditor hostPassword,
            RevealablePasswordEditor privateKeyPassphrase) {
        boolean hasSecret = parsed.password().isPresent();
        boolean usesPrivateKey = parsed.privateKey().isPresent();
        hostPassword.setRevealed(hasSecret && !usesPrivateKey);
        privateKeyPassphrase.setRevealed(hasSecret && usesPrivateKey);
    }

    record ParsedConnection(
            String protocol,
            Optional<String> host,
            Optional<String> username,
            Optional<Integer> port,
            Optional<String> password,
            Optional<String> privateKey) {
        ParsedConnection {
            Objects.requireNonNull(protocol, "protocol");
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(port, "port");
            Objects.requireNonNull(password, "password");
            Objects.requireNonNull(privateKey, "privateKey");
        }

        static ParsedConnection complete(QuickConnectCommandParser.ParsedCommand parsed) {
            return new ParsedConnection(parsed.protocol(), Optional.of(parsed.host()), parsed.username(),
                    parsed.port(), parsed.password(), parsed.privateKey());
        }

        static ParsedConnection credentialsOnly(String username, String password) {
            return new ParsedConnection("ssh", Optional.empty(), Optional.of(username), Optional.empty(),
                    Optional.of(password), Optional.empty());
        }
    }
}
