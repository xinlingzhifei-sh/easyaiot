# SENTINEL Agent 与 iot-node 控制面通信加固（P3：令牌、HTTPS、只出站）

## 状态

- 承接 `2026-10-20-sentinel-agent-hardening.md`（P0 Agent 侧加固、P1 出站通道放行已合入）。本次做需要改 iot-node 的部分：
  1. Agent 令牌在库里只存加盐哈希，常量时间比较，升级时自动迁移；
  2. 轮换令牌后经指令轮询通道推送到节点，新旧令牌在宽限期内都有效，Agent 原子改写 `agent.env` 并可回滚；
  3. `CONTROL_PLANE_URL` 支持 https 与自定义 CA，不提供关闭证书校验的开关；
  4. 补齐轮询通道对 9100 入站功能的覆盖，节点可配置为不监听 9100。
- 硬性约束不变：不绕过、不弱化 `agent_server.py` 的 `X-Agent-Token` 校验；远程节点 `PLATFORM_AGENT=0` 不改。
- 需要用户决定的事项见第 8 节。

## 1. 现状（读代码确认）

### 1.1 同一个令牌双向使用

`compute_node.agent_token` 明文存库，用于：

| 用途 | 位置 | 需要明文？ |
|---|---|---|
| 校验 Agent → 控制面请求（register / heartbeat / commands / remediate / edge runtime-config） | `NodeAgentServiceImpl`、`NodeAgentCommandServiceImpl`、`EdgeNodeServiceImpl`、`NodeSentinelRemediatorServiceImpl`、`NodeSentinelServiceImpl` 各自的 `validateAgent` | 否，哈希即可 |
| 控制面 → Agent 9100 请求的 `X-Agent-Token` 头 | `NodeCommandServiceImpl`、`NodeMediaServiceImpl`、`NodeMqttServiceImpl`、`NodeCameraAccessServiceImpl`、`NodeSentinelAgentClient` 共 5 处各写一份 Hutool 调用 | **是**：旧 Agent 用 `hmac.compare_digest(header, AGENT_TOKEN)` 比明文 |
| SSH 部署写 `agent.env`、部署后核对 `agent.env` | `ComputeNodeServiceImpl.deployAgentBySsh` / `probeAgentEnvConfig` | 是 |
| 平台宿主机 Agent bootstrap、EDGE 登记 / 运行时配置（MQTT 密码字段） | `getPlatformAgentBootstrap`、`EdgeNodeServiceImpl` | 是 |
| WEB 节点详情 / 手工安装模板展示令牌 | `toRespVO(node, true)` → `AgentEnvBatch`、`AgentSetupModal` | 是 |

结论：只要控制面还要调用 9100、还要给节点写 `agent.env`，控制面就必须能取回令牌明文。"库里只存哈希"在字面上无法与"不让已部署节点掉线"同时成立（旧 Agent 只认明文头），见 2.1 的取舍。

### 1.2 指令表在部署环境里不存在

`node_agent_command` 的建表语句只在 `iot-node-biz/src/main/resources/sql/node_agent_command_v1.sql`，该文件只被单测读取：`.scripts/postgresql/iot-node10.sql` 没有这张表，也没有任何启动期初始化。全新安装和升级环境的 `/commands/poll` 与 VIDEO 的 `stream_forward` 入队都会报"relation does not exist"。本次的推送和只出站都依赖它，先补上。

### 1.3 iot-node 的迁移方式

- 全新安装：`.scripts/postgresql/iot-node10.sql`（pg_dump 格式）由 `init-databases.sh` 在空库时导入。
- 升级：没有 Flyway；`NodeRecordingStorageSchemaInitializer` 在启动时执行幂等的 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 与数据修正。`resources/sql/*.sql` 是手工补丁，不会自动执行。

本次沿用"两处都写"：新列与新表写进 `iot-node10.sql`，同时由启动期初始化器幂等补齐；明文转哈希需要应用内的盐与密钥，只能在 Java 里做。

### 1.4 其他相关问题

- `heartbeat` / `register` 把整行 `ComputeNodeDO` 读出再 `updateById`，与 `resetAgentToken` 并发时会把刚写的新令牌覆盖回旧值。引入轮换后这个竞态会更频繁地出现。
- 指令执行器单线程串行、从不续租。租约 30 秒，`reclaimTimedOutCommands` 会把执行超过 30 秒的指令判为超时失败，部署（60–120 秒）和网段扫描（330 秒）无法直接搬到这条通道上。
- `reportResult` 原样接受 Agent 上报的任意 `status` 字符串。

## 2. 令牌存储

