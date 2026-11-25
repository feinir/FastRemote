// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.app.workflow;

import com.ynpe.fastremote.domain.ai.AiSettingsRepository;
import com.ynpe.fastremote.domain.automation.AutomationCapability;
import com.ynpe.fastremote.domain.automation.AutomationLocalEntry;
import com.ynpe.fastremote.domain.automation.AutomationLocalListing;
import com.ynpe.fastremote.domain.automation.AutomationSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class AutomationLocalFiles {
    private static final System.Logger LOGGER = System.getLogger(AutomationLocalFiles.class.getName());
    private final AutomationExecution execution;
    private final AiSettingsRepository aiSettings;

    AutomationLocalFiles(AutomationExecution execution, AiSettingsRepository aiSettings) {
        this.execution = execution;
        this.aiSettings = aiSettings;
    }

    private static final int MAX_TEXT_BYTES = 256 * 1024;
    private static final int MAX_DELETE_ENTRIES = 10_000;
    static final int MAX_LOCAL_ENTRIES = 512;
    public CompletionStage<AutomationLocalListing> listLocal(
            AutomationSource source, Optional<Path> directory) {
        Optional<Path> requested = Objects.requireNonNull(directory, "directory");
        return execution.submit(() -> {
            if (requested.isEmpty()) {
                List<Path> roots = aiSettings.load().allowedLocalRoots();
                execution.requirePermission(source, AutomationCapability.READ_LOCAL, null,
                        com.ynpe.fastremote.i18n.Texts.text("legacy.java.02a93ae04383c683"), com.ynpe.fastremote.i18n.Texts.text("legacy.java.90b13345c3ac5172") + roots.size());
                List<AutomationLocalEntry> entries = existingRoots(roots).stream()
                        .map(root -> localEntry(root.path(), true)).toList();
                return new AutomationLocalListing("", entries, false);
            }
            Path allowed = requireAllowedLocal(requested.orElseThrow(), true);
            if (!Files.isDirectory(allowed)) throw new IllegalArgumentException("Local path is not a directory");
            execution.requirePermission(source, AutomationCapability.READ_LOCAL, null,
                    com.ynpe.fastremote.i18n.Texts.text("legacy.java.970dd1e654d21534"), allowed.toString());
            List<Path> paths;
            try (var stream = Files.list(allowed)) {
                paths = stream.sorted((left, right) -> left.getFileName().toString()
                        .compareToIgnoreCase(right.getFileName().toString()))
                        .limit(MAX_LOCAL_ENTRIES + 1L).toList();
            }
            boolean truncated = paths.size() > MAX_LOCAL_ENTRIES;
            List<AutomationLocalEntry> entries = paths.stream().limit(MAX_LOCAL_ENTRIES)
                    .map(AutomationLocalFiles::localEntry).toList();
            return new AutomationLocalListing(allowed.toString(), entries, truncated);
        });
    }

    public CompletionStage<String> readLocalText(AutomationSource source, Path path) {
        return execution.submit(() -> {
            Path allowed = requireAllowedLocal(path, true);
            return execution.audited(source, AutomationCapability.READ_LOCAL, null, com.ynpe.fastremote.i18n.Texts.text("legacy.java.3c71c650a90a4541"),
                    allowed.toString(), () -> {
                        if (!Files.isRegularFile(allowed, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IllegalArgumentException("Local path is not a regular file");
                        }
                        long size = Files.size(allowed);
                        if (size > MAX_TEXT_BYTES) throw new IllegalArgumentException("Local text file exceeds 256 KiB");
                        byte[] bytes = Files.readAllBytes(allowed);
                        try { return new String(bytes, java.nio.charset.StandardCharsets.UTF_8); }
                        finally { Arrays.fill(bytes, (byte) 0); }
                    });
        });
    }

    public CompletionStage<Boolean> writeLocalText(
            AutomationSource source, Path path, String content, boolean overwrite) {
        String safeContent = Objects.requireNonNull(content, "content");
        if (safeContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Local text content exceeds 256 KiB"));
        }
        return execution.submit(() -> {
            Path allowed = requireAllowedLocal(path, false);
            AutomationCapability capability = overwrite
                    ? AutomationCapability.EDIT_LOCAL : AutomationCapability.CREATE_LOCAL;
            return execution.audited(source, capability, null, com.ynpe.fastremote.i18n.Texts.text("legacy.java.1db4638757c6bece"),
                    allowed + com.ynpe.fastremote.i18n.Texts.text("legacy.java.1443eaf0e0ef3b9a") + overwrite + com.ynpe.fastremote.i18n.Texts.text("legacy.java.ea1b083ad973559f") + safeContent.length(), () -> {
                        boolean exists = Files.exists(allowed, LinkOption.NOFOLLOW_LINKS);
                        if (exists && !overwrite) {
                            throw new IllegalArgumentException("Local target already exists");
                        }
                        if (!exists && overwrite) {
                            throw new IllegalArgumentException("Local target does not exist");
                        }
                        if (exists && !Files.isRegularFile(allowed, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IllegalArgumentException("Local target is not a regular file");
                        }
                        Path temporary = Files.createTempFile(allowed.getParent(), ".fastremote-ai-", ".tmp");
                        try {
                            Files.writeString(temporary, safeContent, java.nio.charset.StandardCharsets.UTF_8,
                                    StandardOpenOption.TRUNCATE_EXISTING);
                            moveAtomic(temporary, allowed, overwrite);
                        } finally { Files.deleteIfExists(temporary); }
                        return true;
                    });
        });
    }

    public CompletionStage<Boolean> createLocalDirectory(AutomationSource source, Path path) {
        return execution.submit(() -> {
            Path allowed = requireAllowedLocal(path, false);
            return execution.audited(source, AutomationCapability.CREATE_LOCAL, null, com.ynpe.fastremote.i18n.Texts.text("legacy.java.27cf0fe357eb3532"),
                    allowed.toString(), () -> { Files.createDirectory(allowed); return true; });
        });
    }

    public CompletionStage<Boolean> renameLocal(
            AutomationSource source, Path sourcePath, Path targetPath) {
        return execution.submit(() -> {
            Path from = requireMutableLocal(sourcePath, true);
            Path to = requireAllowedLocal(targetPath, false);
            return execution.audited(source, AutomationCapability.RENAME_LOCAL, null, com.ynpe.fastremote.i18n.Texts.text("legacy.java.bdd52dbfbf8fd4db"),
                    from + " → " + to, () -> {
                        if (Files.exists(to)) throw new IllegalArgumentException("Local target already exists");
                        moveAtomic(from, to, false); return true;
                    });
        });
    }

    public CompletionStage<Boolean> deleteLocal(
            AutomationSource source, Path path, boolean recursive) {
        return execution.submit(() -> {
            Path allowed = requireMutableLocal(path, true);
            return execution.audited(source, AutomationCapability.DELETE_LOCAL, null, com.ynpe.fastremote.i18n.Texts.text("legacy.java.2f1cfb3bffccc93f"),
                    allowed + com.ynpe.fastremote.i18n.Texts.text("legacy.java.dc6e94d3032f664c") + recursive, () -> {
                        if (Files.isDirectory(allowed, LinkOption.NOFOLLOW_LINKS)) {
                            if (!recursive) { Files.delete(allowed); return true; }
                            try (var walk = Files.walk(allowed)) {
                                List<Path> entries = walk.limit(MAX_DELETE_ENTRIES + 1L).toList();
                                if (entries.size() > MAX_DELETE_ENTRIES) {
                                    throw new IllegalArgumentException("Local delete exceeds 10000 entries");
                                }
                                for (Path entry : entries.reversed()) Files.delete(entry);
                            }
                        } else Files.delete(allowed);
                        return true;
                    });
        });
    }


    Path requireAllowedLocal(Path value, boolean mustExist) {
        Objects.requireNonNull(value, "localPath");
        if (!value.isAbsolute()) throw new IllegalArgumentException("Relative path not allowed: " + value);
        Path path = value.normalize();
        try {
            List<LocalRoot> roots = existingRoots(aiSettings.load().allowedLocalRoots());
            Path checked = mustExist ? path.toRealPath() : realParent(path);
            boolean allowed = roots.stream().anyMatch(root -> checked.startsWith(root.realPath()));
            if (!allowed) throw new SecurityException("Path outside allowed local roots: " + path);
            if (mustExist && Files.notExists(path)) throw new IllegalArgumentException("Local source does not exist");
            return path;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Local path cannot be validated", failure);
        }
    }

    private Path requireMutableLocal(Path value, boolean mustExist) {
        Path allowed = requireAllowedLocal(value, mustExist);
        try {
            Path real = allowed.toRealPath();
            boolean isRoot = existingRoots(aiSettings.load().allowedLocalRoots()).stream()
                    .anyMatch(root -> allowed.equals(root.path()) || real.equals(root.realPath()));
            if (isRoot) throw new SecurityException("Configured local root itself cannot be renamed or deleted");
            return allowed;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Local path cannot be validated", failure);
        }
    }

    private record LocalRoot(Path path, Path realPath) { }

    private List<LocalRoot> existingRoots(List<Path> configured) {
        List<LocalRoot> roots = new ArrayList<>();
        // Resolve on every request: removable volumes can disappear and return without a config edit.
        for (Path root : configured) {
            try {
                Path real = root.toRealPath();
                if (Files.isDirectory(real)) {
                    roots.add(new LocalRoot(root.toAbsolutePath().normalize(), real));
                } else {
                    LOGGER.log(System.Logger.Level.WARNING, "Skipping configured local root that is not a directory: {0}", root);
                }
            } catch (IOException | SecurityException failure) {
                LOGGER.log(System.Logger.Level.WARNING, "Skipping unavailable configured local root: {0}", root);
            }
        }
        if (roots.isEmpty()) throw new SecurityException("No configured local root exists");
        return List.copyOf(roots);
    }

    private static Path realParent(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent == null) throw new IllegalArgumentException("Download target requires a parent directory");
        return parent.toRealPath();
    }

    private static AutomationLocalEntry localEntry(Path path) {
        return localEntry(path, false);
    }

    private static AutomationLocalEntry localEntry(Path path, boolean followLinks) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path, BasicFileAttributes.class, followLinks ? new LinkOption[0] : new LinkOption[] {LinkOption.NOFOLLOW_LINKS});
            String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            return new AutomationLocalEntry(name, path.toAbsolutePath().normalize().toString(),
                    attributes.isDirectory(), attributes.isRegularFile() ? attributes.size() : 0L);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Local entry cannot be read", failure);
        }
    }

    private static void moveAtomic(Path source, Path target, boolean replace) throws IOException {
        try {
            if (replace) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            else Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            if (replace) Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(source, target);
        }
    }
}
