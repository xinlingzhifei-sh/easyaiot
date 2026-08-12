# yFeiEye 專案深度技術架構分析報告

> 分析日期：2026-05-31 | 倉庫：https://gitee.com/volara/yfeieye | 目前分支：main (V9.17.0)

---

## 一、專案概覽

**yFeiEye**（yFei AI Vision Control Platform）是一個**雲邊端一體化智慧 IoT 平台**，專注於 AI 與 IoT 的深度融合。專案願景是「讓 AI 零門檻觸達全球」。

| 指標 | 資料 |
|------|------|
| 總提交數 | 1,760 |
| 主要貢獻者 | 翱翔的雄庫鲁（3,988 commits，占 95%+） |
| 版本迭代 | V1.0.0 → V9.17.0（共 35+ 個版本分支） |
| 程式碼規模 | Java 2,374 檔案 / Python 173 檔案 / Vue 558 檔案 / TypeScript 610 檔案 / C++ 30 檔案 |
| Shell 腳本 | 79 個（部署/運維自動化） |
| SQL 腳本 | 7 個（多資料庫初始化） |

---

## 二、整體架構設計

### 2.1 分層架構

```
┌─────────────────────────────────────────────────────────────┐
│                    WEB 前端（Vue 3 + Ant Design Vue）        │
├─────────────────────────────────────────────────────────────┤
│                 API Gateway（Spring Cloud Gateway）           │
├──────────┬──────────┬──────────┬──────────┬─────────────────┤
│ iot-system │ iot-infra │ iot-device │ iot-dataset │ iot-message │
│  系統管理  │  基礎設施  │  裝置管理   │  資料集管理  │  訊息推送    │
├──────────┴──────────┴──────────┴──────────┴─────────────────┤
│          iot-sink（協定適配層：MQTT/TCP/HTTP/EMQX）           │
├──────────┬──────────┬──────────┬─────────────────────────────┤
│  AI 服務  │ VIDEO 服務│ TASK 模組 │ iot-gb28181（視訊監控協定）  │
│ Flask+YOLO│ Flask+串流處理│ C++ 推理  │   Java SIP 信令            │
├──────────┴──────────┴──────────┴─────────────────────────────┤
│              中介軟體層（Nacos / PostgreSQL / Redis / Kafka / MinIO / TDengine）│
└─────────────────────────────────────────────────────────────┘
```

### 2.2 微服務拆分

專案採用**多語言微服務架構**，按職責域拆分為 5 大模組：

| 模組 | 語言/框架 | 職責 | 服務數 |
|------|-----------|------|--------|
| **DEVICE** | Java 21 + Spring Boot 2.7 + Spring Cloud | 裝置管理、系統管理、訊息推送、資料集、檔案儲存 | 8+ 微服務 |
| **AI** | Python + Flask + PyTorch + YOLO | 模型訓練、推理、部署、OCR、語音、LLM | 1 主服務 + 子服務 |
| **VIDEO** | Python + Flask + OpenCV + FFmpeg | 視訊串流處理、即時/快照演算法、錄影、告警 | 1 主服務 + 6 子服務 |
| **TASK** | C++17 + OpenCV + ONNX Runtime + FFmpeg | 邊緣端即時推理引擎 | 獨立程序 |
| **WEB** | Vue 3 + TypeScript + Vite + Ant Design Vue | 全功能前端管理平台 | SPA |

---

## 三、各模組技術架構詳解

### 3.1 DEVICE 模組（Java 微服務叢集）

**技術棧：**
- **框架**：Spring Boot 2.7.18 + Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.4.0
- **JDK**：Java 21
- **閘道**：Spring Cloud Gateway
- **註冊/設定中心**：Nacos
- **資料庫**：PostgreSQL（主庫）+ TDengine（時序資料）
- **ORM**：MyBatis-Plus 3.5.5 + Dynamic Datasource
- **快取**：Redis + Redisson 3.18.0
- **訊息佇列**：RocketMQ / Kafka
- **物件儲存**：MinIO
- **工作流程**：Flowable 6.8.0
- **定時任務**：XXL-Job 2.3.1
- **API 文件**：Knife4j 4.3.0 + SpringDoc
- **監控**：SkyWalking 8.12.0 + Spring Boot Admin
- **工具庫**：Hutool 5.8.25、MapStruct 1.5.5、EasyExcel 3.3.3