### 2.1 取舍：哈希用于校验，密文用于外发

| 方案 | 库泄露后 | 旧 Agent / 9100 | 结论 |
|---|---|---|---|
| A. 只存哈希 | 拿不到令牌 | 控制面无法再调用 9100、无法重写 `agent.env`，所有旧节点的部署/扫描失效 | 不可行（违反"不掉线"） |
| B. 哈希 + 应用层密钥加密的密文，明文列清空 | 只拿到库拿不到令牌（密钥在配置里，不在库里） | 不受影响 | **采用** |
| C. 继续存明文 | 直接泄露 | 不受影响 | 现状 |

- 所有"Agent → 控制面"校验只用哈希：`HMAC-SHA256(key = 每行 16 字节随机盐, msg = 令牌)`，存为 `hs256$<盐>$<MAC>`（base64url），比较用 `MessageDigest.isEqual`。令牌本身是 128 位随机数，盐不是防暴力破解所必需，按要求加上，同时让相同令牌在不同行的哈希不同。
- 需要明文的场景（1.1 表中"是"的各行）从密文解出：AES-256-GCM，12 字节随机 IV，存为 `gcm1$<base64(IV‖密文‖标签)>`。密钥 = `SHA-256("yfeieye:node-agent-token:v1:" ‖ 口令)`，口令取 `easyaiot.node.agent-token.encryption-key`，未配置时回退到已有的 `mybatis-plus.encryptor.password`（`MYBATIS_ENCRYPTOR_PASSWORD`，SSH 凭据加密已强制依赖它，凡是经 SSH 纳管过节点的环境都已配置）。不复用 `CredentialEncryptUtil`：它是 Hutool 默认的 AES/ECB。
- 第 6 节"只出站"全面启用、EDGE 的 MQTT 密码改为独立凭据后，密文列可以删掉，达到字面意义的"只存哈希"（第 8 节决策 1）。

### 2.2 新增列（`compute_node`）

| 列 | 说明 |
|---|---|
| `agent_token_hash varchar(160)` | 当前令牌哈希 |
| `agent_token_cipher varchar(256)` | 当前令牌密文 |
| `agent_token_prev_hash varchar(160)` / `agent_token_prev_cipher varchar(256)` | 轮换前的令牌，宽限期内仍有效 |
| `agent_token_prev_expires_at timestamp` | 旧令牌失效时间 |
| `agent_token_pending boolean default false` | 已轮换但还没见到 Agent 用新令牌回连 |
| `agent_token_generation integer default 0` | 轮换代次，推送指令据此判断是否过期 |
| `agent_features jsonb` | Agent 在 poll 中声明的能力（支持的指令类型、`inbound` 是否开放） |

`agent_token`（旧明文列）保留不删，迁移后置 NULL。`ComputeNodeDO` 中上述字段与 `agentToken` 全部标 `updateStrategy = NEVER`，`updateById` 不再写它们；令牌只经 `ComputeNodeMapper.updateAgentCredential(id, 新状态, 期望的当前哈希)` 显式更新（带乐观条件），顺带消除 1.4 的覆盖竞态。

### 2.3 迁移（升级时自动、可重入、多副本安全）

启动期 `NodeAgentSecuritySchemaInitializer`：

1. `CREATE TABLE IF NOT EXISTS node_agent_command` 及两个索引（内容同 `node_agent_command_v1.sql`）；
2. `ALTER TABLE compute_node ADD COLUMN IF NOT EXISTS` 上述各列；
3. 逐行处理 `agent_token IS NOT NULL` 的节点：算哈希与密文，`UPDATE ... SET hash, cipher, agent_token = NULL WHERE id = ? AND agent_token = ?`。条件更新保证多个 iot-node 副本同时启动时只有一个生效，另一个影响 0 行即跳过。

节点无感知：Agent 仍发同一个令牌，控制面改为比哈希；控制面调 9100 时从密文解出同一个值。

兜底与回退：

- **未配置密钥**：只写哈希，保留明文列并打 ERROR（与旧行为一致，节点不受影响）；配置密钥后下次启动自动补密文、清明文。
- **迁移前的请求 / 迁移失败的行**：校验时若哈希为空而明文列非空，按旧方式常量时间比较明文，成功后顺手迁移该行。
- **滚动升级期间旧版 iot-node 重置了令牌**：旧版只写明文列，新版启动时把非空明文视为最新值重新迁移，并在校验时同时接受它。
- **降级回旧版 iot-node**：旧版只读明文列。先以 `easyaiot.node.agent-token.retain-plaintext=true` 启动新版一次（迁移器会从密文回填明文列，且之后的签发/轮换也同时写明文），再切回旧版。默认 `false`。

