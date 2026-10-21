package com.basiclab.iot.node.framework.security.config;

import com.basiclab.iot.common.config.SecurityProperties;
import com.basiclab.iot.common.config.YudaoWebSecurityConfigurerAdapter;
import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.common.enums.UserTypeEnum;
import com.basiclab.iot.common.filter.TokenAuthenticationFilter;
import com.basiclab.iot.common.handler.AccessDeniedHandlerImpl;
import com.basiclab.iot.common.handler.AuthenticationEntryPointImpl;
import com.basiclab.iot.common.service.RpcInternalAccess;
import com.basiclab.iot.common.service.SecurityFrameworkService;
import com.basiclab.iot.common.service.SecurityFrameworkServiceImpl;
import com.basiclab.iot.common.web.config.WebProperties;
import com.basiclab.iot.common.web.core.handler.GlobalExceptionHandler;
import com.basiclab.iot.common.web.core.util.WebFrameworkUtils;
import com.basiclab.iot.node.controller.NodeAgentCommandController;
import com.basiclab.iot.node.controller.NodeAgentController;
import com.basiclab.iot.node.controller.NodeSentinelController;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandAckReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandPollReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandRespVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandResultReqVO;
import com.basiclab.iot.node.domain.vo.NodeSentinelRemediateReportReqVO;
import com.basiclab.iot.node.domain.vo.NodeSentinelRemediateReqVO;
import com.basiclab.iot.node.service.NodeAgentCommandService;
import com.basiclab.iot.node.service.NodeAgentService;
import com.basiclab.iot.node.service.NodeSentinelService;
import com.basiclab.iot.system.api.oauth2.OAuth2TokenApi;
import com.basiclab.iot.system.api.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import com.basiclab.iot.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.Filter;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 用真实的 Spring Security 过滤链（YudaoWebSecurityConfigurerAdapter + TokenAuthenticationFilter
 * + iot-node SecurityConfiguration）验证 Agent 出站通道的访问控制：
 * Agent 不带管理端登录态，只凭 Agent Token 调用；Token 由业务层强制校验。
 */
@SpringJUnitWebConfig(NodeAgentEndpointSecurityTest.TestConfig.class)
class NodeAgentEndpointSecurityTest {

    private static final long NODE_ID = 42L;
    private static final String AGENT_TOKEN = "agent-token";
    private static final String ADMIN_ACCESS_TOKEN = "admin-access-token";
    private static final int UNAUTHORIZED = 401;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;
    @Autowired
    private NodeAgentCommandService nodeAgentCommandService;
    @Autowired
    private NodeSentinelService nodeSentinelService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(nodeAgentCommandService, nodeSentinelService);
        when(nodeAgentCommandService.poll(any())).thenReturn(List.of());
        when(nodeSentinelService.remediate(any())).thenReturn(Map.of("success", true));
        when(nodeSentinelService.reportRemediation(any())).thenReturn(Map.of("accepted", true));
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    // ---------- Agent 出站指令通道：无登录态 + Agent Token 应能到达业务层 ----------

    @Test
    void agentCanPollCommandsWithoutAdminLogin() throws Exception {
        mockMvc.perform(json(post("/node/agent/commands/poll"), agentBody()))
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<NodeAgentCommandPollReqVO> captor = ArgumentCaptor.forClass(NodeAgentCommandPollReqVO.class);
        verify(nodeAgentCommandService).poll(captor.capture());
        assertEquals(AGENT_TOKEN, captor.getValue().getAgentToken());
    }

    @Test
    void agentCanAckCommandWithoutAdminLogin() throws Exception {
        mockMvc.perform(json(post("/node/agent/commands/7/ack"), agentBody()))
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<NodeAgentCommandAckReqVO> captor = ArgumentCaptor.forClass(NodeAgentCommandAckReqVO.class);
        verify(nodeAgentCommandService).ack(eq(7L), captor.capture());
        assertEquals(AGENT_TOKEN, captor.getValue().getAgentToken());
    }

    @Test
    void agentCanExtendCommandLeaseWithoutAdminLogin() throws Exception {
        mockMvc.perform(json(post("/node/agent/commands/7/heartbeat"), agentBody()))
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<NodeAgentCommandAckReqVO> captor = ArgumentCaptor.forClass(NodeAgentCommandAckReqVO.class);
        verify(nodeAgentCommandService).heartbeat(eq(7L), captor.capture());
        assertEquals(AGENT_TOKEN, captor.getValue().getAgentToken());
    }

