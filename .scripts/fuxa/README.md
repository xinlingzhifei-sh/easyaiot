# yFeiEye FUXA 组态演示工程

## 内容

工程文件 `easyaiot_scada_demo.fuxap` 含 **4 套高质量中文组态画面**（基于 FUXA 官方演示工艺图增强）：

| 画面名 | 说明 |
|--------|------|
| 水厂工艺总貌 | 水处理工艺：罐体 / 管线 / 阀门 |
| 产线运行看板 | 产线 KPI、电机与阀组状态 |
| 厂区管网组态 | 厂区管网拓扑（高细节工艺图） |
| 配电室电力监视 | 配电室负荷与开关监视 |

封面图位于 `WEB/public/resource/visualize-demo/scada-*-cover.svg`。

默认 FUXA 账号：`admin` / `123456`（请在生产环境立即修改）。

平台项目 `editor_ref` 与画面名一致；预览打开对应运行态画面。

---

## 生产上线步骤（演示只读 + 恢复）

适用场景：演示工艺图曾被用户经 `:1881` 编辑器改删，需要**恢复数据**并**上线只读保护**。

假设：

- FUXA 容器主机内网：`10.0.0.87:1881`（按实际修改）
- WEB 公网反代：`nginx.prod-server.conf` 监听 `:1881` → 容器
- 仓库已包含本次改动（平台强制预览、SSO 桥接、nginx 禁编辑器）

### 1. 在 FUXA 所在机恢复演示工程

容器需已运行。优先用 CentOS 7 脚本（直连本机容器，绕过公网 nginx 只读）：

```bash
cd .scripts/docker
sudo ./start_fuxa_centos7.sh --seed-only
```

或手动：

```bash
# 直连容器内网，勿走公网 :1881（公网已禁工程写入）
FUXA_URL=http://10.0.0.87:1881 bash .scripts/fuxa/seed_fuxa_demo.sh
```

可选：同步平台侧大屏×4 + 组态×4 元数据：

```bash
bash .scripts/go-view/seed_visualize_demo.sh
```

### 2. 让新 SSO 桥接页生效

`easyaiot-sso.html` 由 compose 挂载到 FUXA `client/dist`。仓库更新后重启容器：

```bash
cd .scripts/docker
sudo ./start_fuxa_centos7.sh --restart
# 若同时要恢复演示：sudo ./start_fuxa_centos7.sh --restart --seed
```

确认桥接页可访问：`http://10.0.0.87:1881/easyaiot-sso.html`

### 3. 部署平台侧（强制演示预览）

1. 部署 **iot-visualize**（含 `demo-read-only` / `force-preview`）
2. 部署 **WEB** 前端（演示项目隐藏「打开编辑器」）

生产配置（`application-prod.yaml` / 环境变量）：

| 配置 | 建议值 | 说明 |
|------|--------|------|
| `IOT_FUXA_BASE_URL` | `http://10.0.0.87:1881` | 后端代登录，直连容器 |
| `IOT_FUXA_PUBLIC_URL` | 公网可达 `:1881` | SSO 跳转基址 |
| `IOT_FUXA_DEMO_READ_ONLY` | `true` | 演示组态强制 preview |
| `IOT_FUXA_FORCE_PREVIEW` | `true` | 全部组态强制预览（演示环境推荐） |

### 4. 更新公网 nginx 只读反代

将 `WEB/conf/nginx.prod-server.conf` 部署到 WEB 机，确认 `:1881` server 含：

- `location = /editor` → `deny all`
- `/api/project` → 仅允许 `GET`（禁止覆盖工程）
- `/fuxa` 跳转 → `/home`（不再跳 `/editor`）

```bash
nginx -t && nginx -s reload
```

### 5. 上线验收