## 3. 轮换与推送

### 3.1 控制面

`ComputeNodeService.rotateAgentToken(id, immediate)`（`reset-agent-token` 改为调用它，返回值仍是新令牌字符串，WEB 不用改；新增 `POST /node/rotate-agent-token` 返回 `{agentToken, pushed, generation, previousValidUntil}`）：

1. 生成新令牌；
2. 若当前**不处于** pending，把当前哈希/密文移到 prev，`prev_expires_at = now + 宽限期`；若已处于 pending（Agent 还拿着更早的令牌），prev 保持不动、只顺延到期时间——Agent 实际持有的仍是 prev；
3. `immediate=true` 时不保留 prev（令牌泄露时使用）；
4. 写入新哈希/密文，`pending = true`，`generation + 1`；
5. Agent 在 `agent_features` 中声明了 `agent.token.rotate` 时，入队一条 `agent.token.rotate` 指令（`commandKey = agent.token.rotate:<generation>`，payload 只含 `generation`），并把同节点更早的待执行轮换指令置为 `superseded`；否则 `pushed=false`，管理员需在宽限期内重新部署或手工改 `agent.env`。

**令牌明文不落指令表**：poll 把 `agent.token.rotate` 交给 Agent 时才把当前令牌解密放进响应 payload；若该指令的 `generation` 已不是当前代次，直接标 `superseded` 不下发。经 `/commands/by-key` 等管理接口读出的 payload 不含令牌。

校验（统一由 `NodeAgentCredentialService.authenticate` 完成，替换五处 `validateAgent`）：

- 匹配当前哈希 → 通过；若 pending，则置为已确认，并把 prev 到期时间缩短到 `min(原到期, now + 确认后宽限期)`；
- 匹配 prev 且未过期 → 通过；
- 其他 → `AGENT_TOKEN_INVALID`。

控制面调 9100 用的令牌（`dispatchToken`）：pending 且 prev 有效时用 prev（Agent 还没换），否则用当前令牌。

轮换结果：

- `succeeded`：无额外动作（Agent 用新令牌上报结果时已完成确认）；
- `failed` 且 `generation` 仍是当前代次、仍 pending：**控制面回滚**——prev 恢复为当前令牌、清空 prev、`pending=false`、`generation + 1`，页面上显示过的新令牌作废。理由：Agent 只在已经回滚到旧令牌之后才会上报失败，此时让控制面保持"新令牌为当前"只会让节点在宽限期结束时掉线。

默认值：宽限期 `easyaiot.node.agent-token.rotation-grace-seconds=259200`（72 小时，覆盖离线过周末的节点）；确认后宽限 `confirmed-grace-seconds=300`（覆盖进行中的请求）。

### 3.2 Agent

- 新增进程内唯一的 `AgentCredentials`（`agent_commands.py`）：心跳、指令客户端、自愈上报、9100 鉴权都从它取当前令牌；替换时同步 `os.environ['AGENT_TOKEN']`（`sentinel/remediator.py` 按调用时读环境变量）。
- `agent_server._check_token` 的比较逻辑一行不变（仍是 `hmac.compare_digest` 且拒绝空令牌），只是 `AGENT_TOKEN` 模块变量在轮换时被更新。Agent 端**不**同时接受新旧两个令牌。
- `update_env_file`：读原文件 → 只替换目标键（保留注释、顺序和其他键）→ 同目录 `mkstemp` 写临时文件 → 复制原权限与属主 → `fsync` → `os.replace` → `fsync` 目录。失败时临时文件删除、原文件不动。返回原内容，用于回滚。
- 处理 `agent.token.rotate`（由指令执行器内置处理，而不是普通 executor，因为它要改执行器自己的凭据）：
  1. 校验新令牌格式（非空、≤256、无空白与 `=`）；与当前相同则直接回报成功（重复投递幂等）；
  2. 原子写 `agent.env`，再切换内存令牌；
  3. 用新令牌对该指令重新 `ack`，作为控制面回连验证；
  4. 若控制面明确拒绝新令牌（`code = 1005002000`）：原子写回原 `agent.env`、切回旧令牌，用旧令牌上报 `failed`；
  5. 若只是网络错误：保留新令牌（控制面上新令牌已是当前值，不会失效），继续用新令牌上报；上报失败时租约到期后会重新投递，第 1 步的幂等判断保证不重复改文件；
  6. 写文件失败：不切换，上报 `failed`。
