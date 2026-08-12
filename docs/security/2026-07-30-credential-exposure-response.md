# 2026-07-30 凭据泄露处置清单

## 当前已完成的源码侧封堵

- 已停止跟踪运行时 `.env`、`NODE/agent.env`、前端备份文件、`deploy-packages/`、8 个 FUXA 运行库/配置、Milvus Lite 人脸向量库以及 pnpm/产测/OpenVSCode/根输出等生成态文件；文件仍保留在本机，未删除。
- 根 `.gitignore` 已覆盖运行时环境文件、部署产物、本地输出和备份文件；公开的 `*.env.example` 与明确列出的前端模式文件继续受版本控制。
- `APP/env/.env.example` 关闭客户端字段加密，并将密钥字段留空。客户端内置密钥无法作为秘密，不能替代 TLS 或服务端鉴权。
- `.scripts/docker/env.example` 中的固定凭据已改为 `CHANGE_ME`。
- AI/VIDEO 的 Nacos/MinIO 示例凭据已改为 `CHANGE_ME`；WEB 的 `VITE_GPUSTACK_PASSWORD` 已置空，避免把密码编译进公开前端包。
- WEB development/production 中的天地图浏览器 Key 已置空；仍须在供应商侧轮换或限制域名、配额和 API 范围。APP 构建不再回显整份环境对象，文档图片 URL 的历史签名参数已删除。
- `.scripts/docker/set_permanent_token.sh` 已改为拒绝执行，不再携带固定 Redis 凭据或直接改写登录 Token。
- DEVICE 的 101 份主配置 YAML 和对应 Compose 已改为环境变量契约；`.scripts/verify-device-credential-config.mjs` 会拒绝 YAML/Compose 字面量、URL 内凭据、未声明变量和未传入对应容器的变量，且诊断不回显值。
- 6 份 `TASK/config/test*.ini` 已停止跟踪并加入忽略规则，文件仍留在本机；公开 TASK 示例和两个 ZLM 配置已改为 `CHANGE_ME`。
- ZLM、PostgreSQL、MySQL、MinIO 与 Nexus 辅助脚本不再内置或回显固定凭据；新增 `.scripts/mysql/.env.example` 只保留占位契约。
- `.scripts/postgresql/iot-message10.sql` 中两处第三方 webhook 已替换为 `CHANGE_ME`；`.scripts/postgresql/ruoyi-vue-pro10.sql` 已移除 17325 行访问/错误/登录/操作日志与 OAuth access/refresh Token 运行数据。
- `VIDEO/test_wxcp_alert_chain.py` 的企业微信 Secret 已改为运行时环境变量，缺失时拒绝联调；测试输出不打印 Secret。
- 新增 `.scripts/verify-repository-secret-hygiene.mjs`，会拒绝常见云/服务 Token、JWT、私钥、受跟踪 `.env` 敏感值及已知运行时/部署路径，且诊断不回显匹配值。

这些改动只阻止上述文件继续扩散，**不会清除 Git 历史，也不会使已经泄露的凭据失效**。

## 当前源码侧迁移结果与验证边界

- 2026-07-31 初次复扫在 DEVICE 的 80 个 YAML 中检出 240 个密码/Token 类非环境变量位点；把用户名、URL userinfo/query 与 Compose 字面量纳入后，整改前基线扩展为 386 项。
- 当前门禁扫描 101 份主配置 YAML 加 `DEVICE/docker-compose.yml`，输出 `DEVICE_CREDENTIAL_CONFIG_OK files=102`；配套 5 个测试通过，101 份 YAML 可解析，mini/standard/full 三种 Compose 形态可静态展开，全 DEVICE Maven compile 为 `BUILD SUCCESS`。
- 仓库级门禁扫描 7463 个受跟踪文本文件，输出 `REPOSITORY_SECRET_HYGIENE_OK files=7463`；与 DEVICE 门禁合计 8 个 `node:test` 用例全部通过。`deploy-packages/`、FUXA 运行数据、Milvus Lite 人脸库、根 `output/`/`.artifacts/` 当前跟踪数均为 0。
- `git ls-files -ci --exclude-standard` 当前只返回两个有意保留的例外：`.vscode/settings.json` 和 GB28181 的第三方 JAR；其他已忽略运行时文件不再受 Git 跟踪。
- 这些结果只证明当前工作区的配置契约、语法和编译边界。仍须在受控环境注入真实 Secret，分别启动受影响 profile，验证 Nacos、数据库、Redis、对象存储、MQTT、证书与第三方客户端连接。
- 当前检查针对已确认的 DEVICE 配置与点名泄露路径，不是对全部历史、所有分支/标签或线上 Secret 的替代性认证。

