// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.ai;

import java.net.URI;
import java.util.Optional;

/** Fixed, non-secret provider descriptor backed by the official Codex App Server. */
public final class ChatGptCodexProvider {
    public static final String ID = "openai-chatgpt-codex";
    public static final ModelProviderConfig CONFIG = new ModelProviderConfig(
            ID, "ChatGPT", ModelProviderType.CHATGPT_CODEX, URI.create("https://chatgpt.com"),
            "", Optional.empty(), false);

    private ChatGptCodexProvider() { }
}
