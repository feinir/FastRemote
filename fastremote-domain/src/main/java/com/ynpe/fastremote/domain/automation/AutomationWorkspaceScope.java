// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

/** Groups of visible main-workspace tabs that automation may close. */
public enum AutomationWorkspaceScope {
    ALL,
    ALL_TERMINALS,
    REMOTE_TERMINALS,
    LOCAL_TERMINALS,
    SFTP
}
