package com.basiclab.iot.node.service.impl;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.security.AgentTokenHasher;
import com.basiclab.iot.node.service.NodeAgentCredentialService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;

/**
 * 单测用：构造真实的令牌服务，节点令牌按迁移后的形态（只有哈希 + 密文）存放。
 */
public final class AgentCredentialTestSupport {

    public static final String TEST_ENCRYPTION_KEY = "unit-test-agent-token-key";

    private AgentCredentialTestSupport() {
    }

    public static NodeAgentCredentialServiceImpl credentialService(ComputeNodeMapper mapper) {
        return credentialService(mapper, null, TEST_ENCRYPTION_KEY, false);
    }

    public static NodeAgentCredentialServiceImpl credentialService(ComputeNodeMapper mapper, JdbcTemplate jdbcTemplate,
                                                                   String encryptionKey, boolean retainPlaintext) {
        NodeAgentCredentialServiceImpl service = new NodeAgentCredentialServiceImpl();
        setField(service, "computeNodeMapper", mapper);
        setField(service, "jdbcTemplate", jdbcTemplate);
        setField(service, "encryptionKey", encryptionKey);
        setField(service, "encryptorPassword", "");
        setField(service, "retainPlaintext", retainPlaintext);
        setField(service, "rotationGraceSeconds", 259_200L);
        setField(service, "confirmedGraceSeconds", 300L);
        service.init();
        return service;
    }

    /** 不用 ReflectionTestUtils：它会对值调用 toString，而测试里的 Mapper 代理不实现 Object 方法 */
    private static void setField(Object target, String name, Object value) {
        try {
            Field field = NodeAgentCredentialServiceImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 把令牌以迁移后的形态写到节点上（哈希 + 密文，明文列为空）。
     */
    public static void storeToken(NodeAgentCredentialService service, ComputeNodeDO node, String token) {
        node.setAgentToken(null);
        node.setAgentTokenHash(AgentTokenHasher.hash(token));
        node.setAgentTokenCipher(((NodeAgentCredentialServiceImpl) service).seal(token));
        node.setAgentTokenPending(false);
        node.setAgentTokenGeneration(0);
    }
}