**子模組拆分（12 個）：**

| 子模組 | Java 檔案數 | 職責 |
|--------|------------|------|
| iot-common | 447 | 公共基礎庫（安全、快取、RPC、MQ、MyBatis、租戶等 17 個子模組） |
| iot-gb28181 | 569 | GB28181 國標視訊監控協定接入（SIP 信令、裝置註冊、串流管理） |
| iot-system | 398 | 系統管理（使用者、角色、權限、部門、字典、OAuth2、SMS） |
| iot-device | 272 | 裝置管理（產品、裝置、OTA、物模型、協定管理） |
| iot-sink | 191 | 協定適配層（MQTT/TCP/HTTP/EMQX 上下行訊息處理） |
| iot-infra | 188 | 基礎設施（檔案、日誌、WebSocket、設定、程式碼產生） |
| iot-message | 120 | 訊息推送（郵件、簡訊、釘釘、飛書、微信公眾號/企業微信） |
| iot-dataset | 117 | 資料集管理（標註、匯入匯出、YOLO/COCO/ImageFolder 格式） |
| iot-tdengine | 38 | TDengine 時序資料庫整合 |
| iot-file | 19 | 檔案服務（MinIO/本地儲存） |
| iot-gateway | 15 | API 閘道 |

**架構特點：**
- 標準的 api/biz 分層：每個業務模組拆分為 `xxx-api`（介面定義）和 `xxx-biz`（實作）
- 透過 OpenFeign 進行服務間 RPC 呼叫
- 支援多租戶（iot-common-tenant）
- 支援資料權限（iot-common-data-permission）

### 3.2 AI 模組（Python AI 服務）

**技術棧：**
- **框架**：Flask + Flask-SQLAlchemy
- **深度學習**：PyTorch 2.9+ (CUDA 12.8) + Ultralytics YOLO (v8/v11/v26)
- **推理格式**：PyTorch / ONNX / TorchScript / TensorRT / OpenVINO
- **大模型**：QwenVL3 視覺大模型、Qwen/DeepSeek LLM
- **OCR**：PaddleOCR
- **語音**：訊飛語音 API
- **物件儲存**：MinIO
- **服務註冊**：Nacos
- **資料庫**：PostgreSQL

**功能模組（Blueprint 架構）：**

| Blueprint | 程式碼行數 | 功能 |
|-----------|---------|------|
| llm.py | 1,718 | 大語言模型推理（多模態輸入：RTSP/視訊/圖片/音訊/文字） |
| model.py | 810 | 模型管理（CRUD、版本管理） |
| deploy.py | 805 | 模型部署服務（叢集推理、負載平衡、自動故障轉移） |
| export.py | 677 | 模型匯出（ONNX/TorchScript/TensorRT/OpenVINO） |
| auto_label.py | 664 | 自動標註（AI 輔助標註） |
| train.py | 1,036 | 模型訓練（YOLO 微調、超參數設定、訓練監控） |
| inference.py | 613 | 推理服務（單圖/批次/視訊推理） |
| plate.py | 1,114 | 車牌識別 |
| ocr.py | 385 | OCR 文字識別 |
| speech.py | 247 | 語音識別 |
| cluster.py | 440 | GPU 叢集管理 |
| train_task.py | 372 | 訓練任務排程 |

**核心服務：**
- `inference_service.py`（1,241 行）：核心推理引擎
- `deploy_service.py`（786 行）：模型部署管理
- `deploy_daemon.py`（417 行）：部署守護程序
- `ocr_service.py`（610 行）：OCR 服務
- `speech_service.py`（609 行）：語音服務
- `minio_service.py`（481 行）：物件儲存服務

### 3.3 VIDEO 模組（Python 視訊處理服務）

**技術棧：**
- **框架**：Flask + Flask-CORS
- **視訊處理**：OpenCV + FFmpeg
- **串流媒體**：SRS（Simple Realtime Server）
- **目標偵測**：YOLO (v8/v11/v26) + ByteTrack（目標追蹤）
- **人臉識別**：Milvus 向量資料庫
- **訊息佇列**：Kafka
- **物件儲存**：MinIO
- **裝置發現**：ONVIF + 海康/大華私有協定

