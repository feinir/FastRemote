// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

import java.nio.file.Files;
import java.nio.file.Path;

/** Isolated child process for discovery timeout and output-boundary tests. */
public final class CodexDiscoveryProcessFixture {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "paths" -> {
                System.out.println(args[1]);
                System.out.println("relative-path");
            }
            case "overflow" -> System.out.println("x".repeat(20 * 1024));
            case "failure" -> System.exit(1);
            case "hang" -> {
                Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
                Thread.sleep(30_000);
            }
            default -> throw new IllegalArgumentException("Unknown probe mode");
        }
    }
}
