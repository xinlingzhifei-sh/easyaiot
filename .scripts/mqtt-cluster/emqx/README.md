# EMQX 集群配置说明

MQTT 网关节点通过环境变量配置 EMQX 5 集群，无需额外 conf 模板。

关键变量：

| 变量 | 说明 | 默认 |
|------|------|------|
| `EMQX_NODE_NAME` | 节点名，须为 `emqx@<本机IP>` | `emqx@${MQTT_NODE_HOST}` |
| `EMQX_NODE_COOKIE` | 集群 Cookie，同集群必须一致，至少 24 位 URL-safe 随机值 | 必填，无默认值 |
| `EMQX_DASHBOARD_PASSWORD` | Dashboard 初始密码，至少 16 位 URL-safe 随机值 | 必填，无默认值 |
| `EMQX_CLUSTER_SEEDS` | static discovery 种子列表，逗号分隔 | 本节点 |
| `MQTT_AUTH_URL` | HTTP 认证回调（iot-sink `/mqtt/auth`） | `http://控制面:8090/mqtt/auth` |

多节点集群示例：

```bash
# 节点 A (10.0.0.31)
export MQTT_NODE_HOST=10.0.0.31
export EMQX_NODE_COOKIE='<至少 24 位随机值>'
export EMQX_DASHBOARD_PASSWORD='<至少 16 位随机值>'
export EMQX_CLUSTER_SEEDS=emqx@10.0.0.31,emqx@10.0.0.32
bash install_mqtt_stack.sh

# 节点 B (10.0.0.32)
export MQTT_NODE_HOST=10.0.0.32
export EMQX_CLUSTER_SEEDS=emqx@10.0.0.31,emqx@10.0.0.32
bash install_mqtt_stack.sh
```

平台控制台「MQTT 网关」页支持 SSH 远程自动部署；节点 tags 中可配置
`emqx_cluster_seeds`、`emqx_cookie` 与各端口。