    @Test
    void agentCanReportCommandResultWithoutAdminLogin() throws Exception {
        String body = "{\"nodeId\":" + NODE_ID + ",\"agentToken\":\"" + AGENT_TOKEN + "\",\"status\":\"succeeded\"}";
        mockMvc.perform(json(post("/node/agent/commands/7/result"), body))
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<NodeAgentCommandResultReqVO> captor = ArgumentCaptor.forClass(NodeAgentCommandResultReqVO.class);
        verify(nodeAgentCommandService).reportResult(eq(7L), captor.capture());
        assertEquals(AGENT_TOKEN, captor.getValue().getAgentToken());
    }

    @Test
    void agentCanRequestRemediationWithHeaderToken() throws Exception {
        mockMvc.perform(json(post("/node/sentinel/remediate"), remediateBody())
                        .header("X-Agent-Token", AGENT_TOKEN))
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<NodeSentinelRemediateReqVO> captor = ArgumentCaptor.forClass(NodeSentinelRemediateReqVO.class);
        verify(nodeSentinelService).remediate(captor.capture());
        assertEquals(AGENT_TOKEN, captor.getValue().getAgentToken());
    }

    @Test
    void agentCanReportRemediationWithHeaderToken() throws Exception {
        mockMvc.perform(json(post("/node/sentinel/remediate-report"), remediateBody())
                        .header("X-Agent-Token", AGENT_TOKEN))
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<NodeSentinelRemediateReportReqVO> captor =
                ArgumentCaptor.forClass(NodeSentinelRemediateReportReqVO.class);
        verify(nodeSentinelService).reportRemediation(captor.capture());
        assertEquals(AGENT_TOKEN, captor.getValue().getAgentToken());
    }

    // ---------- 放行之后 Agent Token 仍然是强制的 ----------

    @Test
    void commandPollWithoutAgentTokenNeverReachesService() throws Exception {
        mockMvc.perform(json(post("/node/agent/commands/poll"), "{\"nodeId\":" + NODE_ID + "}"))
                .andExpect(jsonPath("$.code").value(not(0)));

        verifyNoInteractions(nodeAgentCommandService);
    }

    @Test
    void anonymousRemediationWithoutAgentTokenIsRejected() throws Exception {
        mockMvc.perform(json(post("/node/sentinel/remediate"), remediateBody()))
                .andExpect(jsonPath("$.code").value(not(0)));
        mockMvc.perform(json(post("/node/sentinel/remediate"), remediateBody())
                        .header("X-Agent-Token", " "))
                .andExpect(jsonPath("$.code").value(not(0)));

        verify(nodeSentinelService, never()).remediate(any());
    }

    @Test
    void anonymousRemediationReportWithoutAgentTokenIsRejected() throws Exception {
        mockMvc.perform(json(post("/node/sentinel/remediate-report"), remediateBody()))
                .andExpect(jsonPath("$.code").value(not(0)));

        verify(nodeSentinelService, never()).reportRemediation(any());
    }

    @Test
    void adminMayStillRequestRemediationWithoutAgentToken() throws Exception {
        mockMvc.perform(json(post("/node/sentinel/remediate"), remediateBody())
                        .header("Authorization", "Bearer " + ADMIN_ACCESS_TOKEN))
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<NodeSentinelRemediateReqVO> captor = ArgumentCaptor.forClass(NodeSentinelRemediateReqVO.class);
        verify(nodeSentinelService).remediate(captor.capture());
        assertNull(captor.getValue().getAgentToken());
    }

    // ---------- 其余 /node/** 管理接口仍只对管理后台开放 ----------

