# 实施计划：SENTINEL 与 iot-node 控制面通信加固

设计：`docs/superpowers/specs/2026-10-22-sentinel-control-plane-hardening.md`

每个阶段单独提交，提交前在测试服务器容器内跑：

- `mvn -pl iot-node/iot-node-biz -am test`（只测 iot-node 及其依赖）
- `cd SENTINEL && python -m unittest discover -s tests -t .`（`python:3.9-slim` 与 `python:3.11-bullseye`）
- `node .scripts/verify-repository-secret-hygiene.mjs`

## 阶段 1：令牌哈希存储与迁移（iot-node）

1. `security/AgentTokenHasher`（HMAC-SHA256 加盐、常量时间校验）与 `security/AgentTokenCipher`（AES-256-GCM），纯 Java 单测。
2. `ComputeNodeDO` 新增列字段，令牌相关字段 `updateStrategy = NEVER`；`ComputeNodeMapper.updateAgentCredential`（乐观条件）。
3. `NodeAgentSecuritySchemaInitializer`：建 `node_agent_command`、补列、逐行迁移明文；`iot-node10.sql` 同步新列与新表、样例数据不变（启动时迁移）。
4. `NodeAgentCredentialService`：`issue / authenticate / currentToken / dispatchToken / migrateLegacy`；五处 `validateAgent`、创建节点、平台节点、EDGE 登记、bootstrap、详情展示、SSH 部署与 env 核对改用它。
5. `NodeAgentGateway`（先只有 HTTP 传输）：合并 5 处 Hutool 调用，令牌取 `dispatchToken`。
6. 测试：哈希/密文、迁移（含无密钥、retain-plaintext 回填、并发条件更新）、authenticate（旧明文兜底）、网关用 dispatchToken。

## 阶段 2：轮换推送与宽限期

1. iot-node：`rotateAgentToken(id, immediate)`、新接口 `/node/rotate-agent-token`；poll 记录 `agent_features`、为轮换指令注入令牌、处理 superseded；`reportResult` 校验状态、失败回滚；`authenticate` 确认 pending 并缩短旧令牌宽限。
2. SENTINEL：`AgentCredentials`、`update_env_file`（原子写+回滚）、执行器内置 `agent.token.rotate` 处理；`agent_server` / `run_sentinel` / remediator 跟随凭据变化；`persist_credentials` 改原子写。
3. 测试：Java 轮换状态机（pending 再轮换、immediate、确认缩短宽限、失败回滚、过期 generation）；Python 原子写（保留注释/权限、写失败不动原文件）、轮换成功、新令牌被拒回滚、网络错误保留、重复投递幂等。

## 阶段 3：控制面 HTTPS 与自定义 CA

1. SENTINEL：`control_plane_tls_verify()`，所有出站请求传 `verify`；CA 文件不可读时启动失败；http 时告警，`CONTROL_PLANE_REQUIRE_HTTPS=true` 时拒绝启动。
2. iot-node：`easyaiot.agent.control-plane-ca-file` → SSH 部署写 CA 文件与 `CONTROL_PLANE_CA_FILE`。
3. 测试：verify 参数取值、CA 缺失失败、无关闭开关；`AgentDeployUtil` 生成的 env / 安装脚本含 CA。

## 阶段 4：只出站

1. SENTINEL：白名单指令 → 进程内 `test_client` 调用；线程池 + 续租；`AGENT_INBOUND_ENABLED=false` 不起 HTTP；poll 声明能力。
2. iot-node：`NodeAgentGateway` 队列传输（REQUIRES_NEW 入队、轮询结果、超时取消、清空 payload）；`easyaiot.node.agent.inbound-enabled` 写入 env；只出站时部署校验改看心跳。
3. 测试：Python 指令经 test_client 仍校验令牌、未知类型拒绝、续租、并发槽位；Java 传输选择、超时取消、payload 清空、失败映射。

## 收尾

- 更新 `SENTINEL/agent.env.example`、`SENTINEL/README.md` 相关段落。
- 在共享笔记目录留交接笔记；最终报告列出改动、迁移步骤、兼容性与待决策事项。
