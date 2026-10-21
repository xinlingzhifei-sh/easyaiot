package com.basiclab.iot.node.service.impl;

import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeSentinelRemediateLogMapper;
import com.basiclab.iot.node.domain.vo.NodeSentinelRemediateReportReqVO;
import com.basiclab.iot.node.domain.vo.NodeSentinelRemediateReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Map;

import static com.basiclab.iot.node.enums.ErrorCodeConstants.AGENT_TOKEN_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NodeSentinelRemediatorServiceImplTest {

    private static final long NODE_ID = 42L;
    private static final String AGENT_TOKEN = "agent-token";

    @Test
    void mediaServiceDependencyMustBeLazyToAvoidSentinelMediaBeanCycle() throws Exception {
        Field field = NodeSentinelRemediatorServiceImpl.class.getDeclaredField("nodeMediaService");

        assertNotNull(field.getAnnotation(Lazy.class),
                "NodeMediaService participates in the scheduler/sentinel dependency graph and must be injected lazily");
    }

    @Test
    void remediateRejectsWrongAgentTokenBeforeTakingTheLock() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        NodeSentinelRemediatorServiceImpl service = new NodeSentinelRemediatorServiceImpl();
        ReflectionTestUtils.setField(service, "computeNodeMapper", nodeMapper());
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);

        NodeSentinelRemediateReqVO reqVO = new NodeSentinelRemediateReqVO();
        reqVO.setNodeId(NODE_ID);
        reqVO.setComponentId("runtime_cpp");
        reqVO.setAgentToken("agent-token-x");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.remediate(reqVO));
        assertEquals(AGENT_TOKEN_INVALID.getCode(), ex.getCode());
        verifyNoInteractions(redis);
    }

    @Test
    void remediationReportAcceptsOnlyTheNodeAgentToken() {
        NodeSentinelServiceImpl service = new NodeSentinelServiceImpl();
        ReflectionTestUtils.setField(service, "computeNodeMapper", nodeMapper());
        ReflectionTestUtils.setField(service, "nodeSentinelRemediateLogMapper", mock(NodeSentinelRemediateLogMapper.class));

        NodeSentinelRemediateReportReqVO reqVO = new NodeSentinelRemediateReportReqVO();
        reqVO.setNodeId(NODE_ID);
        reqVO.setComponentId("runtime_cpp");
        reqVO.setAgentToken("agent-token-x");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.reportRemediation(reqVO));
        assertEquals(AGENT_TOKEN_INVALID.getCode(), ex.getCode());

        reqVO.setAgentToken(AGENT_TOKEN);
        Map<String, Object> result = service.reportRemediation(reqVO);
        assertEquals(Boolean.TRUE, result.get("accepted"));
    }

    private static ComputeNodeMapper nodeMapper() {
        ComputeNodeDO node = new ComputeNodeDO();
        node.setId(NODE_ID);
        node.setAgentToken(AGENT_TOKEN);
        ComputeNodeMapper mapper = mock(ComputeNodeMapper.class);
        when(mapper.selectById(NODE_ID)).thenReturn(node);
        return mapper;
    }
}
