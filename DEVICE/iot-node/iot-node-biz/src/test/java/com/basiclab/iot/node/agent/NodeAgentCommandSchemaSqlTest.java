package com.basiclab.iot.node.agent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeAgentCommandSchemaSqlTest {

    private static final String SCHEMA_RESOURCE = "sql/node_agent_command_v1.sql";

    @Test
    void schemaCreatesAgentCommandTableAndIndexes() throws IOException {
        String sql = readSchemaSql();

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS node_agent_command"));
        assertTrue(sql.contains("node_id BIGINT NOT NULL"));
        assertTrue(sql.contains("command_type VARCHAR(64) NOT NULL"));
        assertTrue(sql.contains("command_key VARCHAR(160) NOT NULL"));
        assertTrue(sql.contains("payload_json TEXT NOT NULL"));
        assertTrue(sql.contains("status VARCHAR(32) NOT NULL DEFAULT 'pending'"));
        assertTrue(sql.contains("lease_until TIMESTAMP"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_node_agent_command_active_key"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_node_agent_command_poll"));
    }

    @Test
    void schemaIsAdditiveOnly() throws IOException {
        String lowerSql = readSchemaSql().toLowerCase();

        assertFalse(lowerSql.contains("drop table "));
        assertFalse(lowerSql.contains("truncate table "));
        assertFalse(lowerSql.contains("delete from "));
        assertFalse(lowerSql.contains("update "));
    }

    private static String readSchemaSql() throws IOException {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(SCHEMA_RESOURCE);
        assertNotNull(inputStream, SCHEMA_RESOURCE + " should exist on the classpath");
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
