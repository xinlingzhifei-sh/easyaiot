# 逸飞 AI 智眼系统 BP V6 交付实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 生成并验证一套 16 页、可编辑、证据可追溯的逸飞 AI 智眼系统创业大赛 BP V6，同时生成可正常打开同目录 PDF 的最终 Excel。

**Architecture:** 使用一个受测试约束的 Python 内容模型作为事实合同，以 `python-pptx` 生成可编辑 PPTX；通过 LibreOffice 无界面模式把 PPTX 导出为 PDF，并用 Poppler/PyMuPDF 逐页渲染检查。最终 Excel 从已修复模板复制，使用 `openpyxl` 只修改首个工作表 `J3` 的显示值与相对超链接，随后以结构快照和人工点击共同验证。

**Tech Stack:** Python 3.11、pytest 8.3、python-pptx 1.0、openpyxl 3.1、Pillow、PyMuPDF 1.27、pypdf 6.10、LibreOffice Impress、Poppler、PowerShell、Codex 应用内浏览器。

---

## 范围检查

这是一条相互依赖的交付链，不拆成多个独立计划：PDF 必须由 V6 PPTX 导出，Excel 又必须指向最终 PDF 的稳定文件名。内容、界面截图、PDF 渲染和 Excel 链接虽然使用不同工具，但共享同一事实合同、文件名和验收报告，拆分后更容易产生版本漂移。

实施开始前必须在独立工作树运行；当前主工作区包含用户尚未提交的 VIDEO 和 dashboard 修改，不得把它们带入本交付。

## 已核实的本机条件

- Python 依赖已存在：`pytest`、`python-pptx`、`openpyxl`、`Pillow`、`PyMuPDF`、`pypdf`。
- Microsoft YaHei 常规体和粗体已安装。
- Microsoft Office 可执行文件存在，但 64 位和 32 位 PowerShell 均无法创建 Excel COM 对象；计划不依赖 Office COM。
- `soffice`、`pdftoppm` 当前不在 PATH；实施时用 WinGet 安装 LibreOffice 和 Poppler，并立即验证可执行文件。
- 原始 PDF 为 16 页、16:9，保留为不可覆盖基线。
- 最终 Excel 的源文件共有 11 个工作表；首个工作表为 `项目模板`，目标 PDF 单元格为 `J3`。
- `J3` 当前显示及相对链接均为 `逸飞AI智眼系统.pdf`；只把这两个值改为 `逸飞AI智眼系统_创业大赛BP_V6.pdf`。

## 文件结构

### 将创建并纳入 Git 的文件

- `tools/__init__.py`：使交付工具可由 `python -m` 调用。
- `tools/bp_v6/__init__.py`：BP V6 工具包入口。
- `tools/bp_v6/content.py`：唯一事实合同、16 页标题、指标、价格、融资和来源。
- `tools/bp_v6/theme.py`：16:9 画布、颜色、字体、文本框、卡片、页脚和来源链接组件。
- `tools/bp_v6/assets.py`：检查三张已脱敏截图的文件名、尺寸、格式和哈希。
- `tools/bp_v6/build_pptx.py`：读取内容合同与截图，生成 16 页可编辑 PPTX。
- `tools/bp_v6/export_pdf.ps1`：调用 LibreOffice 无界面导出 PDF。
- `tools/bp_v6/render_pdf.py`：使用 PyMuPDF 生成逐页 PNG 和总览联系表。
- `tools/bp_v6/excel_link.py`：复制输入工作簿并只修改首个工作表 `J3`。
- `tools/bp_v6/verify_artifacts.py`：检查 PPTX、PDF、Excel、源文件哈希及关键事实一致性。
- `tools/bp_v6/tests/test_content.py`：事实、公式、页数和禁止主张测试。
- `tools/bp_v6/tests/test_theme.py`：版式常量和基础组件测试。
- `tools/bp_v6/tests/test_assets.py`：截图资产合同测试。
- `tools/bp_v6/tests/test_pptx.py`：PPTX 页数、标题、关键文本、尺寸和敏感信息测试。
- `tools/bp_v6/tests/test_excel_link.py`：Excel 最小改动、相对链接和其他工作表一致性测试。
- `tools/bp_v6/tests/test_final_artifacts.py`：端到端文件与 PDF 文本合同测试。

### 临时工作目录，不纳入 Git

- `E:\yFeiEye\tmp\bp_v6\assets\`：三张已脱敏产品截图。
- `E:\yFeiEye\tmp\bp_v6\render\`：PDF 逐页 PNG。
- `E:\yFeiEye\tmp\bp_v6\reports\`：基线、视觉检查和最终验证报告。
- `E:\yFeiEye\tmp\bp_v6\office-profile\`：LibreOffice 独立用户配置，避免占用用户 Office 配置。

### 最终交付目录

`C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料`

- `逸飞AI智眼系统_创业大赛BP_V6.pptx`
- `逸飞AI智眼系统_创业大赛BP_V6.pdf`
- `创企大赛导入项目模板_逸飞AI智眼系统_最终版.xlsx`

### 不得修改的输入

- `C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统.pdf`
  - SHA-256：`A92530CC54331A14E8950ED55C32F40F1BE6DFF19548E6AFD78BCF2525C4CB54`
- `C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\创企大赛导入项目模板_逸飞AI智眼系统_PDF链接已修复.xlsx`
  - SHA-256：`675B4481F3487936FB3A1BDACDBADBD988D20A173C5BE1731339652CCCEFD086`

## 任务 0：创建隔离工作树并记录基线

**Files:**
- Read: `docs/superpowers/specs/2026-07-15-yfeieye-bp-v6-design.md`
- Create: `E:\yFeiEye\tmp\bp_v6\reports\baseline.json`

- [ ] **Step 1: 在执行会话中加载工作树技能**

使用 `using-git-worktrees` 技能。工作树固定为 `E:\yFeiEye\.worktrees\bp-v6-artifacts`，分支固定为 `codex/bp-v6-artifacts`。

- [ ] **Step 2: 从包含本计划的当前提交创建工作树**

```powershell
$base = git rev-parse HEAD
git worktree add -b codex/bp-v6-artifacts 'E:\yFeiEye\.worktrees\bp-v6-artifacts' $base
git -C 'E:\yFeiEye\.worktrees\bp-v6-artifacts' status --short
```

Expected: 工作树创建成功，`status --short` 无输出；原工作区的 VIDEO 和 dashboard 修改仍只存在于原工作区。

- [ ] **Step 3: 创建临时目录**

```powershell
$root = 'E:\yFeiEye\tmp\bp_v6'
@('assets','render','reports','office-profile') | ForEach-Object {
    New-Item -ItemType Directory -Force -Path (Join-Path $root $_) | Out-Null
}
```

Expected: 四个目录存在，且均位于 `E:\yFeiEye\tmp\bp_v6` 下。

- [ ] **Step 4: 验证不可变输入哈希**

```powershell
$pdf = 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统.pdf'
$xlsx = 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\创企大赛导入项目模板_逸飞AI智眼系统_PDF链接已修复.xlsx'
(Get-FileHash -Algorithm SHA256 -LiteralPath $pdf).Hash
(Get-FileHash -Algorithm SHA256 -LiteralPath $xlsx).Hash
```

Expected: 分别精确等于本计划“不得修改的输入”中记录的两个哈希；不一致时停止，不覆盖任何文件，并向用户报告输入已变化。

- [ ] **Step 5: 记录基线 JSON**

使用 `apply_patch` 创建：

```json
{
  "source_pdf": {
    "path": "C:\\Users\\86135\\Desktop\\逸飞AI智眼系统_创业大赛申报材料\\逸飞AI智眼系统.pdf",
    "sha256": "A92530CC54331A14E8950ED55C32F40F1BE6DFF19548E6AFD78BCF2525C4CB54",
    "pages": 16
  },
  "source_workbook": {
    "path": "C:\\Users\\86135\\Desktop\\逸飞AI智眼系统_创业大赛申报材料\\创企大赛导入项目模板_逸飞AI智眼系统_PDF链接已修复.xlsx",
    "sha256": "675B4481F3487936FB3A1BDACDBADBD988D20A173C5BE1731339652CCCEFD086",
    "sheet_count": 11,
    "first_sheet": "项目模板",
    "pdf_cell": "J3"
  }
}
```

## 任务 1：建立事实合同与失败测试

**Files:**
- Create: `tools/__init__.py`
- Create: `tools/bp_v6/__init__.py`
- Create: `tools/bp_v6/content.py`
- Create: `tools/bp_v6/tests/test_content.py`

- [ ] **Step 1: 写事实合同失败测试**

```python
from tools.bp_v6.content import (
    COMPANY,
    FORECAST,
    FUNDING,
    PILOT_TARGETS,
    PRICING,
    SLIDES,
)


