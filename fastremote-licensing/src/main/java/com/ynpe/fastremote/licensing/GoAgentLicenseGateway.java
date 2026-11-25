// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.licensing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ynpe.fastremote.domain.license.LicenseCapability;
import com.ynpe.fastremote.domain.license.LicenseSnapshot;
import com.ynpe.fastremote.domain.license.LicenseStatus;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class GoAgentLicenseGateway implements LicenseGateway {
    private static final int MAX_FRAME_SIZE = 64 * 1024;
    private final Path executable;
    private final Path stateDirectory;
    private final URI developmentServerUri;
    private final String developmentSigningKeys;
    private final Path integrityManifest;
    private final String expectedAgentVersion;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual()
            .name("fastremote-license-agent-", 0).factory());
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "fastremote-license-agent-watchdog");
        thread.setDaemon(true);
        return thread;
    });
    private final byte[] challenge = new byte[32];
    private Process process;
    private DataInputStream input;
    private DataOutputStream output;
    private boolean closed;

    public GoAgentLicenseGateway(Path executable, Path stateDirectory, URI serverUri) {
        this(executable, stateDirectory, serverUri, "", null, "");
    }

    public GoAgentLicenseGateway(Path executable, Path stateDirectory, URI serverUri, String developmentSigningKeys) {
        this(executable, stateDirectory, serverUri, developmentSigningKeys, null, "");
    }

    public GoAgentLicenseGateway(Path executable, Path stateDirectory, URI serverUri,
                                 String developmentSigningKeys, Path integrityManifest) {
        this(executable, stateDirectory, serverUri, developmentSigningKeys, integrityManifest, "");
    }

    public GoAgentLicenseGateway(Path executable, Path stateDirectory, URI serverUri,
                                 String developmentSigningKeys, Path integrityManifest,
                                 String expectedAgentVersion) {
        this.executable = executable.toAbsolutePath().normalize();
        this.stateDirectory = stateDirectory.toAbsolutePath().normalize();
        this.developmentServerUri = serverUri;
        this.developmentSigningKeys = developmentSigningKeys == null ? "" : developmentSigningKeys.strip();
        this.integrityManifest = integrityManifest == null ? null : integrityManifest.toAbsolutePath().normalize();
        this.expectedAgentVersion = expectedAgentVersion == null ? "" : expectedAgentVersion.strip();
        new SecureRandom().nextBytes(challenge);
    }

    @Override public CompletionStage<LicenseSnapshot> status() { return invoke("status", Map.of()); }
    @Override public CompletionStage<LicenseSnapshot> startTrial() { return invoke("start_trial", Map.of()); }
    @Override public CompletionStage<LicenseSnapshot> syncTrial() { return invoke("sync_trial", Map.of()); }
    @Override public CompletionStage<LicenseSnapshot> refresh() { return invoke("refresh", Map.of()); }
    @Override public CompletionStage<LicenseSnapshot> deactivate() { return invoke("deactivate", Map.of()); }

    @Override public CompletionStage<LicenseSnapshot> activate(char[] code) {
        char[] copy = Arrays.copyOf(code, code.length);
        Arrays.fill(code, '\0');
        return invoke("activate", Map.of("license_code", new String(copy))).whenComplete((ignored, error) ->
                Arrays.fill(copy, '\0'));
    }

    @Override public CompletionStage<LicenseSnapshot> activateOffline(char[] licenseContent) {
        if (licenseContent.length < 1 || licenseContent.length > 32 * 1024) {
            Arrays.fill(licenseContent, '\0');
            return CompletableFuture.failedFuture(new LicenseAgentException("OFFLINE_LICENSE_INVALID", null));
        }
        char[] copy = Arrays.copyOf(licenseContent, licenseContent.length);
        Arrays.fill(licenseContent, '\0');
        String token = new String(copy).strip();
        if (token.isEmpty() || token.getBytes(StandardCharsets.UTF_8).length > 32 * 1024) {
            Arrays.fill(copy, '\0');
            return CompletableFuture.failedFuture(new LicenseAgentException("OFFLINE_LICENSE_INVALID", null));
        }
        return invoke("activate_offline", Map.of("token", token)).whenComplete((ignored, error) ->
                Arrays.fill(copy, '\0'));
    }

    @Override public CompletionStage<Boolean> permits(LicenseCapability capability) {
        return status().thenApply(snapshot -> snapshot.permits(capability) && verifyLease(snapshot.lease(), capability));
    }

    private CompletionStage<LicenseSnapshot> invoke(String method, Map<String, ?> params) {
        return invokeResult(method, params).thenApply(node -> {
            try {
                return snapshot(node);
            } catch (IOException error) {
                throw new CompletionException(new LicenseAgentException("AGENT_RESPONSE_INVALID", error));
            }
        });
    }

    @Override public CompletionStage<String> generateOfflineRequest(char[] licenseCode) {
        char[] copy = Arrays.copyOf(licenseCode, licenseCode.length);
        Arrays.fill(licenseCode, '\0');
        return invokeResult("generate_offline_request", Map.of("license_code", new String(copy))).thenApply(node -> {
            String request = node.path("request").asText().strip();
            if (request.isEmpty() || request.getBytes(StandardCharsets.UTF_8).length > 32 * 1024) {
                throw new CompletionException(new LicenseAgentException("OFFLINE_REQUEST_INVALID", null));
            }
            return request;
        }).whenComplete((ignored, error) -> Arrays.fill(copy, '\0'));
    }

    private CompletionStage<JsonNode> invokeResult(String method, Map<String, ?> params) {
        CompletableFuture<JsonNode> result = CompletableFuture.supplyAsync(() -> {
            try {
                ensureStarted();
                String id = UUID.randomUUID().toString();
                byte[] request = mapper.writeValueAsBytes(Map.of("id", id, "method", method, "params", params));
                writeFrame(request);
                JsonNode response = mapper.readTree(readFrame());
                if (!id.equals(response.path("id").asText())) throw new IOException("license agent response id mismatch");
                if (!response.path("ok").asBoolean()) {
                    throw new LicenseAgentException(response.path("error").path("code").asText("AGENT_ERROR"), null);
                }
                return response.path("result");
            } catch (IOException error) {
                stopProcess();
                throw new LicenseAgentException("AGENT_UNAVAILABLE", error);
            }
        }, executor);
        var timeout = watchdog.schedule(() -> {
            if (result.completeExceptionally(new LicenseAgentException("AGENT_TIMEOUT", null))) {
                stopProcess();
            }
        }, 20, TimeUnit.SECONDS);
        result.whenComplete((ignored, error) -> timeout.cancel(false));
        return result;
    }

    private void ensureStarted() throws IOException {
        if (closed) throw new IOException("license gateway is closed");
        if (process != null && process.isAlive()) return;
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) throw new IOException("license agent is unavailable");
        Files.createDirectories(stateDirectory);
        var command = agentCommand(executable, stateDirectory, developmentServerUri);
        if (!developmentSigningKeys.isBlank()) {
            command.add("--signing-keys");
            command.add(developmentSigningKeys);
        }
        if (integrityManifest != null) {
            command.add("--integrity-manifest");
            command.add(integrityManifest.toString());
        }
        // A jpackage Windows GUI launcher has no console and can expose an invalid inherited
        // stderr handle. Asking CreateProcess to inherit it prevents the Agent from starting even
        // though the executable itself is valid. The protocol carries every actionable error, so
        // discard the Agent's fatal-only diagnostic stream instead of inheriting a parent handle.
        process = agentProcessBuilder(command).start();
        input = new DataInputStream(new BufferedInputStream(process.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
        String id = UUID.randomUUID().toString();
        writeFrame(mapper.writeValueAsBytes(Map.of("id", id, "method", "initialize", "params",
                Map.of("challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)))));
        JsonNode response = mapper.readTree(readFrame());
        if (!validInitializationResponse(response, expectedAgentVersion)) {
            throw new IOException("license agent protocol negotiation failed");
        }
    }

    static boolean validInitializationResponse(JsonNode response, String expectedAgentVersion) {
        String expected = expectedAgentVersion == null ? "" : expectedAgentVersion.strip();
        JsonNode result = response.path("result");
        return response.path("ok").asBoolean()
                && result.path("protocol_version").asInt() == 1
                && (expected.isEmpty() || expected.equals(result.path("agent_version").asText()));
    }

    static ProcessBuilder agentProcessBuilder(java.util.List<String> command) {
        return new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
    }

    static java.util.List<String> agentCommand(Path executable, Path stateDirectory, URI developmentServerUri) {
        var command = new java.util.ArrayList<>(java.util.List.of(executable.toString(), "--state-dir",
                stateDirectory.toString()));
        if (developmentServerUri != null) {
            command.add("--server-url");
            command.add(developmentServerUri.toString());
        }
        return command;
    }

    private LicenseSnapshot snapshot(JsonNode node) throws IOException {
        LicenseStatus status;
        try {
            status = LicenseStatus.valueOf(node.path("status").asText());
            if (status == LicenseStatus.COMMUNITY) throw new IllegalArgumentException("Unexpected Agent status");
        }
        catch (IllegalArgumentException error) { throw new IOException("unknown license status", error); }
        EnumSet<LicenseCapability> capabilities = EnumSet.noneOf(LicenseCapability.class);
        node.path("capabilities").forEach(value -> Arrays.stream(LicenseCapability.values())
                .filter(item -> item.wireName().equals(value.asText())).findFirst().ifPresent(capabilities::add));
        LicenseSnapshot snapshot = new LicenseSnapshot(status, node.path("kind").asText(""),
                instant(node, "expires_at"), instant(node, "refresh_after"), instant(node, "offline_until"),
                capabilities, node.path("lease").asText(""), node.path("license_hint").asText(""),
                node.path("device_code").asText(""), node.path("activation_required").asBoolean(false));
        if (status.permitsCommercialUse() && !verifyLease(snapshot.lease(), null)) throw new IOException("invalid process-bound license lease");
        return snapshot;
    }

    private Optional<Instant> instant(JsonNode node, String name) {
        long value = node.path(name).asLong(0);
        return value > 0 ? Optional.of(Instant.ofEpochSecond(value)) : Optional.empty();
    }

    private boolean verifyLease(String lease, LicenseCapability required) {
        try {
            String[] parts = lease.split("\\.", -1);
            if (parts.length != 2) return false;
            byte[] claims = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(challenge, "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(claims), signature)) return false;
            String[] values = new String(claims, StandardCharsets.UTF_8).split("\\|", -1);
            if (values.length != 4 || Instant.now().getEpochSecond() >= Long.parseLong(values[2])) return false;
            return required == null || Set.of(values[3].split(",")).contains(required.wireName());
        } catch (Exception ignored) { return false; }
    }

    private byte[] readFrame() throws IOException {
        return readFrame(input);
    }

    static byte[] readFrame(DataInputStream source) throws IOException {
        int size = source.readInt();
        if (size < 1 || size > MAX_FRAME_SIZE) throw new IOException("invalid license agent frame size");
        byte[] frame = source.readNBytes(size);
        if (frame.length != size) throw new IOException("truncated license agent frame");
        return frame;
    }
    private void writeFrame(byte[] value) throws IOException {
        if (value.length < 1 || value.length > MAX_FRAME_SIZE) throw new IOException("invalid license agent frame size");
        output.writeInt(value.length); output.write(value); output.flush();
    }

    private void stopProcess() {
        if (process != null) { process.destroy(); process = null; }
        input = null; output = null;
    }

    @Override public void close() {
        closed = true;
        executor.shutdownNow();
        watchdog.shutdownNow();
        stopProcess();
        Arrays.fill(challenge, (byte) 0);
    }

    public static final class LicenseAgentException extends RuntimeException {
        private final String code;
        LicenseAgentException(String code, Throwable cause) { super(code, cause); this.code = code; }
        public String code() { return code; }
    }
}
