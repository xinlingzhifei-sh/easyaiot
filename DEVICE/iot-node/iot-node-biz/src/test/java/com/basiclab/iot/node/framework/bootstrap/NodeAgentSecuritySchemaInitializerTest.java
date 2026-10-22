package com.basiclab.iot.node.framework.bootstrap;

import com.basiclab.iot.node.service.NodeAgentCredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NodeAgentSecuritySchemaInitializerTest {

    @Test
    void createsCommandTableAddsTokenColumnsThenMigrates() {
        RecordingJdbc jdbc = new RecordingJdbc(null);
        NodeAgentCredentialService credentials = mock(NodeAgentCredentialService.class);

        new NodeAgentSecuritySchemaInitializer(jdbc, credentials).initialize();

        assertTrue(jdbc.executed.get(0).startsWith("CREATE TABLE IF NOT EXISTS node_agent_command"));
        assertTrue(jdbc.executed.stream().anyMatch(sql -> sql.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS")));
        for (String column : NodeAgentSecuritySchemaInitializer.COMPUTE_NODE_COLUMNS) {
            assertTrue(jdbc.executed.contains("ALTER TABLE compute_node ADD COLUMN IF NOT EXISTS " + column), column);
        }
        verify(credentials).migrateLegacyTokens();
    }

    @Test
    void skipsMigrationWhenSchemaCannotBeUpgraded() {
        RecordingJdbc jdbc = new RecordingJdbc("permission denied");
        NodeAgentCredentialService credentials = mock(NodeAgentCredentialService.class);

        new NodeAgentSecuritySchemaInitializer(jdbc, credentials).initialize();

        verify(credentials, never()).migrateLegacyTokens();
    }

    @Test
    void migrationFailureDoesNotBreakStartup() {
        NodeAgentCredentialService credentials = mock(NodeAgentCredentialService.class);
        org.mockito.Mockito.when(credentials.migrateLegacyTokens()).thenThrow(new IllegalStateException("boom"));

        new NodeAgentSecuritySchemaInitializer(new RecordingJdbc(null), credentials).initialize();

        verify(credentials).migrateLegacyTokens();
    }

    @Test
    void freshInstallSqlDeclaresTheSameColumnsAndCommandTable() throws Exception {
        Path sql = locateRepoFile(".scripts/postgresql/iot-node10.sql");
        String content = Files.readString(sql, StandardCharsets.UTF_8);

        assertTrue(content.contains("CREATE TABLE public.node_agent_command ("));
        assertTrue(content.contains("uk_node_agent_command_active_key"));
        for (String column : NodeAgentSecuritySchemaInitializer.COMPUTE_NODE_COLUMNS) {
            String name = column.substring(0, column.indexOf(' '));
            assertTrue(content.contains("    " + name + " "), name);
        }
        assertEquals(1, content.split("CREATE TABLE public.compute_node \\(", -1).length - 1);
    }

    private static Path locateRepoFile(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repository file not found: " + relative);
    }

    private static final class RecordingJdbc extends JdbcTemplate {
        private final List<String> executed = new ArrayList<>();
        private final String failure;

        private RecordingJdbc(String failure) {
            this.failure = failure;
        }

        @Override
        public void execute(String sql) {
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
            executed.add(sql);
        }
    }
}
