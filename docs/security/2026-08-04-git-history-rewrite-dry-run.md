# 2026-08-04 Git 历史改写独立镜像演练

## 1. 结论

本次只在 `C:\tmp\yfeieye-history-audit-20260804-001` 的独立 bare 镜像中演练，**没有改写或推送当前仓库与远端**。

- 刷新 origin 后，待处理集合修正为 **27 条路径、96 个唯一历史提交**：21 条整路径反向过滤，6 条保留文件内容替换。
- 覆盖 46 个本地分支、2 个远端分支、stash，共 **927 个唯一提交**的镜像改写成功。
- 21 条整路径规则改写后历史命中为 **0**；两个远端分支旧 SHA 均有新 SHA 映射；改写镜像 `git fsck --full --strict` 通过。
- gitleaks 全 refs 脱敏扫描从 **12284 条 / 86 文件 / 13 提交**降为 **12235 条 / 57 文件 / 9 提交**。
- 剩余结果主要集中在必须保留的 SQL 与 APP 文档签名 URL；因此本次只证明整路径规则可用，**不证明凭据事件已经关闭**。

## 2. 安全边界

- 当前工作分支始终为 `codex/login-page-restoration`，HEAD 始终为 `2ec1a83d69154c48a72de6e586081361b483fae5`。
- 未执行 `git filter-repo` 于 `E:\yFeiEye`，未改写任何本地分支、stash 或标签，未 force-push。
- 为补齐 partial clone，仅增加本地 Git 对象并刷新 `refs/remotes/origin/*`；工作区与索引未由 fetch 改写。
- gitleaks 使用 `--redact=100`；生成的 JSON 中 `Secret` 与 `Match` 未发现未脱敏字段。
- 未读取、打印或写入旧 Secret；因此没有生成 `--replace-text` 真实值清单。
- 一次配置命令曾误在源仓库短暂设置 `core.protectNTFS=false`，随后立即撤销；最终源仓库 local config 中该键为 unset，临时镜像独立设置为 false。

## 3. 工具与完整性

| 工具 | 来源与校验 |
|---|---|
| git-filter-repo v2.47.0 | 下载自 `newren/git-filter-repo` 的 v2.47.0 单文件脚本；`--version` 输出 `a40bce548d2c`；SHA-256 `67447413E273FC76809289111748870B6F6072F08B17EFE94863A92D810B7D94` |
| gitleaks v8.30.1 | 下载官方 Windows x64 release；压缩包 SHA-256 与官方 `gitleaks_8.30.1_checksums.txt` 匹配 |
| Git for Windows | 2.53.0.windows.1 |
| Python | 本机 Python 3，用于运行 git-filter-repo 单文件脚本 |

工具仅位于临时证据目录，没有安装到系统 PATH，也没有加入仓库。

## 4. partial clone 根因与补全

源仓库配置：

- `remote.origin.promisor=true`
- `remote.origin.partialclonefilter=blob:none`
- 禁止 lazy fetch 后，初始缺失 384 个对象。

首次 `git clone --mirror --no-local` 可稳定复现：源仓库在打包时无法取得 promisor Blob，报 `could not fetch ... from promisor remote`。

诊断与修复证据：

1. 单独读取失败对象类型，成功返回 `blob`，缺失数 384 → 383，证明 origin 可提供承诺对象。
2. 逐对象 batch-check 方案 10 分钟只补全 53 个，剩余 330，吞吐不可接受；超时后的 Git 子进程均已自然结束。
3. 使用一次性 `git fetch --refetch --filter=blob:limit=1g` 补全 origin refs，253.8 秒后缺失数为 0。
4. 源仓库 `git fsck --full --strict` 返回 0；随后完整 `--no-local` 镜像克隆成功。

该过程同时把 `origin/main` 从本地旧远端跟踪值刷新为 `12651c5596318bba5a9d6cf5a21e17eb4756d3f1`。origin 当前有 2 个 heads、0 个 tags。

## 5. Windows 非法历史路径

历史中存在 `.image/open-source-guardian/阿旺*.png`，涉及 2 个提交。Git for Windows 默认 fast-import 拒绝该 NTFS 非法路径，导致第一次 filter-repo 演练在第 252 个提交中止。

最小复现实验：

- 默认 `core.protectNTFS`：fast-import 退出 128。
- 仅在一次性 bare 仓库设置 `core.protectNTFS=false`：同一输入退出 0，并可由 `ls-tree` 读回原路径。

后续完整演练只在临时 bare 镜像设置该局部配置。失败镜像保留在临时目录作为诊断证据，没有复用为成功结果。

