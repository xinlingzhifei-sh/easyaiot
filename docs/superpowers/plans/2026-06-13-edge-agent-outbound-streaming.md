# Edge Agent Outbound Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first working Edge/Agent outbound RTSP-to-RTMP slice so private RTSP cameras can stream without exposing the customer-site Agent `9100` port.

**Architecture:** Add a durable long-polling command queue in `iot-node`, let VIDEO enqueue `stream_forward.deploy` commands after media allocation, and let the Python Agent poll, ack, execute, and report results over outbound HTTP. Keep existing direct Agent HTTP deployment for reachable managed nodes, but make the Edge RTSP path explicitly outbound-only.

**Tech Stack:** Java Spring Boot, MyBatis-Plus, PostgreSQL SQL resources, Python 3 `unittest`, `requests`, `subprocess`, existing VIDEO Flask services, existing SRS/ZLM media binding utilities.

---

## Scope

This plan implements the first slice from the spec:

- Edge registers and heartbeats as it does today.
- Platform can enqueue commands for a node.
- Agent polls commands through outbound HTTP.
- Agent executes `stream_forward.deploy` locally without requiring platform access to `9100`.
- VIDEO can create an Edge RTSP stream-forward command after media allocation.

The unified device access center, signed RTMP ingest enforcement, and WebRTC TURN/STUN production rollout remain visible in tests and acceptance notes, but they get their own implementation plans after this slice proves the no-port-mapping Edge path.

## File Structure

### Java `iot-node`

- Create `DEVICE/iot-node/iot-node-biz/src/main/resources/sql/node_agent_command_v1.sql`
  - Add additive schema for `node_agent_command`.
- Create `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/dal/dataobject/NodeAgentCommandDO.java`
  - MyBatis-Plus data object for the command queue.
- Create `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/dal/pgsql/NodeAgentCommandMapper.java`
  - Query and update helpers for leasing, ack, result, and idempotent lookup.
- Create `DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/domain/vo/NodeAgentCommandPollReqVO.java`
- Create `DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/domain/vo/NodeAgentCommandAckReqVO.java`
- Create `DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/domain/vo/NodeAgentCommandResultReqVO.java`
- Create `DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/domain/vo/NodeAgentCommandRespVO.java`
- Create `DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/domain/vo/NodeAgentCommandEnqueueReqVO.java`
  - Agent-facing and platform-internal command contracts.
- Create `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/service/NodeAgentCommandService.java`
- Create `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/service/impl/NodeAgentCommandServiceImpl.java`
  - Validates node token, enqueues commands, leases commands, ack/result handling.
- Create `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/controller/NodeAgentCommandController.java`
  - `/node/agent/commands/*` Agent endpoints plus enqueue endpoint for internal platform callers.
- Modify `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/service/impl/NodeAgentServiceImpl.java`
  - Reuse token validation through a package-private helper or a new validator service.
- Test `DEVICE/iot-node/iot-node-biz/src/test/java/com/basiclab/iot/node/agent/NodeAgentCommandSchemaSqlTest.java`
- Test `DEVICE/iot-node/iot-node-biz/src/test/java/com/basiclab/iot/node/agent/NodeAgentCommandServiceImplTest.java`

### Python Agent

- Create `NODE/agent_commands.py`
  - Client and polling runner.
- Create `NODE/stream_forward_executor.py`
  - Local RTSP-to-RTMP pusher executor.
- Modify `NODE/run_agent.py`
  - Start command poll loop beside heartbeat loop.
- Modify `NODE/agent.env.example`
  - Add command polling interval and outbound mode envs.
- Create `NODE/tests/__init__.py`
- Create `NODE/tests/test_agent_commands.py`
- Create `NODE/tests/test_stream_forward_executor.py`

### VIDEO

- Modify `VIDEO/app/utils/media_client.py`
  - Add command enqueue client for iot-node.
- Create `VIDEO/app/services/edge_stream_forward_service.py`
  - Allocates media, records stream-forward task metadata, enqueues `stream_forward.deploy`.
- Modify `VIDEO/app/blueprints/stream_forward.py`
  - Add `POST /device/<device_id>/ensure-edge-task`.
- Create `VIDEO/tests/test_edge_stream_forward_service.py`

---

### Task 1: Java Command Schema And Mapper

**Files:**
- Create: `DEVICE/iot-node/iot-node-biz/src/main/resources/sql/node_agent_command_v1.sql`
- Create: `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/dal/dataobject/NodeAgentCommandDO.java`
- Create: `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/dal/pgsql/NodeAgentCommandMapper.java`
- Test: `DEVICE/iot-node/iot-node-biz/src/test/java/com/basiclab/iot/node/agent/NodeAgentCommandSchemaSqlTest.java`

- [ ] **Step 1: Write the failing schema test**

```java
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
```

- [ ] **Step 2: Run the schema test and verify it fails**

Run from `E:\yFeiEye\DEVICE`:

```powershell
mvn -pl iot-node/iot-node-biz -Dtest=NodeAgentCommandSchemaSqlTest test
```

Expected: FAIL because `sql/node_agent_command_v1.sql` does not exist.

- [ ] **Step 3: Add the additive schema file**

