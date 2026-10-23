package com.basiclab.iot.node.service.impl;

import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.domain.vo.NodeAgentTokenRotateRespVO;
import com.basiclab.iot.node.security.AgentTokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.basiclab.iot.node.enums.ErrorCodeConstants.AGENT_TOKEN_INVALID;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.COMPUTE_NODE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeAgentCredentialServiceImplTest {

    private static final long NODE_ID = 9L;
    private static final String OLD_TOKEN = "11111111111111111111111111111111";

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 22, 12, 0);
    private ComputeNodeDO node;
    private List<Object[]> credentialUpdates;
    private int updateResult;
    private NodeAgentCredentialServiceImpl service;

    @BeforeEach
    void setUp() {
        node = new ComputeNodeDO();
        node.setId(NODE_ID);
        credentialUpdates = new ArrayList<>();
        updateResult = 1;
        service = AgentCredentialTestSupport.credentialService(mapper());
        service.clock = () -> now;
    }

    private ComputeNodeMapper mapper() {
        return (ComputeNodeMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ComputeNodeMapper.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "selectById":
                            return node != null && node.getId().equals(args[0]) ? node : null;
                        case "updateAgentCredential":
                            credentialUpdates.add(args);
                            return updateResult;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private ComputeNodeDO lastState() {
        return (ComputeNodeDO) credentialUpdates.get(credentialUpdates.size() - 1)[1];
    }

    private String lastExpectedHash() {
        return (String) credentialUpdates.get(credentialUpdates.size() - 1)[2];
    }

    @Test
    void issueStoresOnlyHashAndCipher() {
        String token = service.issue(node);

        assertEquals(32, token.length());
        assertNull(node.getAgentToken(), "配置了密钥时不落明文");
        assertTrue(AgentTokenHasher.matches(node.getAgentTokenHash(), token));
        assertTrue(node.getAgentTokenCipher().startsWith("gcm1$"));
        assertEquals(token, service.currentToken(node));
        assertEquals(token, service.dispatchToken(node));
        assertNull(service.previousToken(node));
    }

    @Test
    void issueWithoutEncryptionKeyKeepsPlaintextCompatibilityMode() {
        service = AgentCredentialTestSupport.credentialService(mapper(), null, "", false);
        String token = service.issue(node);

        assertEquals(token, node.getAgentToken());
        assertEquals("plain$" + token, node.getAgentTokenCipher());
        assertTrue(AgentTokenHasher.matches(node.getAgentTokenHash(), token));
        assertEquals(token, service.currentToken(node));
    }

    @Test
    void authenticateAcceptsCurrentTokenAndRejectsOthers() {
        String token = service.issue(node);

        assertSame(node, service.authenticate(NODE_ID, token));
        ServiceException wrong = assertThrows(ServiceException.class, () -> service.authenticate(NODE_ID, OLD_TOKEN));
        assertEquals(AGENT_TOKEN_INVALID.getCode(), wrong.getCode());
        assertEquals(AGENT_TOKEN_INVALID.getCode(),
                assertThrows(ServiceException.class, () -> service.authenticate(NODE_ID, "")).getCode());
        assertEquals(COMPUTE_NODE_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> service.authenticate(404L, token)).getCode());
        assertTrue(credentialUpdates.isEmpty(), "未轮换时校验不写库");
    }

    @Test
    void authenticateFallsBackToLegacyPlaintextAndMigratesTheRow() {
        node.setAgentToken(OLD_TOKEN);

        service.authenticate(NODE_ID, OLD_TOKEN);

        assertEquals(1, credentialUpdates.size());
        assertNull(lastExpectedHash());
        assertNull(lastState().getAgentToken(), "迁移后清除明文");
        assertTrue(AgentTokenHasher.matches(lastState().getAgentTokenHash(), OLD_TOKEN));
        assertEquals(OLD_TOKEN, service.currentToken(node));
        assertThrows(ServiceException.class, () -> service.authenticate(NODE_ID, "deadbeef"));
    }

    @Test
    void rotationKeepsOldTokenValidDuringGraceAndDispatchesOldUntilAgentSwitches() {
        AgentCredentialTestSupport.storeToken(service, node, OLD_TOKEN);
        String oldHash = node.getAgentTokenHash();

        NodeAgentTokenRotateRespVO resp = service.rotate(node, false);

        assertEquals(oldHash, lastExpectedHash(), "乐观条件：期望库中仍是旧哈希");
        assertEquals(1, resp.getGeneration());
        assertFalse(resp.getPushed());
        assertEquals(now.plusSeconds(259_200), resp.getPreviousValidUntil());
        assertTrue(node.getAgentTokenPending());
        assertEquals(oldHash, node.getAgentTokenPrevHash());
        // 宽限期内新旧令牌都能通过
        service.authenticate(NODE_ID, OLD_TOKEN);
        assertEquals(1, credentialUpdates.size(), "用旧令牌回连不算确认");
        assertEquals(OLD_TOKEN, service.dispatchToken(node), "Agent 尚未切换，9100 仍用旧令牌");
        assertEquals(resp.getAgentToken(), service.currentToken(node));
        assertEquals(OLD_TOKEN, service.previousToken(node));

        // Agent 用新令牌回连：确认切换，旧令牌宽限缩短到 5 分钟
        service.authenticate(NODE_ID, resp.getAgentToken());
        assertEquals(2, credentialUpdates.size());
        assertFalse(node.getAgentTokenPending());
        assertEquals(now.plusSeconds(300), node.getAgentTokenPrevExpiresAt());
        assertEquals(resp.getAgentToken(), service.dispatchToken(node));

        // 宽限期结束后旧令牌失效
        service.clock = () -> now.plusSeconds(301);
        assertThrows(ServiceException.class, () -> service.authenticate(NODE_ID, OLD_TOKEN));
        service.authenticate(NODE_ID, resp.getAgentToken());
    }

    @Test
    void rotatingAgainBeforeAgentSwitchedKeepsTheTokenTheAgentActuallyHolds() {
        AgentCredentialTestSupport.storeToken(service, node, OLD_TOKEN);
        String oldHash = node.getAgentTokenHash();
        NodeAgentTokenRotateRespVO first = service.rotate(node, false);

        service.clock = () -> now.plusHours(1);
        NodeAgentTokenRotateRespVO second = service.rotate(node, false);

        assertEquals(2, second.getGeneration());
        assertEquals(oldHash, node.getAgentTokenPrevHash(), "Agent 仍持有最初的令牌");
        assertEquals(now.plusHours(1).plusSeconds(259_200), node.getAgentTokenPrevExpiresAt(), "宽限期顺延");
        service.authenticate(NODE_ID, OLD_TOKEN);
        assertThrows(ServiceException.class, () -> service.authenticate(NODE_ID, first.getAgentToken()));
        service.authenticate(NODE_ID, second.getAgentToken());
    }

    @Test
    void immediateRotationRevokesOldTokenAtOnce() {
        AgentCredentialTestSupport.storeToken(service, node, OLD_TOKEN);

        NodeAgentTokenRotateRespVO resp = service.rotate(node, true);

        assertNull(resp.getPreviousValidUntil());
        assertFalse(node.getAgentTokenPending());
        assertThrows(ServiceException.class, () -> service.authenticate(NODE_ID, OLD_TOKEN));
        service.authenticate(NODE_ID, resp.getAgentToken());
        assertEquals(resp.getAgentToken(), service.dispatchToken(node));
    }

    @Test
    void rotationFailsWhenConcurrentlyModified() {
        AgentCredentialTestSupport.storeToken(service, node, OLD_TOKEN);
        updateResult = 0;

        assertThrows(IllegalStateException.class, () -> service.rotate(node, false));
        assertEquals(OLD_TOKEN, service.currentToken(node), "失败时内存中的节点保持原状");
    }

    @Test
    void revertRestoresPreviousTokenOnlyForTheCurrentPendingGeneration() {
        AgentCredentialTestSupport.storeToken(service, node, OLD_TOKEN);
        String oldHash = node.getAgentTokenHash();
        String oldCipher = node.getAgentTokenCipher();
        NodeAgentTokenRotateRespVO resp = service.rotate(node, false);

        assertFalse(service.revertRotation(NODE_ID, resp.getGeneration() - 1), "过期的代次不回滚");
        assertTrue(service.revertRotation(NODE_ID, resp.getGeneration()));

        ComputeNodeDO state = lastState();
        assertEquals(oldHash, state.getAgentTokenHash());
        assertEquals(oldCipher, state.getAgentTokenCipher());
        assertNull(state.getAgentTokenPrevHash());
        assertFalse(state.getAgentTokenPending());
        assertEquals(resp.getGeneration() + 1, state.getAgentTokenGeneration());
    }

    @Test
    void revertIsSkippedOnceAgentConfirmedNewToken() {
        AgentCredentialTestSupport.storeToken(service, node, OLD_TOKEN);
        NodeAgentTokenRotateRespVO resp = service.rotate(node, false);
        service.authenticate(NODE_ID, resp.getAgentToken());

        assertFalse(service.revertRotation(NODE_ID, resp.getGeneration()));
    }

    @Test
    void planMigrationHashesLegacyPlaintextAndClearsIt() {
        ComputeNodeDO row = row(OLD_TOKEN, null, null);

        ComputeNodeDO target = service.planMigration(row);

        assertNull(target.getAgentToken());
        assertTrue(AgentTokenHasher.matches(target.getAgentTokenHash(), OLD_TOKEN));
        node = target;
        assertEquals(OLD_TOKEN, service.currentToken(target));
        assertNull(service.planMigration(withId(target)), "已迁移的行不再改动");
    }

    @Test
    void planMigrationWithoutKeyHashesButKeepsPlaintextThenEncryptsOnceKeyIsConfigured() {
        NodeAgentCredentialServiceImpl noKey = AgentCredentialTestSupport.credentialService(mapper(), null, "", false);
        ComputeNodeDO hashedOnly = noKey.planMigration(row(OLD_TOKEN, null, null));
        assertEquals(OLD_TOKEN, hashedOnly.getAgentToken());
        assertEquals("plain$" + OLD_TOKEN, hashedOnly.getAgentTokenCipher());
        assertNull(noKey.planMigration(withId(hashedOnly)), "无密钥时重复启动不改动");

        ComputeNodeDO encrypted = service.planMigration(withId(hashedOnly));
        assertNull(encrypted.getAgentToken());
        assertTrue(encrypted.getAgentTokenCipher().startsWith("gcm1$"));
        assertEquals(hashedOnly.getAgentTokenHash(), encrypted.getAgentTokenHash(), "哈希不变，节点无感知");
        assertEquals(OLD_TOKEN, service.currentToken(encrypted));
    }

    @Test
    void planMigrationTreatsPlaintextWrittenByOldVersionAsCurrentToken() {
        // 滚动升级时旧版 iot-node 重置了令牌：只改了明文列，哈希还是更早的令牌
        ComputeNodeDO row = row("22222222222222222222222222222222", AgentTokenHasher.hash(OLD_TOKEN),
                service.seal(OLD_TOKEN));

        ComputeNodeDO target = service.planMigration(row);

        assertTrue(AgentTokenHasher.matches(target.getAgentTokenHash(), "22222222222222222222222222222222"));
        assertEquals("22222222222222222222222222222222", service.currentToken(target));
    }

    @Test
    void retainPlaintextBackfillsLegacyColumnForDowngrade() {
        NodeAgentCredentialServiceImpl retain =
                AgentCredentialTestSupport.credentialService(mapper(), null,
                        AgentCredentialTestSupport.TEST_ENCRYPTION_KEY, true);
        ComputeNodeDO migrated = service.planMigration(row(OLD_TOKEN, null, null));

        ComputeNodeDO target = retain.planMigration(withId(migrated));

        assertEquals(OLD_TOKEN, target.getAgentToken());
        assertEquals(migrated.getAgentTokenCipher(), target.getAgentTokenCipher());
    }

    @Test
    void migrateLegacyTokensUsesConditionalUpdatePerRow() {
        ComputeNodeDO legacy = row(OLD_TOKEN, null, null);
        ComputeNodeDO done = service.planMigration(row(OLD_TOKEN, null, null));
        done.setId(2L);
        FakeJdbc jdbc = new FakeJdbc(List.of(legacy, done));
        service = AgentCredentialTestSupport.credentialService(mapper(), jdbc,
                AgentCredentialTestSupport.TEST_ENCRYPTION_KEY, false);

        assertEquals(1, service.migrateLegacyTokens());

        assertTrue(jdbc.querySql.contains("agent_token IS NOT NULL"));
        assertEquals(1, jdbc.updates.size());
        assertTrue(jdbc.updateSql.contains("agent_token IS NOT DISTINCT FROM ?"));
        Object[] params = jdbc.updates.get(0);
        assertNull(params[0], "明文列置空");
        assertTrue(AgentTokenHasher.matches((String) params[1], OLD_TOKEN));
        assertNotEquals(OLD_TOKEN, params[2]);
        assertEquals(NODE_ID, params[7]);
        assertEquals(OLD_TOKEN, params[8], "条件：明文列仍是读到的值");
        assertNull(params[9], "条件：哈希列仍为空");
    }

    @Test
    void migrateSkipsRowsThatCannotBeDecrypted() {
        ComputeNodeDO migrated = service.planMigration(row(OLD_TOKEN, null, null));
        FakeJdbc jdbc = new FakeJdbc(List.of(withId(migrated)));
        NodeAgentCredentialServiceImpl other = AgentCredentialTestSupport.credentialService(mapper(), jdbc,
                "another-key", true);

        assertEquals(0, other.migrateLegacyTokens());
        assertTrue(jdbc.updates.isEmpty());
        assertTrue(jdbc.querySql.contains("agent_token_cipher IS NOT NULL"), "retain-plaintext 时也扫描需回填的行");
    }

    /** 只实现迁移用到的两个方法 */
    private static final class FakeJdbc extends JdbcTemplate {
        private final List<ComputeNodeDO> rows;
        private final List<Object[]> updates = new ArrayList<>();
        private String querySql;
        private String updateSql;

        private FakeJdbc(List<ComputeNodeDO> rows) {
            this.rows = rows;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            querySql = sql;
            return (List<T>) rows;
        }

        @Override
        public int update(String sql, Object... args) {
            updateSql = sql;
            updates.add(args);
            return 1;
        }
    }

    private static ComputeNodeDO row(String plaintext, String hash, String cipher) {
        ComputeNodeDO row = new ComputeNodeDO();
        row.setId(NODE_ID);
        row.setAgentToken(plaintext);
        row.setAgentTokenHash(hash);
        row.setAgentTokenCipher(cipher);
        row.setAgentTokenPending(false);
        row.setAgentTokenGeneration(0);
        return row;
    }

    private static ComputeNodeDO withId(ComputeNodeDO state) {
        state.setId(NODE_ID);
        return state;
    }
}
