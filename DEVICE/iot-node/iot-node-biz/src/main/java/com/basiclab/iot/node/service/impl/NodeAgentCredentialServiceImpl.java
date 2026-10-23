package com.basiclab.iot.node.service.impl;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.domain.vo.NodeAgentTokenRotateRespVO;
import com.basiclab.iot.node.security.AgentTokenCipher;
import com.basiclab.iot.node.security.AgentTokenHasher;
import com.basiclab.iot.node.security.NodeTokenGenerator;
import com.basiclab.iot.node.security.NodeTokenVerifier;
import com.basiclab.iot.node.service.NodeAgentCredentialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.AGENT_TOKEN_INVALID;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.COMPUTE_NODE_NOT_EXISTS;

@Service
@Slf4j
public class NodeAgentCredentialServiceImpl implements NodeAgentCredentialService {

    /** 未配置加密密钥时密文列退化为带此前缀的明文，配置密钥后由迁移器重新加密 */
    static final String PLAIN_PREFIX = "plain$";

    enum Match { CURRENT, PREVIOUS, LEGACY_PLAINTEXT, NONE }

    @Resource
    private ComputeNodeMapper computeNodeMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 专用密钥；未配置时回退到 SSH 凭据加密已在使用的 mybatis-plus.encryptor.password */
    @Value("${easyaiot.node.agent-token.encryption-key:}")
    private String encryptionKey;
    @Value("${mybatis-plus.encryptor.password:}")
    private String encryptorPassword;
    /** 为降级回旧版 iot-node 准备：同时保留（并回填）明文列 */
    @Value("${easyaiot.node.agent-token.retain-plaintext:false}")
    private boolean retainPlaintext;
    @Value("${easyaiot.node.agent-token.rotation-grace-seconds:259200}")
    private long rotationGraceSeconds;
    @Value("${easyaiot.node.agent-token.confirmed-grace-seconds:300}")
    private long confirmedGraceSeconds;

    Supplier<LocalDateTime> clock = LocalDateTime::now;

    private AgentTokenCipher cipher;

    @PostConstruct
    public void init() {
        cipher = AgentTokenCipher.fromSecret(StrUtil.isNotBlank(encryptionKey) ? encryptionKey : encryptorPassword);
        if (cipher == null) {
            log.error("未配置 easyaiot.node.agent-token.encryption-key 或 MYBATIS_ENCRYPTOR_PASSWORD，"
                    + "Agent 令牌校验已改用哈希，但明文仍保留在库中；配置后重启即自动加密并清除明文");
        }
    }

    @Override
    public String issue(ComputeNodeDO node) {
        String token = NodeTokenGenerator.newAgentToken();
        copyCredential(freshState(token, 0), node);
        return token;
    }

    @Override
    public ComputeNodeDO authenticate(Long nodeId, String agentToken) {
        ComputeNodeDO node = nodeId != null ? computeNodeMapper.selectById(nodeId) : null;
        if (node == null) {
            throw exception(COMPUTE_NODE_NOT_EXISTS);
        }
        Match match = match(node, agentToken);
        if (match == Match.NONE) {
            throw exception(AGENT_TOKEN_INVALID);
        }
        if (match == Match.CURRENT && Boolean.TRUE.equals(node.getAgentTokenPending())) {
            confirmRotation(node);
        } else if (match == Match.LEGACY_PLAINTEXT) {
            migrateNodeQuietly(node, agentToken);
        }
        return node;
    }

    Match match(ComputeNodeDO node, String token) {
        if (StrUtil.isBlank(token)) {
            return Match.NONE;
        }
        if (AgentTokenHasher.matches(node.getAgentTokenHash(), token)) {
            return Match.CURRENT;
        }
        if (isPreviousValid(node) && AgentTokenHasher.matches(node.getAgentTokenPrevHash(), token)) {
            return Match.PREVIOUS;
        }
        // 尚未迁移的行，或滚动升级期间旧版 iot-node 刚写入的明文
        if (StrUtil.isNotBlank(node.getAgentToken()) && NodeTokenVerifier.matches(node.getAgentToken(), token)) {
            return Match.LEGACY_PLAINTEXT;
        }
        return Match.NONE;
    }

    @Override
    public String currentToken(ComputeNodeDO node) {
        if (node == null) {
            return null;
        }
        String unsealed = unsealQuietly(node.getAgentTokenCipher(), node.getId());
        if (unsealed != null) {
            return unsealed;
        }
        return StrUtil.blankToDefault(node.getAgentToken(), null);
    }