```sql
CREATE TABLE IF NOT EXISTS node_agent_command (
    id BIGSERIAL PRIMARY KEY,
    node_id BIGINT NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    command_key VARCHAR(160) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    attempt_count INT NOT NULL DEFAULT 0,
    lease_until TIMESTAMP,
    last_error TEXT,
    result_json TEXT,
    acked_at TIMESTAMP,
    finished_at TIMESTAMP,
    creator VARCHAR(64),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64),
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_node_agent_command_active_key
    ON node_agent_command(node_id, command_key)
    WHERE deleted = FALSE AND status IN ('pending', 'leased', 'running');

CREATE INDEX IF NOT EXISTS idx_node_agent_command_poll
    ON node_agent_command(node_id, status, lease_until, create_time)
    WHERE deleted = FALSE;
```

- [ ] **Step 4: Add the command data object**

```java
package com.basiclab.iot.node.dal.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.*;

import java.time.LocalDateTime;

@TableName("node_agent_command")
@KeySequence("node_agent_command_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeAgentCommandDO extends BaseDO {

    @TableId
    private Long id;
    private Long nodeId;
    private String commandType;
    private String commandKey;
    private String payloadJson;
    private String status;
    private Integer attemptCount;
    private LocalDateTime leaseUntil;
    private String lastError;
    private String resultJson;
    private LocalDateTime ackedAt;
    private LocalDateTime finishedAt;
}
```

- [ ] **Step 5: Add the mapper helpers**

```java
package com.basiclab.iot.node.dal.pgsql;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.node.dal.dataobject.NodeAgentCommandDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NodeAgentCommandMapper extends BaseMapperX<NodeAgentCommandDO> {

    default NodeAgentCommandDO selectActiveByCommandKey(Long nodeId, String commandKey) {
        return selectOne(new LambdaQueryWrapperX<NodeAgentCommandDO>()
                .eq(NodeAgentCommandDO::getNodeId, nodeId)
                .eq(NodeAgentCommandDO::getCommandKey, commandKey)
                .in(NodeAgentCommandDO::getStatus, List.of("pending", "leased", "running")));
    }

    default List<NodeAgentCommandDO> selectPollable(Long nodeId, LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<NodeAgentCommandDO>()
                .eq(NodeAgentCommandDO::getNodeId, nodeId)
                .and(wrapper -> wrapper
                        .eq(NodeAgentCommandDO::getStatus, "pending")
                        .or()
                        .eq(NodeAgentCommandDO::getStatus, "leased")
                        .lt(NodeAgentCommandDO::getLeaseUntil, now))
                .orderByAsc(NodeAgentCommandDO::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
    }
}
```

- [ ] **Step 6: Run the schema test and verify it passes**

Run:

```powershell
mvn -pl iot-node/iot-node-biz -Dtest=NodeAgentCommandSchemaSqlTest test
```

Expected: PASS.

- [ ] **Step 7: Commit Task 1**

```powershell
git add -- DEVICE/iot-node/iot-node-biz/src/main/resources/sql/node_agent_command_v1.sql DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/dal/dataobject/NodeAgentCommandDO.java DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/dal/pgsql/NodeAgentCommandMapper.java DEVICE/iot-node/iot-node-biz/src/test/java/com/basiclab/iot/node/agent/NodeAgentCommandSchemaSqlTest.java
git commit -m "feat: add agent command queue schema"
```

### Task 2: Java Command Service And Agent API

**Files:**
- Create: `DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/domain/vo/NodeAgentCommandPollReqVO.java`
- Create: `DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/domain/vo/NodeAgentCommandAckReqVO.java`
- Create: `DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/domain/vo/NodeAgentCommandResultReqVO.java`
- Create: `DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/domain/vo/NodeAgentCommandRespVO.java`
- Create: `DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/domain/vo/NodeAgentCommandEnqueueReqVO.java`
- Create: `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/service/NodeAgentCommandService.java`
- Create: `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/service/impl/NodeAgentCommandServiceImpl.java`
- Create: `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/controller/NodeAgentCommandController.java`
- Test: `DEVICE/iot-node/iot-node-biz/src/test/java/com/basiclab/iot/node/agent/NodeAgentCommandServiceImplTest.java`

- [ ] **Step 1: Write the failing service test**

