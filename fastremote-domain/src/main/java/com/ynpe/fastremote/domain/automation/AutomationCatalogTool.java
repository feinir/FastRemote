// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.automation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The catalog tools shared by built-in AI and MCP. */
public enum AutomationCatalogTool {
    GET_HOST("Get editable saved-host metadata by exact ID.", "host_id", ""),
    ADD_HOST("Add a saved SSH host without opening a connection. Set credential_id to reuse a saved credential, or supply password or private_key (full SSH private-key text) with optional passphrase for a host-specific credential.",
            "name host username", "port:i group_id jump_host_id credential_id favorite:b icon password private_key passphrase"),
    UPDATE_HOST("Edit a saved host. Omitted fields are preserved. Empty group_id, jump_host_id or credential_id clears that reference. Supply password or private_key with optional passphrase to replace only this host's authentication. Do not combine inline credentials with credential_id.",
            "host_id", "name host username port:i group_id jump_host_id credential_id favorite:b icon password private_key passphrase"),
    DELETE_HOST("Delete a saved host. Dependent hosts are switched to direct connections and returned in the result.",
            "host_id", ""),
    ADD_HOST_GROUP("Create a host group. parent_id optionally identifies a top-level group.",
            "name", "parent_id color"),
    UPDATE_HOST_GROUP("Edit a host group. Omitted fields are preserved. Empty parent_id moves the group to the root.",
            "group_id", "name parent_id color"),
    DELETE_HOST_GROUP("Delete an empty host group.", "group_id", ""),
    LIST_CREDENTIALS("List reusable SSH credential metadata and IDs. Secrets are never returned.", "", ""),
    LIST_CREDENTIAL_GROUPS("List reusable SSH credential groups and IDs.", "", ""),
    ADD_CREDENTIAL_GROUP("Create a reusable credential group.", "name", "color"),
    UPDATE_CREDENTIAL_GROUP("Edit a credential group. Omitted fields are preserved.", "group_id", "name color"),
    DELETE_CREDENTIAL_GROUP("Delete an empty credential group.", "group_id", ""),
    ADD_CREDENTIAL("Create a reusable credential in the unlocked vault. Supply exactly one of password or private_key (full SSH private-key text). Optional passphrase applies to private_key only. Optional type (PASSWORD or PRIVATE_KEY) must match the supplied field.",
            "label username", "type password private_key passphrase group_id"),
    UPDATE_CREDENTIAL("Edit reusable credential metadata or replace its secret. Omitted fields are preserved; an empty group_id removes the group. To replace the secret, supply password or private_key with optional passphrase, as in add_credential. All hosts referencing this credential use the replacement.",
            "credential_id", "label username group_id type password private_key passphrase"),
    DELETE_CREDENTIAL("Delete a reusable vault credential. Credentials referenced by a host cannot be deleted.",
            "credential_id", ""),
    UPDATE_COMMAND("Edit a saved command. Omitted fields are preserved. Empty group_id removes the group.",
            "command_id", "name command description group_id"),
    UPDATE_COMMAND_GROUP("Edit a command group. Omitted fields are preserved.", "group_id", "name color"),
    DELETE_COMMAND_GROUP("Delete an empty command group.", "group_id", ""),
    UPDATE_VARIABLE("Edit a saved variable. Omitted fields are preserved. Empty group_id removes the group.",
            "variable_id", "name variable_name type delimiter value description group_id"),
    UPDATE_VARIABLE_GROUP("Edit a variable group. Omitted fields are preserved.", "group_id", "name color"),
    DELETE_VARIABLE_GROUP("Delete an empty variable group.", "group_id", "");

    public static final String GUIDANCE = "Use catalog tools to manage saved hosts, host groups, vault credentials, "
            + "commands and variables. Resolve exact IDs with list tools before editing or deleting. "
            + "Use list_credentials to reuse a saved SSH credential. Trusted clients can pass password or "
            + "private_key and optional passphrase directly as JSON arguments for encrypted storage in the unlocked "
            + "vault. Keep secrets out of chat, command lines and logs; never ask the user to paste them into chat. ";

    public static final List<String> SECRET_FIELDS = List.of("password", "private_key", "passphrase");

    private final String description;
    private final List<String> required;
    private final List<Field> fields;

    AutomationCatalogTool(String description, String required, String optional) {
        this.description = description;
        this.required = words(required);
        this.fields = words(required + " " + optional).stream().map(Field::parse).toList();
    }

    public String toolName() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String description() { return description; }
    public List<String> required() { return required; }
    public List<Field> fields() { return fields; }
    public boolean readOnly() { return this == GET_HOST || name().startsWith("LIST_"); }
    public boolean acceptsSecrets() {
        return this == ADD_HOST || this == UPDATE_HOST || this == ADD_CREDENTIAL || this == UPDATE_CREDENTIAL;
    }

    public static Optional<AutomationCatalogTool> find(String name) {
        return Arrays.stream(values()).filter(tool -> tool.toolName().equals(name)).findFirst();
    }

    public void validate(Map<String, Object> arguments) {
        arguments.forEach((key, value) -> {
            Field field = fields.stream().filter(candidate -> candidate.name().equals(key)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown catalog tool argument"));
            if (value == null && !required.contains(key)) return;
            if (!field.valueClass().isInstance(value)) {
                throw new IllegalArgumentException(field.name() + " must be " + field.type());
            }
            if ((key.equals("host_id") || key.endsWith("_id")) && value instanceof String raw && !raw.isBlank()) {
                try {
                    if (!java.util.UUID.fromString(raw.strip()).toString().equalsIgnoreCase(raw.strip())) {
                        throw new IllegalArgumentException();
                    }
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalArgumentException(key + " must be a UUID");
                }
            }
        });
        for (String field : required) {
            if (!(arguments.get(field) instanceof String value) || value.isBlank()) {
                throw new IllegalArgumentException(field + " is required");
            }
        }
    }

    private static List<String> words(String value) {
        return value.isBlank() ? List.of() : List.of(value.strip().split(" +"));
    }

    public record Field(String name, String type, Class<?> valueClass) {
        private static Field parse(String value) {
            if (value.endsWith(":i")) return new Field(value.substring(0, value.length() - 2), "integer", Integer.class);
            if (value.endsWith(":b")) return new Field(value.substring(0, value.length() - 2), "boolean", Boolean.class);
            return new Field(value, "string", String.class);
        }
    }
}
