# SENTINEL Agent 攻击面收敛设计

## 状态

- 推荐：先落地方案 C 中对控制面零影响的部分（本次已完成），以方案 B（只出站）为目标架构分阶段推进
- 本次实施：Agent 侧改用 waitress、监听地址支持多地址、离线包缺 waitress 时降级不阻断部署
- 2026-10-21 追加（仍只改 Agent）：方案 C 第 4 项的鉴权失败限速与摄像头网段扫描并发上限，以及第 5 项的应用层替代——可选来源白名单 `AGENT_ALLOWED_SOURCES`，见第 6 节"P0 追加"
- 待决策：见第 7 节

## 背景

架构评估把 SENTINEL Agent 列为 P0 安全项：`SENTINEL/agent_server.py` 默认监听 `0.0.0.0:9100`，用 Flask 开发服务器（`app.run(threaded=True)`）提供明文 HTTP，鉴权只有 `X-Agent-Token`。该端口能下发 `docker run` / 进程启动、媒体栈与 MQTT 部署，是节点上权限最高的入口。

硬性约束：不得绕过或削弱 `X-Agent-Token` 校验；远程节点 `agent.env` 中 `PLATFORM_AGENT=0` 不得改为 1；不能破坏 iot-node 与 Agent 的现有通信。

## 1. 现状调查

### 1.1 控制面 → Agent（入站 9100）

地址统一为 `http://{compute_node.host}:{compute_node.agent_port}`（`agent_port` 默认 9100），明文 HTTP，Hutool `HttpRequest`，请求头带 `X-Agent-Token`。

| 调用方（iot-node） | Agent 接口 | 超时 | 语义 |
|---|---|---|---|
| `NodeCommandServiceImpl.deployWorkload` | `POST /workload/deploy` | 60s | 同步拿 `pid`，同一事务写 `node_workload_binding`；返回"工作负载已运行"时先停再重试 |
| `NodeCommandServiceImpl.stopWorkload` | `POST /workload/stop` | 60s | 同步拿 `removed`，为 false 时报错 |
| `NodeMediaServiceImpl` | `POST /media/deploy` | 120s | 同步 |
| `NodeMqttServiceImpl` | `POST /mqtt/deploy` | 120s | 同步 |
| `NodeSentinelAgentClient`（`NodeSentinelServiceImpl`） | `POST /sentinel/probe`、`POST /sentinel/validate` | 120s | `validate` 在每次 `deployWorkload` 前同步调用 |
| `NodeCameraAccessServiceImpl` | `POST /camera/{discover,scan-segment,probe-onvif,probe-stream,nvr-channels}` | 90s，`scan-segment` 330s | 管理端页面同步等待 |

- Agent 暴露但控制面未调用：`GET /workload/list`、`POST /media/stop`、`POST /mqtt/stop`、`GET /sentinel/profile`。其中 `/workload/deploy`、`/workload/list` 还被运维脚本 `TRANSFORM/scripts/agent-deploy-containers.sh` 直接调用。
- `GET /health` 不鉴权。iot-node 的部署校验（`ComputeNodeServiceImpl.buildAgentHealthProbeScript`）是经 SSH 在节点本机 `curl http://127.0.0.1:{port}/health`，不走网络入站；所以 Agent 至少要监听回环地址。

### 1.2 Agent → 控制面（出站）

`CONTROL_PLANE_URL` 默认经网关 `http://<gateway>:48080/admin-api/node/agent`，同样是明文。

| Agent 代码 | 接口 | 频率 | 凭据位置 |
|---|---|---|---|
| `run_sentinel.register/heartbeat` | `POST /node/agent/register`、`/heartbeat` | 心跳 10s | JSON body `nodeId` + `agentToken` |
| `agent_commands.AgentCommandClient` | `POST /node/agent/commands/poll`、`/{id}/ack`、`/{id}/heartbeat`、`/{id}/result` | 空闲时每 3s 轮询 | JSON body |
| `sentinel/remediator.py` | `POST /node/sentinel/remediate`、`/remediate-report` | 缺组件时 | JSON body（控制面也接受 `X-Agent-Token` 头） |
| `run_sentinel.try_refresh_credentials` | `GET /node/platform-agent-bootstrap` | 仅 `PLATFORM_AGENT=1` | `X-Bootstrap-Token` |

