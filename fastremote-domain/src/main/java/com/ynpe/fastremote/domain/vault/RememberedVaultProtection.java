// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.vault;

import java.util.Arrays;

/** Short-lived, validated password-derived material for encrypted portable exports. */
public final class RememberedVaultProtection implements AutoCloseable {
    private final byte[] key;
    private final byte[] salt;
    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;

    public RememberedVaultProtection(byte[] key, byte[] salt, int memoryKiB, int iterations, int parallelism) {
        this.key = key.clone();
        this.salt = salt.clone();
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    public byte[] copyKey() { return key.clone(); }
    public byte[] copySalt() { return salt.clone(); }
    public int memoryKiB() { return memoryKiB; }
    public int iterations() { return iterations; }
    public int parallelism() { return parallelism; }

    @Override
    public void close() {
        Arrays.fill(key, (byte) 0);
        Arrays.fill(salt, (byte) 0);
    }
}