**功能模組（Blueprint 架構）：**

| Blueprint | 程式碼行數 | 功能 |
|-----------|---------|------|
| snap.py | 943 | 快照管理（定時抓拍、儲存、檢索） |
| stream_forward.py | 529 | 串流轉發（RTSP/RTMP 推拉串流） |
| algorithm_task.py | ~500 | 演算法任務管理（即時/快照兩種模式） |
| camera.py | ~400 | 攝影機管理（多協定接入） |
| alert.py | ~400 | 告警事件管理 |
| record.py | 251 | 錄影管理 |
| playback.py | 304 | 回放管理 |
| face.py | ~300 | 人臉識別 |
| device_detection_region.py | ~300 | 偵測區域繪製 |

**子服務架構（6 個獨立微服務）：**

| 服務 | 職責 |
|------|------|
| realtime_algorithm_service | 即時視訊串流 AI 分析 |
| snapshot_algorithm_service | 快照圖片 AI 分析 |
| frame_extractor_service | 視訊幀提取 |
| sorter_service | 分析結果排序 |
| pusher_service | 視訊推串流 |
| stream_forward_service | 串流轉發 |

**核心服務能力：**
- 多協定攝影機接入（GB28181、ONVIF、RTSP、海康/大華私有協定）
- 即時視訊串流 AI 分析（毫秒級回應）
- 偵測區域視覺化繪製（矩形/多邊形）
- 三重聯動告警機制（偵測區域 × 防禦時段 × 事件類型）
- 人臉識別 + Milvus 向量檢索
- 錄影儲存與回放
- NVR 批次掃描註冊

### 3.4 TASK 模組（C++ 邊緣推理引擎）

**技術棧：**
- **語言**：C++17
- **建置**：CMake + vcpkg
- **推理引擎**：ONNX Runtime（GPU 加速）
- **目標偵測**：YOLOv11
- **視訊處理**：OpenCV + FFmpeg（libavcodec/libavformat/libavutil/libswscale）
- **日誌**：glog
- **JSON**：jsoncpp
- **網路**：libcurl（HTTP 回呼）
- **平台**：Windows + Linux

**架構設計：**
```
main.cpp → Manage (Server) → Config → ConfigParser
         → Yolov11Engine (推理引擎)
         → Yolov11ThreadPool (线程池)
         → Detech (检测逻辑)
         → Draw (绘制标注)
         → RTMPEncoder (RTMP 编码推流)
         → AlarmCallback (告警回调)
```

**核心特性：**
- 獨立程序執行，透過 INI 設定檔驅動
- 支援 RTSP 串流即時拉取 + YOLO 推理
- 多執行緒推理執行緒池
- RTMP 編碼推串流
- HTTP 告警回呼機制
- 跨平台支援（Windows/Linux）

### 3.5 WEB 模組（Vue 3 前端）

**技術棧：**
- **框架**：Vue 3.4 + TypeScript
- **建置**：Vite
- **UI 庫**：Ant Design Vue 4.0 + Element UI 2.15
- **狀態管理**：Pinia 2.1
- **路由**：Vue Router 4.3
- **國際化**：Vue I18n 9.6（中/英）
- **圖表**：ECharts 5.5 + echarts-liquidfill + echarts-wordcloud
- **視訊播放**：EasyPlayer + Jessibuca（WebRTC/WebSocket）
- **地圖**：高德地圖 API
- **富文字**：TinyMCE 5.10 + Vditor
- **3D**：Three.js 0.145
- **拖曳**：vuedraggable + sortablejs
- **CSS**：UnoCSS + Less + Sass

**頁面模組（14 個業務域）：**

| 模組 | Vue 檔案數 | 功能 |
|------|-----------|------|
| camera | 60 | 攝影機管理（多協定接入、串流轉發、偵測區域、錄影空間） |
| system | 55 | 系統管理（使用者、角色、部門、選單、字典、日誌） |
| train | 34 | 訓練管理（訓練任務、模型管理、部署服務、推理結果、模型匯出） |
| infra | 31 | 基礎設施（API 日誌、程式碼產生、設定、檔案、定時任務） |
| dataset | 31 | 資料集管理（標註、匯入匯出、格式轉換） |
| notice | 30 | 訊息通知（郵件、簡訊、釘釘、飛書、微信） |
| devices | 30 | 裝置管理（產品、裝置、物模型、OTA） |
| gb28181 | 21 | GB28181 視訊監控（分屏監控、裝置目錄） |
| dashboard | 6 | 監控大屏（演算法告警、裝置狀態、GPU 監控） |
| alert | 4 | 告警事件 |
| product | 14 | 產品管理 |
| rulechains | 5 | 規則鏈 |
| ota | 3 | OTA 升級 |