    @Override
    public String previousToken(ComputeNodeDO node) {
        if (node == null || !isPreviousValid(node)) {
            return null;
        }
        return unsealQuietly(node.getAgentTokenPrevCipher(), node.getId());
    }

    @Override
    public String dispatchToken(ComputeNodeDO node) {
        if (node != null && Boolean.TRUE.equals(node.getAgentTokenPending())) {
            String previous = previousToken(node);
            if (previous != null) {
                return previous;
            }
        }
        return currentToken(node);
    }

    @Override
    public NodeAgentTokenRotateRespVO rotate(ComputeNodeDO node, boolean immediate) {
        String token = NodeTokenGenerator.newAgentToken();
        ComputeNodeDO state = freshState(token, generationOf(node) + 1);
        if (!immediate) {
            if (Boolean.TRUE.equals(node.getAgentTokenPending()) && isPreviousValid(node)) {
                // Agent 还没切到上一轮的新令牌：它手里的仍是 prev，保留 prev 并顺延
                state.setAgentTokenPrevHash(node.getAgentTokenPrevHash());
                state.setAgentTokenPrevCipher(node.getAgentTokenPrevCipher());
            } else {
                String current = currentToken(node);
                if (StrUtil.isNotBlank(node.getAgentTokenHash()) && StrUtil.isNotBlank(node.getAgentTokenCipher())) {
                    state.setAgentTokenPrevHash(node.getAgentTokenHash());
                    state.setAgentTokenPrevCipher(node.getAgentTokenCipher());
                } else if (current != null) {
                    state.setAgentTokenPrevHash(AgentTokenHasher.hash(current));
                    state.setAgentTokenPrevCipher(seal(current));
                }
            }
            if (state.getAgentTokenPrevHash() != null) {
                state.setAgentTokenPrevExpiresAt(clock.get().plusSeconds(Math.max(0, rotationGraceSeconds)));
                state.setAgentTokenPending(true);
            }
        }
        if (computeNodeMapper.updateAgentCredential(node.getId(), state, node.getAgentTokenHash()) == 0) {
            throw new IllegalStateException("节点 " + node.getId() + " 的 Agent 令牌正在被并发修改，请重试");
        }
        copyCredential(state, node);
        NodeAgentTokenRotateRespVO resp = new NodeAgentTokenRotateRespVO();
        resp.setAgentToken(token);
        resp.setGeneration(state.getAgentTokenGeneration());
        resp.setPushed(false);
        resp.setPreviousValidUntil(state.getAgentTokenPrevExpiresAt());
        log.info("节点 Agent 令牌已轮换 nodeId={} generation={} previousValidUntil={}",
                node.getId(), state.getAgentTokenGeneration(), state.getAgentTokenPrevExpiresAt());
        return resp;
    }

    @Override
    public boolean revertRotation(Long nodeId, int generation) {
        ComputeNodeDO node = computeNodeMapper.selectById(nodeId);
        if (node == null || generationOf(node) != generation
                || !Boolean.TRUE.equals(node.getAgentTokenPending())
                || StrUtil.isBlank(node.getAgentTokenPrevHash())) {
            return false;
        }
        ComputeNodeDO state = new ComputeNodeDO();
        state.setAgentTokenHash(node.getAgentTokenPrevHash());
        state.setAgentTokenCipher(node.getAgentTokenPrevCipher());
        state.setAgentToken(keepPlaintext() ? unsealQuietly(node.getAgentTokenPrevCipher(), nodeId) : null);
        state.setAgentTokenPending(false);
        state.setAgentTokenGeneration(generation + 1);
        if (computeNodeMapper.updateAgentCredential(nodeId, state, node.getAgentTokenHash()) == 0) {
            return false;
        }
        log.warn("节点 Agent 未能应用新令牌，控制面已回滚到旧令牌 nodeId={} generation={}", nodeId, generation);
        return true;
    }

