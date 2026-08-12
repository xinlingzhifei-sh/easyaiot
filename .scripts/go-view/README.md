# yFeiEye GoView / VISUALIZE 脚本与 SQL

GoView 大屏（`VISUALIZE`）相关的 **演示种子、增量补丁、生成脚本与文档** 统一放在本目录，不再放在 `.scripts/postgresql/`。

组态（FUXA）工程见 [`.scripts/fuxa/`](../fuxa/)。  
库结构 dump（`iot-visualize10.sql`）仍按装机规约放在 [`.scripts/postgresql/iot-visualize10.sql`](../postgresql/iot-visualize10.sql)。

## 目录结构

```
.scripts/go-view/
├── README.md                          # 本文档
├── seed_visualize_demo.sh             # 导入演示种子到 iot-visualize20
├── gen_visualize_dashboard_demo.py    # 生成 4 套高质量大屏 content + 封面（秒级）
├── patches/                           # 增量 SQL / 演示种子
│   ├── visualize_demo_seed.sql
│   ├── visualize_project_type.sql
│   ├── visualize_menu*.sql
│   └── visualize_datasource_deploy.sql
├── visualize-demo/content/            # 大屏画布 JSON（生成物）
└── _gen/                              # 生成器临时目录（可空）
```

## 演示内容

| 类型 | 数量 | 说明 |
|------|------|------|
| **大屏** `dashboard` | 4 | 智慧工厂 / 智慧园区 / 设备运维 / 能源能耗 |
| **组态** `scada` | 4 | 水厂 / 产线 / 厂区管网 / 配电室（FUXA） |
| 模板 / 素材 / 数据源 / 投放 | 配套 | 见 `patches/visualize_demo_seed.sql` |

封面：`WEB/public/resource/visualize-demo/dash-*-cover.svg`、`scada-*-cover.svg`

## 一键导入

```bash
# 平台元数据 + 大屏 content（秒级）
bash .scripts/go-view/seed_visualize_demo.sh

# 组态画面（需 fuxa-server 已启动）
bash .scripts/fuxa/seed_fuxa_demo.sh
```

## 重新生成大屏

纯 Python，无浏览器、无网络，通常 <1s：

```bash
python3 .scripts/go-view/gen_visualize_dashboard_demo.py
```

会更新：

- `.scripts/go-view/visualize-demo/content/*.json`
- `.scripts/go-view/patches/visualize_demo_seed.sql`
- 大屏封面 SVG

> 不要用 Chromium/Puppeteer 截图生成封面，容易卡住很久无输出。

## 四套大屏

| 项目 | 亮点 |
|------|------|
| 智慧工厂综合态势 | OEE / 车间产量 / 良率环图 / 产线负荷 / 实时告警 |
| 智慧园区运行监测 | 人流车位 / 楼宇能耗 / 空间占用 / 通行安防 |
| 设备运维健康看板 | 在线率 / 健康评分 / 故障分布 / 工单滚动 |
| 能源能耗分析大屏 | 电/气/碳排 / 峰谷分析 / 节电排名 / 能耗异常 |

打开编辑器：可视化管理 → 项目卡片 → 打开编辑器（大屏走 VISUALIZE `:8002`）。

## 常用补丁

| 文件 | 库 | 说明 |
|------|-----|------|
| `patches/visualize_project_type.sql` | iot-visualize20 | `project_type` / `editor_ref` |
| `patches/visualize_menu.sql` 等 | ruoyi-vue-pro20 | 可视化菜单与权限 |
| `patches/visualize_demo_seed.sql` | iot-visualize20 | 演示数据（由 gen 脚本维护） |