**前端工程化：**
- 558 個 Vue 元件 + 610 個 TypeScript 檔案
- 完整的 Hooks 體系（50+ 自訂 Hook）
- 權限管理（路由守衛 + 按鈕級權限）
- 多分頁管理
- 主題客製（暗色/亮色/自訂）
- 國際化（中/英雙語）
- 程式碼規範（ESLint + Stylelint + Husky + lint-staged + commitlint）

---

## 四、中介軟體與基礎設施

### 4.1 中介軟體棧

| 元件 | 版本 | 用途 |
|------|------|------|
| PostgreSQL | 18 | 主資料庫（6 個業務庫：ruoyi-vue-pro、iot-ai、iot-device、iot-gb28181、iot-message、iot-video） |
| Nacos | v2.5.1 | 服務註冊與設定中心 |
| Redis | latest | 快取、分散式鎖、工作階段管理 |
| Kafka | latest | 訊息佇列（裝置資料、告警事件） |
| MinIO | latest | 物件儲存（模型檔案、快照、錄影、資料集） |
| TDengine | 3.x | 時序資料庫（裝置遙測資料） |
| SRS | latest | 串流媒體伺服器（RTSP/RTMP 轉發） |

### 4.2 部署架構

- **Docker Compose 統一編排**：每個模組獨立 `docker-compose.yml`
- **統一安裝腳本**：`.scripts/docker/install_linux.sh` 一鍵部署全部服務
- **兩階段建置**：Dockerfile.base（Maven 依賴快取）→ 各模組 Dockerfile
- **GPU 支援**：自動偵測 GPU 並啟用 NVIDIA Container Runtime
- **ARM 支援**：提供 ARM64 專用安裝腳本和 Dockerfile
- **銀河麒麟**：提供國產化適配腳本

### 4.3 資料庫設計

- **6 個 PostgreSQL 庫**：按業務域隔離
- **SQL 初始化腳本**：`.scripts/postgresql/` 下 7 個 SQL 檔案
- **自動初始化**：Docker 啟動時透過 `initdb.d` 自動執行
- **TDengine 超級表**：`.scripts/tdengine/tdengine_super_tables.sql`

---

## 五、專案完成度評估

### 5.1 功能完成度

| 功能域 | 完成度 | 說明 |
|--------|--------|------|
| **裝置接入管理** | ★★★★★ | GB28181/ONVIF/RTSP 多協定、NVR 批次掃描、海康/大華私有協定 |
| **視訊串流處理** | ★★★★★ | 即時串流分析、串流轉發、錄影回放、分屏監控 |
| **AI 演算法能力** | ★★★★★ | YOLO 目標偵測、人臉識別、OCR、語音、車牌、LLM |
| **模型管理** | ★★★★★ | 訓練、匯出（ONNX/TensorRT/OpenVINO）、部署、版本管理、叢集推理 |
| **資料集管理** | ★★★★☆ | 標註、匯入匯出（YOLO/COCO/ImageFolder）、自動標註 |
| **告警系統** | ★★★★★ | 三重聯動告警、多通道推送（郵件/簡訊/釘釘/飛書/微信） |
| **系統管理** | ★★★★★ | 使用者、角色、權限、部門、字典、日誌、OAuth2、多租戶 |
| **IoT 協定** | ★★★★☆ | MQTT/TCP/HTTP/EMQX 適配、物模型、OTA |
| **監控大屏** | ★★★★☆ | GPU 監控、裝置狀態、演算法告警統計 |
| **邊緣推理** | ★★★☆☆ | C++ 邊緣引擎（Windows 為主，Linux 適配中） |
| **前端 UI** | ★★★★★ | 558 個 Vue 元件，功能完整 |

### 5.2 技術成熟度