```java
package com.basiclab.iot.node.agent;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeAgentCommandDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeAgentCommandMapper;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandPollReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandResultReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandRespVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandEnqueueReqVO;
import com.basiclab.iot.node.service.impl.NodeAgentCommandServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeAgentCommandServiceImplTest {

    @Mock
    private ComputeNodeMapper computeNodeMapper;
    @Mock
    private NodeAgentCommandMapper nodeAgentCommandMapper;
    @InjectMocks
    private NodeAgentCommandServiceImpl service;

    @Test
    void enqueueCreatesPendingCommandForNode() {
        when(computeNodeMapper.selectById(7L)).thenReturn(node(7L));
        when(nodeAgentCommandMapper.selectActiveByCommandKey(7L, "stream_forward:cam-001")).thenReturn(null);

        NodeAgentCommandEnqueueReqVO req = new NodeAgentCommandEnqueueReqVO();
        req.setNodeId(7L);
        req.setCommandType("stream_forward.deploy");
        req.setCommandKey("stream_forward:cam-001");
        req.setPayload(Map.of("deviceId", "cam-001", "rtspUrl", "rtsp://10.0.0.8/live"));

        NodeAgentCommandRespVO resp = service.enqueue(req);

        assertEquals(7L, resp.getNodeId());
        assertEquals("stream_forward.deploy", resp.getCommandType());
        assertEquals("pending", resp.getStatus());
        verify(nodeAgentCommandMapper).insert(any(NodeAgentCommandDO.class));
    }

    @Test
    void pollLeasesOnlyAuthenticatedNodeCommands() {
        when(computeNodeMapper.selectById(7L)).thenReturn(node(7L));
        when(nodeAgentCommandMapper.selectPollable(any(), any(), any(Integer.class))).thenReturn(List.of(command(101L, 7L)));

        NodeAgentCommandPollReqVO req = new NodeAgentCommandPollReqVO();
        req.setNodeId(7L);
        req.setAgentToken("secret");
        req.setMaxCommands(5);

        List<NodeAgentCommandRespVO> commands = service.poll(req);

        assertEquals(1, commands.size());
        assertEquals(101L, commands.get(0).getId());
        assertEquals("leased", commands.get(0).getStatus());
        verify(nodeAgentCommandMapper).updateById(any(NodeAgentCommandDO.class));
    }

    @Test
    void reportResultStoresSuccessPayload() {
        when(computeNodeMapper.selectById(7L)).thenReturn(node(7L));
        when(nodeAgentCommandMapper.selectById(101L)).thenReturn(command(101L, 7L));

        NodeAgentCommandResultReqVO req = new NodeAgentCommandResultReqVO();
        req.setNodeId(7L);
        req.setAgentToken("secret");
        req.setStatus("succeeded");
        req.setResult(Map.of("pid", 4321));

        service.reportResult(101L, req);

        verify(nodeAgentCommandMapper).updateById(any(NodeAgentCommandDO.class));
    }

    private static ComputeNodeDO node(Long id) {
        ComputeNodeDO node = new ComputeNodeDO();
        node.setId(id);
        node.setAgentToken("secret");
        node.setStatus("online");
        return node;
    }

    private static NodeAgentCommandDO command(Long id, Long nodeId) {
        NodeAgentCommandDO command = new NodeAgentCommandDO();
        command.setId(id);
        command.setNodeId(nodeId);
        command.setCommandType("stream_forward.deploy");
        command.setCommandKey("stream_forward:cam-001");
        command.setPayloadJson("{\"deviceId\":\"cam-001\"}");
        command.setStatus("pending");
        command.setAttemptCount(0);
        return command;
    }
}
```

- [ ] **Step 2: Run the service test and verify it fails**

Run:

```powershell
mvn -pl iot-node/iot-node-biz -Dtest=NodeAgentCommandServiceImplTest test
```

Expected: FAIL because command VO and service classes do not exist.

- [ ] **Step 3: Add command VO classes**

Use these fields exactly:

```java
// NodeAgentCommandPollReqVO
private Long nodeId;
private String agentToken;
private Map<String, Boolean> capabilities;
private Integer maxCommands;
```

```java
// NodeAgentCommandAckReqVO
private Long nodeId;
private String agentToken;
```

```java
// NodeAgentCommandResultReqVO
private Long nodeId;
private String agentToken;
private String status;
private Map<String, Object> result;
private String error;
```

```java
// NodeAgentCommandEnqueueReqVO
private Long nodeId;
private String commandType;
private String commandKey;
private Map<String, Object> payload;
```

```java
// NodeAgentCommandRespVO
private Long id;
private Long nodeId;
private String commandType;
private String commandKey;
private Map<String, Object> payload;
private String status;
private Integer attemptCount;
```

- [ ] **Step 4: Add the service interface**

```java
package com.basiclab.iot.node.service;

import com.basiclab.iot.node.domain.vo.NodeAgentCommandAckReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandEnqueueReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandPollReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandRespVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandResultReqVO;

import java.util.List;

public interface NodeAgentCommandService {

    NodeAgentCommandRespVO enqueue(NodeAgentCommandEnqueueReqVO reqVO);

    List<NodeAgentCommandRespVO> poll(NodeAgentCommandPollReqVO reqVO);

    void ack(Long commandId, NodeAgentCommandAckReqVO reqVO);

    void reportResult(Long commandId, NodeAgentCommandResultReqVO reqVO);
}
```

- [ ] **Step 5: Add minimal service implementation**

Core behaviors:

```java
private ComputeNodeDO validateAgent(Long nodeId, String agentToken) {
    ComputeNodeDO node = computeNodeMapper.selectById(nodeId);
    if (node == null) {
        throw exception(COMPUTE_NODE_NOT_EXISTS);
    }
    if (StrUtil.isBlank(agentToken) || !agentToken.equals(node.getAgentToken())) {
        throw exception(AGENT_TOKEN_INVALID);
    }
    return node;
}

private NodeAgentCommandRespVO toResp(NodeAgentCommandDO command) {
    NodeAgentCommandRespVO resp = BeanUtils.toBean(command, NodeAgentCommandRespVO.class);
    resp.setPayload(JSONUtil.parseObj(command.getPayloadJson()));
    return resp;
}
```