def test_locked_company_facts():
    assert COMPANY["established"] == "2025-12-09"
    assert COMPANY["stage"] == "已有平台能力，商业化启动"
    assert COMPANY["revenue_since_establishment_wan"] == 0
    assert COMPANY["formal_customers"] == 0
    assert COMPANY["formal_contracts"] == 0
    assert COMPANY["formal_pilots"] == 0
    assert COMPANY["core_team_size"] == 1


def test_exactly_sixteen_slides():
    assert len(SLIDES) == 16
    assert [slide.number for slide in SLIDES] == list(range(1, 17))
    assert SLIDES[0].title == "让监管告警走向闭环办结"
    assert SLIDES[-1].title == "寻找首个付费试点与司法安防生态伙伴"


def test_forecast_is_explained_by_project_mix():
    assert FORECAST[2027]["target"] == 200
    assert FORECAST[2027]["calculated"] == 204.4
    assert FORECAST[2028]["target"] == 650
    assert FORECAST[2028]["calculated"] == 667.6
    assert FORECAST[2029]["target"] == 1500
    assert FORECAST[2029]["calculated"] == 1535.2


def test_pricing_funding_and_pilot_targets():
    assert PRICING == {
        "trial_8_route": 26.8,
        "standard_site": 75.4,
        "full_site": 156.2,
        "cluster_64_route": 188.8,
        "annual_governance": 15.8,
        "algorithm_rule_pack": 2.8,
    }
    assert sum(item["amount"] for item in FUNDING) == 500
    assert sum(item["ratio"] for item in FUNDING) == 100
    assert PILOT_TARGETS["routes"] == 8
    assert PILOT_TARGETS["days"] == 30
    assert PILOT_TARGETS["latency_p95_seconds"] == 10
    assert PILOT_TARGETS["evidence_completeness_percent"] == 95
    assert PILOT_TARGETS["closed_loop_percent"] == 90
    assert PILOT_TARGETS["repeat_false_positive_reduction_percent"] == 30


def test_planning_language_and_forbidden_claims():
    all_copy = "\n".join(
        [slide.title + "\n" + "\n".join(slide.body) for slide in SLIDES]
    )
    assert "试点验收目标，不是历史业绩" in all_copy
    for forbidden in (
        "100%自研",
        "完全自主知识产权",
        "国内领先",
        "已通过等保",
        "已服务多家单位",
        "已签约",
        "已落地",
    ):
        assert forbidden not in all_copy
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
python -m pytest tools/bp_v6/tests/test_content.py -q
```

Expected: FAIL，错误为 `ModuleNotFoundError: No module named 'tools.bp_v6'` 或缺少合同常量。

- [ ] **Step 3: 实现内容数据结构和锁定值**

`content.py` 使用不可变数据结构，禁止在页面构建代码中重复写关键数字：

```python
from dataclasses import dataclass


@dataclass(frozen=True)
class SlideSpec:
    number: int
    title: str
    layout: str
    body: tuple[str, ...]
    source_keys: tuple[str, ...] = ()


COMPANY = {
    "name": "心灵智飞（上海）科技有限公司",
    "project": "逸飞AI智眼系统",
    "established": "2025-12-09",
    "stage": "已有平台能力，商业化启动",
    "revenue_since_establishment_wan": 0,
    "formal_customers": 0,
    "formal_contracts": 0,
    "formal_pilots": 0,
    "formal_channels": 0,
    "core_team_size": 1,
}

FOUNDER = {
    "name": "刘飞",
    "education": "上海理工大学｜本科｜计算机科学与技术",
    "experience": "7年软件研发 + 5年项目经理｜从业13年",
    "resources": "参与多次行业峰会；与司法安防相关部门开展多次技术交流和需求沟通。受合作单位保密要求，不披露单位名称。尚未形成正式客户、合同或试点。",
    "responsibilities": (
        "产品策略与系统架构",
        "核心平台研发与AI视频能力整合",
        "售前方案与项目交付准备",
        "外部行业资源协调",
    ),
}

PRICING = {
    "trial_8_route": 26.8,
    "standard_site": 75.4,
    "full_site": 156.2,
    "cluster_64_route": 188.8,
    "annual_governance": 15.8,
    "algorithm_rule_pack": 2.8,
}

FORECAST = {
    2027: {"target": 200, "calculated": 204.4, "mix": "2个试用包 + 2个标准项目"},
    2028: {"target": 650, "calculated": 667.6, "mix": "2个试用包 + 4个标准项目 + 2个全所项目"},
    2029: {"target": 1500, "calculated": 1535.2, "mix": "3个试用包 + 6个标准项目 + 4个全所项目 + 2个集群项目"},
}

FUNDING = (
    {"name": "产品研发与核心招聘", "ratio": 40, "amount": 200},
    {"name": "司法试点、部署与交付", "ratio": 25, "amount": 125},
    {"name": "算力、数据治理、标注与模型优化", "ratio": 15, "amount": 75},
    {"name": "渠道、行业活动与市场拓展", "ratio": 10, "amount": 50},
    {"name": "合规、安全、知识产权与流动资金", "ratio": 10, "amount": 50},
)