    @Override
    public int migrateLegacyTokens() {
        StringBuilder where = new StringBuilder("agent_token IS NOT NULL");
        where.append(" OR agent_token_cipher LIKE 'plain$%' OR agent_token_prev_cipher LIKE 'plain$%'");
        if (retainPlaintext) {
            where.append(" OR (agent_token IS NULL AND agent_token_cipher IS NOT NULL)");
        }
        List<ComputeNodeDO> rows = jdbcTemplate.query(
                "SELECT id, agent_token, agent_token_hash, agent_token_cipher, agent_token_prev_hash, "
                        + "agent_token_prev_cipher, agent_token_prev_expires_at, agent_token_pending, "
                        + "agent_token_generation FROM compute_node WHERE " + where,
                (rs, rowNum) -> {
                    ComputeNodeDO row = new ComputeNodeDO();
                    row.setId(rs.getLong("id"));
                    row.setAgentToken(rs.getString("agent_token"));
                    row.setAgentTokenHash(rs.getString("agent_token_hash"));
                    row.setAgentTokenCipher(rs.getString("agent_token_cipher"));
                    row.setAgentTokenPrevHash(rs.getString("agent_token_prev_hash"));
                    row.setAgentTokenPrevCipher(rs.getString("agent_token_prev_cipher"));
                    Timestamp expires = rs.getTimestamp("agent_token_prev_expires_at");
                    row.setAgentTokenPrevExpiresAt(expires != null ? expires.toLocalDateTime() : null);
                    row.setAgentTokenPending(rs.getBoolean("agent_token_pending"));
                    row.setAgentTokenGeneration(rs.getInt("agent_token_generation"));
                    return row;
                });
        int migrated = 0;
        for (ComputeNodeDO row : rows) {
            ComputeNodeDO target;
            try {
                target = planMigration(row);
            } catch (RuntimeException e) {
                log.error("Agent 令牌迁移跳过 nodeId={}: {}", row.getId(), e.getMessage());
                continue;
            }
            if (target == null) {
                continue;
            }
            int updated = jdbcTemplate.update(
                    "UPDATE compute_node SET agent_token = ?, agent_token_hash = ?, agent_token_cipher = ?, "
                            + "agent_token_prev_hash = ?, agent_token_prev_cipher = ?, agent_token_prev_expires_at = ?, "
                            + "agent_token_pending = ? "
                            + "WHERE id = ? AND agent_token IS NOT DISTINCT FROM ? "
                            + "AND agent_token_hash IS NOT DISTINCT FROM ?",
                    target.getAgentToken(), target.getAgentTokenHash(), target.getAgentTokenCipher(),
                    target.getAgentTokenPrevHash(), target.getAgentTokenPrevCipher(),
                    target.getAgentTokenPrevExpiresAt(), Boolean.TRUE.equals(target.getAgentTokenPending()),
                    row.getId(), row.getAgentToken(), row.getAgentTokenHash());
            // 0 行：另一个副本已处理，或期间被轮换，下次启动再看
            migrated += updated;
        }
        if (migrated > 0) {
            log.info("已迁移 {} 个节点的 Agent 令牌存储（哈希 + {}）", migrated,
                    cipher != null ? "密文" : "明文兼容模式");
        }
        return migrated;
    }

    /**
     * 计算一行的迁移目标状态；无需改动时返回 null。纯函数，不访问数据库。
     */
    ComputeNodeDO planMigration(ComputeNodeDO row) {
        String plain = StrUtil.blankToDefault(row.getAgentToken(), null);
        ComputeNodeDO target = new ComputeNodeDO();
        copyCredential(row, target);
        if (plain != null && !AgentTokenHasher.matches(row.getAgentTokenHash(), plain)) {
            // 从未迁移，或旧版 iot-node 重置后只写了明文：明文即当前令牌
            ComputeNodeDO fresh = freshState(plain, generationOf(row));
            copyCredential(fresh, target);
            return target;
        }
        String current = plain != null ? plain : unsealQuietly(row.getAgentTokenCipher(), row.getId());
        if (cipher != null && current != null && !isSealedByCipher(row.getAgentTokenCipher())) {
            target.setAgentTokenCipher(cipher.encrypt(current));
        }
        String previous = unsealQuietly(row.getAgentTokenPrevCipher(), row.getId());
        if (cipher != null && previous != null && !isSealedByCipher(row.getAgentTokenPrevCipher())) {
            target.setAgentTokenPrevCipher(cipher.encrypt(previous));
        }
        target.setAgentToken(keepPlaintext() ? current : null);
        boolean changed = !Objects.equals(target.getAgentToken(), row.getAgentToken())
                || !Objects.equals(target.getAgentTokenCipher(), row.getAgentTokenCipher())
                || !Objects.equals(target.getAgentTokenPrevCipher(), row.getAgentTokenPrevCipher());
        return changed ? target : null;
    }

