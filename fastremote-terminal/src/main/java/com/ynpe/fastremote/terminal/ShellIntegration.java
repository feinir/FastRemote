// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.terminal;

/** Session-only hooks. POSIX outer syntax lets unsupported shells decline without parsing Bash/Zsh code. */
public final class ShellIntegration {
    private ShellIntegration() { }
    /** Source at local shell startup, before its first prompt; never send this text as terminal input. */
    public static String startupScript(String nonce) { return script(nonce, true) + "\n"; }
    static String bootstrap(String nonce) { return script(nonce, false) + "\r"; }
    private static String script(String nonce, boolean startup) {
        if (!nonce.matches("[a-f0-9]+")) throw new IllegalArgumentException("Invalid nonce");
        String emit = "printf '\\033]6973;" + nonce + ";%s\\007'";
        String bash = "_fr_running=0; _fr_prompting=1; "
                + "_fr_prompt() { local s=$?; _fr_prompting=1; " + emit
                + " \"D;$s\"; _fr_running=0; return \"$s\"; }; "
                + "_fr_ready() { _fr_prompting=0; " + emit + " R; }; "
                + "_fr_preexec() { if [ \"$_fr_prompting\" = 0 ] && [ \"$_fr_running\" = 0 ] "
                + "&& [ \"$BASH_COMMAND\" != _fr_prompt ] && [ \"$BASH_COMMAND\" != _fr_ready ]; then "
                + "_fr_running=1; " + emit + " C; fi; }; "
                + "if [ \"${BASH_VERSINFO[0]}\" -gt 5 ] || { [ \"${BASH_VERSINFO[0]}\" -eq 5 ] && [ \"${BASH_VERSINFO[1]}\" -ge 1 ]; }; then "
                + "PROMPT_COMMAND=(_fr_prompt \"${PROMPT_COMMAND[@]}\" _fr_ready); "
                + "else PROMPT_COMMAND=\"_fr_prompt; ${PROMPT_COMMAND:-:}; _fr_ready\"; fi; trap '_fr_preexec' DEBUG";
        String zsh = "_fr_running=0; _fr_preexec() { _fr_running=1; " + emit + " C; }; "
                + "_fr_precmd() { local s=$?; " + emit
                + " \"D;$s\"; _fr_running=0; " + (startup ? "" : emit + " R; ") + "return \"$s\"; }; "
                + "preexec_functions+=(_fr_preexec); precmd_functions=(_fr_precmd ${precmd_functions[@]})";
        if (startup) {
            // precmd precedes theme hooks and ZLE entering raw input mode. Sending at that point
            // produces an extra kernel echo (and can truncate long commands) on a slow prompt.
            zsh += "; _fr_ready() { " + emit + " R; }; if [[ -o zle ]]; then "
                    + "autoload -Uz add-zle-hook-widget; add-zle-hook-widget line-init _fr_ready; "
                    + "else precmd_functions+=(_fr_ready); fi";
        }
        String script = "if [ -n \"${BASH_VERSION-}\" ]; then if [ -z \"$(trap -p DEBUG)\" ]; then eval " + quote(bash)
                + "; else " + emit + " U; fi; elif [ -n \"${ZSH_VERSION-}\" ]; then eval " + quote(zsh)
                + "; else " + emit + " U; fi";
        // A newly opened PTY may still be in canonical mode before the shell enables its line editor.
        // Keep physical lines below the kernel input limit, including inside the quoted eval bodies.
        return script.replace("; ", ";\n");
    }
    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
}
