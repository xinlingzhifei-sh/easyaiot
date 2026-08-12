#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""快速生成 4 套高质量 VISUALIZE 大屏演示数据（纯 Python，无浏览器/无网络）。

输出：
  - WEB/public/resource/visualize-demo/dash-*-cover.svg 等封面
  - .scripts/go-view/visualize-demo/content/*.json 画布 JSON
  - 重写 patches/visualize_demo_seed.sql 中项目/模板/投放（保留素材/数据源/组态）

用法：
  python3 .scripts/go-view/gen_visualize_dashboard_demo.py
  bash .scripts/go-view/seed_visualize_demo.sh
"""

from __future__ import annotations

import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COVER_DIR = ROOT / "WEB/public/resource/visualize-demo"
CONTENT_DIR = ROOT / ".scripts/go-view/visualize-demo/content"
SEED_SQL = ROOT / ".scripts/go-view/patches/visualize_demo_seed.sql"


def log(msg: str) -> None:
    print(f"[gen] {msg}", flush=True)


def styles() -> dict:
    return {
        "filterShow": False,
        "hueRotate": 0,
        "saturate": 1,
        "contrast": 1,
        "brightness": 1,
        "opacity": 1,
        "rotateZ": 0,
        "rotateX": 0,
        "rotateY": 0,
        "skewX": 0,
        "skewY": 0,
        "blendMode": "normal",
        "animations": [],
    }


def request_static() -> dict:
    return {
        "requestDataType": 0,
        "requestHttpType": "get",
        "requestUrl": "",
        "requestInterval": None,
        "requestIntervalUnit": "second",
        "requestContentType": 0,
        "requestParamsBodyType": "none",
        "requestSQLContent": {"sql": "select * from  where"},
        "requestParams": {
            "Body": {"form-data": {}, "x-www-form-urlencoded": {}, "json": "", "xml": ""},
            "Header": {},
            "Params": {},
        },
    }


def events() -> dict:
    return {
        "baseEvent": {
            "onclick": None,
            "ondblclick": None,
            "onmouseenter": None,
            "onmouseleave": None,
        },
        "advancedEvents": {"vnodeMounted": None, "vnodeBeforeMount": None},
        "interactEvents": [],
    }


CHART_META = {
    "TextCommon": {
        "chartKey": "VTextCommon",
        "conKey": "VCTextCommon",
        "title": "文字",
        "category": "Texts",
        "categoryName": "文本",
        "package": "Informations",
        "chartFrame": "common",
        "image": "text_static.png",
    },
    "Number": {
        "chartKey": "VNumber",
        "conKey": "VCNumber",
        "title": "数字计数",
        "category": "Mores",
        "categoryName": "更多",
        "package": "Decorates",
        "chartFrame": "common",
        "image": "number.png",
    },
    "TimeCommon": {
        "chartKey": "VTimeCommon",
        "conKey": "VCTimeCommon",
        "title": "通用时间",
        "category": "Mores",
        "categoryName": "更多",
        "package": "Decorates",
        "chartFrame": "static",
        "image": "time.png",
    },
    "Border01": {
        "chartKey": "VBorder01",
        "conKey": "VCBorder01",
        "title": "边框-01",
        "category": "Borders",
        "categoryName": "边框",
        "package": "Decorates",
        "chartFrame": "static",
        "image": "border01.png",
    },
    "Border04": {
        "chartKey": "VBorder04",
        "conKey": "VCBorder04",
        "title": "边框-04",
        "category": "Borders",
        "categoryName": "边框",
        "package": "Decorates",
        "chartFrame": "static",
        "image": "border04.png",
    },
    "Border06": {
        "chartKey": "VBorder06",
        "conKey": "VCBorder06",
        "title": "边框-06",
        "category": "Borders",
        "categoryName": "边框",
        "package": "Decorates",
        "chartFrame": "static",
        "image": "border06.png",
    },
    "Decorates01": {
        "chartKey": "VDecorates01",
        "conKey": "VCDecorates01",
        "title": "装饰-01",
        "category": "Decorates",
        "categoryName": "装饰",
        "package": "Decorates",
        "chartFrame": "static",
        "image": "decorates01.png",
    },
    "BarCommon": {
        "chartKey": "VBarCommon",
        "conKey": "VCBarCommon",
        "title": "柱状图",
        "category": "Bars",
        "categoryName": "柱状图",
        "package": "Charts",
        "chartFrame": "echarts",
        "image": "bar_x.png",
    },
    "LineCommon": {
        "chartKey": "VLineCommon",
        "conKey": "VCLineCommon",
        "title": "折线图",
        "category": "Lines",
        "categoryName": "折线图",
        "package": "Charts",
        "chartFrame": "echarts",
        "image": "line.png",
    },
    "PieCommon": {
        "chartKey": "VPieCommon",
        "conKey": "VCPieCommon",
        "title": "饼图",
        "category": "Pies",
        "categoryName": "饼图",
        "package": "Charts",
        "chartFrame": "echarts",
        "image": "pie.png",
    },
    "CapsuleChart": {
        "chartKey": "VCapsuleChart",
        "conKey": "VCCapsuleChart",
        "title": "胶囊柱图",
        "category": "Bars",
        "categoryName": "柱状图",
        "package": "Charts",
        "chartFrame": "common",
        "image": "capsule.png",
    },
    "TableScrollBoard": {
        "chartKey": "VTableScrollBoard",
        "conKey": "VCTableScrollBoard",
        "title": "轮播列表",
        "category": "Tables",
        "categoryName": "表格",
        "package": "Tables",
        "chartFrame": "common",
        "image": "table_scrollboard.png",
    },
}


def make_comp(cid: str, key: str, x: int, y: int, w: int, h: int, z: int, option: dict) -> dict:
    meta = CHART_META[key]
    return {
        "id": cid,
        "isGroup": False,
        "key": key,
        "attr": {"x": x, "y": y, "w": w, "h": h, "zIndex": z, "offsetX": 0, "offsetY": 0},
        "styles": styles(),
        "preview": {"overFlowHidden": False},
        "status": {"lock": False, "hide": False},
        "request": request_static(),
        "events": events(),
        "chartConfig": {"key": key, **meta},
        "option": option,
    }


def text_opt(dataset: str, size: int = 20, color: str = "#ffffff", weight: str = "normal", align: str = "center") -> dict:
    return {
        "link": "",
        "linkHead": "http://",
        "dataset": dataset,
        "fontSize": size,
        "fontColor": color,
        "paddingX": 6,
        "paddingY": 4,
        "textAlign": align,
        "fontWeight": weight,
        "borderWidth": 0,
        "borderColor": "#ffffff",
        "borderRadius": 5,
        "letterSpacing": 2,
        "writingMode": "horizontal-tb",
        "backgroundColor": "#00000000",
    }


def number_opt(value, prefix: str = "", suffix: str = "", color: str = "#4a9ef8", size: int = 32) -> dict:
    return {
        "dataset": value,
        "from": 0,
        "dur": 2.2,
        "precision": 0 if isinstance(value, int) else 1,
        "showSeparator": True,
        "numberSize": size,
        "numberColor": color,
        "prefixText": prefix,
        "prefixColor": color,
        "suffixText": suffix,
        "suffixColor": color,
    }


def border01_opt(c1: str = "#4fd2dd", c2: str = "#235fa7") -> dict:
    return {"dur": 0.5, "colors": [c1, c2], "backgroundColor": "#00000000"}


def border04_opt(title: str, c1: str = "#8aaafb", c2: str = "#1f33a2") -> dict:
    return {
        "borderTitle": title,
        "borderTitleWidth": min(280, max(140, 16 * len(title) + 40)),
        "borderTitleHeight": 32,
        "borderTitleSize": 16,
        "borderTitleColor": "#fff",
        "colors": [c1, c2],
        "backgroundColor": "#00000000",
    }


def border06_opt(c1: str = "#3140ad", c2: str = "#1089ff") -> dict:
    return {"colors": [c1, c2], "backgroundColor": "#00000000"}


def bar_opt(categories: list, s1: list, s2: list, name1: str = "本期", name2: str = "同期") -> dict:
    source = [{"product": c, "data1": a, "data2": b} for c, a, b in zip(categories, s1, s2)]
    series_item = {
        "type": "bar",
        "barWidth": 14,
        "label": {"show": False, "position": "top", "color": "#fff", "fontSize": 12},
        "itemStyle": {"color": None, "borderRadius": 2},
    }
    return {
        "backgroundColor": "rgba(0,0,0,0)",
        "tooltip": {"show": True, "trigger": "axis", "axisPointer": {"show": True, "type": "shadow"}},
        "legend": {"show": True, "textStyle": {"color": "#cfe8ff"}},
        "xAxis": {"show": True, "type": "category"},
        "yAxis": {"show": True, "type": "value"},
        "grid": {"left": 50, "right": 20, "top": 40, "bottom": 30},
        "dataset": {"dimensions": ["product", "data1", "data2"], "source": source},
        "series": [
            {**series_item, "name": name1},
            {**series_item, "name": name2},
        ],
    }


def line_opt(categories: list, s1: list, s2: list, name1: str = "实际", name2: str = "目标") -> dict:
    source = [{"product": c, "data1": a, "data2": b} for c, a, b in zip(categories, s1, s2)]
    series_item = {
        "type": "line",
        "label": {"show": False, "position": "top", "color": "#fff", "fontSize": 12},
        "symbolSize": 6,
        "itemStyle": {"color": None, "borderRadius": 0},
        "lineStyle": {"type": "solid", "width": 2, "color": None},
        "areaStyle": {"opacity": 0.12},
    }
    return {
        "backgroundColor": "rgba(0,0,0,0)",
        "tooltip": {"show": True, "trigger": "axis", "axisPointer": {"type": "line"}},
        "legend": {"show": True, "textStyle": {"color": "#cfe8ff"}},
        "xAxis": {"show": True, "type": "category"},
        "yAxis": {"show": True, "type": "value"},
        "grid": {"left": 50, "right": 20, "top": 40, "bottom": 30},
        "dataset": {"dimensions": ["product", "data1", "data2"], "source": source},
        "series": [
            {**series_item, "name": name1},
            {**series_item, "name": name2},
        ],
    }


def pie_opt(items: list[tuple[str, float]]) -> dict:
    source = [{"product": n, "data1": v} for n, v in items]
    return {
        "backgroundColor": "rgba(0,0,0,0)",
        "isCarousel": False,
        "type": "ring",
        "tooltip": {"show": True, "trigger": "item"},
        "legend": {"show": True, "textStyle": {"color": "#cfe8ff"}, "orient": "vertical", "right": 8, "top": "middle"},
        "dataset": {"dimensions": ["product", "data1"], "source": source},
        "series": [
            {
                "type": "pie",
                "radius": ["42%", "68%"],
                "center": ["38%", "52%"],
                "roseType": False,
                "avoidLabelOverlap": True,
                "itemStyle": {"show": True, "borderRadius": 6, "borderColor": "#0b1220", "borderWidth": 2},
                "label": {
                    "show": False,
                    "position": "center",
                    "formatter": "{b}",
                    "fontWeight": "normal",
                    "fontSize": 14,
                    "color": "#E6F7FF",
                    "textBorderColor": "#00000000",
                    "textBorderWidth": 0,
                },
                "emphasis": {"label": {"show": True, "fontSize": 18, "fontWeight": "bold", "color": "#fff"}},
                "labelLine": {"show": False},
            }
        ],
    }


def capsule_opt(items: list[tuple[str, float]], unit: str = "") -> dict:
    return {
        "dataset": {"dimensions": ["name", "value"], "source": [{"name": n, "value": v} for n, v in items]},
        "colors": ["#3fb1e3", "#6be6c1", "#a0a7e6", "#96dee8", "#c4ebad", "#f6c88d"],
        "unit": unit,
        "itemHeight": 12,
        "valueFontSize": 14,
        "paddingRight": 40,
        "paddingLeft": 40,
        "showValue": True,
    }


def table_opt(header: list[str], rows: list[list], widths: list[int] | None = None) -> dict:
    return {
        "header": header,
        "dataset": rows,
        "index": True,
        "columnWidth": widths or [36, 120, 100, 90],
        "align": ["center"] * (len(header) + 1),
        "rowNum": 6,
        "waitTime": 2.5,
        "headerHeight": 36,
        "carousel": "single",
        "headerBGC": "#0E4A7B",
        "oddRowBGC": "#0A2732",
        "evenRowBGC": "#0D3340",
    }


def canvas(name: str, bg: str = "#070b14") -> dict:
    return {
        "projectName": name,
        "width": 1920,
        "height": 1080,
        "filterShow": False,
        "hueRotate": 0,
        "saturate": 1,
        "contrast": 1,
        "brightness": 1,
        "opacity": 1,
        "rotateZ": 0,
        "rotateX": 0,
        "rotateY": 0,
        "skewX": 0,
        "skewY": 0,
        "blendMode": "normal",
        "background": bg,
        "backgroundImage": None,
        "selectColor": True,
        "chartThemeColor": "dark",
        "chartThemeSetting": {},
        "vChartThemeName": "vScreenVolcanoBlue",
        "previewScaleType": "fit",
    }


def global_cfg() -> dict:
    return {
        "requestDataPond": [],
        "requestOriginUrl": "",
        "requestInterval": 30,
        "requestIntervalUnit": "second",
        "requestParams": {
            "Body": {"form-data": {}, "x-www-form-urlencoded": {}, "json": "", "xml": ""},
            "Header": {},
            "Params": {},
        },
    }


def build_screen(
    prefix: str,
    title: str,
    subtitle: str,
    accent: tuple[str, str],
    kpis: list[tuple[str, object, str, str, str]],
    bar,
    line,
    pie,
    capsule,
    table,
) -> dict:
    """统一大屏骨架：顶栏 + 4 KPI + 三列图表 + 底栏胶囊/列表。"""
    c1, c2 = accent
    comps: list[dict] = []
    z = 0

    def add(key, x, y, w, h, option):
        nonlocal z
        z += 1
        comps.append(make_comp(f"{prefix}-{key}-{z}", key, x, y, w, h, z, option))

    # 顶栏装饰与标题
    add("Decorates01", 80, 18, 1760, 18, {"colors": [c1, "#ffffff"], "dur": 3, "lineHeight": 2, "endWidth": 5})
    add("TextCommon", 460, 36, 1000, 56, text_opt(title, 40, "#ffffff", "bold"))
    add("TextCommon", 560, 92, 800, 32, text_opt(subtitle, 16, c1, "normal"))
    add(
        "TimeCommon",
        1580,
        48,
        280,
        48,
        {
            "timeSize": 20,
            "timeLineHeight": 48,
            "timeTextIndent": 2,
            "timeColor": "#E6F7FF",
            "fontWeight": "normal",
            "showShadow": True,
            "hShadow": 0,
            "vShadow": 0,
            "blurShadow": 8,
            "colorShadow": c2,
        },
    )

    # KPI 区
    kpi_w, gap = 420, 20
    for i, (label, value, prefix_t, suffix_t, color) in enumerate(kpis):
        x = 80 + i * (kpi_w + gap)
        add("Border06", x, 140, kpi_w, 120, border06_opt(c2, c1))
        add("TextCommon", x + 20, 152, kpi_w - 40, 28, text_opt(label, 15, "#9ec9e8", "normal", "left"))
        add("Number", x + 24, 178, kpi_w - 48, 64, number_opt(value, prefix_t, suffix_t, color, 34))

    # 中部三列
    add("Border04", 80, 290, 580, 420, border04_opt(bar[0], c1, c2))
    add("BarCommon", 110, 340, 520, 340, bar_opt(*bar[1:]))

    add("Border04", 680, 290, 560, 420, border04_opt(line[0], c1, c2))
    add("LineCommon", 710, 340, 500, 340, line_opt(*line[1:]))

    add("Border04", 1260, 290, 580, 420, border04_opt(pie[0], c1, c2))
    add("PieCommon", 1290, 340, 520, 340, pie_opt(pie[1]))

    # 底栏
    add("Border04", 80, 740, 900, 300, border04_opt(capsule[0], c1, c2))
    add("CapsuleChart", 120, 790, 820, 220, capsule_opt(capsule[1], capsule[2] if len(capsule) > 2 else ""))

    add("Border04", 1000, 740, 840, 300, border04_opt(table[0], c1, c2))
    add("TableScrollBoard", 1030, 790, 780, 220, table_opt(table[1], table[2], table[3] if len(table) > 3 else None))

    return {
        "editCanvasConfig": canvas(title),
        "requestGlobalConfig": global_cfg(),
        "componentList": comps,
    }


# ---------------------------------------------------------------------------
# 4 套主题数据
# ---------------------------------------------------------------------------

HOURS = [f"{h:02d}:00" for h in range(8, 20)]
DAYS = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
LINES = ["一车间", "二车间", "三车间", "四车间", "五车间", "六车间"]


SCREENS = [
    {
        "id": 9301,
        "key": "factory",
        "name": "智慧工厂综合态势",
        "cover": "dash-factory-cover.svg",
        "remarks": "产能 KPI、产线对比、良率与告警",
        "subtitle": "yFeiEye · Smart Factory Situation Awareness",
        "accent": ("#5eead4", "#0e7490"),
        "cover_theme": dict(c1="#061820", c2="#0a4a5c", c3="#14b8a6", accent="#5eead4", motif="factory"),
        "kpis": [
            ("今日产量 (件)", 128460, "", "", "#5eead4"),
            ("综合 OEE", 86.4, "", "%", "#67e8f9"),
            ("在线设备", 968, "", "台", "#38bdf8"),
            ("待处理告警", 12, "", "条", "#fbbf24"),
        ],
        "bar": ("车间产量对比", LINES, [1280, 960, 1420, 880, 1100, 1250], [1180, 1020, 1300, 900, 980, 1190], "今日", "昨日"),
        "line": ("24h 产量趋势", HOURS, [820, 860, 910, 980, 1040, 1120, 1180, 1210, 1190, 1140, 1080, 990], [800, 840, 900, 960, 1000, 1080, 1150, 1180, 1160, 1100, 1040, 960], "实际", "计划"),
        "pie": ("良率分布", [("优等", 72), ("合格", 21), ("返工", 5), ("报废", 2)]),
        "capsule": ("产线负荷率", [("SMT-A", 92), ("SMT-B", 84), ("组装一线", 78), ("组装二线", 71), ("包装线", 66)], "%"),
        "table": (
            "实时告警",
            ["设备", "指标", "状态"],
            [
                ["注塑机-03", "温度偏高", "预警"],
                ["传送带-B2", "速度波动", "提示"],
                ["AGV-12", "电量低", "预警"],
                ["空压机-01", "压力偏低", "告警"],
                ["质检相机-07", "掉线", "严重"],
                ["烘箱-02", "门未关", "提示"],
                ["涂装线-A", "VOC 超标", "告警"],
                ["冷却塔-1", "液位低", "预警"],
            ],
            [40, 160, 120, 90],
        ),
        "deploy": (9401, "工厂态势正式投放", "DEMO-DASH-FACTORY", 1),
    },
    {
        "id": 9302,
        "key": "park",
        "name": "智慧园区运行监测",
        "cover": "dash-park-cover.svg",
        "remarks": "园区人流、车位能耗与通行安防",
        "subtitle": "yFeiEye · Smart Park Operations Monitor",
        "accent": ("#86efac", "#15803d"),
        "cover_theme": dict(c1="#052e16", c2="#166534", c3="#22c55e", accent="#86efac", motif="park"),
        "kpis": [
            ("在园人数", 3246, "", "人", "#86efac"),
            ("车位占用", 78.5, "", "%", "#4ade80"),
            ("今日能耗", 42.6, "", "MWh", "#34d399"),
            ("安防事件", 7, "", "起", "#fbbf24"),
        ],
        "bar": ("楼宇能耗对比", ["A座", "B座", "C座", "研发楼", "数据中心", "食堂"], [420, 380, 310, 290, 560, 180], [400, 360, 300, 270, 540, 170], "今日", "昨日"),
        "line": ("人流趋势", HOURS, [120, 260, 480, 620, 580, 540, 610, 690, 640, 520, 380, 210], [100, 240, 450, 600, 560, 520, 590, 670, 620, 500, 360, 200], "进园", "出园"),
        "pie": ("空间占用", [("办公", 46), ("研发", 22), ("仓储", 14), ("配套", 12), ("空置", 6)]),
        "capsule": ("停车分区占用", [("地下一层", 91), ("地下二层", 84), ("地面南区", 73), ("地面北区", 68), ("访客区", 55)], "%"),
        "table": (
            "通行与安防",
            ["位置", "事件", "时间"],
            [
                ["南门闸机", "访客登记", "09:12"],
                ["B座电梯厅", "尾随告警", "09:41"],
                ["地下车库", "逆行抓拍", "10:05"],
                ["数据中心", "门禁异常", "10:33"],
                ["西门", "危化车辆", "11:02"],
                ["食堂后门", "常开告警", "11:28"],
                ["屋顶机房", "烟感触发", "12:01"],
                ["东门", "黑名单车", "12:46"],
            ],
            [40, 140, 140, 90],
        ),
        "deploy": (9402, "园区监测正式投放", "DEMO-DASH-PARK", 1),
    },
    {
        "id": 9303,
        "key": "device",
        "name": "设备运维健康看板",
        "cover": "dash-device-cover.svg",
        "remarks": "设备在线率、健康评分与工单",
        "subtitle": "yFeiEye · Device Health & Maintenance Board",
        "accent": ("#93c5fd", "#1d4ed8"),
        "cover_theme": dict(c1="#0b1220", c2="#1e3a8a", c3="#3b82f6", accent="#93c5fd", motif="device"),
        "kpis": [
            ("接入设备", 1042, "", "台", "#93c5fd"),
            ("在线率", 94.8, "", "%", "#60a5fa"),
            ("今日工单", 36, "", "单", "#38bdf8"),
            ("超期未修", 5, "", "台", "#f87171"),
        ],
        "bar": ("分类健康评分", ["传感器", "网关", "PLC", "摄像头", "仪表", "机器人"], [96, 91, 88, 84, 93, 79], [94, 90, 86, 82, 92, 77], "本周", "上周"),
        "line": ("告警趋势(7日)", DAYS, [18, 22, 15, 27, 19, 12, 16], [20, 20, 18, 24, 18, 14, 15], "告警", "基线"),
        "pie": ("故障类型", [("通信中断", 34), ("超限告警", 28), ("硬件故障", 18), ("配置异常", 12), ("其他", 8)]),
        "capsule": ("重点设备健康度", [("冷水机组-1", 96), ("空压机-2", 88), ("变压器-A", 91), ("锅炉-1", 76), ("冷却塔-3", 83)], "分"),
        "table": (
            "运维工单",
            ["单号", "设备", "状态"],
            [
                ["WO-2401", "网关-GW08", "处理中"],
                ["WO-2402", "温湿度-TH21", "待派工"],
                ["WO-2403", "PLC-Line3", "已完成"],
                ["WO-2404", "摄像头-C12", "处理中"],
                ["WO-2405", "电表-EM09", "待验收"],
                ["WO-2406", "机器人-R2", "处理中"],
                ["WO-2407", "变频器-VFD4", "待派工"],
                ["WO-2408", "烟感-S33", "已完成"],
            ],
            [40, 110, 150, 90],
        ),
        "deploy": (9403, "设备运维预发布", "DEMO-DASH-DEVICE", 0),
    },
    {
        "id": 9304,
        "key": "energy",
        "name": "能源能耗分析大屏",
        "cover": "dash-energy-cover.svg",
        "remarks": "用电用气、峰谷分析与节电排名",
        "subtitle": "yFeiEye · Energy Consumption Analytics",
        "accent": ("#fcd34d", "#b45309"),
        "cover_theme": dict(c1="#1c1006", c2="#92400e", c3="#f59e0b", accent="#fcd34d", motif="energy"),
        "kpis": [
            ("今日用电", 186.4, "", "MWh", "#fcd34d"),
            ("今日用气", 28.7, "", "万m³", "#fbbf24"),
            ("碳排放", 92.3, "", "tCO₂e", "#fb923c"),
            ("节能完成率", 81.2, "", "%", "#86efac"),
        ],
        "bar": ("分项用电", ["动力", "照明", "空调", "生产", "IT机房", "其他"], [52, 18, 41, 48, 19, 8], [50, 17, 39, 46, 18, 9], "今日", "昨日"),
        "line": ("负荷曲线", HOURS, [6.2, 7.1, 8.4, 9.6, 10.8, 11.2, 10.9, 10.1, 9.4, 8.6, 7.8, 6.9], [6.0, 6.8, 8.0, 9.2, 10.4, 10.8, 10.5, 9.8, 9.0, 8.2, 7.4, 6.5], "实际负荷", "预测"),
        "pie": ("峰谷电量", [("峰时段", 42), ("平时段", 35), ("谷时段", 23)]),
        "capsule": ("车间节电排名", [("一车间", 96), ("三车间", 88), ("五车间", 81), ("二车间", 74), ("四车间", 69)], "分"),
        "table": (
            "能耗异常",
            ["回路", "偏差", "等级"],
            [
                ["空调-AHU3", "+18%", "关注"],
                ["空压总管", "+12%", "预警"],
                ["照明-B区", "+9%", "提示"],
                ["IT 机房 PDU2", "+22%", "告警"],
                ["锅炉给水泵", "-8%", "提示"],
                ["冷却塔风机", "+15%", "预警"],
                ["食堂热厨", "+11%", "关注"],
                ["充电桩群", "+27%", "告警"],
            ],
            [40, 150, 100, 90],
        ),
        "deploy": (9404, "能耗分析正式投放", "DEMO-DASH-ENERGY", 1),
    },
]


SCADA_PROJECTS = [
    (9311, "水厂工艺总貌", "scada-water-cover.svg", "水处理工艺：罐体、管线与阀门", "水厂工艺总貌"),
    (9312, "产线运行看板", "scada-line-cover.svg", "产线 KPI、电机与阀组状态", "产线运行看板"),
    (9313, "厂区管网组态", "scada-plant-cover.svg", "厂区管网拓扑与设备连线", "厂区管网组态"),
    (9314, "配电室电力监视", "scada-power-cover.svg", "配电室负荷、开关与电力监视", "配电室电力监视"),
]

SCADA_DEPLOYS = [
    (9411, "水厂组态正式投放", 9311, "水厂工艺总貌", "DEMO-SCADA-WATER", 1, "/home?view=%E6%B0%B4%E5%8E%82%E5%B7%A5%E8%89%BA%E6%80%BB%E8%B2%8C"),
    (9412, "产线看板正式投放", 9312, "产线运行看板", "DEMO-SCADA-LINE", 1, "/home?view=%E4%BA%A7%E7%BA%BF%E8%BF%90%E8%A1%8C%E7%9C%8B%E6%9D%BF"),
    (9413, "厂区管网预发布", 9313, "厂区管网组态", "DEMO-SCADA-PLANT", 0, "/home?view=%E5%8E%82%E5%8C%BA%E7%AE%A1%E7%BD%91%E7%BB%84%E6%80%81"),
    (9414, "配电室监视投放", 9314, "配电室电力监视", "DEMO-SCADA-POWER", 1, "/home?view=%E9%85%8D%E7%94%B5%E5%AE%A4%E7%94%B5%E5%8A%9B%E7%9B%91%E8%A7%86"),
]


def cover_svg(title: str, subtitle: str, theme: dict, kind: str = "DASHBOARD") -> str:
    """卡片封面：满幅渐变 + 主题图形。

    不在封面上放大标题（卡片 body 已有项目名），避免 background-size:cover 裁切文字。
    """
    _ = (title, subtitle)  # 保留参数兼容调用方
    c1, c2, c3, accent = theme["c1"], theme["c2"], theme["c3"], theme["accent"]
    motif = theme.get("motif", "factory")
    _ = kind
    # 接近卡片封面比例，减少裁切
    w, h = 800, 480
    if motif == "factory":
        art = f"""
  <rect x="90" y="140" width="70" height="160" rx="8" fill="{accent}" fill-opacity="0.2" stroke="{accent}" stroke-opacity="0.45"/>
  <rect x="180" y="100" width="70" height="200" rx="8" fill="{accent}" fill-opacity="0.26" stroke="{accent}" stroke-opacity="0.55"/>
  <rect x="270" y="170" width="70" height="130" rx="8" fill="{accent}" fill-opacity="0.18" stroke="{accent}" stroke-opacity="0.4"/>
  <path d="M90 330 H380" stroke="{accent}" stroke-opacity="0.35" stroke-width="5"/>
  <circle cx="600" cy="210" r="78" fill="none" stroke="{accent}" stroke-opacity="0.32" stroke-width="12"/>
  <circle cx="600" cy="210" r="46" fill="{accent}" fill-opacity="0.14"/>
"""
    elif motif == "park":
        art = f"""
  <path d="M80 340 L170 130 L260 340 Z" fill="{accent}" fill-opacity="0.2" stroke="{accent}" stroke-opacity="0.45"/>
  <path d="M220 340 L340 100 L460 340 Z" fill="{accent}" fill-opacity="0.15" stroke="{accent}" stroke-opacity="0.4"/>
  <rect x="500" y="180" width="190" height="110" rx="12" fill="{accent}" fill-opacity="0.12" stroke="{accent}" stroke-opacity="0.4"/>
  <circle cx="560" cy="235" r="20" fill="{accent}" fill-opacity="0.35"/>
  <circle cx="640" cy="235" r="20" fill="{accent}" fill-opacity="0.25"/>
"""
    elif motif == "device":
        art = f"""
  <rect x="90" y="130" width="230" height="160" rx="16" fill="{accent}" fill-opacity="0.12" stroke="{accent}" stroke-opacity="0.45"/>
  <circle cx="170" cy="210" r="40" fill="none" stroke="{accent}" stroke-opacity="0.55" stroke-width="9"/>
  <rect x="250" y="170" width="42" height="80" rx="8" fill="{accent}" fill-opacity="0.3"/>
  <path d="M360 160 H620" stroke="{accent}" stroke-opacity="0.35" stroke-width="3" stroke-dasharray="10 8"/>
  <circle cx="660" cy="160" r="14" fill="{accent}" fill-opacity="0.5"/>
  <circle cx="660" cy="230" r="14" fill="{accent}" fill-opacity="0.35"/>
  <circle cx="660" cy="300" r="14" fill="{accent}" fill-opacity="0.22"/>
"""
    elif motif == "line":
        art = f"""
  <rect x="100" y="150" width="140" height="90" rx="10" fill="{accent}" fill-opacity="0.14" stroke="{accent}" stroke-opacity="0.4"/>
  <rect x="280" y="130" width="140" height="130" rx="10" fill="{accent}" fill-opacity="0.18" stroke="{accent}" stroke-opacity="0.45"/>
  <rect x="460" y="170" width="140" height="70" rx="10" fill="{accent}" fill-opacity="0.12" stroke="{accent}" stroke-opacity="0.35"/>
  <path d="M100 290 H640" stroke="{accent}" stroke-opacity="0.35" stroke-width="6"/>
  <circle cx="170" cy="290" r="9" fill="{accent}" fill-opacity="0.55"/>
  <circle cx="350" cy="290" r="9" fill="{accent}" fill-opacity="0.45"/>
  <circle cx="530" cy="290" r="9" fill="{accent}" fill-opacity="0.35"/>
"""
    elif motif == "plant":
        art = f"""
  <circle cx="180" cy="210" r="62" fill="none" stroke="{accent}" stroke-opacity="0.35" stroke-width="8"/>
  <circle cx="350" cy="170" r="46" fill="none" stroke="{accent}" stroke-opacity="0.4" stroke-width="8"/>
  <circle cx="520" cy="230" r="54" fill="none" stroke="{accent}" stroke-opacity="0.3" stroke-width="8"/>
  <path d="M242 210 H304 M396 170 H466 M574 230 H660" stroke="{accent}" stroke-opacity="0.4" stroke-width="4"/>
  <rect x="640" y="180" width="80" height="90" rx="8" fill="{accent}" fill-opacity="0.15" stroke="{accent}" stroke-opacity="0.4"/>
"""
    elif motif == "power":
        art = f"""
  <rect x="110" y="120" width="100" height="190" rx="10" fill="{accent}" fill-opacity="0.14" stroke="{accent}" stroke-opacity="0.4"/>
  <rect x="250" y="150" width="100" height="160" rx="10" fill="{accent}" fill-opacity="0.18" stroke="{accent}" stroke-opacity="0.45"/>
  <rect x="390" y="100" width="100" height="210" rx="10" fill="{accent}" fill-opacity="0.12" stroke="{accent}" stroke-opacity="0.35"/>
  <path d="M530 140 L600 230 L670 160 L740 280" fill="none" stroke="{accent}" stroke-opacity="0.5" stroke-width="6"/>
"""
    else:  # energy
        art = f"""
  <path d="M110 340 V150 H170 V340" fill="none" stroke="{accent}" stroke-opacity="0.45" stroke-width="12"/>
  <path d="M210 340 V100 H270 V340" fill="none" stroke="{accent}" stroke-opacity="0.55" stroke-width="12"/>
  <path d="M310 340 V170 H370 V340" fill="none" stroke="{accent}" stroke-opacity="0.4" stroke-width="12"/>
  <path d="M450 270 L520 150 L590 240 L660 110 L730 200" fill="none" stroke="{accent}" stroke-opacity="0.55" stroke-width="6"/>
"""
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" preserveAspectRatio="xMidYMid slice">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="{c1}"/>
      <stop offset="52%" stop-color="{c2}"/>
      <stop offset="100%" stop-color="{c3}"/>
    </linearGradient>
    <linearGradient id="shine" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#ffffff" stop-opacity="0.16"/>
      <stop offset="55%" stop-color="#ffffff" stop-opacity="0"/>
    </linearGradient>
  </defs>
  <rect width="{w}" height="{h}" fill="url(#bg)"/>
  <rect width="{w}" height="{h}" fill="url(#shine)"/>
  <circle cx="720" cy="50" r="120" fill="#ffffff" fill-opacity="0.05"/>
  <circle cx="40" cy="440" r="150" fill="#ffffff" fill-opacity="0.04"/>
  {art}
</svg>
"""


def write_scada_covers() -> None:
    """组态封面与大屏封面同一套满幅规范。"""
    specs = [
        ("scada-water-cover.svg", "水厂工艺总貌", "Water Treatment HMI",
         dict(c1="#061820", c2="#0a4a5c", c3="#14b8a6", accent="#5eead4", motif="factory")),
        ("scada-line-cover.svg", "产线运行看板", "Production Line Dashboard",
         dict(c1="#0b1220", c2="#123a5c", c3="#266cfb", accent="#93c5fd", motif="line")),
        ("scada-plant-cover.svg", "厂区管网组态", "Plant Network Topology",
         dict(c1="#1c1006", c2="#7c2d12", c3="#ea580c", accent="#fdba74", motif="plant")),
        ("scada-power-cover.svg", "配电室电力监视", "Power Room Monitoring",
         dict(c1="#1e1033", c2="#4c1d95", c3="#7c3aed", accent="#c4b5fd", motif="power")),
    ]
    for name, title, subtitle, theme in specs:
        path = COVER_DIR / name
        path.write_text(cover_svg(title, subtitle, theme, kind="SCADA"), encoding="utf-8")
        log(f"  scada cover -> {path.relative_to(ROOT)}")


def dq(tag: str, s: str) -> str:
    return f"${tag}${s}${tag}$"


def sql_escape_literal(s: str) -> str:
    return "'" + s.replace("'", "''") + "'"


def build_seed_sql(contents: dict[str, str], templates: dict[str, str]) -> str:
    """生成完整可重复执行的种子 SQL。"""
    lines: list[str] = []
    lines.append("-- =============================================================================")
    lines.append("-- VISUALIZE 演示种子数据（可重复执行）")
    lines.append("-- 覆盖：素材库 / 数据源 / 模板中心 / 大屏×4 + 组态×4 / 服务部署")
    lines.append("-- 固定 ID 段：9001–9499，tenant_id=1")
    lines.append("--")
    lines.append("-- 生成：python3 .scripts/go-view/gen_visualize_dashboard_demo.py")
    lines.append("-- 导入：bash .scripts/go-view/seed_visualize_demo.sh")
    lines.append("-- =============================================================================")
    lines.append("")
    lines.append("BEGIN;")
    lines.append("")
    lines.append("DELETE FROM public.visualize_deploy WHERE id BETWEEN 9401 AND 9499;")
    lines.append("DELETE FROM public.visualize_project WHERE id BETWEEN 9301 AND 9399;")
    lines.append("DELETE FROM public.visualize_template WHERE id BETWEEN 9201 AND 9299;")
    lines.append("DELETE FROM public.visualize_datasource WHERE id BETWEEN 9101 AND 9199;")
    lines.append("DELETE FROM public.visualize_asset WHERE id BETWEEN 9001 AND 9099;")
    lines.append("")

    # assets
    lines.append("INSERT INTO public.visualize_asset")
    lines.append("  (id, asset_name, asset_type, file_url, file_size, remarks, tenant_id, creator, updater, deleted)")
    lines.append("VALUES")
    assets = [
        (9001, "yFeiEye Logo", "image", "/resource/visualize-demo/asset-logo.svg", 18200, "演示：品牌 Logo"),
        (9002, "深色大屏背景", "image", "/resource/visualize-demo/asset-bg.svg", 96000, "演示：1920x1080 深色背景"),
        (9003, "在线状态图标", "image", "/resource/visualize-demo/asset-icon.svg", 8200, "演示：状态图标素材"),
        (9004, "产线巡检视频封面", "video", "/resource/visualize-demo/asset-video.svg", 128000, "演示：视频类素材登记"),
        (9005, "工厂大屏封面素材", "image", "/resource/visualize-demo/dash-factory-cover.svg", 12000, "演示：工厂主题封面"),
        (9006, "园区大屏封面素材", "image", "/resource/visualize-demo/dash-park-cover.svg", 12000, "演示：园区主题封面"),
        (9007, "设备大屏封面素材", "image", "/resource/visualize-demo/dash-device-cover.svg", 12000, "演示：设备主题封面"),
        (9008, "能源大屏封面素材", "image", "/resource/visualize-demo/dash-energy-cover.svg", 12000, "演示：能源主题封面"),
    ]
    for i, a in enumerate(assets):
        comma = "," if i < len(assets) - 1 else ";"
        lines.append(
            f"  ({a[0]}, {sql_escape_literal(a[1])}, {sql_escape_literal(a[2])}, {sql_escape_literal(a[3])}, "
            f"{a[4]}, {sql_escape_literal(a[5])}, 1, 'admin', 'admin', 0){comma}"
        )
    lines.append("")

    # datasources
    lines.append("INSERT INTO public.visualize_datasource")
    lines.append("  (id, ds_name, ds_type, request_method, request_url, request_headers, request_body,")
    lines.append("   sql_content, static_data, status, remarks, tenant_id, creator, updater, deleted)")
    lines.append("VALUES")
    static_ds = json.dumps(
        {
            "dimensions": ["name", "value"],
            "source": [
                {"name": "产线A", "value": 128},
                {"name": "产线B", "value": 96},
                {"name": "产线C", "value": 142},
                {"name": "产线D", "value": 88},
            ],
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    lines.append(
        f"  (9101, '产线产能静态数据', 'static', 'GET', NULL, NULL, NULL, NULL,\n"
        f"   {dq('ds', static_ds)}, 0, '演示：静态 JSON，可直接绑定图表', 1, 'admin', 'admin', 0),"
    )
    lines.append(
        "  (9102, '公开 HTTP 演示接口', 'http', 'GET', 'https://jsonplaceholder.typicode.com/todos/1',\n"
        "   '{\"Accept\":\"application/json\"}', NULL, NULL, NULL, 0,\n"
        "   '演示：HTTP GET（公开接口，便于联调）', 1, 'admin', 'admin', 0),"
    )
    lines.append(
        "  (9103, '设备日产量 SQL 示例', 'sql', 'GET', NULL, NULL, NULL,\n"
        "   'SELECT line_name AS name, output_qty AS value FROM factory_daily_output WHERE stat_date = CURRENT_DATE',\n"
        "   NULL, 0, '演示：SQL 数据源配置示例（需自行对接实际库表）', 1, 'admin', 'admin', 0),"
    )
    lines.append(
        "  (9104, '设备影子（停用示例）', 'device', 'GET', NULL, NULL, NULL, NULL,\n"
        "   '{\"deviceId\":\"9720084293632004\",\"metrics\":[\"online\",\"alarm\"]}', 1,\n"
        "   '演示：设备数据源（停用状态，便于测试筛选）', 1, 'admin', 'admin', 0);"
    )
    lines.append("")

    # templates
    lines.append("INSERT INTO public.visualize_template")
    lines.append("  (id, template_name, category, cover_image, remarks, content, sort, tenant_id, creator, updater, deleted)")
    lines.append("VALUES")
    tpl_rows = [
        (9201, "物联网监控模板", "物联网", "/resource/visualize-demo/tpl-iot.svg", "演示模板：标题+KPI骨架+柱状图，可另存为项目", templates["iot"], 10),
        (9202, "运维态势模板", "运维", "/resource/visualize-demo/tpl-ops.svg", "演示模板：运维告警列表布局，适合值班大屏", templates["ops"], 20),
        (9203, "空白深色画布", "基础", "/resource/visualize-demo/tpl-blank.svg", "演示模板：仅深色画布，从零搭建", templates["blank"], 30),
    ]
    for i, t in enumerate(tpl_rows):
        comma = "," if i < len(tpl_rows) - 1 else ";"
        lines.append(
            f"  ({t[0]}, {sql_escape_literal(t[1])}, {sql_escape_literal(t[2])}, {sql_escape_literal(t[3])}, "
            f"{sql_escape_literal(t[4])},\n   {dq(f't{t[0]}', t[5])}, {t[6]}, 1, 'admin', 'admin', 0){comma}"
        )
    lines.append("")

    # projects: dashboards + scada
    lines.append("INSERT INTO public.visualize_project")
    lines.append("  (id, project_name, project_type, state, index_image, remarks, content, editor_ref, tenant_id, creator, updater, deleted)")
    lines.append("VALUES")
    proj_parts: list[str] = []
    for s in SCREENS:
        content = contents[s["key"]]
        proj_parts.append(
            f"  ({s['id']}, {sql_escape_literal(s['name'])}, 'dashboard', 1, "
            f"{sql_escape_literal('/resource/visualize-demo/' + s['cover'])},\n"
            f"   {sql_escape_literal(s['remarks'])}, {dq(f'p{s['id']}', content)}, NULL, 1, 'admin', 'admin', 0)"
        )
    for pid, name, cover, remarks, editor_ref in SCADA_PROJECTS:
        proj_parts.append(
            f"  ({pid}, {sql_escape_literal(name)}, 'scada', 1, "
            f"{sql_escape_literal('/resource/visualize-demo/' + cover)},\n"
            f"   {sql_escape_literal(remarks)}, NULL, {sql_escape_literal(editor_ref)}, 1, 'admin', 'admin', 0)"
        )
    lines.append(",\n".join(proj_parts) + ";")
    lines.append("")

    # deploys
    lines.append("INSERT INTO public.visualize_deploy")
    lines.append("  (id, deploy_name, project_id, project_name, deploy_code, status, access_path, expire_time,")
    lines.append("   remarks, tenant_id, creator, updater, deleted)")
    lines.append("VALUES")
    dep_parts: list[str] = []
    for s in SCREENS:
        did, dname, code, status = s["deploy"]
        path = f"/chart/preview/{s['id']}"
        rem = f"演示：{'已上线' if status == 1 else '草稿'}，预览打开大屏「{s['name']}」"
        dep_parts.append(
            f"  ({did}, {sql_escape_literal(dname)}, {s['id']}, {sql_escape_literal(s['name'])}, "
            f"{sql_escape_literal(code)}, {status},\n"
            f"   {sql_escape_literal(path)}, NULL, {sql_escape_literal(rem)}, 1, 'admin', 'admin', 0)"
        )
    for did, dname, pid, pname, code, status, path in SCADA_DEPLOYS:
        rem = f"演示：{'已上线' if status == 1 else '草稿'}，预览打开 FUXA「{pname}」"
        dep_parts.append(
            f"  ({did}, {sql_escape_literal(dname)}, {pid}, {sql_escape_literal(pname)}, "
            f"{sql_escape_literal(code)}, {status},\n"
            f"   {sql_escape_literal(path)}, NULL, {sql_escape_literal(rem)}, 1, 'admin', 'admin', 0)"
        )
    lines.append(",\n".join(dep_parts) + ";")
    lines.append("")

    lines.append("SELECT setval('public.visualize_asset_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.visualize_asset), 1));")
    lines.append("SELECT setval('public.visualize_datasource_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.visualize_datasource), 1));")
    lines.append("SELECT setval('public.visualize_template_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.visualize_template), 1));")
    lines.append("SELECT setval('public.visualize_project_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.visualize_project), 1));")
    lines.append("SELECT setval('public.visualize_deploy_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.visualize_deploy), 1));")
    lines.append("")
    lines.append("COMMIT;")
    lines.append("")
    lines.append("SELECT 'asset' AS module, count(*) AS cnt FROM public.visualize_asset WHERE id BETWEEN 9001 AND 9099")
    lines.append("UNION ALL SELECT 'datasource', count(*) FROM public.visualize_datasource WHERE id BETWEEN 9101 AND 9199")
    lines.append("UNION ALL SELECT 'template', count(*) FROM public.visualize_template WHERE id BETWEEN 9201 AND 9299")
    lines.append("UNION ALL SELECT 'project', count(*) FROM public.visualize_project WHERE id BETWEEN 9301 AND 9399")
    lines.append("UNION ALL SELECT 'deploy', count(*) FROM public.visualize_deploy WHERE id BETWEEN 9401 AND 9499;")
    lines.append("")
    return "\n".join(lines)


def build_templates() -> dict[str, str]:
    """基于工厂大屏裁剪出可用模板。"""
    factory = SCREENS[0]
    full = build_screen(
        "tpliot",
        "物联网监控模板",
        factory["subtitle"],
        factory["accent"],
        factory["kpis"],
        factory["bar"],
        factory["line"],
        factory["pie"],
        factory["capsule"],
        factory["table"],
    )
    # 模板精简：去掉底栏表格，保留主体
    full["componentList"] = [c for c in full["componentList"] if not c["id"].startswith("tpliot-TableScrollBoard")]
    full["editCanvasConfig"]["projectName"] = "物联网监控模板"

    ops = build_screen(
        "tplops",
        "运维态势模板",
        SCREENS[2]["subtitle"],
        SCREENS[2]["accent"],
        SCREENS[2]["kpis"],
        SCREENS[2]["bar"],
        SCREENS[2]["line"],
        SCREENS[2]["pie"],
        SCREENS[2]["capsule"],
        SCREENS[2]["table"],
    )
    ops["editCanvasConfig"]["projectName"] = "运维态势模板"

    blank = {
        "editCanvasConfig": canvas("空白深色画布"),
        "requestGlobalConfig": global_cfg(),
        "componentList": [],
    }
    return {
        "iot": json.dumps(full, ensure_ascii=False, separators=(",", ":")),
        "ops": json.dumps(ops, ensure_ascii=False, separators=(",", ":")),
        "blank": json.dumps(blank, ensure_ascii=False, separators=(",", ":")),
    }


def write_compat_covers() -> None:
    """兼容旧封面文件名（列表里可能仍引用）。"""
    mapping = {
        "factory-cover.svg": "dash-factory-cover.svg",
        "park-cover.svg": "dash-park-cover.svg",
        "device-cover.svg": "dash-device-cover.svg",
        "tpl-iot.svg": "dash-factory-cover.svg",
        "tpl-ops.svg": "dash-device-cover.svg",
        "tpl-blank.svg": "dash-energy-cover.svg",
    }
    for dst, src in mapping.items():
        src_path = COVER_DIR / src
        if src_path.exists():
            (COVER_DIR / dst).write_text(src_path.read_text(encoding="utf-8"), encoding="utf-8")


def main() -> int:
    t0 = time.time()
    log("start (pure python, no browser)")
    COVER_DIR.mkdir(parents=True, exist_ok=True)
    CONTENT_DIR.mkdir(parents=True, exist_ok=True)

    contents: dict[str, str] = {}
    for i, s in enumerate(SCREENS, 1):
        log(f"build screen {i}/4: {s['name']}")
        data = build_screen(
            s["key"],
            s["name"],
            s["subtitle"],
            s["accent"],
            s["kpis"],
            s["bar"],
            s["line"],
            s["pie"],
            s["capsule"],
            s["table"],
        )
        raw = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
        contents[s["key"]] = raw
        out = CONTENT_DIR / f"{s['key']}.json"
        out.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        log(f"  components={len(data['componentList'])} json={len(raw)}B -> {out.relative_to(ROOT)}")

        cover_path = COVER_DIR / s["cover"]
        cover_path.write_text(cover_svg(s["name"], s["subtitle"], s["cover_theme"], kind="DASHBOARD"), encoding="utf-8")
        log(f"  cover -> {cover_path.relative_to(ROOT)}")

    log("build scada covers")
    write_scada_covers()

    log("build templates")
    templates = build_templates()
    for k, v in templates.items():
        (CONTENT_DIR / f"tpl-{k}.json").write_text(
            json.dumps(json.loads(v), ensure_ascii=False, indent=2), encoding="utf-8"
        )

    log("write compat cover aliases")
    write_compat_covers()

    log("write seed SQL")
    sql = build_seed_sql(contents, templates)
    SEED_SQL.write_text(sql, encoding="utf-8")
    log(f"  seed -> {SEED_SQL.relative_to(ROOT)} ({len(sql)} bytes)")

    # validate JSON roundtrip from SQL dollar quotes conceptually
    for k, raw in contents.items():
        json.loads(raw)
    for v in templates.values():
        json.loads(v)

    elapsed = time.time() - t0
    log(f"done in {elapsed:.2f}s")
    log("next: bash .scripts/go-view/seed_visualize_demo.sh")
    log("      (optional) bash .scripts/fuxa/seed_fuxa_demo.sh  # 组态画面")
    return 0


if __name__ == "__main__":
    sys.exit(main())