| 維度 | 評分 | 說明 |
|------|------|------|
| **架構設計** | ★★★★★ | 清晰的微服務拆分、多語言協作、api/biz 分層 |
| **程式碼品質** | ★★★★☆ | 規範的套件結構、註解完善，但部分模組存在重複程式碼 |
| **工程化** | ★★★★★ | Docker Compose 編排、一鍵部署、CI/CD 腳本、程式碼規範工具鏈 |
| **文件** | ★★★★☆ | README 多語言版本（6 種語言）、模組 README、故障排查文件 |
| **測試覆蓋** | ★★☆☆☆ | 有少量測試檔案，但整體測試覆蓋率較低 |
| **版本管理** | ★★★★★ | 35+ 版本分支，語意化版本，規範的 Git 工作流程 |

### 5.3 迭代活躍度

- **版本跨度**：V1.0.0 → V9.17.0（9 個大版本，17 個小版本）
- **最近提交**：2026 年 5 月 31 日（持續活躍開發中）
- **近期重點**：
  - 資料集標註功能優化
  - GB28181 循環依賴修復
  - 分屏監控黑屏問題修復
  - 視訊處理演算法優化
  - 多種裝置新增方式合一

---

## 六、架構亮點與創新點

### 6.1 多語言微服務協作
- **Java**：負責業務邏輯、系統管理、裝置管理（穩定性、生態成熟）
- **Python**：負責 AI 推理、視訊處理（AI 生態優勢）
- **C++**：負責邊緣端即時推理（效能極致）
- **TypeScript/Vue**：負責前端展示（使用者體驗）

### 6.2 雲邊端一體化
- **雲**：Java 微服務叢集 + Python AI 服務
- **邊**：C++ TASK 推理引擎（可部署在邊緣裝置）
- **端**：攝影機、感測器等 IoT 裝置

### 6.3 AI 全鏈路閉環
```
数据采集 → 数据标注 → 模型训练 → 模型导出 → 模型部署 → 实时推理 → 告警通知
```

### 6.4 視訊處理流水線
```
摄像头 → 流拉取 → 帧提取 → AI 推理 → 结果排序 → 告警/存储
```

### 6.5 裝置協定適配層
- iot-sink 模組實現了 MQTT/TCP/HTTP/EMQX 四種協定的統一適配
- 支援裝置影子、物模型、OTA 等 IoT 核心概念

---

## 七、潛在風險與改進建議

### 7.1 風險點

| 風險 | 嚴重度 | 說明 |
|------|--------|------|
| **單點貢獻者** | 🔴 高 | 95%+ 程式碼由一人完成，存在核心人員風險 |
| **測試覆蓋不足** | 🟡 中 | 缺乏系統化的單元測試和整合測試 |
| **Spring Boot 2.7** | 🟡 中 | 已 EOL，建議升級到 Spring Boot 3.x |
| **Java 21 + Spring Boot 2.7** | 🟡 中 | 非標準組合，可能存在相容性問題 |
| **依賴版本管理** | 🟡 中 | 部分依賴版本較舊（如 FastJSON 1.x） |

### 7.2 改進建議

1. **測試體系建設**：補充單元測試、整合測試，建立 CI/CD 流水線
2. **框架升級**：逐步遷移到 Spring Boot 3.x + Java 21 LTS
3. **文件完善**：補充 API 文件、部署文件、開發者指南
4. **程式碼審查**：建立 PR 審查機制，降低單人風險
5. **監控完善**：補充 Prometheus + Grafana 監控體系
6. **安全加固**：依賴漏洞掃描、安全稽核

---

## 八、總結

yFeiEye 是一個**功能完備、架構清晰、技術棧豐富**的 AIoT 平台。專案在不到兩年內完成了從 V1.0 到 V9.17 的快速迭代，涵蓋了裝置接入、視訊處理、AI 推理、模型管理、告警通知等完整的業務閉環。多語言微服務架構（Java + Python + C++）的設計思路值得肯定，能夠充分發揮各語言的優勢。

專案最大的特色是 **AI 全鏈路能力**（從資料標註到模型部署的完整閉環）和**多協定裝置接入能力**（GB28181/ONVIF/RTSP/MQTT/TCP），這在同類專案中較為罕見。

**綜合完成度：★★★★☆（85%）** — 核心功能已完備，處於持續優化和打磨階段。
