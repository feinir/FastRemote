// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record AutomationPermissions(Map<AutomationCapability, AutomationPermission> values) {
    public AutomationPermissions {
        Objects.requireNonNull(values, "values");
        EnumMap<AutomationCapability, AutomationPermission> copy = new EnumMap<>(AutomationCapability.class);
        for (AutomationCapability capability : AutomationCapability.values()) {
            copy.put(capability, Objects.requireNonNullElse(
                    values.get(capability), AutomationPermission.DENY));
        }
        values = Collections.unmodifiableMap(copy);
    }

    public AutomationPermission permission(AutomationCapability capability) {
        return values.get(Objects.requireNonNull(capability, "capability"));
    }

    public AutomationPermissions with(AutomationCapability capability, AutomationPermission permission) {
        EnumMap<AutomationCapability, AutomationPermission> copy = new EnumMap<>(AutomationCapability.class);
        copy.putAll(values);
        copy.put(Objects.requireNonNull(capability, "capability"),
                Objects.requireNonNull(permission, "permission"));
        return new AutomationPermissions(copy);
    }

    public static AutomationPermissions builtInDefaults() {
        EnumMap<AutomationCapability, AutomationPermission> values = denied();
        values.put(AutomationCapability.READ_HOST_METADATA, AutomationPermission.ALLOW);
        values.put(AutomationCapability.OPEN_HOST, AutomationPermission.ASK);
        values.put(AutomationCapability.MANAGE_WORKSPACE, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_WORKSPACE, AutomationPermission.ASK);
        values.put(AutomationCapability.EDIT_WORKSPACE, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_WORKSPACE_LOG, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_TERMINAL_OUTPUT, AutomationPermission.ASK);
        values.put(AutomationCapability.IMPORT_EXPORT, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_LIBRARY, AutomationPermission.ASK);
        values.put(AutomationCapability.MANAGE_LIBRARY, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_LOCAL, AutomationPermission.ASK);
        values.put(AutomationCapability.CREATE_LOCAL, AutomationPermission.ASK);
        values.put(AutomationCapability.EDIT_LOCAL, AutomationPermission.ASK);
        values.put(AutomationCapability.DELETE_LOCAL, AutomationPermission.ASK);
        values.put(AutomationCapability.RENAME_LOCAL, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_REMOTE, AutomationPermission.ASK);
        values.put(AutomationCapability.CREATE_REMOTE, AutomationPermission.ASK);
        values.put(AutomationCapability.EDIT_REMOTE, AutomationPermission.ASK);
        values.put(AutomationCapability.DELETE_REMOTE, AutomationPermission.ASK);
        values.put(AutomationCapability.RENAME_REMOTE, AutomationPermission.ASK);
        values.put(AutomationCapability.RUN_COMMAND, AutomationPermission.ASK);
        values.put(AutomationCapability.UPLOAD, AutomationPermission.ASK);
        values.put(AutomationCapability.DOWNLOAD, AutomationPermission.ASK);
        return new AutomationPermissions(values);
    }

    public static AutomationPermissions mcpDefaults() {
        EnumMap<AutomationCapability, AutomationPermission> values = denied();
        values.put(AutomationCapability.READ_HOST_METADATA, AutomationPermission.ASK);
        values.put(AutomationCapability.OPEN_HOST, AutomationPermission.ASK);
        values.put(AutomationCapability.MANAGE_WORKSPACE, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_WORKSPACE, AutomationPermission.ASK);
        values.put(AutomationCapability.EDIT_WORKSPACE, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_WORKSPACE_LOG, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_TERMINAL_OUTPUT, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_LIBRARY, AutomationPermission.ASK);
        values.put(AutomationCapability.MANAGE_LIBRARY, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_LOCAL, AutomationPermission.ASK);
        values.put(AutomationCapability.CREATE_LOCAL, AutomationPermission.ASK);
        values.put(AutomationCapability.EDIT_LOCAL, AutomationPermission.ASK);
        values.put(AutomationCapability.DELETE_LOCAL, AutomationPermission.ASK);
        values.put(AutomationCapability.RENAME_LOCAL, AutomationPermission.ASK);
        values.put(AutomationCapability.READ_REMOTE, AutomationPermission.ASK);
        values.put(AutomationCapability.CREATE_REMOTE, AutomationPermission.ASK);
        values.put(AutomationCapability.EDIT_REMOTE, AutomationPermission.ASK);
        values.put(AutomationCapability.DELETE_REMOTE, AutomationPermission.ASK);
        values.put(AutomationCapability.RENAME_REMOTE, AutomationPermission.ASK);
        values.put(AutomationCapability.RUN_COMMAND, AutomationPermission.ASK);
        values.put(AutomationCapability.UPLOAD, AutomationPermission.ASK);
        values.put(AutomationCapability.DOWNLOAD, AutomationPermission.ASK);
        return new AutomationPermissions(values);
    }

    private static EnumMap<AutomationCapability, AutomationPermission> denied() {
        EnumMap<AutomationCapability, AutomationPermission> values = new EnumMap<>(AutomationCapability.class);
        for (AutomationCapability capability : AutomationCapability.values()) {
            values.put(capability, AutomationPermission.DENY);
        }
        return values;
    }
}
