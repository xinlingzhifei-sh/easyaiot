# yFeiEye Node-RED 演示规则链

## 内容

工程文件 `easyaiot_flows_demo.json` 含 **4 条高质量中文演示规则链**（仅用 Node-RED 核心节点）：

| 页签（规则链） | 固定 ID | 说明 |
|----------------|---------|------|
| 设备遥测采集链路 | `easyaiot_demo_telemetry` | MQTT 遥测接入 → 解析 → 温度阈值 → 调试 |
| 告警分级推送链路 | `easyaiot_demo_alert` | 告警事件分级（紧急/重要/一般）→ 模板推送 |
| 工控协议桥接链路 | `easyaiot_demo_bridge` | 模拟点位 → 物模型映射 → MQTT 上行 |
| 视觉质检联动链路 | `easyaiot_demo_vision` | HTTP 质检回调 → AI 判定 → NG 剔除下发 |

容器页面标题 / 顶栏：**yFeiEye**（`settings.js` → `editorTheme`）。

## 上线步骤

生产环境请按顺序执行（内网地址按实际修改，示例为 `10.0.0.87:1880`）：

### 1) 部署 / 重建 Node-RED（挂载 yFeiEye 配置）

`docker-compose.yml` 已挂载：

- `../node-red/settings.js` → 标题 yFeiEye + 演示只读中间件
- `../node-red/easyaiot_flows_demo.json` → 锁定演示工程
- `../node-red/public` → 编辑器只读脚本

```bash
cd .scripts/docker

# 方式 A：仅启动/重建 NodeRED
docker compose up -d --force-recreate NodeRED
# 或旧版：docker-compose up -d --force-recreate NodeRED

# 方式 B：CentOS 7 一键脚本（推荐首次部署）
sudo ./start_nodered_centos7.sh --seed
```

确认容器健康：

```bash
docker ps | grep nodered-server
curl -sf http://127.0.0.1:1880/ >/dev/null && echo OK
# 浏览器打开 http://<主机>:1880 ，页面标题应为 yFeiEye
```

### 2) 导入演示规则链

若未使用 `--seed`，或演示数据被改坏，请**直连容器内网**导入（绕过公网 nginx 只读）：

```bash
# 仓库根目录执行
NODERED_URL=http://10.0.0.87:1880 bash .scripts/node-red/seed_nodered_demo.sh

# 或容器已在跑时用 CentOS 脚本只恢复：
cd .scripts/docker && sudo ./start_nodered_centos7.sh --seed-only
```

若刚更新 `settings.js` 挂载但标题/中间件未生效：

```bash
docker restart nodered-server
```

### 3) 部署平台前端与公网 nginx

1. 部署 WEB（含规则链演示只读逻辑：`WEB/src/utils/noderedDemo.ts`）
2. 发布/重启 `iot-gateway`（需含 `/admin-api/nodeRed/**` → Node-RED 路由）
3. 重载公网 nginx（`WEB/conf/nginx.prod-server.conf`）
   - Admin API：`/dev-api/nodeRed/{flows,flow,flow/*}` → Gateway → Node-RED（CRUD 经网关入口）
   - 编辑器静态/WS：其余 `/dev-api/nodeRed/` 仍直连内网 `1880`
   - 确保 `/dev-api/nodeRed/flow/easyaiot_demo_*` 仅允许 GET

```bash
# 示例（按实际 nginx 安装路径）
nginx -t && nginx -s reload
```

### 4) 验收清单

- [ ] `http://<主机>:1880` 标题为 **yFeiEye**
- [ ] 规则链列表可见 4 条演示项，**无编辑/删除**，可查看打开
- [ ] 公网经 `/dev-api/nodeRed/` 无法 PUT/DELETE `easyaiot_demo_*`
- [ ] 演示页签 Deploy 被禁用或部署后演示内容被回填锁定副本
- [ ] 新增/删除规则链成功（流量经 Gateway，浏览器路径仍为 `/dev-api/nodeRed/...`）
- [ ] 规则链编辑器 iframe 打开正常（静态仍直连 1880）

## 导入 / 恢复被改坏的演示数据

```bash
# 需 nodered-server 已启动；生产务必直连内网（勿走公网/Gateway）
NODERED_URL=http://10.0.0.87:1880 bash .scripts/node-red/seed_nodered_demo.sh

# 本地默认：
bash .scripts/node-red/seed_nodered_demo.sh

# CentOS 7：
#   cd .scripts/docker && sudo ./start_nodered_centos7.sh --seed
#   cd .scripts/docker && sudo ./start_nodered_centos7.sh --seed-only
```

## 演示数据只读保护（防界面改删规则链）

| 层 | 行为 |
|----|------|
| 平台前端 | 演示项（上表 4 个 ID/名称）隐藏编辑/删除，仅保留查看 |
| Node-RED `settings.js` | `httpAdminMiddleware`：禁止 PUT/DELETE 演示页签；`POST /flows` 部署时强制回填锁定副本 |
| 编辑器脚本 | `public/easyaiot-demo-guard.js`：演示页签禁用 Deploy |
| 公网 nginx | Admin API（`/flows`、`/flow`）经 Gateway；`/dev-api/nodeRed/flow/easyaiot_demo_*` 仅允许 GET；编辑器静态仍直连 1880 |

运维若需修改演示链路：

1. **直连容器内网**（例如 `http://10.0.0.87:1880`），不要走公网反代
2. 改完后固化到仓库：导出/覆盖 `easyaiot_flows_demo.json`
3. 被改坏时执行 `--seed-only` 或 `seed_nodered_demo.sh` 整包恢复

相关文件：

- 工程：`.scripts/node-red/easyaiot_flows_demo.json`
- 配置：`.scripts/node-red/settings.js`（compose 挂载到 Node-RED `/data/settings.js`）
- 编辑器只读脚本：`.scripts/node-red/public/easyaiot-demo-guard.js`
- 种子脚本：`.scripts/node-red/seed_nodered_demo.sh`
- CentOS 部署：`.scripts/docker/start_nodered_centos7.sh`
- 前端识别：`WEB/src/utils/noderedDemo.ts`
- 公网反代：`WEB/conf/nginx.prod-server.conf`、`WEB/conf/nginx.conf`

## yFeiEye 打开方式

平台「规则链」列表点击封面/查看 → iframe 打开 `/dev-api/nodeRed/#flow/<id>`，浏览器页签标题为规则链名称；Node-RED 编辑器自身标题为 **yFeiEye**。