PILOT_TARGETS = {
    "routes": 8,
    "days": 30,
    "rules": "2-3类",
    "latency_p95_seconds": 10,
    "evidence_completeness_percent": 95,
    "closed_loop_percent": 90,
    "repeat_false_positive_reduction_percent": 30,
    "label": "试点验收目标，不是历史业绩",
}
```

`SLIDES` 必须按下列精确合同构建；正文采用设计规格对应章节中的批准文案，不得重新扩展主张：

| 页 | layout | title | 必须出现的正文锚点 | source_keys |
| --- | --- | --- | --- | --- |
| 1 | cover | 让监管告警走向闭环办结 | 视频接入、AI识别、人工复核、闭环处置 | 无 |
| 2 | pain | 摄像头看见了异常，组织仍可能无法完成处置 | 告警孤岛、人工盯屏、重复误报、处置断点 | association_2025、procurement_examples |
| 3 | flow | 从“算法命中”到“事件办结”，需要一条可追溯工作流 | 接入、识别、复核、派单、处置、复查、归档 | 无 |
| 4 | stage | 平台能力已经可演示，商业化从首个付费试点启动 | 2025-12-09、0万元、暂无正式客户/合同/试点 | 无 |
| 5 | screenshots | 真实系统已覆盖设备接入、算法任务与线索复核 | 演示环境实机界面、已做数据脱敏、不是客户运营指标 | 无 |
| 6 | architecture | 核心数据可留在客户内网，告警在私有边界内闭环 | GB28181、ONVIF、RTSP、MQTT、HTTP、客户内网/专有云 | 无 |
| 7 | comparison | 差异化来自“兼容接入 + 多模型编排 + 闭环审计” | 传统视频/SI、单点算法、逸飞AI智眼系统 | 无 |
| 8 | compliance | 合规能力必须内建，而不是项目交付后的补丁 | 最小必要、RBAC、审计、导出审批、人工确认 | privacy_law、data_law、cyber_law_2025、video_regulation、gb22239 |
| 9 | pilot | 首个付费试点用8路、30天验证闭环价值 | P95≤10秒、≥95%、≥90%、≥30%、不是历史业绩 | 无 |
| 10 | market | 司法监管是可切入的专门市场，需求已有公开采购佐证 | 7.9-16.4亿元、2350万元、1.4%-3.0%、统计年份 | institution_counts、procurement_examples |
| 11 | pricing | 从低风险试用到整所运营，客单价可逐级扩展 | 26.8、75.4、156.2、188.8、15.8、2.8万元 | 无 |
| 12 | flow | 获客从技术交流切入，以集成协同和付费试点完成验证 | 需求交流、联合方案、付费试点、单所扩容、多点复制 | 无 |
| 13 | founder | 创始人兼具研发、项目管理与行业沟通经验 | 刘飞、上海理工大学、13年、当前单人团队 | 无 |
| 14 | timeline | 2026年先完成可复用交付与首个付费验证 | Q3计划、Q4目标、8路30天 | 无 |
| 15 | finance | 三年目标由可解释的项目组合支撑，500万用于补齐商业化能力 | 200、650、1500万元、500万元、五项资金用途 | 无 |
| 16 | cta | 寻找首个付费试点与司法安防生态伙伴 | 付费试点、集成协同、算力/数据治理、产业资源 | 无 |

`SOURCES` 至少包含设计规格第 22 节的全部链接，并为每个条目保存 `label`、`url`、`year` 和 `kind`。页面只显示可读短标签，PPTX 运行文本的超链接地址使用完整 `url`。

- [ ] **Step 4: 运行内容测试**

```powershell
python -m pytest tools/bp_v6/tests/test_content.py -q
```

Expected: `5 passed`。

- [ ] **Step 5: 提交事实合同**

```powershell
git add tools/__init__.py tools/bp_v6/__init__.py tools/bp_v6/content.py tools/bp_v6/tests/test_content.py
git diff --cached --check
git commit -m "test: lock BP V6 facts and slide contract"
```

## 任务 2：实现版式与可编辑组件

**Files:**
- Create: `tools/bp_v6/theme.py`
- Create: `tools/bp_v6/tests/test_theme.py`

- [ ] **Step 1: 写版式失败测试**

```python
from pptx import Presentation

from tools.bp_v6.theme import (
    FONT_BOLD,
    FONT_REGULAR,
    SLIDE_HEIGHT,
    SLIDE_WIDTH,
    add_footer,
    add_title,
    new_presentation,
)


def test_slide_size_and_fonts():
    prs = new_presentation()
    assert prs.slide_width == SLIDE_WIDTH
    assert prs.slide_height == SLIDE_HEIGHT
    assert FONT_REGULAR == "Microsoft YaHei"
    assert FONT_BOLD == "Microsoft YaHei"


def test_title_and_footer_are_editable_text():
    prs = new_presentation()
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_title(slide, "测试标题", 3)
    add_footer(slide, 3)
    text = "\n".join(shape.text for shape in slide.shapes if hasattr(shape, "text"))
    assert "测试标题" in text
    assert "03" in text
    assert "逸飞AI智眼系统｜创业大赛 BP V6" in text
```

- [ ] **Step 2: 运行测试并确认失败**

```powershell
python -m pytest tools/bp_v6/tests/test_theme.py -q
```

Expected: FAIL，`tools.bp_v6.theme` 不存在。

- [ ] **Step 3: 实现主题常量与基础组件**

`theme.py` 必须使用以下锁定值：

```python
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Emu, Inches, Pt

SLIDE_WIDTH = Emu(12192000)
SLIDE_HEIGHT = Inches(7.5)
FONT_REGULAR = "Microsoft YaHei"
FONT_BOLD = "Microsoft YaHei"

NAVY = RGBColor(11, 24, 39)
INK = RGBColor(31, 41, 55)
MUTED = RGBColor(100, 116, 139)
PAPER = RGBColor(247, 249, 252)
WHITE = RGBColor(255, 255, 255)
CYAN = RGBColor(30, 153, 184)
AMBER = RGBColor(217, 119, 6)
RED = RGBColor(185, 28, 28)
LINE = RGBColor(214, 222, 232)


def new_presentation() -> Presentation:
    prs = Presentation()
    prs.slide_width = SLIDE_WIDTH
    prs.slide_height = SLIDE_HEIGHT
    return prs


def add_text(slide, text, x, y, w, h, *, size=18, color=INK,
             bold=False, align=PP_ALIGN.LEFT, valign=MSO_ANCHOR.TOP):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    frame = box.text_frame
    frame.clear()
    frame.margin_left = frame.margin_right = Inches(0.04)
    frame.margin_top = frame.margin_bottom = Inches(0.02)
    frame.vertical_anchor = valign
    paragraph = frame.paragraphs[0]
    paragraph.alignment = align
    run = paragraph.add_run()
    run.text = text
    run.font.name = FONT_BOLD if bold else FONT_REGULAR
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    return box


def add_title(slide, title: str, page: int):
    add_text(slide, f"{page:02d}", 0.55, 0.42, 0.55, 0.35,
             size=12, color=CYAN, bold=True)
    return add_text(slide, title, 1.18, 0.34, 11.45, 0.74,
                    size=25, color=NAVY, bold=True)


def add_card(slide, x, y, w, h, *, fill=WHITE, line=LINE, radius=True):
    shape_type = MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE
    card = slide.shapes.add_shape(shape_type, Inches(x), Inches(y), Inches(w), Inches(h))
    card.fill.solid()
    card.fill.fore_color.rgb = fill
    card.line.color.rgb = line
    card.line.width = Pt(0.8)
    return card


def add_footer(slide, page: int):
    add_text(slide, "逸飞AI智眼系统｜创业大赛 BP V6",
             0.55, 7.13, 5.7, 0.2, size=8, color=MUTED)
    add_text(slide, f"{page:02d}/16", 11.95, 7.13, 0.8, 0.2,
             size=8, color=MUTED, align=PP_ALIGN.RIGHT)


def add_source(slide, label: str, url: str, x: float, y: float, w: float):
    box = add_text(slide, label, x, y, w, 0.22, size=7, color=MUTED)
    run = box.text_frame.paragraphs[0].runs[0]
    run.hyperlink.address = url
    return box