- 平台宿主机 Agent 的 bootstrap 刷新（`persist_credentials`）改用同一个原子写函数，原来的"截断后逐行写"在中途失败时会留下半个文件。

## 4. 控制面 HTTPS

- 控制面侧已支持：`easyaiot.agent.control-plane-url` 可直接写 `https://...`，写入节点 `agent.env` 的 `CONTROL_PLANE_URL` 随之变为 https。TLS 终止在网关前的 nginx/负载均衡（统一入口方案见 `2026-10-21-unified-ingress.md`），iot-node 本身不改监听。
- 新增控制面配置 `easyaiot.agent.control-plane-ca-file`：指向 PEM 文件时，SSH 部署把内容写到节点 `/opt/easyaiot/sentinel-agent/control-plane-ca.pem`（0644），并在 `agent.env` 写 `CONTROL_PLANE_CA_FILE=`。
- Agent：所有出站请求（心跳、注册、指令、自愈、bootstrap）统一用 `control_plane_tls_verify()` 作为 `requests` 的 `verify` 参数：
  - 配了 `CONTROL_PLANE_CA_FILE` → 该路径；文件不存在或不可读 → 启动失败退出（不回退到系统 CA，更不回退到不校验）；
  - 未配置 → `True`（系统/certifi CA）；
  - 不提供任何关闭校验的变量；`REQUESTS_CA_BUNDLE` 等环境变量只能替换 CA，不能关闭校验。
- `CONTROL_PLANE_URL` 仍是 http 时启动打 WARNING（令牌明文传输）；`CONTROL_PLANE_REQUIRE_HTTPS=true` 时直接拒绝启动。默认不强制，兼容现有 http 部署。

## 5. 9100 入站功能盘点

| 入站接口 | 控制面调用方 | 轮询通道覆盖（本次） | 指令类型 |
|---|---|---|---|
| `POST /workload/deploy`、`/workload/stop` | `NodeCommandServiceImpl` | 是 | `workload.deploy` / `workload.stop` |
| `POST /media/deploy` | `NodeMediaServiceImpl` | 是 | `media.deploy` |
| `POST /mqtt/deploy` | `NodeMqttServiceImpl` | 是 | `mqtt.deploy` |
| `POST /sentinel/probe`、`/sentinel/validate` | `NodeSentinelAgentClient` | 是 | `sentinel.probe` / `sentinel.validate` |
| `POST /camera/{discover,scan-segment,probe-onvif,probe-stream,nvr-channels}` | `NodeCameraAccessServiceImpl` | 是 | `camera.<op>` |
| `POST /media/stop`、`/mqtt/stop`、`GET /workload/list`、`GET /sentinel/profile` | 控制面未调用 | 一并注册（`media.stop`、`mqtt.stop`、`workload.list`、`sentinel.profile`） | — |
| `GET /health` | iot-node 经 SSH 在节点本机 curl `127.0.0.1` | 不需要；只出站时部署校验改看心跳 | — |
| `TRANSFORM/scripts/agent-deploy-containers.sh` 直连 `/workload/*` | 运维脚本 | 不覆盖；只出站节点上请用平台接口，或保留回环监听 | — |

## 6. 只出站

### 6.1 Agent

- 新变量 `AGENT_INBOUND_ENABLED`（默认 `true`）。为 `false` 时不启动 HTTP 服务，主线程守护心跳与指令线程；poll 时声明 `inbound=false`。只想关掉对外暴露、保留本机运维的，用已有的 `AGENT_LISTEN_HOST=127.0.0.1`。
- 第 5 节的指令类型统一由一个执行器处理：按**白名单**把指令类型映射到 `(方法, 路径)`，用 Flask `test_client` 在进程内发起请求，请求头带当前 `X-Agent-Token`。这样请求完整经过 `auth_middleware`（令牌校验、来源白名单、并发上限），HTTP 与轮询两条路径共用同一份处理代码，不会出现行为漂移；也不存在"轮询通道绕过令牌校验"的旁路。响应体 `code != 0` 时以 `msg` 作为失败原因上报，成功时上报 `{"data": <原 data>}`。
- 指令执行改为线程池（`COMMAND_MAX_CONCURRENCY`，默认 4），poll 只取空闲槽位数量的指令；执行中每 `COMMAND_LEASE_RENEW_SECONDS`（默认 10）秒调用 `/commands/{id}/heartbeat` 续租，长任务不再被判超时。轮换令牌指令在执行器线程外单独串行处理。
- poll 的 `capabilities` 声明：`inbound`（布尔）、`agent.token.rotate` 及所有已注册的指令类型。

### 6.2 控制面