Status transitions:

- `enqueue`: insert `pending` when no active command key exists, otherwise update existing `pending` command payload.
- `poll`: select pollable commands, set `status=leased`, increment `attemptCount`, set `leaseUntil=now.plusSeconds(30)`.
- `ack`: validate node/token, load command id, require matching node, set `status=running`, set `ackedAt=now`.
- `reportResult`: validate node/token, load command id, require matching node, set `status=succeeded` or `failed`, set `resultJson` or `lastError`, set `finishedAt=now`.

- [ ] **Step 6: Add Agent command controller**

```java
@Tag(name = "Agent - Command Queue")
@RestController
@RequestMapping("/node/agent/commands")
@Validated
public class NodeAgentCommandController {

    @Resource
    private NodeAgentCommandService nodeAgentCommandService;

    @PostMapping("/enqueue")
    public CommonResult<NodeAgentCommandRespVO> enqueue(@Valid @RequestBody NodeAgentCommandEnqueueReqVO reqVO) {
        return success(nodeAgentCommandService.enqueue(reqVO));
    }

    @PostMapping("/poll")
    @TenantIgnore
    public CommonResult<List<NodeAgentCommandRespVO>> poll(@Valid @RequestBody NodeAgentCommandPollReqVO reqVO) {
        return success(nodeAgentCommandService.poll(reqVO));
    }

    @PostMapping("/{commandId}/ack")
    @TenantIgnore
    public CommonResult<Boolean> ack(@PathVariable Long commandId, @Valid @RequestBody NodeAgentCommandAckReqVO reqVO) {
        nodeAgentCommandService.ack(commandId, reqVO);
        return success(true);
    }

    @PostMapping("/{commandId}/result")
    @TenantIgnore
    public CommonResult<Boolean> result(@PathVariable Long commandId, @Valid @RequestBody NodeAgentCommandResultReqVO reqVO) {
        nodeAgentCommandService.reportResult(commandId, reqVO);
        return success(true);
    }
}
```

- [ ] **Step 7: Run the service test and verify it passes**

Run:

```powershell
mvn -pl iot-node/iot-node-biz -Dtest=NodeAgentCommandServiceImplTest test
```

Expected: PASS.

- [ ] **Step 8: Commit Task 2**

```powershell
git add -- DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/domain/vo/NodeAgentCommand*.java DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/service/NodeAgentCommandService.java DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/service/impl/NodeAgentCommandServiceImpl.java DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/controller/NodeAgentCommandController.java DEVICE/iot-node/iot-node-biz/src/test/java/com/basiclab/iot/node/agent/NodeAgentCommandServiceImplTest.java
git commit -m "feat: add outbound agent command API"
```

### Task 3: Python Agent Command Client And Poll Loop

**Files:**
- Create: `NODE/agent_commands.py`
- Modify: `NODE/run_agent.py`
- Modify: `NODE/agent.env.example`
- Create: `NODE/tests/__init__.py`
- Test: `NODE/tests/test_agent_commands.py`

- [ ] **Step 1: Write the failing Python Agent command test**

```python
import unittest
from unittest.mock import Mock

from agent_commands import AgentCommandClient, AgentCommandRunner


class AgentCommandClientTest(unittest.TestCase):

    def test_poll_posts_node_token_and_returns_commands(self):
        session = Mock()
        response = Mock()
        response.json.return_value = {
            "code": 0,
            "data": [{"id": 101, "commandType": "stream_forward.deploy", "payload": {"deviceId": "cam-001"}}],
        }
        response.raise_for_status.return_value = None
        session.post.return_value = response

        client = AgentCommandClient(
            base_url="http://platform/admin-api/node/agent",
            node_id=7,
            agent_token="secret",
            session=session,
        )

        commands = client.poll(max_commands=3)

        self.assertEqual(101, commands[0]["id"])
        session.post.assert_called_once()
        payload = session.post.call_args.kwargs["json"]
        self.assertEqual(7, payload["nodeId"])
        self.assertEqual("secret", payload["agentToken"])
        self.assertEqual(3, payload["maxCommands"])

    def test_runner_reports_success_without_calling_local_http(self):
        client = Mock()
        client.poll.return_value = [{"id": 101, "commandType": "stream_forward.deploy", "payload": {"deviceId": "cam-001"}}]
        executor = Mock(return_value={"pid": 4321})
        runner = AgentCommandRunner(client=client, executors={"stream_forward.deploy": executor})

        runner.run_once()

        client.ack.assert_called_once_with(101)
        executor.assert_called_once_with({"deviceId": "cam-001"})
        client.report_result.assert_called_once_with(101, "succeeded", {"pid": 4321}, None)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the Python test and verify it fails**

Run from `E:\yFeiEye`:

```powershell
$env:PYTHONPATH="E:\yFeiEye\NODE"
python -m unittest NODE.tests.test_agent_commands
```

Expected: FAIL because `agent_commands.py` does not exist.

- [ ] **Step 3: Add `AgentCommandClient` and `AgentCommandRunner`**

```python
import logging
import time
from typing import Any, Callable, Dict, List, Optional