## 必须在受控环境完成的轮换

1. 立即吊销并重新签发 NODE Agent Token、平台 Agent 引导 Token、EDGE 加入 Token。
2. 轮换 PostgreSQL、Redis、Nacos、MinIO、EMQX、MySQL、ZLM、GB28181/SIP、Nexus 及 AI/VIDEO/DEVICE 服务使用的全部密码、访问密钥、证书口令和应用 Secret。
3. 分别生成并部署不同的 `EMQX_WEBHOOK_TOKEN`、`IOT_MESSAGE_INTERNAL_TOKEN`、`IOT_SINK_POST_PROCESS_TOKEN`、`GB28181_MEDIA_HOOK_TOKEN`、`SMS_CALLBACK_TOKEN`；每项至少 32 字节，不得复用用户登录 Token、中间件密码或彼此复用。EMQX、VIDEO/DEVICE 客户端、ZLM/ABL 及短信供应商回调配置必须与服务端同步；查询参数承载 Token 时必须使用 HTTPS 并对接入日志中的 query string 脱敏。
4. 轮换 FUXA signing/API/user 凭据、企业微信联调 Secret、飞书/企业微信 webhook、天地图 Key，并收紧第三方平台上的来源域名、权限和配额。
5. 若 APP 历史 AES 字段加密密钥仍被服务端接受，停止接受旧密钥；客户端改为仅依赖 HTTPS 与服务端会话鉴权。
6. 评估已入库的 Milvus Lite 人脸向量数据是否属于真实生物特征数据；如是，按数据事件流程确认访问范围、留存与重新采集/删除要求，而不是只做 Git 清理。
7. 更新服务器、密钥管理系统和部署平台中的变量后，逐服务重启并验证；不要把新值回填到仓库文件。
8. 检查相关系统在泄露窗口内的登录、Token 签发、远程部署、对象存储、消息 webhook 和数据库审计日志。

## Git 历史清理方案

历史改写会改变所有受影响提交的哈希，并要求强制更新远端分支和标签。执行前必须取得仓库管理员授权，冻结写入，备份远端，并通知所有协作者重新克隆。建议使用 `git filter-repo` 按以下路径做反向过滤：

- `.scripts/docker/.env.docker`
- `AI/.env`、`AI/.env.docker`、`AI/.env.prod`
- `AI/services/ai_service/.env.docker`、`AI/services/ai_service/.env.prod`
- `APP/env/.env`
- `NODE/agent.env`
- `VIDEO/.env`、`VIDEO/.env.docker`、`VIDEO/.env.prod`
- `WEB/.env.development.bak`
- `TASK/config/test*.ini`
- `deploy-packages/`
- `.scripts/docker/fuxa_data/`
- `VIDEO/data/face_db/milvus_lite.db`
- `.pnpm-store/`、`.artifacts/`、`.scripts/docker/vscode_data/`
- `.scripts/docker/.web_deploy_profile_built`
- `output/doc/`

DEVICE YAML、TASK ZLM 配置、`WEB/.env.development`、`WEB/.env.production`、`VIDEO/test_wxcp_alert_chain.py`、APP 文档案例、两份 PostgreSQL 种子 SQL 及辅助脚本仍需保留当前安全版本，不能简单整路径删除。应先基于已经轮换的旧值生成受控 `--replace-text` 清单；对 SQL 运行数据需用可审计的路径/内容过滤规则，再对所有分支/标签执行改写。清单和扫描日志不得回显或提交秘密。