### 1.3 Token 来源与轮换

- 每个节点一个 token：`compute_node.agent_token`，由 `IdUtil.fastSimpleUUID()` 生成，明文存库。
- 同一个值双向使用：控制面调 Agent 放在 `X-Agent-Token` 头，Agent 调控制面放在 body 的 `agentToken`。任一侧泄露即两侧都失守。
- 轮换：`ComputeNodeServiceImpl.resetAgentToken` 只改库，不推送到 Agent；节点旧 token 心跳失败后需要重新 SSH 部署（重写 `agent.env`）。只有控制面宿主机 Agent（`PLATFORM_AGENT=1`）能经 bootstrap 自动刷新。

### 1.4 监听地址由谁决定

`AGENT_LISTEN_HOST=0.0.0.0` 被所有正式安装路径显式写进 `agent.env`（`AgentDeployUtil.buildEnvContent`、`.scripts/node/ensure_platform_agent.sh`、`ensure_platform_agent_centos7.sh`、WEB 手工安装模板 `WEB/src/views/node/utils/constants.ts`、`SENTINEL/agent.env.example`），自 2026-06-12 首个版本起就如此。`probeAgentEnvConfig` 校验 NODE_ID / TOKEN / CONTROL_PLANE_URL / LISTEN_PORT，不校验监听地址。iot-node 重新部署时会用 `0.0.0.0` 覆盖节点上的 `agent.env`。

### 1.5 已有的出站指令通道

`node_agent_command` 表 + `NodeAgentCommandServiceImpl`（30s lease、最多 3 次尝试、`commandKey` 幂等），设计见 `docs/superpowers/specs/2026-06-13-edge-agent-outbound-streaming-design.md`。目前只有 VIDEO 的 `stream_forward.deploy/stop` 走这条通道，Agent 侧 `build_command_executors` 也只注册了这两种。

### 1.6 调查中发现、不在本次改动范围内的问题

以下结论来自阅读代码与配置，未在运行环境中复现：

1. **出站指令通道可能被安全配置拦截**：iot-node `SecurityConfiguration` 只放行 `/node/agent/register` 与 `/heartbeat`；`/node/agent/commands/poll|ack|heartbeat|result` 和 `/node/sentinel/remediate*` 落入 `/node/** → @ss.isAdminUser()`，Agent 不带管理端登录态，按配置应被拒。方案 B 依赖这条通道，必须先修。
2. **iot-node 同步的 Agent 文件不全**：`AgentDeployUtil.SYNC_RELATIVE_FILES` 缺 `env_security.py`、`agent_commands.py`、`stream_forward_executor.py`；`media_manager.py` / `mqtt_manager.py` 顶层 import `env_security`，全新节点经 iot-node SSH 部署时 `install.sh` 的 import 校验应会失败。
3. **token 强度与比较方式**：`fastSimpleUUID` 基于 `ThreadLocalRandom`，不是密码学安全随机数；控制面 `NodeAgentCommandServiceImpl.validateAgent` 用 `String.equals`，不是常量时间比较。
4. **离线包就绪判断过宽**：`isAgentPipWheelsReady` 只检查 flask / requests / psutil / minio 等少数 wheel，`requirements.txt` 新增依赖后控制面缓存的旧离线包不会自动刷新（本次的 waitress 就会碰到，见第 6 节的降级处理）。
5. `WSDiscovery==2.1.2`、`onvif-zeep==0.2.12` 在 PyPI 只有 sdist，`export_pip_wheels.sh` 用 `--only-binary=:all:` 时只能依赖 `.build-cache/*/pip-wheels` 里预先构建的 wheel。

## 2. 哪些入站调用可以改为 Agent 主动拉取