```

图表和架构节点同样使用 PowerPoint 原生形状和文本框；除三张产品截图外，不把文字烘焙进图片，保证可编辑性。

- [ ] **Step 4: 运行版式测试**

```powershell
python -m pytest tools/bp_v6/tests/test_theme.py -q
```

Expected: `2 passed`。

- [ ] **Step 5: 提交版式组件**

```powershell
git add tools/bp_v6/theme.py tools/bp_v6/tests/test_theme.py
git diff --cached --check
git commit -m "feat: add editable BP V6 slide primitives"
```

## 任务 3：采集并验证真实产品界面

**Files:**
- Create: `tools/bp_v6/assets.py`
- Create: `tools/bp_v6/tests/test_assets.py`
- Create outside Git: `E:\yFeiEye\tmp\bp_v6\assets\algorithm-task.png`
- Create outside Git: `E:\yFeiEye\tmp\bp_v6\assets\device-archive.png`
- Create outside Git: `E:\yFeiEye\tmp\bp_v6\assets\clue-review.png`
- Create outside Git: `E:\yFeiEye\tmp\bp_v6\reports\asset-manifest.json`

- [ ] **Step 1: 写资产失败测试**

```python
from pathlib import Path

from tools.bp_v6.assets import verify_assets


def test_three_sanitized_product_screenshots_exist():
    root = Path(r"E:\yFeiEye\tmp\bp_v6\assets")
    report = verify_assets(root)
    assert [item["name"] for item in report] == [
        "algorithm-task.png",
        "clue-review.png",
        "device-archive.png",
    ]
    assert all(item["width"] >= 1400 for item in report)
    assert all(item["height"] >= 760 for item in report)
    assert all(len(item["sha256"]) == 64 for item in report)
```

- [ ] **Step 2: 运行测试并确认失败**

```powershell
python -m pytest tools/bp_v6/tests/test_assets.py -q
```

Expected: FAIL，资产目录中缺少三个 PNG。

- [ ] **Step 3: 加载应用内浏览器技能并进入演示环境**

使用 `browser:control-in-app-browser` 技能。若登录出现验证码，停在验证码页面，由用户手动完成；不得尝试绕过。

固定页面：

- 算法任务：`https://eye.yfeiai.com/yfeieye/camera/index`，切换到 Algorithm Task。
- 设备档案：`https://eye.yfeiai.com/yfeieye/device`。
- 线索复核：`https://eye.yfeiai.com/yfeieye/alert`，进入 `线索复核`。

- [ ] **Step 4: 在浏览器 DOM 中临时脱敏后截图**

只修改当前浏览器呈现，不提交或保存服务器数据：

- 将任务名/项目名改为 `司法场景任务-01`。
- 将设备名改为 `设备-01`，通道名改为 `演示通道-01`。
- 隐藏或替换 IP、端口、组织、账号、人员、设备编码和序列号。
- 保留真实模型类别、运行状态、控制按钮以及“确认前不会执行任何动作”的人机协同提示。
- 截图不包含浏览器地址栏、密码、验证码、开发者工具或通知弹窗。

截图按上述三个固定文件名写入资产目录。不得保存含原始标识的截图；若浏览器先生成了原始截图，确认安全版本后立即删除原始文件。

- [ ] **Step 5: 实现资产验证器**

```python
import hashlib
from pathlib import Path

from PIL import Image

EXPECTED = (
    "algorithm-task.png",
    "clue-review.png",
    "device-archive.png",
)


def verify_assets(root: Path) -> list[dict[str, object]]:
    report = []
    found = sorted(path.name for path in root.glob("*.png"))
    if tuple(found) != EXPECTED:
        raise AssertionError(f"asset set mismatch: {found}")
    for name in found:
        path = root / name
        with Image.open(path) as image:
            if image.format != "PNG":
                raise AssertionError(f"not PNG: {name}")
            width, height = image.size
        report.append({
            "name": name,
            "width": width,
            "height": height,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        })
    return report
```

- [ ] **Step 6: 运行自动和视觉检查**

```powershell
python -m pytest tools/bp_v6/tests/test_assets.py -q
```

Expected: `1 passed`。

随后使用本地图片查看工具以原始分辨率检查三张图，逐项确认：不存在可识别 IP、组织、人员、账号、设备编码；关键状态和提示可读；画面不是空白页或权限异常页。

- [ ] **Step 7: 提交验证代码，不提交截图**

```powershell
git add tools/bp_v6/assets.py tools/bp_v6/tests/test_assets.py
git diff --cached --check
git commit -m "test: define sanitized BP product asset contract"
```

## 任务 4：生成 16 页可编辑 PPTX

**Files:**
- Create: `tools/bp_v6/build_pptx.py`
- Create: `tools/bp_v6/tests/test_pptx.py`
- Create outside Git: `C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统_创业大赛BP_V6.pptx`

- [ ] **Step 1: 写 PPTX 失败测试**

```python
from pathlib import Path

from pptx import Presentation

from tools.bp_v6.content import SLIDES

PPTX = Path(r"C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统_创业大赛BP_V6.pptx")


def slide_text(slide):
    return "\n".join(
        shape.text for shape in slide.shapes
        if hasattr(shape, "text") and shape.text
    )


def test_pptx_structure_and_titles():
    prs = Presentation(PPTX)
    assert len(prs.slides) == 16
    assert prs.slide_width == 12192000
    assert prs.slide_height == 6858000
    for slide, spec in zip(prs.slides, SLIDES, strict=True):
        assert spec.title in slide_text(slide)


def test_locked_facts_and_planning_labels_are_visible():
    prs = Presentation(PPTX)
    pages = [slide_text(slide) for slide in prs.slides]
    assert "0万元" in pages[3]
    assert "暂无正式客户" in pages[3]
    assert "试点验收目标，不是历史业绩" in pages[8]
    assert all(value in pages[14] for value in ("200万元", "650万元", "1500万元", "500万元"))


def test_no_placeholders_or_forbidden_claims():
    prs = Presentation(PPTX)
    text = "\n".join(slide_text(slide) for slide in prs.slides)
    for forbidden in (
        "TBD",
        "TODO",
        "待补充",
        "100%自研",
        "完全自主知识产权",
        "国内领先",
        "已通过等保",
        "已服务多家单位",
    ):
        assert forbidden not in text
```

- [ ] **Step 2: 运行测试并确认失败**

```powershell
python -m pytest tools/bp_v6/tests/test_pptx.py -q
```

Expected: FAIL，目标 PPTX 不存在。

- [ ] **Step 3: 实现构建器 CLI 和联系人读取**

`build_pptx.py` 的公开入口必须固定：

