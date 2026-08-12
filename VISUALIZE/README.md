# VISUALIZE

高效、高性能的拖拽式低代码数据可视化**编辑器**。

本应用**仅保留画布编辑与预览能力**，不含独立登录与项目管理。项目管理请在 yFeiEye 管理后台（WEB）「可视化 → 可视化项目管理」中操作。

配套后台：DEVICE `iot-visualize`（`visualize-server`，库 `iot-visualize20`）。

## 快速开始

```bash
pnpm install
pnpm run dev   # 固定 http://localhost:8002 ，/admin-api 代理到 Gateway :48080
```

## 从 WEB 打开编辑器

管理后台点击「打开编辑器」会新开：

`{VITE_GLOB_VISUALIZE_URL}/#/chart/home/{projectId}?accessToken=...`

编辑器路由守卫会落库 Token 并清理 URL。

## 路由

| 路径 | 说明 |
|------|------|
| `/#/chart/home/:id` | 主编辑器 |
| `/#/chart/preview/:id` | 预览 |
| `/#/chart/edit/:id` | JSON 源码编辑（可选） |
| `/#/home` | 未带项目 ID 时的引导提示 |