| 接口 | 能否改拉取 | 要点 |
|---|---|---|
| `/workload/deploy`、`/workload/stop` | 能 | 结果经 `/commands/{id}/result` 回报 `pid` / `removed`；`deployWorkload` 需从"同步拿 pid 再写 binding"改为"binding 先记 deploying，结果回来再更新" |
| `/media/deploy`、`/mqtt/deploy` | 能 | 本身就慢（120s 超时），异步化收益最大 |
| `/sentinel/validate` | 能，需改语义 | Agent 拉到部署指令后先本地 validate，失败直接回报 failed；控制面用心跳里的 sentinel 快照做预筛 |
| `/sentinel/probe` | 能 | L0/L1 已随心跳上报；按需的 L2 作为一条 `sentinel.probe` 指令 |
| `/camera/*` | 能，但交互变慢 | 页面同步等待最长 330s；改为"入队 + 服务端等待结果（带超时）"，需要长轮询，否则每次多 0–3s |
| `/health` | 不需要 | 已只在节点本机经 SSH 调用 |

复用心跳响应下发指令：可行但不建议做主通道——心跳 10s 一次、payload 大、心跳失败会触发重注册。更合适的做法是心跳响应只带"有待拉取指令"标志，命令循环见到后立即 poll。

## 3. 方案 A：保留入站端口，加 TLS + 按节点独立 token

做法：

- Agent 以 HTTPS 提供服务。waitress 不支持 TLS，需要前置 stunnel / nginx sidecar，或换成 cheroot、gunicorn 等支持 TLS 的服务器（新依赖）。
- 证书可以自签（控制面按节点 pin 指纹），也可以由控制面 CA 在 SSH 部署时签发并下发。
- token 本身已是每节点独立；需要再拆成"控制面→Agent"与"Agent→控制面"两个方向的不同 token，用 SecureRandom 生成，库里存哈希。

对 iot-node 的影响：

- 5 处 `callAgent` / `postAgent` 改 HTTPS 并校验证书；`compute_node` 增加 scheme、证书指纹或 CA 字段。
- 部署流程增加签发与分发证书的步骤；`resetAgentToken` 需要推送到节点；证书到期要有轮换任务。

迁移：双栈期 Agent 同时监听 HTTP 9100 与 HTTPS 新端口，控制面按节点字段逐台切换；全部切完再关闭 HTTP。

评价：同步语义和时延不变，改动集中；但入站端口仍在，NAT 或防火墙后的节点仍不可达，而且证书生命周期带来持续运维成本。

## 4. 方案 B：只出站，拉取指令，去掉入站端口

做法：

- 扩展 `node_agent_command` 的 `commandType`：`workload.deploy/stop`、`media.deploy`、`mqtt.deploy`、`sentinel.probe/validate`、`camera.*`。
- Agent 在 `build_command_executors` 注册对应 executor，直接复用 `agent_server` 中的 manager，不重写业务逻辑。
- 轮询改成长轮询：poll 请求最多挂起约 25s，有指令立即返回；控制面用 `DeferredResult`，避免占住 servlet 线程。
- iot-node 的同步接口保持外观不变：内部 enqueue 后在服务端等待 result（带超时）。

前置条件：

- 修复 1.6 第 1 条，放行 `commands/*` 与 `sentinel/remediate*`（业务层已校验 nodeId + agentToken）。
- 控制面入口上 HTTPS。出站请求同样携带 token，明文同样能被嗅探。

对 iot-node 的影响（最大）：

- `deployWorkload` 的同步事务改成异步状态机。
- 摄像头扫描改为服务端阻塞等待或前端异步。
- 删除 Hutool 直连代码；命令表需要清理任务。
- 部署校验从"端口在监听"改为"最近一次 poll 时间"。

迁移：

1. 修安全放行；Agent 实现全部 executor，并在 poll 的 `capabilities` 里声明支持的 `commandType`。入站接口保留，完全向后兼容。
2. 控制面按节点能力位逐类切到队列；不支持的旧 Agent 继续走 HTTP。
3. 全部切换后新增 `AGENT_INBOUND_ENABLED`，默认 false，关闭 9100；`TRANSFORM/scripts/agent-deploy-containers.sh` 改为调用控制面接口。

评价：从根本上消除入站面，NAT 后的节点也能用（与 2026-06-13 设计目标一致），token 只出现在出站 HTTPS 中；代价是工作量大，同步交互多一个往返。

## 5. 方案 C：最小改动——生产级 WSGI、收紧监听、速率限制

做法：