```python
from argparse import ArgumentParser
from pathlib import Path

from openpyxl import load_workbook

from tools.bp_v6.content import SLIDES
from tools.bp_v6.theme import new_presentation


def read_contact(workbook_path: Path) -> dict[str, str]:
    workbook = load_workbook(workbook_path, read_only=False, data_only=False)
    sheet = workbook.worksheets[0]
    return {
        "name": str(sheet["P3"].value or "刘飞"),
        "phone": str(sheet["Q3"].value or ""),
        "email": str(sheet["R3"].value or ""),
    }


def build_presentation(assets_dir: Path, contact_workbook: Path, output: Path) -> None:
    presentation = new_presentation()
    contact = read_contact(contact_workbook)
    builders = {
        "cover": build_cover,
        "pain": build_pain,
        "flow": build_flow,
        "stage": build_stage,
        "screenshots": build_screenshots,
        "architecture": build_architecture,
        "comparison": build_comparison,
        "compliance": build_compliance,
        "pilot": build_pilot,
        "market": build_market,
        "pricing": build_pricing,
        "founder": build_founder,
        "timeline": build_timeline,
        "finance": build_finance,
        "cta": build_cta,
    }
    for spec in SLIDES:
        builders[spec.layout](presentation, spec, assets_dir, contact)
    output.parent.mkdir(parents=True, exist_ok=True)
    presentation.save(output)


def main() -> None:
    parser = ArgumentParser()
    parser.add_argument("--assets-dir", type=Path, required=True)
    parser.add_argument("--contact-workbook", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    build_presentation(args.assets_dir, args.contact_workbook, args.output)


if __name__ == "__main__":
    main()
```

同一布局可被多页复用，但每个构建函数必须完成以下明确职责：

| Builder | 页面 | 完整构成 |
| --- | --- | --- |
| `build_cover` | 1 | 深色全页背景、项目名、主张、四步价值链、创业大赛与日期 |
| `build_pain` | 2 | 四张痛点卡、三张公开采购金额卡、协会规模脚注；采购卡标明“公开需求证据，非本项目客户” |
| `build_flow` | 3、12 | 页 3 为七步闭环；页 12 为五步获客路径，并显示“暂无正式渠道合作” |
| `build_stage` | 4 | “已具备”与“待验证”双栏；成立日期、0万元、无客户/合同/试点 |
| `build_screenshots` | 5 | 1 主 2 辅 PNG、三条证据说明、统一脱敏图注 |
| `build_architecture` | 6 | 客户内网/专有云大边界、七级价值链、底部五项横向安全控制带 |
| `build_comparison` | 7 | 三类方案 × 五维对比表，页脚声明为产品定位比较而非第三方性能审计 |
| `build_compliance` | 8 | 数据生命周期、RBAC/审计、导出审批/水印、私有部署、人工确认；五项法规/标准短标签及链接 |
| `build_pilot` | 9 | 8 路、30 天、2-3 类规则；四个目标指标卡；显著历史业绩否认标签 |
| `build_market` | 10 | TAM/SAM/SOM 漏斗、1051 所口径、7.9-16.4 亿元、2350 万元、1.4%-3.0%、采购证据脚注 |
| `build_pricing` | 11 | 三级主报价阶梯、64 路集群、年度治理、算法包；商务条款脚注 |
| `build_founder` | 13 | 刘飞履历与分工；“当前团队 1 人”和“融资后拟招聘”严格分栏 |
| `build_timeline` | 14 | Q3/Q4 两段时间轴，每项前置“计划/目标”标签 |
| `build_finance` | 15 | 三年收入柱形图、项目组合假设、500 万元五项用途条形图；规划标签 |
| `build_cta` | 16 | 三类合作诉求、收束价值链、从申报表读取的联系人；电话/邮箱为空则不渲染该行 |

构建规则：

- 每页调用 `add_footer`；封面可省页脚文字但仍保留 `01/16`。
- 主标题不小于 24 pt，正文不小于 13 pt，来源不小于 7 pt。
- 表格不使用 PowerPoint 默认蓝色模板；用原生矩形和文本框绘制。
- 所有图表数字来自 `content.py`，不得在构建函数中写另一套数值。
- 外部来源短标签使用 `add_source`，既可读又保留超链接。
- 三张截图只从参数 `assets_dir` 读取；文件名严格由资产合同决定。
- 不加入客户 Logo、合同图、伪造现场图、专利/认证图标或通用 AI 机器人图片。

- [ ] **Step 4: 生成 PPTX**

```powershell
$source = 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\创企大赛导入项目模板_逸飞AI智眼系统_PDF链接已修复.xlsx'
$pptx = 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统_创业大赛BP_V6.pptx'
python -m tools.bp_v6.build_pptx `
  --assets-dir 'E:\yFeiEye\tmp\bp_v6\assets' `
  --contact-workbook $source `
  --output $pptx
```

Expected: 生成非空 PPTX，原始 PDF 和输入 Excel 哈希不变。

- [ ] **Step 5: 运行 PPTX 测试**

```powershell
python -m pytest tools/bp_v6/tests/test_pptx.py -q
```

Expected: `3 passed`。

- [ ] **Step 6: 提交 PPTX 构建器**

```powershell
git add tools/bp_v6/build_pptx.py tools/bp_v6/tests/test_pptx.py
git diff --cached --check
git commit -m "feat: generate editable 16-slide BP V6 deck"
```

最终二进制 PPTX 不纳入 Git，只保存在交付目录。

## 任务 5：从 PPTX 导出并渲染 PDF

**Files:**
- Create: `tools/bp_v6/export_pdf.ps1`
- Create: `tools/bp_v6/render_pdf.py`
- Create outside Git: `C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统_创业大赛BP_V6.pdf`
- Create outside Git: `E:\yFeiEye\tmp\bp_v6\render\slide-01.png` through `slide-16.png`
- Create outside Git: `E:\yFeiEye\tmp\bp_v6\render\contact-sheet.png`

- [ ] **Step 1: 安装并验证无界面转换依赖**

```powershell
if (-not (Get-Command soffice -ErrorAction SilentlyContinue) -and
    -not (Test-Path 'C:\Program Files\LibreOffice\program\soffice.exe')) {
    winget install --exact --id TheDocumentFoundation.LibreOffice `
      --accept-package-agreements --accept-source-agreements --silent
}
if (-not (Get-Command pdftoppm -ErrorAction SilentlyContinue)) {
    winget install --exact --id oschwartz10612.Poppler `
      --accept-package-agreements --accept-source-agreements --silent
}
```

Expected: LibreOffice `soffice.exe` 可定位；Poppler `pdftoppm.exe` 可定位。若 WinGet 安装返回失败，停止在依赖步骤，不改写现有交付文件。

- [ ] **Step 2: 实现 PPTX 到 PDF 导出脚本**

```powershell
param(
    [Parameter(Mandatory=$true)][string]$InputPptx,
    [Parameter(Mandatory=$true)][string]$OutputPdf,
    [Parameter(Mandatory=$true)][string]$ProfileDir
)

$ErrorActionPreference = 'Stop'
$soffice = (Get-Command soffice.exe -ErrorAction SilentlyContinue).Source
if (-not $soffice) {
    $candidate = 'C:\Program Files\LibreOffice\program\soffice.exe'
    if (Test-Path -LiteralPath $candidate) { $soffice = $candidate }
}
if (-not $soffice) { throw 'LibreOffice soffice.exe not found' }
if (-not (Test-Path -LiteralPath $InputPptx)) { throw "PPTX not found: $InputPptx" }