## 6. refs 覆盖修正

直接对 mirror 运行 filter-repo 时，工具会删除 `refs/remotes/*`；第一次成功改写只解析 886 个本地提交，遗漏 origin 独有历史，因此被判定为无效演练。

最终镜像在改写前显式增加：

- `refs/heads/__history_audit_remote_main` → 原 `refs/remotes/origin/main`
- `refs/heads/__history_audit_remote_codex_login_page_restoration` → 原 `refs/remotes/origin/codex/login-page-restoration`

这两个临时 heads 与 46 个本地 heads、stash 一并使 filter-repo 解析 **927/927** 个唯一提交。

远端提交映射：

| 远端分支 | 旧 SHA | 演练新 SHA |
|---|---|---|
| `main` | `12651c5596318bba5a9d6cf5a21e17eb4756d3f1` | `48fbf616686923cc02106c17224a40c3c0f7e2c0` |
| `codex/login-page-restoration` | `2271f4a3d82642686405a5ce48b7a0d31577a72f` | `be1d37ab48c9141c195d8ca018bd7d0f2a669196` |

这些 SHA 仅属于本次镜像演练，不得直接当作正式推送依据；正式执行前 refs 可能再次变化。

## 7. 路径过滤结果

21 条整路径规则与 `docs/security/2026-07-30-credential-exposure-response.md` 一致，包含运行时 `.env`、NODE Agent 配置、TASK 联调 INI、`deploy-packages/`、FUXA 运行数据、Milvus Lite 数据库及生成态目录。

结果：

- filter-repo 解析 927 个提交，写入新历史 3.41 秒，总耗时 16.53 秒。
- 改写后唯一提交数 925；2 个只包含被移除内容的提交被剪除。
- 21 条整路径跨全部改写 refs 的 `git log --all` 命中为 0。
- 6 条内容替换路径仍保留，并命中 32 个改写后提交。
- `git fsck --full --strict` 通过。

6 条内容替换路径：

1. `.scripts/postgresql/iot-message10.sql`
2. `.scripts/postgresql/ruoyi-vue-pro10.sql`
3. `VIDEO/test_wxcp_alert_chain.py`
4. `WEB/.env.development`
5. `WEB/.env.production`
6. `APP/docs/.vitepress/composables/cases.ts`

第 6 条由本次 gitleaks 历史扫描补充；旧 26 路径清单虽然正文提到 APP 文档案例，但可复现路径集合漏列了它。

## 8. gitleaks 脱敏复扫

扫描命令覆盖 `--all` refs，并固定 `--redact=100`、JSON 报告、`exit-code=0`。gitleaks 在改写前/后分别显示处理 822/820 个具有可扫描补丁的提交；镜像自身的 refs/提交覆盖由 filter-repo 927/927 与 `rev-list --all` 独立证明。

| 指标 | 路径改写前 | 路径改写后 |
|---|---:|---:|
| Findings | 12284 | 12235 |
| 有命中的提交 | 13 | 9 |
| 有命中的文件 | 86 | 57 |
| 未脱敏 Secret/Match 字段 | 0 | 0 |

改写后规则分布：

- `generic-api-key`: 12230
- `alibaba-access-key-id`: 2
- `aws-access-token`: 2
- `jwt`: 1

其中 `.scripts/postgresql/ruoyi-vue-pro10.sql` 占 12138 条。非 generic 规则还命中两份 PostgreSQL SQL 与 APP 文档同一历史签名 URL。规则名称只代表检测模式，不证明供应商归属；但这些命中足以否定“删除 21 条路径即可完成历史清理”。

## 9. 正式执行前仍缺少的证据

1. 所有已暴露凭据、Token、Key、Webhook、密码与证书口令已在供应商/服务端轮换并证明旧值失效。
2. 仓库冻结写入，重新 fetch 所有 heads/tags 后重算路径与提交范围。
3. 由已轮换旧值在受限环境生成内容替换清单；清单、命令行与日志不得回显旧值。
4. 在新的完整镜像中同时执行 21 条整路径过滤和 6 条内容替换，再运行 gitleaks 与仓库自有门禁，要求高置信残留按确认后的 allowlist/修复策略归零。
5. 备份远端、保存 refs 清单与 commit-map，经仓库管理员批准后才 force-push 两个远端分支；协作者必须重新克隆。
6. 源码提交、远端历史改写、生产变量轮换、服务重启、版本/健康/鉴权负向探测分别留证，不能互相替代。

因此，本次状态是：**整路径历史改写方案已在全 refs 独立镜像中验证可行；内容替换、正式改写、轮换、推送与运行态验证仍未完成。**