    // ---------------------------------------------------------------- 内部

    private void confirmRotation(ComputeNodeDO node) {
        ComputeNodeDO state = new ComputeNodeDO();
        copyCredential(node, state);
        state.setAgentTokenPending(false);
        LocalDateTime cap = clock.get().plusSeconds(Math.max(0, confirmedGraceSeconds));
        if (state.getAgentTokenPrevHash() != null
                && (state.getAgentTokenPrevExpiresAt() == null || state.getAgentTokenPrevExpiresAt().isAfter(cap))) {
            state.setAgentTokenPrevExpiresAt(cap);
        }
        if (computeNodeMapper.updateAgentCredential(node.getId(), state, node.getAgentTokenHash()) > 0) {
            copyCredential(state, node);
            log.info("节点 Agent 已切换到新令牌 nodeId={} generation={}", node.getId(), node.getAgentTokenGeneration());
        }
    }

    private void migrateNodeQuietly(ComputeNodeDO node, String token) {
        try {
            ComputeNodeDO state = freshState(token, generationOf(node));
            if (computeNodeMapper.updateAgentCredential(node.getId(), state, node.getAgentTokenHash()) > 0) {
                copyCredential(state, node);
            }
        } catch (RuntimeException e) {
            log.warn("Agent 令牌按需迁移失败 nodeId={}: {}", node.getId(), e.getMessage());
        }
    }

    ComputeNodeDO freshState(String token, int generation) {
        ComputeNodeDO state = new ComputeNodeDO();
        state.setAgentTokenHash(AgentTokenHasher.hash(token));
        state.setAgentTokenCipher(seal(token));
        state.setAgentToken(keepPlaintext() ? token : null);
        state.setAgentTokenPrevHash(null);
        state.setAgentTokenPrevCipher(null);
        state.setAgentTokenPrevExpiresAt(null);
        state.setAgentTokenPending(false);
        state.setAgentTokenGeneration(generation);
        return state;
    }

    String seal(String token) {
        return cipher != null ? cipher.encrypt(token) : PLAIN_PREFIX + token;
    }

    private String unsealQuietly(String sealed, Long nodeId) {
        if (StrUtil.isBlank(sealed)) {
            return null;
        }
        if (sealed.startsWith(PLAIN_PREFIX)) {
            return sealed.substring(PLAIN_PREFIX.length());
        }
        if (cipher == null) {
            log.error("节点 {} 的 Agent 令牌已加密，但当前未配置加密密钥", nodeId);
            return null;
        }
        try {
            return cipher.decrypt(sealed);
        } catch (IllegalStateException e) {
            log.error("节点 {} 的 Agent 令牌无法解密（加密密钥是否更换过？），需要重新部署 Agent: {}",
                    nodeId, e.getMessage());
            return null;
        }
    }

    private boolean isSealedByCipher(String sealed) {
        return StrUtil.isNotBlank(sealed) && !sealed.startsWith(PLAIN_PREFIX);
    }

    private boolean keepPlaintext() {
        return cipher == null || retainPlaintext;
    }

    boolean isPreviousValid(ComputeNodeDO node) {
        return StrUtil.isNotBlank(node.getAgentTokenPrevHash())
                && node.getAgentTokenPrevExpiresAt() != null
                && node.getAgentTokenPrevExpiresAt().isAfter(clock.get());
    }

    private static int generationOf(ComputeNodeDO node) {
        return node.getAgentTokenGeneration() != null ? node.getAgentTokenGeneration() : 0;
    }

    static void copyCredential(ComputeNodeDO from, ComputeNodeDO to) {
        to.setAgentToken(from.getAgentToken());
        to.setAgentTokenHash(from.getAgentTokenHash());
        to.setAgentTokenCipher(from.getAgentTokenCipher());
        to.setAgentTokenPrevHash(from.getAgentTokenPrevHash());
        to.setAgentTokenPrevCipher(from.getAgentTokenPrevCipher());
        to.setAgentTokenPrevExpiresAt(from.getAgentTokenPrevExpiresAt());
        to.setAgentTokenPending(from.getAgentTokenPending());
        to.setAgentTokenGeneration(from.getAgentTokenGeneration());
    }
}
