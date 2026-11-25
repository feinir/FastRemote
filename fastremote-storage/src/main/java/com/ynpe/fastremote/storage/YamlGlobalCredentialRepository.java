// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.domain.credential.GlobalCredential;
import com.ynpe.fastremote.domain.credential.GlobalCredentialRepository;
import com.ynpe.fastremote.domain.credential.CredentialScope;
import com.ynpe.fastremote.domain.credential.CredentialGroup;
import com.ynpe.fastremote.domain.credential.CredentialGroupId;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import com.ynpe.fastremote.domain.catalog.HostId;
import com.ynpe.fastremote.domain.vault.SecretId;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class YamlGlobalCredentialRepository implements GlobalCredentialRepository {
    private final YamlGlobalConfigStore store;

    public YamlGlobalCredentialRepository(YamlGlobalConfigStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public List<GlobalCredential> list() throws IOException {
        Map<String, Object> entries = YamlGlobalConfigStore.requireMap(store.read(), "credentials");
        List<GlobalCredential> result = new ArrayList<>();
        for (Map.Entry<String, Object> item : entries.entrySet()) {
            if (!(item.getValue() instanceof Map<?, ?> raw)) {
                throw new IOException("Global credential entry must be a mapping");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) raw;
            Object label = entry.get("label");
            Object username = entry.get("username");
            Object authentication = entry.get("authentication");
            if (!(label instanceof String labelText) || !(username instanceof String usernameText)
                    || !(authentication instanceof String authenticationText)) {
                throw new IOException("Global credential metadata is incomplete");
            }
            try {
                result.add(new GlobalCredential(
                        SecretId.parse(item.getKey()),
                        labelText,
                        usernameText,
                        AuthenticationType.valueOf(authenticationText),
                        scope(entry),
                        owner(entry), group(entry), integer(entry, "sortOrder", 0)));
            } catch (IllegalArgumentException invalidEntry) {
                throw new IOException("Global credential metadata is invalid", invalidEntry);
            }
        }
        result.sort(Comparator.comparingInt(GlobalCredential::sortOrder)
                .thenComparing(GlobalCredential::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    @Override
    public Optional<GlobalCredential> find(SecretId id) throws IOException {
        Objects.requireNonNull(id, "id");
        return list().stream().filter(credential -> credential.id().equals(id)).findFirst();
    }

    @Override
    public void save(GlobalCredential credential) throws IOException {
        Objects.requireNonNull(credential, "credential");
        Map<String, Object> currentEntries = YamlGlobalConfigStore.requireMap(store.read(), "credentials");
        if (currentEntries.entrySet().stream().filter(item -> !item.getKey().equals(credential.id().toString()))
                .map(Map.Entry::getValue).filter(Map.class::isInstance).map(Map.class::cast)
                .map(value -> value.get("label")).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(label -> label.equalsIgnoreCase(credential.label()))) {
            throw new IOException("Credential name already exists: " + credential.label());
        }
        if (credential.groupId().isPresent() && listGroups().stream()
                .noneMatch(group -> group.id().equals(credential.groupId().orElseThrow()))) {
            throw new IOException("Credential group does not exist");
        }
        try {
            store.update(document -> {
                try {
                    Map<String, Object> entries = YamlGlobalConfigStore.requireMap(document, "credentials");
                    Object value = entries.get(credential.id().toString());
                    if (!(value instanceof Map<?, ?> raw)) {
                        throw new CredentialMutationException(new IOException(
                                "Cannot attach metadata to a missing encrypted credential"));
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entry = (Map<String, Object>) raw;
                    entry.put("label", credential.label());
                    entry.put("username", credential.username());
                    entry.put("authentication", credential.authenticationType().name());
                    entry.put("scope", credential.scope().name());
                    entry.put("ownerHostId", credential.ownerHostId().map(HostId::toString).orElse(null));
                    entry.put("groupId", credential.groupId().map(CredentialGroupId::toString).orElse(null));
                    entry.put("sortOrder", credential.sortOrder());
                    return null;
                } catch (IOException failure) {
                    throw new CredentialMutationException(failure);
                }
            });
        } catch (CredentialMutationException failure) {
            throw (IOException) failure.getCause();
        }
    }

    @Override
    public List<CredentialGroup> listGroups() throws IOException {
        Map<String, Object> entries = YamlGlobalConfigStore.requireMap(store.read(), "credentialGroups");
        List<CredentialGroup> result = new ArrayList<>();
        for (Map.Entry<String, Object> item : entries.entrySet()) {
            if (!(item.getValue() instanceof Map<?, ?> raw)) throw new IOException("Credential group entry must be a mapping");
            @SuppressWarnings("unchecked") Map<String, Object> entry = (Map<String, Object>) raw;
            Object name = entry.get("name");
            Object color = entry.get("color");
            if (!(name instanceof String text) || !(color instanceof String colorText)) {
                throw new IOException("Credential group metadata is incomplete");
            }
            try {
                result.add(new CredentialGroup(CredentialGroupId.parse(item.getKey()), text,
                        integer(entry, "sortOrder", 0), GroupColor.valueOf(colorText)));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Credential group metadata is invalid", invalid);
            }
        }
        result.sort(Comparator.comparingInt(CredentialGroup::sortOrder));
        return List.copyOf(result);
    }

    @Override
    public void saveGroup(CredentialGroup group) throws IOException {
        Objects.requireNonNull(group, "group");
        if (listGroups().stream().anyMatch(existing -> !existing.id().equals(group.id())
                && existing.name().equalsIgnoreCase(group.name()))) throw new IOException("Credential group name already exists");
        try {
            store.update(document -> {
                try {
                    Map<String, Object> groups = YamlGlobalConfigStore.requireMap(document, "credentialGroups");
                    Map<String, Object> value = new java.util.LinkedHashMap<>();
                    value.put("name", group.name()); value.put("sortOrder", group.sortOrder());
                    value.put("color", group.color().name()); groups.put(group.id().toString(), value); return null;
                } catch (IOException failure) { throw new CredentialMutationException(failure); }
            });
        } catch (CredentialMutationException failure) { throw (IOException) failure.getCause(); }
    }

    @Override
    public void deleteGroup(CredentialGroupId id) throws IOException {
        Objects.requireNonNull(id, "id");
        if (list().stream().anyMatch(value -> value.groupId().filter(id::equals).isPresent())) {
            throw new IOException("Credential group is not empty");
        }
        try {
            store.update(document -> {
                try { YamlGlobalConfigStore.requireMap(document, "credentialGroups").remove(id.toString()); return null; }
                catch (IOException failure) { throw new CredentialMutationException(failure); }
            });
        } catch (CredentialMutationException failure) { throw (IOException) failure.getCause(); }
    }

    private static CredentialScope scope(Map<String, Object> entry) throws IOException {
        Object value = entry.get("scope");
        if (!(value instanceof String text)) throw new IOException("Global credential scope is missing");
        try {
            return CredentialScope.valueOf(text);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Global credential scope is invalid", invalid);
        }
    }

    private static Optional<HostId> owner(Map<String, Object> entry) throws IOException {
        Object value = entry.get("ownerHostId");
        if (value == null) return Optional.empty();
        if (value instanceof String text && !text.isBlank()) return Optional.of(HostId.parse(text));
        throw new IOException("Global credential ownerHostId is invalid");
    }

    private static Optional<CredentialGroupId> group(Map<String, Object> entry) throws IOException {
        Object value = entry.get("groupId");
        if (value == null) return Optional.empty();
        if (value instanceof String text && !text.isBlank()) return Optional.of(CredentialGroupId.parse(text));
        throw new IOException("Global credential groupId is invalid");
    }

    private static int integer(Map<String, Object> entry, String key, int fallback) throws IOException {
        Object value = entry.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number && number.intValue() >= 0) return number.intValue();
        throw new IOException("Global credential " + key + " is invalid");
    }

    private static final class CredentialMutationException extends RuntimeException {
        private CredentialMutationException(IOException cause) {
            super(cause);
        }
    }
}