import requests

logger = logging.getLogger("easyaiot-node-agent.commands")


class AgentCommandClient:
    def __init__(self, base_url: str, node_id: int, agent_token: str, session: Optional[requests.Session] = None):
        self.base_url = base_url.rstrip("/")
        self.node_id = node_id
        self.agent_token = agent_token
        self.session = session or requests.Session()

    def poll(self, max_commands: int = 5) -> List[Dict[str, Any]]:
        response = self.session.post(
            f"{self.base_url}/commands/poll",
            json={"nodeId": self.node_id, "agentToken": self.agent_token, "maxCommands": max_commands},
            timeout=35,
        )
        response.raise_for_status()
        body = response.json()
        if body.get("code") != 0:
            raise RuntimeError(body.get("msg") or "command poll failed")
        return body.get("data") or []

    def ack(self, command_id: int) -> None:
        response = self.session.post(
            f"{self.base_url}/commands/{command_id}/ack",
            json={"nodeId": self.node_id, "agentToken": self.agent_token},
            timeout=10,
        )
        response.raise_for_status()

    def report_result(
        self,
        command_id: int,
        status: str,
        result: Optional[Dict[str, Any]],
        error: Optional[str],
    ) -> None:
        response = self.session.post(
            f"{self.base_url}/commands/{command_id}/result",
            json={
                "nodeId": self.node_id,
                "agentToken": self.agent_token,
                "status": status,
                "result": result or {},
                "error": error,
            },
            timeout=15,
        )
        response.raise_for_status()


class AgentCommandRunner:
    def __init__(self, client: AgentCommandClient, executors: Dict[str, Callable[[Dict[str, Any]], Dict[str, Any]]]):
        self.client = client
        self.executors = executors

    def run_once(self) -> int:
        commands = self.client.poll()
        for command in commands:
            command_id = int(command["id"])
            command_type = command["commandType"]
            payload = command.get("payload") or {}
            self.client.ack(command_id)
            executor = self.executors.get(command_type)
            if executor is None:
                self.client.report_result(command_id, "failed", {}, f"unsupported command type: {command_type}")
                continue
            try:
                result = executor(payload)
                self.client.report_result(command_id, "succeeded", result, None)
            except Exception as exc:
                logger.exception("command failed id=%s type=%s", command_id, command_type)
                self.client.report_result(command_id, "failed", {}, str(exc))
        return len(commands)

    def run_forever(self, interval_seconds: float) -> None:
        while True:
            try:
                handled = self.run_once()
                time.sleep(0 if handled else interval_seconds)
            except Exception as exc:
                logger.warning("command poll failed: %s", exc)
                time.sleep(interval_seconds)
```

- [ ] **Step 4: Wire the command loop into `run_agent.py`**

Add envs near existing env constants:

```python
COMMAND_POLL_ENABLED = os.environ.get("COMMAND_POLL_ENABLED", "true").lower() in ("1", "true", "yes", "on")
COMMAND_POLL_INTERVAL = float(os.environ.get("COMMAND_POLL_INTERVAL", "3"))
```

Add startup code after the heartbeat thread starts:

```python
if COMMAND_POLL_ENABLED:
    from agent_commands import AgentCommandClient, AgentCommandRunner
    from stream_forward_executor import StreamForwardExecutor

    command_client = AgentCommandClient(CONTROL_PLANE_URL, NODE_ID, AGENT_TOKEN)
    stream_forward_executor = StreamForwardExecutor()
    command_runner = AgentCommandRunner(
        command_client,
        {
            "stream_forward.deploy": stream_forward_executor.deploy,
            "stream_forward.stop": stream_forward_executor.stop,
        },
    )
    threading.Thread(target=command_runner.run_forever, args=(COMMAND_POLL_INTERVAL,), daemon=True).start()
```

- [ ] **Step 5: Add env examples**

Append:

```dotenv
COMMAND_POLL_ENABLED=true
COMMAND_POLL_INTERVAL=3
```

- [ ] **Step 6: Run the Python Agent command test and verify it passes**

Run:

```powershell
$env:PYTHONPATH="E:\yFeiEye\NODE"
python -m unittest NODE.tests.test_agent_commands
```

Expected: PASS.

- [ ] **Step 7: Commit Task 3**

```powershell
git add -- NODE/agent_commands.py NODE/run_agent.py NODE/agent.env.example NODE/tests/__init__.py NODE/tests/test_agent_commands.py
git commit -m "feat: add outbound agent command polling"
```

### Task 4: Python Stream Forward Executor

**Files:**
- Create: `NODE/stream_forward_executor.py`
- Test: `NODE/tests/test_stream_forward_executor.py`

- [ ] **Step 1: Write the failing executor test**

```python
import unittest
from unittest.mock import Mock, patch

from stream_forward_executor import StreamForwardExecutor


