package com.techsenger.jeditermfx.core;

import org.jetbrains.annotations.NotNull;

/** Receives the working-directory URI reported by OSC 7. */
@FunctionalInterface
public interface TerminalWorkingDirectoryListener {

    void onWorkingDirectoryChanged(@NotNull String uri);
}
