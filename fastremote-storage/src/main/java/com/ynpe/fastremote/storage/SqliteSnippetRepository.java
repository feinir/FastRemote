// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.storage;

import com.ynpe.fastremote.domain.snippet.Snippet;
import com.ynpe.fastremote.domain.snippet.SnippetGroup;
import com.ynpe.fastremote.domain.snippet.SnippetGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetId;
import com.ynpe.fastremote.domain.snippet.SnippetRepository;
import com.ynpe.fastremote.domain.snippet.SnippetVariable;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroup;
import com.ynpe.fastremote.domain.snippet.SnippetVariableGroupId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableId;
import com.ynpe.fastremote.domain.snippet.SnippetVariableType;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippet;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetConflictPolicy;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetGroup;
import com.ynpe.fastremote.domain.snippet.BuiltinSnippetImportResult;
import com.ynpe.fastremote.domain.catalog.GroupColor;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SqliteSnippetRepository implements SnippetRepository {
    private final FastRemoteDatabase database;

    public SqliteSnippetRepository(FastRemoteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public List<SnippetGroup> listGroups() throws IOException {
        return query(connection -> {
            List<SnippetGroup> groups = new ArrayList<>();
            try (var statement = connection.prepareStatement(
                    "SELECT id, name, sort_order, icon_color FROM snippet_groups ORDER BY sort_order, name COLLATE NOCASE");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) groups.add(new SnippetGroup(
                        SnippetGroupId.parse(result.getString("id")), result.getString("name"),
                        result.getInt("sort_order"), GroupColor.valueOf(result.getString("icon_color"))));
            }
            return List.copyOf(groups);
        });
    }

    @Override
    public List<Snippet> listSnippets() throws IOException {
        return query(connection -> {
            List<Snippet> snippets = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    SELECT id, group_id, name, description, content, sort_order, created_at, updated_at
                    FROM snippets ORDER BY group_id, sort_order, name COLLATE NOCASE
                    """); ResultSet result = statement.executeQuery()) {
                while (result.next()) snippets.add(snippet(result));
            }
            return List.copyOf(snippets);
        });
    }

    @Override
    public void saveGroup(SnippetGroup group) throws IOException {
        Objects.requireNonNull(group, "group");
        update(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO snippet_groups(id, name, sort_order, icon_color) VALUES(?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET name=excluded.name, sort_order=excluded.sort_order,
                        icon_color=excluded.icon_color
                    """)) {
                statement.setString(1, group.id().toString());
                statement.setString(2, group.name());
                statement.setInt(3, group.sortOrder());
                statement.setString(4, group.color().name());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void deleteGroup(SnippetGroupId id) throws IOException {
        Objects.requireNonNull(id, "id");
        update(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM snippet_groups WHERE id=?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void saveSnippet(Snippet snippet) throws IOException {
        Objects.requireNonNull(snippet, "snippet");
        update(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO snippets(id, group_id, name, description, content, sort_order, created_at, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET group_id=excluded.group_id, name=excluded.name,
                        description=excluded.description, content=excluded.content,
                        sort_order=excluded.sort_order, updated_at=excluded.updated_at
                    """)) {
                bindSnippet(statement, snippet);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void saveSnippets(List<Snippet> snippets) throws IOException {
        List<Snippet> values = List.copyOf(Objects.requireNonNull(snippets, "snippets"));
        if (values.isEmpty()) return;
        update(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO snippets(id, group_id, name, description, content, sort_order, created_at, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET group_id=excluded.group_id, name=excluded.name,
                        description=excluded.description, content=excluded.content,
                        sort_order=excluded.sort_order, updated_at=excluded.updated_at
                    """)) {
                for (Snippet snippet : values) {
                    bindSnippet(statement, Objects.requireNonNull(snippet, "snippet"));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    @Override
    public BuiltinSnippetImportResult importBuiltinSnippets(
            String languageTag,
            BuiltinSnippetConflictPolicy conflictPolicy,
            List<BuiltinSnippetGroup> groups,
            List<BuiltinSnippet> snippets) throws IOException {
        String language = Objects.requireNonNull(languageTag, "languageTag").strip();
        BuiltinSnippetConflictPolicy selectedPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        if (language.isEmpty() || language.length() > 35) {
            throw new IllegalArgumentException("Invalid built-in command language tag");
        }
        List<BuiltinSnippetGroup> checkedGroups = List.copyOf(Objects.requireNonNull(groups, "groups"));
        List<BuiltinSnippet> checkedSnippets = List.copyOf(Objects.requireNonNull(snippets, "snippets"));
        Set<String> groupIds = new HashSet<>();
        for (BuiltinSnippetGroup group : checkedGroups) {
            if (!groupIds.add(Objects.requireNonNull(group, "group").builtinId())) {
                throw new IllegalArgumentException("Duplicate built-in group identifier");
            }
        }
        Set<String> commandIds = new HashSet<>();
        for (BuiltinSnippet snippet : checkedSnippets) {
            BuiltinSnippet command = Objects.requireNonNull(snippet, "snippet");
            if (!commandIds.add(command.builtinId())) {
                throw new IllegalArgumentException("Duplicate built-in command identifier");
            }
            if (command.groupBuiltinId().isPresent() && !groupIds.contains(command.groupBuiltinId().get())) {
                throw new IllegalArgumentException("Built-in command references an unknown group identifier");
            }
        }
        try {
            return database.transaction(connection -> {
                Map<String, SnippetGroupId> mappedGroups = new HashMap<>();
                try (var statement = connection.prepareStatement(
                        "SELECT builtin_id, group_id FROM builtin_snippet_groups");
                     var result = statement.executeQuery()) {
                    while (result.next()) mappedGroups.put(result.getString(1),
                            SnippetGroupId.parse(result.getString(2)));
                }
                Map<String, SnippetGroupId> groupsByName = new HashMap<>();
                try (var statement = connection.prepareStatement("SELECT id, name FROM snippet_groups");
                     var result = statement.executeQuery()) {
                    while (result.next()) groupsByName.put(result.getString(2).strip().toLowerCase(java.util.Locale.ROOT),
                            SnippetGroupId.parse(result.getString(1)));
                }
                for (BuiltinSnippetGroup definition : checkedGroups) {
                    SnippetGroupId mappedGroupId = mappedGroups.get(definition.builtinId());
                    if (mappedGroupId != null) {
                        if (selectedPolicy == BuiltinSnippetConflictPolicy.OVERWRITE) {
                            saveGroup(connection, new SnippetGroup(mappedGroupId, definition.name(),
                                    definition.sortOrder(), definition.color()));
                            updateBuiltinGroupLanguage(connection, definition.builtinId(), language);
                        }
                        continue;
                    }
                    SnippetGroupId groupId = groupsByName.get(
                            definition.name().toLowerCase(java.util.Locale.ROOT));
                    if (groupId == null) {
                        groupId = SnippetGroupId.create();
                        saveGroup(connection, new SnippetGroup(groupId, definition.name(),
                                definition.sortOrder(), definition.color()));
                        groupsByName.put(definition.name().toLowerCase(java.util.Locale.ROOT), groupId);
                    }
                    try (var statement = connection.prepareStatement("""
                            INSERT INTO builtin_snippet_groups(builtin_id, group_id, imported_language)
                            VALUES(?, ?, ?)
                            """)) {
                        statement.setString(1, definition.builtinId());
                        statement.setString(2, groupId.toString());
                        statement.setString(3, language);
                        statement.executeUpdate();
                    }
                    mappedGroups.put(definition.builtinId(), groupId);
                }

                Map<String, SnippetId> importedIds = new HashMap<>();
                try (var statement = connection.prepareStatement(
                        "SELECT builtin_id, snippet_id FROM builtin_snippets");
                     var result = statement.executeQuery()) {
                    while (result.next()) importedIds.put(
                            result.getString(1), SnippetId.parse(result.getString(2)));
                }
                Map<String, SnippetId> snippetsByName = new HashMap<>();
                try (var statement = connection.prepareStatement("SELECT id, group_id, name FROM snippets");
                     var result = statement.executeQuery()) {
                    while (result.next()) snippetsByName.put(commandNameKey(
                            result.getString(2), result.getString(3)), SnippetId.parse(result.getString(1)));
                }
                int imported = 0;
                int overwritten = 0;
                int skipped = 0;
                Instant now = Instant.now();
                for (BuiltinSnippet definition : checkedSnippets) {
                    SnippetId importedId = importedIds.get(definition.builtinId());
                    if (importedId != null) {
                        if (selectedPolicy == BuiltinSnippetConflictPolicy.SKIP) {
                            skipped++;
                            continue;
                        }
                        updateBuiltinSnippet(connection, importedId, definition,
                                definition.groupBuiltinId().map(mappedGroups::get), now);
                        updateBuiltinSnippetLanguage(connection, definition.builtinId(), language);
                        overwritten++;
                        continue;
                    }
                    Optional<SnippetGroupId> groupId = definition.groupBuiltinId().map(mappedGroups::get);
                    String groupValue = groupId.map(SnippetGroupId::toString).orElse(null);
                    String nameKey = commandNameKey(groupValue, definition.name());
                    SnippetId sameName = snippetsByName.get(nameKey);
                    if (sameName != null) {
                        recordBuiltinSnippet(connection, definition.builtinId(), sameName, language);
                        importedIds.put(definition.builtinId(), sameName);
                        skipped++;
                        continue;
                    }
                    Snippet snippet = new Snippet(SnippetId.create(), groupId, definition.name(),
                            definition.description(), definition.content(), definition.sortOrder(), now, now);
                    try (var statement = connection.prepareStatement("""
                            INSERT INTO snippets(id, group_id, name, description, content, sort_order, created_at, updated_at)
                            VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                            """)) {
                        bindSnippet(statement, snippet);
                        statement.executeUpdate();
                    }
                    recordBuiltinSnippet(connection, definition.builtinId(), snippet.id(), language);
                    importedIds.put(definition.builtinId(), snippet.id());
                    snippetsByName.put(nameKey, snippet.id());
                    imported++;
                }
                return new BuiltinSnippetImportResult(imported, overwritten, skipped);
            });
        } catch (SQLException failure) {
            throw new IOException("Built-in command import failed", failure);
        }
    }

    private static String commandNameKey(String groupId, String name) {
        return Objects.requireNonNullElse(groupId, "") + '\0'
                + name.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private static void recordBuiltinSnippet(
            java.sql.Connection connection, String builtinId, SnippetId snippetId, String language)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO builtin_snippets(builtin_id, snippet_id, imported_language)
                VALUES(?, ?, ?)
                """)) {
            statement.setString(1, builtinId);
            statement.setString(2, snippetId.toString());
            statement.setString(3, language);
            statement.executeUpdate();
        }
    }

    private static void updateBuiltinGroupLanguage(
            java.sql.Connection connection, String builtinId, String language) throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE builtin_snippet_groups SET imported_language=? WHERE builtin_id=?")) {
            statement.setString(1, language);
            statement.setString(2, builtinId);
            statement.executeUpdate();
        }
    }

    private static void updateBuiltinSnippet(
            java.sql.Connection connection,
            SnippetId snippetId,
            BuiltinSnippet definition,
            Optional<SnippetGroupId> groupId,
            Instant updatedAt) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE snippets
                SET group_id=?, name=?, description=?, content=?, sort_order=?, updated_at=?
                WHERE id=?
                """)) {
            if (groupId.isPresent()) statement.setString(1, groupId.get().toString());
            else statement.setNull(1, java.sql.Types.VARCHAR);
            statement.setString(2, definition.name());
            statement.setString(3, definition.description());
            statement.setString(4, definition.content());
            statement.setInt(5, definition.sortOrder());
            statement.setLong(6, updatedAt.toEpochMilli());
            statement.setString(7, snippetId.toString());
            statement.executeUpdate();
        }
    }

    private static void updateBuiltinSnippetLanguage(
            java.sql.Connection connection, String builtinId, String language) throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE builtin_snippets SET imported_language=? WHERE builtin_id=?")) {
            statement.setString(1, language);
            statement.setString(2, builtinId);
            statement.executeUpdate();
        }
    }

    private static void bindSnippet(java.sql.PreparedStatement statement, Snippet snippet) throws SQLException {
        statement.setString(1, snippet.id().toString());
        if (snippet.groupId().isPresent()) statement.setString(2, snippet.groupId().get().toString());
        else statement.setNull(2, java.sql.Types.VARCHAR);
        statement.setString(3, snippet.name());
        statement.setString(4, snippet.description());
        statement.setString(5, snippet.content());
        statement.setInt(6, snippet.sortOrder());
        statement.setLong(7, snippet.createdAt().toEpochMilli());
        statement.setLong(8, snippet.updatedAt().toEpochMilli());
    }

    @Override
    public void deleteSnippet(SnippetId id) throws IOException {
        Objects.requireNonNull(id, "id");
        update(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM snippets WHERE id=?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public List<SnippetVariable> listVariables() throws IOException {
        return query(connection -> {
            List<SnippetVariable> variables = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    SELECT id, group_id, name, variable_name, value_type, delimiter, value, description,
                        sort_order, created_at, updated_at
                    FROM snippet_variables ORDER BY group_id, sort_order, name COLLATE NOCASE
                    """);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) variables.add(variable(result));
            }
            return List.copyOf(variables);
        });
    }

    @Override
    public List<SnippetVariableGroup> listVariableGroups() throws IOException {
        return query(connection -> {
            List<SnippetVariableGroup> groups = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    SELECT id, name, sort_order, icon_color FROM snippet_variable_groups
                    ORDER BY sort_order, name COLLATE NOCASE
                    """); ResultSet result = statement.executeQuery()) {
                while (result.next()) groups.add(new SnippetVariableGroup(
                        SnippetVariableGroupId.parse(result.getString("id")), result.getString("name"),
                        result.getInt("sort_order"), GroupColor.valueOf(result.getString("icon_color"))));
            }
            return List.copyOf(groups);
        });
    }

    @Override
    public void saveVariableGroup(SnippetVariableGroup group) throws IOException {
        Objects.requireNonNull(group, "group");
        update(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO snippet_variable_groups(id, name, sort_order, icon_color) VALUES(?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET name=excluded.name, sort_order=excluded.sort_order,
                        icon_color=excluded.icon_color
                    """)) {
                statement.setString(1, group.id().toString());
                statement.setString(2, group.name());
                statement.setInt(3, group.sortOrder());
                statement.setString(4, group.color().name());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void deleteVariableGroup(SnippetVariableGroupId id) throws IOException {
        Objects.requireNonNull(id, "id");
        update(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM snippet_variable_groups WHERE id=?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void saveVariable(SnippetVariable variable) throws IOException {
        Objects.requireNonNull(variable, "variable");
        saveVariables(List.of(variable));
    }

    @Override
    public void saveVariables(List<SnippetVariable> variables) throws IOException {
        List<SnippetVariable> values = List.copyOf(Objects.requireNonNull(variables, "variables"));
        if (values.isEmpty()) return;
        update(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO snippet_variables(
                        id, group_id, name, variable_name, value_type, delimiter, value, description,
                        sort_order, created_at, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET group_id=excluded.group_id, name=excluded.name,
                        variable_name=excluded.variable_name, value_type=excluded.value_type,
                        delimiter=excluded.delimiter, value=excluded.value, description=excluded.description,
                        sort_order=excluded.sort_order, updated_at=excluded.updated_at
                    """)) {
                for (SnippetVariable variable : values) {
                    SnippetVariable checked = Objects.requireNonNull(variable, "variable");
                    statement.setString(1, checked.id().toString());
                    if (checked.groupId().isPresent()) statement.setString(2, checked.groupId().get().toString());
                    else statement.setNull(2, java.sql.Types.VARCHAR);
                    statement.setString(3, checked.name());
                    statement.setString(4, checked.variableName());
                    statement.setString(5, checked.type().name());
                    statement.setString(6, checked.delimiter());
                    statement.setString(7, checked.value());
                    statement.setString(8, checked.description());
                    statement.setInt(9, checked.sortOrder());
                    statement.setLong(10, checked.createdAt().toEpochMilli());
                    statement.setLong(11, checked.updatedAt().toEpochMilli());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    @Override
    public void saveLibraryImport(
            List<SnippetGroup> groups,
            List<Snippet> snippets,
            List<SnippetVariableGroup> variableGroups,
            List<SnippetVariable> variables) throws IOException {
        List<SnippetGroup> checkedGroups = List.copyOf(Objects.requireNonNull(groups, "groups"));
        List<Snippet> checkedSnippets = List.copyOf(Objects.requireNonNull(snippets, "snippets"));
        List<SnippetVariableGroup> checkedVariableGroups = List.copyOf(
                Objects.requireNonNull(variableGroups, "variableGroups"));
        List<SnippetVariable> checkedVariables = List.copyOf(Objects.requireNonNull(variables, "variables"));
        update(connection -> {
            for (SnippetGroup group : checkedGroups) saveGroup(connection, Objects.requireNonNull(group, "group"));
            for (SnippetVariableGroup group : checkedVariableGroups) {
                saveVariableGroup(connection, Objects.requireNonNull(group, "variableGroup"));
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO snippets(id, group_id, name, description, content, sort_order, created_at, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET group_id=excluded.group_id, name=excluded.name,
                        description=excluded.description, content=excluded.content,
                        sort_order=excluded.sort_order, updated_at=excluded.updated_at
                    """)) {
                for (Snippet snippet : checkedSnippets) {
                    bindSnippet(statement, Objects.requireNonNull(snippet, "snippet"));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            try (var statement = variableUpsert(connection)) {
                for (SnippetVariable variable : checkedVariables) {
                    bindVariable(statement, Objects.requireNonNull(variable, "variable"));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    private static void saveGroup(java.sql.Connection connection, SnippetGroup group) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO snippet_groups(id, name, sort_order, icon_color) VALUES(?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name, sort_order=excluded.sort_order,
                    icon_color=excluded.icon_color
                """)) {
            statement.setString(1, group.id().toString());
            statement.setString(2, group.name());
            statement.setInt(3, group.sortOrder());
            statement.setString(4, group.color().name());
            statement.executeUpdate();
        }
    }

    private static void saveVariableGroup(
            java.sql.Connection connection, SnippetVariableGroup group) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO snippet_variable_groups(id, name, sort_order, icon_color) VALUES(?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name, sort_order=excluded.sort_order,
                    icon_color=excluded.icon_color
                """)) {
            statement.setString(1, group.id().toString());
            statement.setString(2, group.name());
            statement.setInt(3, group.sortOrder());
            statement.setString(4, group.color().name());
            statement.executeUpdate();
        }
    }

    private static java.sql.PreparedStatement variableUpsert(java.sql.Connection connection) throws SQLException {
        return connection.prepareStatement("""
                INSERT INTO snippet_variables(
                    id, group_id, name, variable_name, value_type, delimiter, value, description,
                    sort_order, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET group_id=excluded.group_id, name=excluded.name,
                    variable_name=excluded.variable_name, value_type=excluded.value_type,
                    delimiter=excluded.delimiter, value=excluded.value, description=excluded.description,
                    sort_order=excluded.sort_order, updated_at=excluded.updated_at
                """);
    }

    private static void bindVariable(
            java.sql.PreparedStatement statement, SnippetVariable checked) throws SQLException {
        statement.setString(1, checked.id().toString());
        if (checked.groupId().isPresent()) statement.setString(2, checked.groupId().get().toString());
        else statement.setNull(2, java.sql.Types.VARCHAR);
        statement.setString(3, checked.name());
        statement.setString(4, checked.variableName());
        statement.setString(5, checked.type().name());
        statement.setString(6, checked.delimiter());
        statement.setString(7, checked.value());
        statement.setString(8, checked.description());
        statement.setInt(9, checked.sortOrder());
        statement.setLong(10, checked.createdAt().toEpochMilli());
        statement.setLong(11, checked.updatedAt().toEpochMilli());
    }

    @Override
    public void deleteVariable(SnippetVariableId id) throws IOException {
        Objects.requireNonNull(id, "id");
        update(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM snippet_variables WHERE id=?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
            }
        });
    }

    private static Snippet snippet(ResultSet result) throws SQLException {
        String group = result.getString("group_id");
        return new Snippet(
                SnippetId.parse(result.getString("id")),
                group == null ? Optional.empty() : Optional.of(SnippetGroupId.parse(group)),
                result.getString("name"), result.getString("description"), result.getString("content"),
                result.getInt("sort_order"),
                Instant.ofEpochMilli(result.getLong("created_at")), Instant.ofEpochMilli(result.getLong("updated_at")));
    }

    private static SnippetVariable variable(ResultSet result) throws SQLException {
        String group = result.getString("group_id");
        return new SnippetVariable(
                SnippetVariableId.parse(result.getString("id")),
                group == null ? Optional.empty() : Optional.of(SnippetVariableGroupId.parse(group)),
                result.getString("name"), result.getString("variable_name"),
                SnippetVariableType.valueOf(result.getString("value_type")),
                result.getString("delimiter"), result.getString("value"), result.getString("description"),
                result.getInt("sort_order"), Instant.ofEpochMilli(result.getLong("created_at")),
                Instant.ofEpochMilli(result.getLong("updated_at")));
    }

    private <T> T query(FastRemoteDatabase.SqlFunction<T> operation) throws IOException {
        try {
            return database.query(operation);
        } catch (SQLException failure) {
            throw new IOException("命令数据库读取失败", failure);
        }
    }

    private void update(SqlConsumer operation) throws IOException {
        try {
            database.transaction(connection -> {
                operation.accept(connection);
                return null;
            });
        } catch (SQLException failure) {
            throw new IOException("命令数据库写入失败", failure);
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(java.sql.Connection connection) throws SQLException;
    }
}