刷新 origin 后，当前“96 个唯一历史提交”的可复现路径集合共 27 条路径：上列 21 个反向过滤 pathspec，再加 `.scripts/postgresql/iot-message10.sql`、`.scripts/postgresql/ruoyi-vue-pro10.sql`、`VIDEO/test_wxcp_alert_chain.py`、`WEB/.env.development`、`WEB/.env.production` 与 `APP/docs/.vitepress/composables/cases.ts`；对该集合执行只输出提交哈希的 `git log --all` 并去重，当前计数为 96。后六项必须做内容级替换，不能整文件删除。APP 文档路径由 2026-08-04 的 gitleaks 脱敏历史扫描补充，原 26 路径清单漏列了它。

按上列待反向过滤路径以及本轮点名的 FUXA/SQL/前端/联调路径统计，当前涉及 96 个唯一历史提交；DEVICE 101 份 YAML 的内容级扫描范围不计入该数字。该数字是路径级命中范围，不表示 96 个提交都含仍有效的秘密；正式改写前仍需冻结写入，并基于已轮换旧值生成不回显秘密的受控替换规则。

历史清理完成后仍须保留轮换动作；历史改写不能撤销已经被读取或复制的凭据。

## 2026-08-04 独立镜像演练结果

- 当前仓库原为 `blob:none` partial clone，本地缺 384 个 promisor Blob；单对象验证后，以一次性 refetch 补全为 0，`git fsck --full --strict` 通过。该步骤只补全本地对象并刷新 `refs/remotes/origin/*`，未改工作区、索引或本地分支。
- origin 当前只有 `main` 与 `codex/login-page-restoration` 两个分支、0 个标签；本地另有 46 个分支。演练镜像把两个远端分支显式映射为临时 heads，与本地分支和 stash 一并覆盖 927 个唯一提交。
- 历史中有 `.image/open-source-guardian/阿旺*.png` 这一 Windows 非法路径。Git for Windows 默认 fast-import 可复现失败；只在临时 bare 镜像设置 `core.protectNTFS=false` 后，同一最小输入与完整 git-filter-repo v2.47.0 改写均成功。源仓库该配置保持 unset。
- 21 条整路径规则改写后跨全部改写 refs 的历史命中为 0；两个远端分支旧 SHA 均存在新 SHA 映射；改写镜像 `git fsck --full --strict` 通过。正式远端尚未改写或推送。
- gitleaks v8.30.1 使用官方 checksum 校验后，以 `--all --redact=100` 扫描：改写前 12284 条、86 个文件、13 个提交有命中；整路径改写后为 12235 条、57 个文件、9 个提交，Secret 与 Match 字段均完全脱敏。
- 剩余结果中 12138 条集中在 `.scripts/postgresql/ruoyi-vue-pro10.sql`；非 generic 高置信规则还命中两份 PostgreSQL SQL 与 `APP/docs/.vitepress/composables/cases.ts`。规则名称不等于供应商归属证明，但足以证明这六条保留路径必须做内容级替换，不能只完成 21 条路径删除。
- 演练证据目录为 `C:\tmp\yfeieye-history-audit-20260804-001`。其中包含脱敏 JSON、commit-map 与多个诊断/改写镜像；未包含未脱敏 Secret 报告。该目录未上传、未加入仓库，也未永久删除。

## 验收证据

- 当前索引不得再返回上述运行时文件、`TASK/config/test*.ini`、FUXA/人脸库或 `deploy-packages/`；DEVICE 与仓库级配置门禁必须保持 0 违规。
- 所有敏感文件在本机仍存在，避免中断现有部署；后续由运维将它们迁移到密钥管理或主机级受限配置。
- 对所有分支和标签复查历史路径，并用专用密钥扫描器复查内容；扫描日志不得打印密钥明文。天地图、企业微信、FUXA 与 webhook 供应商侧必须提供旧值失效或权限收紧证据。
- 分别记录源码提交、远端历史改写、服务端变量轮换、服务重启和线上健康检查，不能以其中任一项代替其余证据。