class StreamForwardExecutorTest(unittest.TestCase):

    @patch("stream_forward_executor.subprocess.Popen")
    def test_deploy_starts_ffmpeg_rtsp_to_rtmp_process(self, popen):
        process = Mock()
        process.pid = 4321
        popen.return_value = process
        executor = StreamForwardExecutor()

        result = executor.deploy({
            "deviceId": "cam-001",
            "rtspUrl": "rtsp://user:pass@10.0.0.8/live",
            "rtmpPushUrl": "rtmp://media.example.com/live/cam-001",
            "transport": "tcp",
            "logDir": "E:/tmp/yfeieye-edge/cam-001",
        })

        self.assertEqual(4321, result["pid"])
        cmd = popen.call_args.args[0]
        self.assertIn("-rtsp_transport", cmd)
        self.assertIn("tcp", cmd)
        self.assertIn("rtmp://media.example.com/live/cam-001", cmd)

    def test_deploy_requires_rtsp_and_rtmp_urls(self):
        executor = StreamForwardExecutor()

        with self.assertRaises(ValueError):
            executor.deploy({"deviceId": "cam-001", "rtspUrl": "", "rtmpPushUrl": ""})


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the executor test and verify it fails**

Run:

```powershell
$env:PYTHONPATH="E:\yFeiEye\NODE"
python -m unittest NODE.tests.test_stream_forward_executor
```

Expected: FAIL because `stream_forward_executor.py` does not exist.

- [ ] **Step 3: Add the executor**

```python
import os
import subprocess
from typing import Any, Dict


class StreamForwardExecutor:
    def __init__(self):
        self._processes: Dict[str, subprocess.Popen] = {}

    def deploy(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        device_id = str(payload.get("deviceId") or "").strip()
        rtsp_url = str(payload.get("rtspUrl") or "").strip()
        rtmp_push_url = str(payload.get("rtmpPushUrl") or "").strip()
        transport = str(payload.get("transport") or "tcp").strip().lower()
        log_dir = str(payload.get("logDir") or os.path.join(os.getcwd(), "logs", "edge", device_id))

        if not device_id:
            raise ValueError("deviceId is required")
        if not rtsp_url.startswith("rtsp://"):
            raise ValueError("rtspUrl must start with rtsp://")
        if not rtmp_push_url.startswith("rtmp://"):
            raise ValueError("rtmpPushUrl must start with rtmp://")
        if transport not in ("tcp", "udp"):
            transport = "tcp"

        existing = self._processes.get(device_id)
        if existing and existing.poll() is None:
            return {"pid": existing.pid, "deviceId": device_id, "alreadyRunning": True}

        os.makedirs(log_dir, exist_ok=True)
        log_path = os.path.join(log_dir, "stream-forward.log")
        log_file = open(log_path, "a", encoding="utf-8")
        cmd = [
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "warning",
            "-rtsp_transport",
            transport,
            "-i",
            rtsp_url,
            "-an",
            "-c:v",
            "copy",
            "-f",
            "flv",
            "-flvflags",
            "no_duration_filesize",
            rtmp_push_url,
        ]
        process = subprocess.Popen(cmd, stdout=log_file, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        self._processes[device_id] = process
        return {"pid": process.pid, "deviceId": device_id, "logPath": log_path, "rtmpPushUrl": rtmp_push_url}

    def stop(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        device_id = str(payload.get("deviceId") or "").strip()
        process = self._processes.get(device_id)
        if not process:
            return {"deviceId": device_id, "stopped": False}
        if process.poll() is None:
            process.terminate()
        self._processes.pop(device_id, None)
        return {"deviceId": device_id, "stopped": True}
```

- [ ] **Step 4: Run executor and command tests**

Run:

```powershell
$env:PYTHONPATH="E:\yFeiEye\NODE"
python -m unittest NODE.tests.test_stream_forward_executor NODE.tests.test_agent_commands
```

Expected: PASS.

- [ ] **Step 5: Commit Task 4**

```powershell
git add -- NODE/stream_forward_executor.py NODE/tests/test_stream_forward_executor.py
git commit -m "feat: execute edge rtsp stream forward commands"
```

### Task 5: VIDEO Edge RTSP Orchestration

**Files:**
- Modify: `VIDEO/app/utils/media_client.py`
- Create: `VIDEO/app/services/edge_stream_forward_service.py`
- Modify: `VIDEO/app/blueprints/stream_forward.py`
- Test: `VIDEO/tests/test_edge_stream_forward_service.py`

- [ ] **Step 1: Write the failing VIDEO service test**