| 检查项 | 期望 |
|--------|------|
| 平台打开演示组态 | 进入 `/home?view=…` 运行态，无「打开编辑器」 |
| 公网访问 `http://<公网>:1881/editor` | 403 / 拒绝 |
| 公网 `POST /api/project` | 拒绝 |
| 内网 `http://10.0.0.87:1881/editor` | 运维可进（改图用） |
| 演示四画面可预览 | 水厂 / 产线 / 厂区管网 / 配电室 正常 |

### 6. 日常运维速查

```bash
# 仅恢复被改坏的演示工艺图（FUXA 机）
cd .scripts/docker && sudo ./start_fuxa_centos7.sh --seed-only

# 运维改图：浏览器打开内网编辑器（勿走公网）
#   http://10.0.0.87:1881/editor
# 改完如需固化到仓库：从 FUXA 导出工程覆盖 easyaiot_scada_demo.fuxap
```

---

## 导入 / 恢复被改坏的演示数据（简版）

```bash
# 1) FUXA 画面（需 fuxa-server 已启动）
# CentOS 机推荐：
#   cd .scripts/docker && sudo ./start_fuxa_centos7.sh --seed-only
# 或直连内网：
#   FUXA_URL=http://10.0.0.87:1881 bash .scripts/fuxa/seed_fuxa_demo.sh
bash .scripts/fuxa/seed_fuxa_demo.sh

# 2) 平台侧大屏×4 + 组态×4 元数据
bash .scripts/go-view/seed_visualize_demo.sh
```

---

## 演示数据只读保护（防界面改删工艺图）

演示工艺图曾因用户跳转到 FUXA `:1881` 编辑器被改删。仓库内已加多层保护：

| 层 | 行为 |
|----|------|
| 平台前端 | 演示项目（ID 9311–9314 / 上表画面名）隐藏「打开编辑器」，只保留预览 |
| 平台后端 | `iot.fuxa.demo-read-only=true`：演示打开强制 `preview`；生产默认 `force-preview=true` |
| SSO 桥接页 | `easyaiot-sso.html` 对演示画面拒绝 `mode=edit` / `target=/editor` |
| 公网 nginx `:1881` | `deny /editor`；`/api/project` 仅允许 GET（禁止覆盖工程） |
| CentOS 脚本 | `--seed` / `--seed-only` 直连本机容器恢复；宿主机 `/editor` 仍留给运维 |

相关配置：

- `DEVICE/.../application-prod.yaml` → `iot.fuxa.demo-read-only` / `force-preview`
- `WEB/conf/nginx.prod-server.conf` → `:1881` server 只读规则
- `.scripts/docker/start_fuxa_centos7.sh` → 独立部署 / 恢复
- 环境变量：`IOT_FUXA_DEMO_READ_ONLY`、`IOT_FUXA_FORCE_PREVIEW`

大屏 content 重新生成（纯 Python，秒级；勿用浏览器截图 gen）：

```bash
python3 .scripts/go-view/gen_visualize_dashboard_demo.py
```
---

## yFeiEye 免登跳转（SSO）

已开启 FUXA `secureEnabled` 时，平台通过代登录 + 同源桥接页免登进入：

1. 前端调用 `GET /visualize/project/fuxa-open?id=&mode=edit|preview`
2. 后端向 FUXA `/api/signin` 获取 token（演示/强制预览时降级为 preview）
3. 浏览器打开 `http://<fuxa>/easyaiot-sso.html?token=...&mode=...&view=...`
4. 桥接页写入 `sessionStorage.currentUser` 后跳转 `/home?view=...`（演示不再进 `/editor`）

相关文件：

- 桥接页：`.scripts/fuxa/easyaiot-sso.html`（compose 挂载到 FUXA `client/dist`）
- 配置：`iot.fuxa.*`（`DEVICE/iot-visualize/.../application.yaml`）
- FUXA 鉴权：`fuxa_data/appdata/settings.js` 中 `secureEnabled` / `secretCode`
- CentOS 部署：`.scripts/docker/start_fuxa_centos7.sh`