1. 用 waitress 替换 Flask 开发服务器（本次已做）。
2. `AGENT_LISTEN_HOST` 支持多地址（本次已做）。推荐配置为 `<节点内网 IP>,127.0.0.1`：内网 IP 供控制面访问，回环地址供 iot-node 经 SSH 做健康探测。
3. iot-node 写 `agent.env` 时不再写 `0.0.0.0`，改写节点内网地址加 `127.0.0.1`。
4. 按来源 IP 对鉴权失败限速（例如 60s 内失败 20 次后返回 429），并限制 `/camera/scan-segment` 等重操作的并发。
5. 主机防火墙只允许控制面出口 IP 访问 9100：`install.sh` 可选生成 nftables / firewalld 规则，或写入部署文档。

对 iot-node 的影响：

- 第 1、2 项：零影响（新代码兼容旧 `agent.env`，见第 6 节）。
- 第 3 项：要改 `AgentDeployUtil.buildEnvContent`。`compute_node.host` 可能是 NAT 后的公网地址、本机并不存在，waitress 绑定会失败，所以要么由运维确认，要么让 Agent 在绑定失败时回退并告警。
- 第 4 项：如果控制面用旧 token 调用（例如刚重置 token、节点尚未重新部署），限速可能把控制面 IP 也挡在外面。
- 第 5 项：需要知道控制面出口 IP；多副本或网关 NAT 时这个 IP 会变。

迁移：Agent 侧改动随下次部署或 `install.sh update` 生效；控制面只改 `AgentDeployUtil` 一处；防火墙按环境逐步启用。

评价：小、快、可回退；但仍是明文 HTTP 加入站端口，同网段可以嗅探 token 后重放，也不解决 NAT。

## 6. 推荐与分阶段计划

推荐：**先做 C 的零影响部分，以 B 为目标架构**。

不建议把 A 作为终点：证书运维成本高，入站面也还在。如果在 B 完成前必须跨不可信网络（公网）直连节点，可以用"在 stunnel / nginx sidecar 上终止 TLS"作为过渡。

| 阶段 | 内容 | 是否动 iot-node |
|---|---|---|
| P0（本次） | waitress；多地址监听；监听所有网卡时启动告警；离线包缺 waitress 时降级安装；鉴权失败限速、扫描并发上限、可选来源白名单 | 否 |
| P1 | 放行 `commands/*` 与 `sentinel/remediate*`；补齐 `SYNC_RELATIVE_FILES`；token 改 SecureRandom，控制面改常量时间比较；`isAgentPipWheelsReady` 增加 waitress 检查；`AgentDeployUtil` 改写监听地址 | 是（小改） |
| P2 | B 主体：Agent 注册全部 executor，控制面逐类切到队列加长轮询 | 是（大改） |
| P3 | 默认关闭入站端口；控制面入口 HTTPS；token 按方向拆分并哈希存储 | 是 |

### 本次 P0 落地内容

- `agent_server.run_server` 改用 `waitress.serve`，线程数由 `AGENT_HTTP_THREADS` 控制，默认 16。Flask 开发服务器原本是每连接一个线程；控制面最长请求 330s，线程太少会让并发扫描挤占健康探测。waitress 的 `channel_timeout` 只回收没有在途请求的空闲连接，不会打断长请求。
- waitress 无法导入时（旧节点只执行了 `install.sh update` 而没有重装依赖，或控制面用的是旧离线包），回退到 Flask 开发服务器并打 WARNING，保证 Agent 照常启动、与控制面的通信不中断。
- `AGENT_LISTEN_HOST` 支持逗号或空格分隔的多个地址，IPv6 地址自动加方括号。同时写了通配地址（如 `0.0.0.0,127.0.0.1`）时自动去掉同族的具体地址，避免 Linux 下端口冲突导致 Agent 起不来。未设置时默认值仍是 `0.0.0.0`，监听所有网卡时启动打 WARNING。
- `X-Agent-Token` 校验（`_check_token` 与 `before_request`）一行未改，Flask 应用对象与路由不变。
- `requirements.txt` 锁定 `waitress==3.0.2`：纯 Python，无传递依赖，要求 Python ≥ 3.9。3.0.1 修复了 GHSA-3f84-rpwh-47g6（半开连接导致忙等）和 GHSA-9298-4cf8-g4wj（请求走私）。现有的 `WSDiscovery==2.1.2` 已要求 Python ≥ 3.9，所以 Agent 的 Python 下限没有变化。
- `export_pip_wheels.sh` 在没有目标 Python 做 dry-run 校验时，按文件名检查的清单也加入 waitress，旧离线包会被判为不完整并刷新。
- `install.sh`：离线 wheel 目录缺 waitress 时，跳过它安装其余依赖并告警，不让整次部署失败（对应 1.6 第 4 条）；安装后检查 waitress 能否导入，不能则告警。