- 新增 `NodeAgentGateway`，5 处 Hutool 调用合并到这里（错误统一抛 `AGENT_COMMAND_FAILED`，原媒体/MQTT 两处抛 `IllegalStateException` 的调用方没有捕获，行为上只是错误文案更明确）。
- 传输选择：节点 `agent_features.inbound == false` → 走指令队列；该节点 Agent 未声明对应指令类型 → 直接报错，不回退 HTTP（节点没开端口，回退必然失败）。其他情况（包括旧 Agent、未声明）→ 照旧 HTTP。`easyaiot.node.agent.prefer-queue=true` 时只要 Agent 声明支持就走队列。
- 队列调用：在**独立事务**（`REQUIRES_NEW`）里入队（调用方如 `deployWorkload` 处于事务中，否则 Agent 看不到未提交的指令，只能等到超时），每 500ms 在新事务里读一次状态，直到 `succeeded/failed` 或超时（沿用原 HTTP 超时）。超时时仍未被领取的指令置为 `canceled`。结束后把 `payload_json` 置为 `{}`——媒体/MQTT 部署的 env 含中间件凭据，HTTP 方式从不落库，队列方式也不应长期留存。
- `reportResult` 只接受 `succeeded` / `failed`。
- 新控制面配置 `easyaiot.node.agent.inbound-enabled`（默认 `true`）写入 SSH 部署的 `agent.env`。为 `false` 时部署后的"服务验证"步骤不再 curl `127.0.0.1:9100/health`，改为 `systemctl is-active` + 等待该节点 60 秒内出现新心跳。

### 6.3 默认值为什么暂不改成"不监听"

- 代码默认值（Agent 侧 `AGENT_INBOUND_ENABLED=true`）：手工安装、`install.sh update`、旧版控制面都可能与新 Agent 搭配，旧控制面只会走 HTTP；默认关掉会让这类节点"在线但所有部署失败"。
- 控制面写入的默认值（`easyaiot.node.agent.inbound-enabled=true`）：这里控制面确知自己支持队列，技术上可以默认 `false`。但出站通道（含本次补的建表）尚未在真实环境端到端验证过（见上轮笔记"Needs real-env verification"）；网段扫描（330 秒）走队列多出 0–3 秒轮询延迟、占用一个执行槽。建议在测试环境验证后再把控制面默认值改为 `false`（第 8 节决策 3）。

## 7. 兼容性

| 组合 | 行为 |
|---|---|
| 新控制面 + 旧 Agent | 不受影响：哈希校验同一令牌；9100 调用用解密出的同一令牌；轮换不推送（旧 Agent 不声明能力），旧令牌在宽限期内仍可用，期间重新部署即可 |
| 旧控制面 + 新 Agent | 不受影响：新 Agent 默认仍监听 9100；poll 多带的 `capabilities` 字段旧控制面忽略 |
| 新控制面 + 新 Agent（默认配置） | 轮换自动推送；9100 仍开放，控制面仍走 HTTP |
| 新控制面 + 新 Agent（`AGENT_INBOUND_ENABLED=false`） | 第 5 节功能全部经队列；`TRANSFORM/scripts/agent-deploy-containers.sh` 不可用 |
| 降级 iot-node | 见 2.3"降级" |

## 8. 需要用户决定的事项

1. **"只存哈希"的字面要求**：本设计保留了应用层密钥加密的密文（库泄露拿不到令牌，但配置与库同时泄露可以）。彻底去掉密文需要：所有节点只出站、EDGE 的 MQTT 密码改用独立凭据、WEB 只在创建/重置时展示令牌（详情页不再能查看）。是否作为下一阶段目标？
2. **密钥来源**：默认回退复用 `MYBATIS_ENCRYPTOR_PASSWORD`；是否要求单独配置 `easyaiot.node.agent-token.encryption-key`（更换 MyBatis 加密口令时不牵连令牌，但多一个必配项）？两者都更换时已有密文全部失效，需重新部署节点。
3. **控制面写入的 `AGENT_INBOUND_ENABLED` 默认值**：建议测试环境端到端验证后改为 `false`。
4. **宽限期**：72 小时 / 确认后 5 分钟是否合适；令牌疑似泄露时应使用 `immediate=true`，WEB 目前没有入口。
5. **轮换推送失败时控制面回滚到旧令牌**（3.1）：替代方案是保持新令牌、等待人工处理，但宽限期结束后节点会掉线。
6. **控制面 HTTPS 的证书由谁签发**：本次只做 Agent 侧校验与 CA 分发；网关前的 TLS 终止归统一入口方案。
