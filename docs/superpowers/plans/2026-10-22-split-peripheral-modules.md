# 实施计划：周边模块拆分为独立仓库

设计：`docs/superpowers/specs/2026-10-22-split-peripheral-modules.md`

## 阶段 0：生成独立仓库（主仓库外，不提交）

1. 只读 blobless 裸克隆 `https://github.com/xinlingzhifei-sh/yFeiEye.git` → `E:\yFeiEye-split\_full`，`pushurl` 置空，核对 `main` 与本地 `8aa884e3c` 一致。
2. 对 TERMINAL / HARNESS / IDEA / SITE / TWIN 逐个运行拆分脚本（subtree split 等价规则），推送到 `E:\yFeiEye-split\<模块>.git`，`fsck --full`，核对最终树等于 `main:<模块>`，保存 `<模块>.map.txt`。
3. 每个仓库追加"拆出为独立仓库"提交：迁入 ignore / attributes 规则、README 来源说明；`git add --renormalize` 确认无行尾漂移。

## 阶段 1：设计与计划（提交 1）

- 提交 spec 与本计划。

## 阶段 2：外部模块机制与调用点，模块目录仍在（提交 2）

- `deploy-profiles.json` 增 `externalModules`（五个模块的仓库地址、`ref: main`）。
- `generate-deploy-profiles.mjs`：校验并生成 `deploy_profile_manifest_external_modules` / `_external_module_repository` / `_external_module_ref`；重新生成。
- 新增 `.scripts/docker/external_modules.sh`，`deploy_profile.sh` 加载它。
- 新增 `.scripts/docker/external-modules.test.mjs`：本地裸仓库做远端，覆盖非外部模块、只读命令不拉取、已存在目录、克隆、`update` 快进、归档、`FETCH=0`、克隆失败不留半成品。
- 验证：`node --test .scripts/docker/*.test.mjs`（服务器）。

## 阶段 3：调用点（与阶段 2 同一提交：契约测试同时断言机制与调用点）

- `install_linux.sh` / `install_linux_arm.sh` / `install_linux_kylin.sh` / `install_mac.sh` / `install_desktop_common.sh` 的 `execute_module_command`，`install_business_linux.sh` 的 `execute_module`，三个 `run_site_module`，`runtime_image.sh` 的 `build_module_with_install_script`：开头一行 `ensure_external_module_checkout`。
- `verify_update_chain.sh`：外部模块未检出时跳过而非判失败。
- 契约测试断言各调用点存在。

## 阶段 4：删除目录（提交 3）

- `git rm -r TERMINAL HARNESS IDEA SITE TWIN`。
- `.gitignore`：删五个模块的细则，改为忽略 `/TERMINAL/ /HARNESS/ /IDEA/ /SITE/ /TWIN/`；`.gitattributes`、`.dockerignore` 删对应规则。
- `deploy-profile-manifest.test.mjs` 去掉 `TWIN/docker-compose.yml`；`WEB/tests/visibleBrandingResidue.test.ts` 去掉读取已移出文件的断言。
- `WEB/docker-compose.yaml` SITE 挂载注释；PR 模板去掉 IDEA / SITE 勾选项。
- 验证：`.scripts` `node --test`；WEB `tests/*.test.ts`（tsx）与 `pnpm type:check`（服务器）。

## 阶段 5：文档（提交 4）

- `AGENTS.md` 第 1 节模块三、第 2 节目录、第 3 节命令；6 份 README 的模块介绍加"独立仓库"说明；`.scripts/docker/Readme.md`。
- `_sync_readme_updates.py` 是一次性的赞助者表同步脚本（硬编码 `/projects/new/easyaiot`），不适用，6 份 README 手工同步。

## 测试约束

- 只在 `root@eye.yfeiai.com` 的临时容器里跑单元测试，开跑前 `free -m`，`git archive` 只打包 `.scripts` 与 `WEB`，放 `/opt/yfeieye-unittest-split-peripheral/`，跑完删除。
- 提交前 `node .scripts/verify-repository-secret-hygiene.mjs`。