### P0 追加（2026-10-21，仍只改 Agent）

全部实现在 `agent_server.py` 内，没有新增模块文件：iot-node 按固定清单同步 Agent 文件，新增文件不会同步到已部署节点，会导致导入失败。

- **来源白名单** `AGENT_ALLOWED_SOURCES`（IP 或 CIDR，逗号/空格分隔），默认为空、不限制。配置后不在名单内的来源一律 HTTP 403，包括 `/health`。回环地址始终放行，iot-node 经 SSH 的健康探测不受影响。条目全部写错时只放行回环地址（收紧而不是放开），启动时打 ERROR。只看套接字对端地址，不读 `X-Forwarded-For`。这是第 5 项主机防火墙的应用层替代，不依赖 nftables / firewalld。
- **鉴权失败限速**：同一来源在 `AGENT_AUTH_FAIL_WINDOW`（默认 60s）内失败 `AGENT_AUTH_FAIL_LIMIT`（默认 20）次后，封禁 `AGENT_AUTH_BLOCK_SECONDS`（默认 300s），期间返回 HTTP 429 并带 `Retry-After`。封禁期内携带正确令牌的请求同样拒绝，否则"猜中 200、猜错 429"仍可用来试探令牌。成功请求不计数；回环地址不限速；`/health` 不计数；`LIMIT=0` 关闭。状态只在内存中，最多跟踪 4096 个来源。第 5 节提到的"控制面用旧令牌被挡"问题影响有限：令牌不对时请求本来就会失败，而 iot-node 重新部署会重写 `agent.env` 并重启 Agent，限速状态随之清空。
- **摄像头网段扫描并发上限** `AGENT_CAMERA_SCAN_CONCURRENCY`（默认 2，0 表示不限）：超出时立即返回 HTTP 429，不排队占线程。iot-node 把非 2xx 作为 `AGENT_COMMAND_FAILED` 原样报给管理端。
- 原有鉴权失败契约不变：HTTP 400、body `code=401`、`msg=Agent 认证失败`。

### 为什么本次不把默认监听地址改成 127.0.0.1

所有正式安装路径都显式写了 `AGENT_LISTEN_HOST=0.0.0.0`（见 1.4），代码里的默认值只对缺这一行的手写 `agent.env` 生效。改默认值对正式部署没有任何安全收益，却可能让这类手写配置的节点"心跳在线、控制面调用全部连接被拒"，而且 iot-node 经 SSH 走 127.0.0.1 的健康探测发现不了这个问题。真正有效的收紧是 P1 中由 iot-node 写入具体地址，那属于控制面改动，留待决策。

## 7. 需要决策的事项

1. 是否以方案 B 为目标架构。它会改变部署、摄像头扫描等接口的同步语义，并带来较大的 iot-node 工作量。
2. P1 的控制面小改是否立即排期。其中 1.6 第 1、2 条疑似已经影响功能（出站指令通道、全新节点部署），建议先在测试环境复现确认。
3. iot-node 写入 `agent.env` 的监听地址取什么值：`compute_node.host` 加 `127.0.0.1`，还是新增"节点内网地址"字段；以及绑定失败时是回退到 `0.0.0.0` 还是让 Agent 启动失败。
4. 控制面网关入口是否上 HTTPS。这会改变所有节点 `agent.env` 中的 `CONTROL_PLANE_URL`，A 和 B 都以它为前提。
5. 是否引入鉴权失败限速和主机防火墙白名单，以及控制面出口 IP 如何确定。
