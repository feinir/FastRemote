// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import java.net.URL;
import java.util.Objects;

public final class MainView {
    private MainView() {
    }

    public static URL fxml() {
        return requiredResource("MainView.fxml");
    }

    static URL aiAssistantFxml() {
        return requiredResource("AiAssistantView.fxml");
    }

    static java.util.List<URL> includedFxml() {
        return java.util.List.of(aiAssistantFxml(), requiredResource("HostLibraryView.fxml"),
                requiredResource("SnippetLibraryView.fxml"), requiredResource("VariableLibraryView.fxml"),
                requiredResource("CredentialLibraryView.fxml"), requiredResource("DiagnosticLogView.fxml"),
                requiredResource("LocalFileWorkspaceView.fxml"));
    }

    public static URL stylesheet() {
        return requiredResource("main.css");
    }

    static URL darkStylesheet() {
        return requiredResource("theme-dark.css");
    }

    static URL slateStylesheet() {
        return requiredResource("theme-slate.css");
    }

    private static URL requiredResource(String name) {
        return Objects.requireNonNull(
                MainView.class.getResource(name),
                () -> "Missing UI resource: " + name);
    }
}
