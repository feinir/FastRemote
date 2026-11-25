// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.connection.ConnectionProfile;
import com.ynpe.fastremote.domain.connection.Host;
import com.ynpe.fastremote.domain.connection.Port;
import com.ynpe.fastremote.domain.connection.Username;
import com.ynpe.fastremote.domain.profile.ConnectionProfileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public final class FileConnectionProfileRepository implements ConnectionProfileRepository {
    private static final String SCHEMA_VERSION = "1";

    private final Path file;

    public FileConnectionProfileRepository(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public static FileConnectionProfileRepository inDefaultLocation() {
        return new FileConnectionProfileRepository(
                FastRemotePaths.applicationDataDirectory().resolve("last-connection.properties"));
    }

    @Override
    public synchronized Optional<ConnectionProfile> loadLastProfile() throws IOException {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        try {
            AuthenticationType authentication = AuthenticationType.valueOf(required(properties, "authentication"));
            Optional<Path> keyPath = authentication == AuthenticationType.PRIVATE_KEY
                    ? Optional.of(Path.of(required(properties, "privateKeyPath")))
                    : Optional.empty();
            return Optional.of(new ConnectionProfile(
                    new Host(required(properties, "host")),
                    new Port(Integer.parseInt(required(properties, "port"))),
                    new Username(required(properties, "username")),
                    authentication,
                    keyPath,
                    Boolean.parseBoolean(properties.getProperty("legacyAlgorithms", "false"))));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Stored connection profile is invalid", invalid);
        }
    }

    @Override
    public synchronized void saveLastProfile(ConnectionProfile profile) throws IOException {
        Objects.requireNonNull(profile, "profile");
        Properties properties = new Properties();
        properties.setProperty("schemaVersion", SCHEMA_VERSION);
        properties.setProperty("host", profile.host().value());
        properties.setProperty("port", Integer.toString(profile.port().value()));
        properties.setProperty("username", profile.username().value());
        properties.setProperty("authentication", profile.authenticationType().name());
        properties.setProperty("legacyAlgorithms", Boolean.toString(profile.legacyAlgorithmsEnabled()));
        profile.privateKeyPath().ifPresent(path -> properties.setProperty("privateKeyPath", path.toString()));

        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "last-connection-", ".tmp");
        boolean moved = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "FastRemote connection settings");
            }
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static String required(Properties properties, String name) throws IOException {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IOException("Stored connection profile is missing " + name);
        }
        return value;
    }
}
