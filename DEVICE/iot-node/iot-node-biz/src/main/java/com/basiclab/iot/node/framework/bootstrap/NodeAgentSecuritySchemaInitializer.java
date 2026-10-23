package com.basiclab.iot.node.framework.bootstrap;

import com.basiclab.iot.node.service.NodeAgentCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 为既有 iot-node 数据库幂等补齐 Agent 指令表与令牌存储列，并把明文令牌迁移为哈希 + 密文。
 * 全新安装由 .scripts/postgresql/iot-node10.sql 建好同样的结构。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeAgentSecuritySchemaInitializer {

    static final String COMMAND_SCHEMA_RESOURCE = "sql/node_agent_command_v1.sql";

    static final List<String> COMPUTE_NODE_COLUMNS = List.of(
            "agent_token_hash VARCHAR(160)",
            "agent_token_cipher VARCHAR(256)",
            "agent_token_prev_hash VARCHAR(160)",
            "agent_token_prev_cipher VARCHAR(256)",
            "agent_token_prev_expires_at TIMESTAMP",
            "agent_token_pending BOOLEAN NOT NULL DEFAULT FALSE",
            "agent_token_generation INTEGER NOT NULL DEFAULT 0",
            "agent_features JSONB");

    private final JdbcTemplate jdbcTemplate;
    private final NodeAgentCredentialService nodeAgentCredentialService;

    @PostConstruct
    public void initialize() {
        try {
            for (String statement : commandSchemaStatements()) {
                jdbcTemplate.execute(statement);
            }
            for (String column : COMPUTE_NODE_COLUMNS) {
                jdbcTemplate.execute("ALTER TABLE compute_node ADD COLUMN IF NOT EXISTS " + column);
            }
        } catch (Exception e) {
            // 与录像字段初始化一致：启动阶段保留可诊断性，后续访问新字段会明确暴露权限/迁移问题
            log.error("初始化 Agent 指令表/令牌字段失败: {}", e.getMessage(), e);
            return;
        }
        try {
            nodeAgentCredentialService.migrateLegacyTokens();
        } catch (Exception e) {
            // 迁移失败的行在校验时仍按旧明文兼容，并在 Agent 下次请求时按需迁移
            log.error("迁移 Agent 明文令牌失败: {}", e.getMessage(), e);
        }
    }

    static List<String> commandSchemaStatements() throws IOException {
        String sql;
        try (InputStream in = new ClassPathResource(COMMAND_SCHEMA_RESOURCE).getInputStream()) {
            sql = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
        List<String> statements = new ArrayList<>();
        for (String part : sql.split(";")) {
            if (!part.isBlank()) {
                statements.add(part.trim());
            }
        }
        return statements;
    }
}