$outputDir = Split-Path -Parent $OutputPdf
New-Item -ItemType Directory -Force -Path $outputDir, $ProfileDir | Out-Null
$profileUri = 'file:///' + (($ProfileDir -replace '\\','/') -replace ' ','%20')
& $soffice --headless "-env:UserInstallation=$profileUri" `
  --convert-to 'pdf:impress_pdf_Export' --outdir $outputDir $InputPptx
if ($LASTEXITCODE -ne 0) { throw "LibreOffice export failed: $LASTEXITCODE" }

$generated = Join-Path $outputDir ((Split-Path -LeafBase $InputPptx) + '.pdf')
if (-not (Test-Path -LiteralPath $generated)) { throw 'Expected PDF was not created' }
if ($generated -ne $OutputPdf) { Move-Item -LiteralPath $generated -Destination $OutputPdf -Force }
```

- [ ] **Step 3: 实现 PDF 渲染器**

```python
from argparse import ArgumentParser
from pathlib import Path

import fitz
from PIL import Image, ImageOps, ImageDraw


def render(pdf_path: Path, output_dir: Path) -> list[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    document = fitz.open(pdf_path)
    if len(document) != 16:
        raise AssertionError(f"expected 16 pages, got {len(document)}")
    paths = []
    matrix = fitz.Matrix(2, 2)
    for index, page in enumerate(document, 1):
        pixmap = page.get_pixmap(matrix=matrix, alpha=False)
        path = output_dir / f"slide-{index:02d}.png"
        pixmap.save(path)
        paths.append(path)
    return paths


def contact_sheet(paths: list[Path], output: Path) -> None:
    thumbs = []
    for path in paths:
        image = Image.open(path).convert("RGB")
        image.thumbnail((480, 270))
        thumbs.append(ImageOps.expand(image, border=2, fill="#d6dee8"))
    canvas = Image.new("RGB", (1928, 1096), "#eef2f7")
    draw = ImageDraw.Draw(canvas)
    for index, image in enumerate(thumbs):
        x = 4 + (index % 4) * 480
        y = 4 + (index // 4) * 270
        canvas.paste(image, (x, y))
        draw.text((x + 8, y + 8), f"{index + 1:02d}", fill="#0b1827")
    canvas.save(output)


def main() -> None:
    parser = ArgumentParser()
    parser.add_argument("pdf", type=Path)
    parser.add_argument("output_dir", type=Path)
    args = parser.parse_args()
    paths = render(args.pdf, args.output_dir)
    contact_sheet(paths, args.output_dir / "contact-sheet.png")


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: 导出并渲染**

```powershell
$pptx = 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统_创业大赛BP_V6.pptx'
$pdf = 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统_创业大赛BP_V6.pdf'
& 'tools\bp_v6\export_pdf.ps1' `
  -InputPptx $pptx `
  -OutputPdf $pdf `
  -ProfileDir 'E:\yFeiEye\tmp\bp_v6\office-profile'
python -m tools.bp_v6.render_pdf $pdf 'E:\yFeiEye\tmp\bp_v6\render'
$popplerDir = 'E:\yFeiEye\tmp\bp_v6\render\poppler'
New-Item -ItemType Directory -Force -Path $popplerDir | Out-Null
$pdftoppm = (Get-Command pdftoppm.exe -ErrorAction SilentlyContinue).Source
if (-not $pdftoppm) {
    $pdftoppm = Get-ChildItem `
      "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" `
      -Filter pdftoppm.exe -Recurse -ErrorAction SilentlyContinue |
      Select-Object -First 1 -ExpandProperty FullName
}
if (-not $pdftoppm) { throw 'Poppler pdftoppm.exe not found' }
& $pdftoppm -png -r 160 $pdf (Join-Path $popplerDir 'slide')
if ((Get-ChildItem -LiteralPath $popplerDir -Filter 'slide-*.png').Count -ne 16) {
    throw 'Poppler did not render exactly 16 pages'
}
```

Expected: PDF 为 16 页；PyMuPDF 输出 `slide-01.png` 至 `slide-16.png` 和 `contact-sheet.png`；Poppler 子目录也恰好输出 16 张逐页 PNG。

- [ ] **Step 5: 提交导出与渲染代码**

```powershell
git add tools/bp_v6/export_pdf.ps1 tools/bp_v6/render_pdf.py
git diff --cached --check
git commit -m "feat: export and render BP V6 PDF"
```

## 任务 6：逐页视觉复核并迭代

**Files:**
- Modify: `tools/bp_v6/build_pptx.py`
- Create outside Git: `E:\yFeiEye\tmp\bp_v6\reports\visual-review.md`

- [ ] **Step 1: 检查 16 页总览**

使用本地图片查看工具打开 `contact-sheet.png`，检查：页序完整、标题层级一致、留白均衡、页面风格统一、没有一页明显过密或过空。总览通过后，以 Poppler 子目录中的 PNG 作为最终逐页视觉依据。

- [ ] **Step 2: 以原始分辨率检查每页**

逐张打开 `slide-01.png` 至 `slide-16.png`，在 `visual-review.md` 中按页记录以下结果：

- 文字无裁切、重叠、黑块、乱码或异常替代字体。
- 正文最小字号在 100% 缩放下可读。
- 三张产品界面清晰且无敏感标识。
- 页 4 的 `0万元` 与无客户/合同/试点事实清晰。
- 页 9 的四个数字明确标为试点验收目标。
- 页 10 的统计年份、下限属性和“非本项目客户”脚注可读。
- 页 15 的收入规划标签、项目组合和融资用途一致。
- 页 8 不出现“已认证/完全合规”暗示。

- [ ] **Step 3: 修正发现的视觉缺陷**

每次只修改造成缺陷的页面构建函数；不重构相邻页面或扩展内容。修改后重新运行：

```powershell
python -m tools.bp_v6.build_pptx `
  --assets-dir 'E:\yFeiEye\tmp\bp_v6\assets' `
  --contact-workbook 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\创企大赛导入项目模板_逸飞AI智眼系统_PDF链接已修复.xlsx' `
  --output 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统_创业大赛BP_V6.pptx'
& 'tools\bp_v6\export_pdf.ps1' `
  -InputPptx 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统_创业大赛BP_V6.pptx' `
  -OutputPdf 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统_创业大赛BP_V6.pdf' `
  -ProfileDir 'E:\yFeiEye\tmp\bp_v6\office-profile'
python -m tools.bp_v6.render_pdf `
  'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统_创业大赛BP_V6.pdf' `
  'E:\yFeiEye\tmp\bp_v6\render'
```

Expected: 最新 PNG 反映本次修改；旧 PNG 被同名覆盖，不混入旧版本。

- [ ] **Step 4: 运行结构回归测试**

```powershell
python -m pytest tools/bp_v6/tests/test_content.py tools/bp_v6/tests/test_theme.py tools/bp_v6/tests/test_assets.py tools/bp_v6/tests/test_pptx.py -q
```

Expected: 全部通过。

- [ ] **Step 5: 提交视觉修正**

```powershell
git add tools/bp_v6/build_pptx.py
git diff --cached --check
git commit -m "fix: polish BP V6 rendered layout"
```

如果没有代码变化，不创建空提交。

## 任务 7：生成最终 Excel 相对链接

**Files:**
- Create: `tools/bp_v6/excel_link.py`
- Create: `tools/bp_v6/tests/test_excel_link.py`
- Create outside Git: `C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\创企大赛导入项目模板_逸飞AI智眼系统_最终版.xlsx`

- [ ] **Step 1: 写 Excel 最小改动失败测试**

```python
from pathlib import Path

from openpyxl import load_workbook

from tools.bp_v6.excel_link import update_pdf_link