```python
import unittest
from unittest.mock import Mock, patch

from app.services.edge_stream_forward_service import ensure_edge_rtsp_forward


class EdgeStreamForwardServiceTest(unittest.TestCase):

    @patch("app.services.edge_stream_forward_service.enqueue_agent_command")
    @patch("app.services.edge_stream_forward_service.allocate_device_media")
    @patch("app.services.edge_stream_forward_service.db")
    @patch("app.services.edge_stream_forward_service.Device")
    def test_ensure_edge_rtsp_forward_allocates_media_and_enqueues_command(
        self,
        device_model,
        db,
        allocate_media,
        enqueue,
    ):
        device = Mock()
        device.id = "cam-001"
        device.name = "Gate Camera"
        device.source = "rtsp://user:pass@10.0.0.8/live"
        device.rtmp_stream = ""
        device.http_stream = ""
        device.ai_rtmp_stream = ""
        device.ai_http_stream = ""
        device_model.query.get.return_value = device
        allocate_media.return_value = {
            "rtmpStream": "rtmp://media.example.com/live/cam-001",
            "httpStream": "https://eye.example.com/live/cam-001.flv",
        }
        enqueue.return_value = {"id": 101, "status": "pending"}

        result = ensure_edge_rtsp_forward("cam-001", edge_node_id=7)

        self.assertEqual(101, result["command"]["id"])
        enqueue.assert_called_once()
        payload = enqueue.call_args.kwargs["payload"]
        self.assertEqual("cam-001", payload["deviceId"])
        self.assertEqual("rtsp://user:pass@10.0.0.8/live", payload["rtspUrl"])
        self.assertEqual("rtmp://media.example.com/live/cam-001", payload["rtmpPushUrl"])
        db.session.commit.assert_called_once()

    @patch("app.services.edge_stream_forward_service.Device")
    def test_ensure_edge_rtsp_forward_rejects_non_rtsp_source(self, device_model):
        device = Mock()
        device.source = "gb28181://34020000001320000001"
        device_model.query.get.return_value = device

        with self.assertRaises(ValueError):
            ensure_edge_rtsp_forward("cam-001", edge_node_id=7)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the VIDEO test and verify it fails**

Run:

```powershell
$env:PYTHONPATH="E:\yFeiEye\VIDEO"
python -m unittest VIDEO.tests.test_edge_stream_forward_service
```

Expected: FAIL because `edge_stream_forward_service.py` does not exist.

- [ ] **Step 3: Extend `media_client.py` with command enqueue**

```python
AGENT_COMMAND_API_BASE = f'{JAVA_BACKEND_URL}/admin-api/node/agent/commands'


def enqueue_agent_command(
    *,
    node_id: int,
    command_type: str,
    command_key: str,
    payload: Dict[str, Any],
) -> Dict[str, Any]:
    body = {
        'nodeId': node_id,
        'commandType': command_type,
        'commandKey': command_key,
        'payload': payload,
    }
    url = f'{AGENT_COMMAND_API_BASE}/enqueue'
    resp = requests.post(url, json=body, headers=_headers(), timeout=REQUEST_TIMEOUT)
    resp.raise_for_status()
    data = resp.json()
    if data.get('code') != 0:
        raise RuntimeError(data.get('msg') or f'Agent command enqueue failed: {url}')
    return data.get('data') or {}
```

- [ ] **Step 4: Add the Edge orchestration service**

```python
import os
from typing import Any, Dict

from models import Device, db
from app.utils.media_client import allocate_device_media, enqueue_agent_command


def ensure_edge_rtsp_forward(device_id: str, *, edge_node_id: int, transport: str = "tcp") -> Dict[str, Any]:
    device = Device.query.get(device_id)
    if not device:
        raise ValueError(f"device not found: {device_id}")
    source = (device.source or "").strip()
    if not source.lower().startswith("rtsp://"):
        raise ValueError("edge rtsp forwarding requires a rtsp:// device source")
    if not edge_node_id:
        raise ValueError("edge_node_id is required")

    binding = allocate_device_media(device_id, need_srs_live=True, need_srs_ai=True, need_zlm=False)
    rtmp_stream = binding.get("rtmpStream") or device.rtmp_stream
    http_stream = binding.get("httpStream") or device.http_stream
    if not rtmp_stream:
        raise ValueError("media allocation did not return rtmpStream")

    device.rtmp_stream = rtmp_stream
    device.http_stream = http_stream or device.http_stream
    device.ai_rtmp_stream = binding.get("aiRtmpStream") or device.ai_rtmp_stream
    device.ai_http_stream = binding.get("aiHttpStream") or device.ai_http_stream
    device.enable_forward = True
    db.session.commit()

    payload = {
        "deviceId": device_id,
        "rtspUrl": source,
        "rtmpPushUrl": rtmp_stream,
        "streamName": f"live/{device_id}",
        "transport": transport if transport in ("tcp", "udp") else "tcp",
        "heartbeatUrl": os.getenv("VIDEO_EDGE_HEARTBEAT_URL", ""),
        "logDir": os.path.join(os.getenv("EDGE_STREAM_LOG_ROOT", "/opt/easyaiot/logs/edge-stream"), device_id),
    }
    command = enqueue_agent_command(
        node_id=int(edge_node_id),
        command_type="stream_forward.deploy",
        command_key=f"stream_forward:{device_id}",
        payload=payload,
    )
    return {"deviceId": device_id, "edgeNodeId": edge_node_id, "payload": payload, "command": command}
```

- [ ] **Step 5: Add the blueprint endpoint**

In `VIDEO/app/blueprints/stream_forward.py`, add:

```python
@stream_forward_bp.route('/device/<string:device_id>/ensure-edge-task', methods=['POST'])
def ensure_edge_task(device_id):
    try:
        from app.services.edge_stream_forward_service import ensure_edge_rtsp_forward

        data = request.get_json(silent=True) or {}
        edge_node_id = data.get('edge_node_id') or data.get('edgeNodeId')
        transport = (data.get('transport') or 'tcp').strip().lower()
        result = ensure_edge_rtsp_forward(device_id, edge_node_id=int(edge_node_id), transport=transport)
        return jsonify({'code': 0, 'msg': 'success', 'data': result})
    except Exception as e:
        logger.error('ensure edge stream-forward task failed device_id=%s: %s', device_id, e, exc_info=True)
        return jsonify({'code': 500, 'msg': str(e)}), 500