    @Test
    void commandEnqueueStillRequiresAdminLogin() throws Exception {
        String body = "{\"nodeId\":" + NODE_ID + ",\"commandType\":\"stream_forward.deploy\","
                + "\"commandKey\":\"video-1\",\"agentToken\":\"" + AGENT_TOKEN + "\"}";
        mockMvc.perform(json(post("/node/agent/commands/enqueue"), body)
                        .header("X-Agent-Token", AGENT_TOKEN))
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED));

        verify(nodeAgentCommandService, never()).enqueue(any());
    }

    @Test
    void commandLookupByKeyRequiresAdminLogin() throws Exception {
        mockMvc.perform(get("/node/agent/commands/by-key").param("commandKey", "stream_forward:cam-001")
                        .header("X-Agent-Token", AGENT_TOKEN))
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED));

        verify(nodeAgentCommandService, never()).getLatestByCommandKey(any(), any());
    }

    @Test
    void adminCanLookUpLatestCommandByKey() throws Exception {
        NodeAgentCommandRespVO command = new NodeAgentCommandRespVO();
        command.setId(101L);
        command.setNodeId(NODE_ID);
        command.setCommandKey("stream_forward:cam-001");
        command.setStatus("succeeded");
        command.setAttemptCount(1);
        when(nodeAgentCommandService.getLatestByCommandKey("stream_forward:cam-001", NODE_ID)).thenReturn(command);

        mockMvc.perform(get("/node/agent/commands/by-key")
                        .param("commandKey", "stream_forward:cam-001")
                        .param("nodeId", String.valueOf(NODE_ID))
                        .header("Authorization", "Bearer " + ADMIN_ACCESS_TOKEN))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.status").value("succeeded"))
                .andExpect(jsonPath("$.data.attemptCount").value(1));
    }

    @Test
    void adminLookupOfUnknownCommandKeyReturnsEmptyData() throws Exception {
        mockMvc.perform(get("/node/agent/commands/by-key").param("commandKey", "stream_forward:missing")
                        .header("Authorization", "Bearer " + ADMIN_ACCESS_TOKEN))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(nodeAgentCommandService).getLatestByCommandKey("stream_forward:missing", null);
    }

    @Test
    void otherSentinelEndpointsStillRequireAdminLogin() throws Exception {
        mockMvc.perform(json(post("/node/sentinel/probe"), "{\"nodeId\":" + NODE_ID + "}")
                        .header("X-Agent-Token", AGENT_TOKEN))
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED));
        mockMvc.perform(get("/node/sentinel/remediate-logs").param("nodeId", String.valueOf(NODE_ID))
                        .header("X-Agent-Token", AGENT_TOKEN))
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED));

        verify(nodeSentinelService, never()).probe(any());
        verify(nodeSentinelService, never()).listRemediateLogs(anyLong());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String agentBody() {
        return "{\"nodeId\":" + NODE_ID + ",\"agentToken\":\"" + AGENT_TOKEN + "\"}";
    }

    private static String remediateBody() {
        return "{\"nodeId\":" + NODE_ID + ",\"componentId\":\"runtime_cpp\"}";
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
            YudaoWebSecurityConfigurerAdapter.class,
            SecurityConfiguration.class,
            NodeAgentCommandController.class,
            NodeAgentController.class,
            NodeSentinelController.class,
    })
    static class TestConfig {

        @Bean
        public WebProperties webProperties() {
            return new WebProperties();
        }

        @Bean
        public WebFrameworkUtils webFrameworkUtils(WebProperties webProperties) {
            return new WebFrameworkUtils(webProperties);
        }

        @Bean
        public SecurityProperties securityProperties() {
            return new SecurityProperties();
        }

        @Bean
        public AuthenticationEntryPoint authenticationEntryPoint() {
            return new AuthenticationEntryPointImpl();
        }

        @Bean
        public AccessDeniedHandler accessDeniedHandler() {
            return new AccessDeniedHandlerImpl();
        }

        @Bean
        public GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler("node-server");
        }

        @Bean
        public OAuth2TokenApi oauth2TokenApi() {
            OAuth2TokenApi api = mock(OAuth2TokenApi.class);
            OAuth2AccessTokenCheckRespDTO admin = new OAuth2AccessTokenCheckRespDTO();
            admin.setUserId(1L);
            admin.setUserType(UserTypeEnum.ADMIN.getValue());
            admin.setTenantId(1L);
            when(api.checkAccessToken(ADMIN_ACCESS_TOKEN)).thenReturn(CommonResult.success(admin));
            return api;
        }

        @Bean
        public TokenAuthenticationFilter authenticationTokenFilter(SecurityProperties securityProperties,
                                                                   GlobalExceptionHandler globalExceptionHandler,
                                                                   OAuth2TokenApi oauth2TokenApi) {
            return new TokenAuthenticationFilter(securityProperties, globalExceptionHandler, oauth2TokenApi);
        }

        @Bean("ss")
        public SecurityFrameworkService securityFrameworkService() {
            return new SecurityFrameworkServiceImpl(mock(PermissionApi.class));
        }

        @Bean("rpcInternalAccess")
        public RpcInternalAccess rpcInternalAccess() {
            return mock(RpcInternalAccess.class);
        }

        @Bean
        public NodeAgentCommandService nodeAgentCommandService() {
            return mock(NodeAgentCommandService.class);
        }

        @Bean
        public NodeAgentService nodeAgentService() {
            return mock(NodeAgentService.class);
        }

        @Bean
        public NodeSentinelService nodeSentinelService() {
            return mock(NodeSentinelService.class);
        }
    }
}