SOURCE = Path(r"C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\创企大赛导入项目模板_逸飞AI智眼系统_PDF链接已修复.xlsx")
PDF_NAME = "逸飞AI智眼系统_创业大赛BP_V6.pdf"


def cell_signature(cell):
    hyperlink = cell.hyperlink.target if cell.hyperlink else None
    comment = cell.comment.text if cell.comment else None
    return (cell.value, cell.data_type, cell.number_format, cell.style_id, hyperlink, comment)


def workbook_signature(path):
    workbook = load_workbook(path, data_only=False, read_only=False)
    result = {
        "sheetnames": tuple(workbook.sheetnames),
        "sheets": {},
    }
    for sheet in workbook.worksheets:
        cells = {}
        for row in sheet.iter_rows():
            for cell in row:
                if cell.value is not None or cell.hyperlink or cell.comment:
                    cells[cell.coordinate] = cell_signature(cell)
        result["sheets"][sheet.title] = {
            "max_row": sheet.max_row,
            "max_column": sheet.max_column,
            "freeze_panes": str(sheet.freeze_panes or ""),
            "merged_ranges": tuple(sorted(str(item) for item in sheet.merged_cells.ranges)),
            "auto_filter": sheet.auto_filter.ref,
            "print_area": str(sheet.print_area or ""),
            "print_title_rows": str(sheet.print_title_rows or ""),
            "print_title_cols": str(sheet.print_title_cols or ""),
            "row_dimensions": tuple(
                (key, value.height, value.hidden, value.outlineLevel)
                for key, value in sorted(sheet.row_dimensions.items())
            ),
            "column_dimensions": tuple(
                (key, value.width, value.hidden, value.outlineLevel)
                for key, value in sorted(sheet.column_dimensions.items())
            ),
            "data_validation_count": len(sheet.data_validations.dataValidation),
            "cells": cells,
        }
    return result


def test_only_j3_value_and_hyperlink_change(tmp_path):
    output = tmp_path / "final.xlsx"
    update_pdf_link(SOURCE, output, PDF_NAME)
    before = workbook_signature(SOURCE)
    after = workbook_signature(output)
    assert before["sheetnames"] == after["sheetnames"]
    assert len(after["sheetnames"]) == 11
    for sheet_name in before["sheetnames"]:
        if sheet_name != "项目模板":
            assert before["sheets"][sheet_name] == after["sheets"][sheet_name]
    before_cells = before["sheets"]["项目模板"]["cells"]
    after_cells = after["sheets"]["项目模板"]["cells"]
    for key in before["sheets"]["项目模板"]:
        if key != "cells":
            assert after["sheets"]["项目模板"][key] == before["sheets"]["项目模板"][key]
    for coordinate, signature in before_cells.items():
        if coordinate != "J3":
            assert after_cells[coordinate] == signature
    assert after_cells["J3"][0] == PDF_NAME
    assert after_cells["J3"][4] == PDF_NAME
    assert ":\\" not in after_cells["J3"][4]
```

- [ ] **Step 2: 运行测试并确认失败**

```powershell
python -m pytest tools/bp_v6/tests/test_excel_link.py -q
```

Expected: FAIL，`tools.bp_v6.excel_link` 不存在。

- [ ] **Step 3: 实现只修改 J3 的更新器**

```python
from argparse import ArgumentParser
from pathlib import Path
from shutil import copy2

from openpyxl import load_workbook


def update_pdf_link(source: Path, output: Path, pdf_name: str) -> None:
    if source.resolve() == output.resolve():
        raise ValueError("source workbook must not be overwritten")
    if Path(pdf_name).name != pdf_name or Path(pdf_name).is_absolute():
        raise ValueError("pdf_name must be a same-folder relative filename")
    output.parent.mkdir(parents=True, exist_ok=True)
    copy2(source, output)
    workbook = load_workbook(output, data_only=False, read_only=False)
    sheet = workbook.worksheets[0]
    if sheet.title != "项目模板":
        raise AssertionError(f"unexpected first sheet: {sheet.title}")
    cell = sheet["J3"]
    cell.value = pdf_name
    cell.hyperlink = pdf_name
    workbook.save(output)


def main() -> None:
    parser = ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("pdf_name")
    args = parser.parse_args()
    update_pdf_link(args.source, args.output, args.pdf_name)


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: 运行测试并生成最终 Excel**

```powershell
python -m pytest tools/bp_v6/tests/test_excel_link.py -q
python -m tools.bp_v6.excel_link `
  'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\创企大赛导入项目模板_逸飞AI智眼系统_PDF链接已修复.xlsx' `
  'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\创企大赛导入项目模板_逸飞AI智眼系统_最终版.xlsx' `
  '逸飞AI智眼系统_创业大赛BP_V6.pdf'
```

Expected: 测试通过；最终 Excel 与 V6 PDF 位于同一目录。

- [ ] **Step 5: 验证移动后的相对关系**

```powershell
$probe = 'E:\yFeiEye\tmp\bp_v6\move-probe'
New-Item -ItemType Directory -Force -Path $probe | Out-Null
Copy-Item -LiteralPath 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\创企大赛导入项目模板_逸飞AI智眼系统_最终版.xlsx' -Destination $probe
Copy-Item -LiteralPath 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料\逸飞AI智眼系统_创业大赛BP_V6.pdf' -Destination $probe
python -c "from openpyxl import load_workbook; from pathlib import Path; p=Path(r'E:\yFeiEye\tmp\bp_v6\move-probe\创企大赛导入项目模板_逸飞AI智眼系统_最终版.xlsx'); c=load_workbook(p).worksheets[0]['J3']; assert c.hyperlink.target == '逸飞AI智眼系统_创业大赛BP_V6.pdf'; assert (p.parent / c.hyperlink.target).exists(); print('relative-link-ok')"
```

Expected: `relative-link-ok`。

- [ ] **Step 6: 在 Excel 中人工点击一次**

打开最终 Excel，定位首个工作表 `J3`，点击“逸飞AI智眼系统_创业大赛BP_V6.pdf”。Expected: 同目录 V6 PDF 打开，无“找不到文件”或安全损坏提示。此步骤只验证，不保存 Excel 的其他改动。

- [ ] **Step 7: 提交 Excel 更新器与测试**

```powershell
git add tools/bp_v6/excel_link.py tools/bp_v6/tests/test_excel_link.py
git diff --cached --check
git commit -m "fix: create portable BP PDF link in final workbook"
```

## 任务 8：端到端验证和最终交付

**Files:**
- Create: `tools/bp_v6/verify_artifacts.py`
- Create: `tools/bp_v6/tests/test_final_artifacts.py`
- Create outside Git: `E:\yFeiEye\tmp\bp_v6\reports\final-verification.json`

- [ ] **Step 1: 写端到端失败测试**

```python
from pathlib import Path

from tools.bp_v6.verify_artifacts import verify_all


def test_final_delivery_contract():
    report = verify_all(
        delivery_dir=Path(r"C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料"),
        baseline_path=Path(r"E:\yFeiEye\tmp\bp_v6\reports\baseline.json"),
    )
    assert report["pptx"]["slides"] == 16
    assert report["pdf"]["pages"] == 16
    assert report["xlsx"]["sheets"] == 11
    assert report["xlsx"]["pdf_target"] == "逸飞AI智眼系统_创业大赛BP_V6.pdf"
    assert report["source_pdf_unchanged"] is True
    assert report["source_workbook_unchanged"] is True