```

- [ ] **Step 6: Run the VIDEO test and verify it passes**

Run:

```powershell
$env:PYTHONPATH="E:\yFeiEye\VIDEO"
python -m unittest VIDEO.tests.test_edge_stream_forward_service
```

Expected: PASS.

- [ ] **Step 7: Commit Task 5**

```powershell
git add -- VIDEO/app/utils/media_client.py VIDEO/app/services/edge_stream_forward_service.py VIDEO/app/blueprints/stream_forward.py VIDEO/tests/test_edge_stream_forward_service.py
git commit -m "feat: enqueue edge rtsp stream forward commands"
```

### Task 6: First-Slice Verification

**Files:**
- Modify: `docs/superpowers/specs/2026-06-13-edge-agent-outbound-streaming-design.md`
  - Add an implementation status section only after the slice passes.

- [ ] **Step 1: Run focused Java tests**

Run:

```powershell
Set-Location E:\yFeiEye\DEVICE
mvn -pl iot-node/iot-node-biz -Dtest=NodeAgentCommandSchemaSqlTest,NodeAgentCommandServiceImplTest test
```

Expected: PASS with both test classes green.

- [ ] **Step 2: Run focused Python Agent tests**

Run:

```powershell
Set-Location E:\yFeiEye
$env:PYTHONPATH="E:\yFeiEye\NODE"
python -m unittest NODE.tests.test_agent_commands NODE.tests.test_stream_forward_executor
```

Expected: PASS.

- [ ] **Step 3: Run focused VIDEO test**

Run:

```powershell
Set-Location E:\yFeiEye
$env:PYTHONPATH="E:\yFeiEye\VIDEO"
python -m unittest VIDEO.tests.test_edge_stream_forward_service
```

Expected: PASS.

- [ ] **Step 4: Verify the no-inbound-Agent invariant**

Run:

```powershell
rg -n "stream_forward.deploy|ensure-edge-task|commands/poll|/workload/deploy|agentPort|9100" NODE DEVICE\iot-node VIDEO\app
```

Expected evidence:

- Edge path files contain `commands/poll` and `stream_forward.deploy`.
- Edge path code does not call `http://{node.host}:{agentPort}`.
- Existing direct deployment code may still contain `/workload/deploy`, `agentPort`, and `9100` for non-Edge managed nodes.

- [ ] **Step 5: Add implementation status to the spec**

Append:

```markdown
## Implementation Status

The first Edge RTSP outbound slice is implemented when:

- `iot-node` command queue tests pass.
- Python Agent command polling and stream-forward executor tests pass.
- VIDEO Edge orchestration test passes.
- The Edge path enqueues `stream_forward.deploy` and does not require the platform to reach Agent `9100`.

Remaining follow-on slices are signed RTMP ingest enforcement, unified access-center UI/state integration across all protocols, and production WebRTC TURN/STUN validation.
```

- [ ] **Step 6: Commit verification notes**

```powershell
git add -- docs/superpowers/specs/2026-06-13-edge-agent-outbound-streaming-design.md
git commit -m "docs: record edge outbound slice status"
```

### Task 7: Follow-On Planning Gates

**Files:**
- Create after Task 6 passes: `docs/superpowers/specs/2026-06-13-device-access-state-center-design.md`
- Create after Task 6 passes: `docs/superpowers/specs/2026-06-13-signed-rtmp-ingest-design.md`
- Create after Task 6 passes: `docs/superpowers/specs/2026-06-13-webrtc-nat-production-design.md`

- [ ] **Step 1: Device access center gate**

Write a separate spec that maps protocol events into these states:

```text
pending_config
registering
registered
stream_online
play_ready
ai_ready
error
```

Acceptance: GB28181, direct RTSP, Edge RTSP, and RTMP push each have one writer into the shared state vocabulary.

- [ ] **Step 2: Signed RTMP ingest gate**

Write a separate spec for HMAC push URLs:

```text
rtmp://media.example.com/live/{deviceId}?tenant={tenantId}&exp={unixSeconds}&ver={tokenVersion}&sig={hmac}
```

Acceptance: SRS/ZLM publish hooks reject missing, expired, wrong-tenant, and rotated-token pushes.

- [ ] **Step 3: WebRTC NAT gate**

Write a separate spec for media-node WebRTC production settings:

```text
STUN server
TURN server
candidate public IP/domain rewrite
HTTPS/WSS browser playback
mobile and cross-carrier smoke checks
```

Acceptance: public browser playback works over HTTPS/WSS from LAN, mobile hotspot, and a second carrier network.

---

## Final Verification Checklist

- [ ] Java focused tests pass.
- [ ] Python Agent focused tests pass.
- [ ] VIDEO Edge orchestration focused test passes.
- [ ] `rg` confirms Edge command path uses poll/result and not customer-site `9100`.
- [ ] Existing direct Agent deployment remains available for reachable managed nodes.
- [ ] `git status --short` reviewed so unrelated README and deployment-package changes are not staged into Edge commits.