```

- [ ] **Step 2: 运行测试并确认失败**

```powershell
python -m pytest tools/bp_v6/tests/test_final_artifacts.py -q
```

Expected: FAIL，最终验证器不存在。

- [ ] **Step 3: 实现最终验证器**

`verify_artifacts.py` 必须执行并返回以下检查结果：

```python
import hashlib
import json
import re
from pathlib import Path

import fitz
from openpyxl import load_workbook
from pptx import Presentation


PPTX_NAME = "逸飞AI智眼系统_创业大赛BP_V6.pptx"
PDF_NAME = "逸飞AI智眼系统_创业大赛BP_V6.pdf"
XLSX_NAME = "创企大赛导入项目模板_逸飞AI智眼系统_最终版.xlsx"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def pptx_text(path: Path) -> tuple[int, list[str]]:
    presentation = Presentation(path)
    pages = []
    for slide in presentation.slides:
        pages.append("\n".join(
            shape.text for shape in slide.shapes
            if hasattr(shape, "text") and shape.text
        ))
    return len(presentation.slides), pages


def pdf_text(path: Path) -> tuple[int, list[str]]:
    document = fitz.open(path)
    return len(document), [page.get_text() for page in document]


def normalized(text: str) -> str:
    return re.sub(r"\s+", "", text)


def verify_all(delivery_dir: Path, baseline_path: Path) -> dict[str, object]:
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    pptx = delivery_dir / PPTX_NAME
    pdf = delivery_dir / PDF_NAME
    xlsx = delivery_dir / XLSX_NAME
    for path in (pptx, pdf, xlsx):
        if not path.is_file() or path.stat().st_size == 0:
            raise AssertionError(f"missing artifact: {path}")

    slide_count, slide_pages = pptx_text(pptx)
    pdf_count, pdf_pages = pdf_text(pdf)
    required = {
        4: ("0万元", "暂无正式客户"),
        9: ("试点验收目标，不是历史业绩", "P95"),
        10: ("7.9", "16.4", "1.4%", "3.0%"),
        15: ("200万元", "650万元", "1500万元", "500万元"),
    }
    for page, tokens in required.items():
        for token in tokens:
            if (normalized(token) not in normalized(slide_pages[page - 1]) or
                    normalized(token) not in normalized(pdf_pages[page - 1])):
                raise AssertionError(f"page {page} missing token: {token}")

    workbook = load_workbook(xlsx, data_only=False, read_only=False)
    cell = workbook.worksheets[0]["J3"]
    target = cell.hyperlink.target if cell.hyperlink else None
    if target != PDF_NAME or not (xlsx.parent / target).is_file():
        raise AssertionError("portable PDF hyperlink invalid")

    report = {
        "pptx": {"path": str(pptx), "slides": slide_count, "sha256": sha256(pptx)},
        "pdf": {"path": str(pdf), "pages": pdf_count, "sha256": sha256(pdf)},
        "xlsx": {"path": str(xlsx), "sheets": len(workbook.sheetnames), "pdf_target": target, "sha256": sha256(xlsx)},
        "source_pdf_unchanged": sha256(Path(baseline["source_pdf"]["path"])) == baseline["source_pdf"]["sha256"],
        "source_workbook_unchanged": sha256(Path(baseline["source_workbook"]["path"])) == baseline["source_workbook"]["sha256"],
    }
    if slide_count != 16 or pdf_count != 16:
        raise AssertionError("deck/page count mismatch")
    return report


def main() -> None:
    from argparse import ArgumentParser

    parser = ArgumentParser()
    parser.add_argument("--delivery-dir", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    report = verify_all(args.delivery_dir, args.baseline)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: 运行全部自动验证**

```powershell
python -m pytest tools/bp_v6/tests -q
python -m tools.bp_v6.verify_artifacts `
  --delivery-dir 'C:\Users\86135\Desktop\逸飞AI智眼系统_创业大赛申报材料' `
  --baseline 'E:\yFeiEye\tmp\bp_v6\reports\baseline.json' `
  --report 'E:\yFeiEye\tmp\bp_v6\reports\final-verification.json'
git diff --check
```

Expected: 全部测试通过；验证报告中的三个文件存在、PPTX/PDF 均为 16 页、Excel 为 11 个工作表、两份输入哈希不变。

- [ ] **Step 5: 最终人工验收**

- 打开 PPTX：可进入编辑模式，标题、正文、图表和架构节点是可编辑对象；三张截图作为图像对象存在。
- 打开 PDF：16 页可连续浏览，中文无乱码，产品截图清晰，来源脚注可读。
- 打开 Excel 并点击 `J3`：V6 PDF 正常打开。
- 确认交付目录中原始 `逸飞AI智眼系统.pdf` 仍存在且哈希不变。
- 确认没有生成客户合同、客户 Logo、认证证书或其他虚构证据。

- [ ] **Step 6: 清理敏感和无用中间产物**

删除任何含未脱敏标识的原始截图；保留已脱敏截图、逐页渲染图和验证报告到用户确认交付完成。不得删除三份最终交付物或两个输入基线。

- [ ] **Step 7: 提交最终验证器**

```powershell
git add tools/bp_v6/verify_artifacts.py tools/bp_v6/tests/test_final_artifacts.py
git diff --cached --check
git commit -m "test: verify BP V6 delivery artifacts"
```

## 需求覆盖映射

| 设计规格要求 | 实施任务 |
| --- | --- |
| 16 页叙事、事实分层、禁止主张 | 任务 1、任务 4、任务 8 |
| 真实界面和完整脱敏 | 任务 3、任务 6 |
| 私有化架构、闭环流程、竞争定位 | 任务 4 |
| 创始人履历、当前单人团队、拟招聘分栏 | 任务 1、任务 4 |
| 8 路/30 天试点目标和指标口径 | 任务 1、任务 4、任务 8 |
| 市场规模、采购证据、来源年份 | 任务 1、任务 4、任务 6 |
| 定价、三年预测、500 万融资用途 | 任务 1、任务 4、任务 8 |
| 视频隐私、权限审计、私有部署、AI 辅助判断 | 任务 1、任务 4、任务 6 |
| 可编辑 PPTX、从 PPTX 导出的 PDF | 任务 4、任务 5、任务 6 |
| Excel 首个工作表 PDF 相对链接 | 任务 7、任务 8 |
| 原始 PDF/Excel 不覆盖 | 任务 0、任务 7、任务 8 |
| 逐页视觉检查与最终验收 | 任务 5、任务 6、任务 8 |

## 完成定义

只有同时满足以下条件才可向用户声明完成：

1. 三份最终文件位于指定桌面目录并可打开。
2. PPTX 和 PDF 均恰好 16 页，关键事实和规划标签一致。
3. 16 张最新 PDF 渲染图已逐页检查，无裁切、重叠、乱码、黑块或不可读脚注。
4. 三张产品截图为真实演示环境界面，且不存在可恢复的敏感标识。
5. Excel `J3` 是同目录相对链接，移动探针和 Excel 人工点击均通过。
6. 原始 PDF 与输入 Excel 的 SHA-256 与基线完全一致。
7. 全部 pytest、结构检查和 `git diff --check` 通过。
8. Git 提交只包含本计划列出的工具和测试，不包含用户在原工作区的业务代码修改或临时二进制文件。
