# yFeiEye In-Depth Technical Architecture Analysis Report

> Analysis Date: 2026-05-31 | Repository: https://gitee.com/volara/yfeieye | Current Branch: main (V9.17.0)

---

## I. Project Overview

**yFeiEye** (yFei AI Vision Control Platform) is a **AI vision intelligent IoT platform** focused on deep integration of AI and IoT. The project vision is "Making AI accessible to the world with zero barriers."

| Metric | Data |
|------|------|
| Total Commits | 1,760 |
| Primary Contributor | reese (3,988 commits, 95%+) |
| Version Iteration | V1.0.0 → V9.17.0 (35+ version branches) |
| Codebase Size | Java 2,374 files / Python 173 files / Vue 558 files / TypeScript 610 files / C++ 30 files |
| Shell Scripts | 79 (deployment/operations automation) |
| SQL Scripts | 7 (multi-database initialization) |

---

## II. Overall Architecture Design

### 2.1 Layered Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    WEB Frontend (Vue 3 + Ant Design Vue)        │
├─────────────────────────────────────────────────────────────┤
│                 API Gateway (Spring Cloud Gateway)           │
├──────────┬──────────┬──────────┬──────────┬─────────────────┤
│ iot-system │ iot-infra │ iot-device │ iot-dataset │ iot-message │
│  System Mgmt  │  Infrastructure  │  Device Mgmt   │  Dataset Mgmt  │  Message Push    │
├──────────┴──────────┴──────────┴──────────┴─────────────────┤
│          iot-sink (Protocol Adaptation Layer: MQTT/TCP/HTTP/EMQX)           │
├──────────┬──────────┬──────────┬─────────────────────────────┤
│  AI Service  │ VIDEO Service│ TASK Module │ iot-gb28181 (Video Surveillance Protocol)  │
│ Flask+YOLO│ Flask+Stream Processing│ C++ Inference  │   Java SIP Signaling            │
├──────────┴──────────┴──────────┴─────────────────────────────┤
│              Middleware Layer (Nacos / PostgreSQL / Redis / Kafka / MinIO / TDengine)│
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Microservice Decomposition

The project adopts a **multi-language microservice architecture**, decomposed into 5 major modules by responsibility domain:

| Module | Language/Framework | Responsibility | Service Count |
|------|-----------|------|--------|
| **DEVICE** | Java 21 + Spring Boot 2.7 + Spring Cloud | Device management, system management, message push, datasets, file storage | 8+ microservices |
| **AI** | Python + Flask + PyTorch + YOLO | Model training, inference, deployment, OCR, speech, LLM | 1 main service + sub-services |
| **VIDEO** | Python + Flask + OpenCV + FFmpeg | Video stream processing, real-time/snapshot algorithms, recording, alerts | 1 main service + 6 sub-services |
| **TASK** | C++17 + OpenCV + ONNX Runtime + FFmpeg | Edge real-time inference engine | Standalone process |
| **WEB** | Vue 3 + TypeScript + Vite + Ant Design Vue | Full-featured frontend management platform | SPA |

---

## III. Detailed Technical Architecture by Module

### 3.1 DEVICE Module (Java Microservice Cluster)

**Technology Stack:**
- **Framework**: Spring Boot 2.7.18 + Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.4.0
- **JDK**: Java 21
- **Gateway**: Spring Cloud Gateway
- **Registry/Config Center**: Nacos
- **Database**: PostgreSQL (primary) + TDengine (time-series data)
- **ORM**: MyBatis-Plus 3.5.5 + Dynamic Datasource
- **Cache**: Redis + Redisson 3.18.0
- **Message Queue**: RocketMQ / Kafka
- **Object Storage**: MinIO
- **Workflow**: Flowable 6.8.0
- **Scheduled Tasks**: XXL-Job 2.3.1
- **API Documentation**: Knife4j 4.3.0 + SpringDoc
- **Monitoring**: SkyWalking 8.12.0 + Spring Boot Admin
- **Utility Libraries**: Hutool 5.8.25, MapStruct 1.5.5, EasyExcel 3.3.3

**Sub-module Decomposition (12 modules):**

| Sub-module | Java File Count | Responsibility |
|--------|------------|------|
| iot-common | 447 | Common foundation library (17 sub-modules: security, cache, RPC, MQ, MyBatis, tenant, etc.) |
| iot-gb28181 | 569 | GB28181 national standard video surveillance protocol integration (SIP signaling, device registration, stream management) |
| iot-system | 398 | System management (users, roles, permissions, departments, dictionaries, OAuth2, SMS) |
| iot-device | 272 | Device management (products, devices, OTA, thing models, protocol management) |
| iot-sink | 191 | Protocol adaptation layer (MQTT/TCP/HTTP/EMQX uplink/downlink message processing) |
| iot-infra | 188 | Infrastructure (files, logs, WebSocket, configuration, code generation) |
| iot-message | 120 | Message push (email, SMS, DingTalk, Feishu, WeChat Official Account/Enterprise WeChat) |
| iot-dataset | 117 | Dataset management (annotation, import/export, YOLO/COCO/ImageFolder formats) |
| iot-tdengine | 38 | TDengine time-series database integration |
| iot-file | 19 | File service (MinIO/local storage) |
| iot-gateway | 15 | API gateway |

**Architectural Characteristics:**
- Standard api/biz layering: each business module is split into `xxx-api` (interface definition) and `xxx-biz` (implementation)
- Inter-service RPC calls via OpenFeign
- Multi-tenant support (iot-common-tenant)
- Data permission support (iot-common-data-permission)

### 3.2 AI Module (Python AI Service)

**Technology Stack:**
- **Framework**: Flask + Flask-SQLAlchemy
- **Deep Learning**: PyTorch 2.9+ (CUDA 12.8) + Ultralytics YOLO (v8/v11/v26)
- **Inference Formats**: PyTorch / ONNX / TorchScript / TensorRT / OpenVINO
- **Large Models**: QwenVL3 vision model, Qwen/DeepSeek LLM
- **OCR**: PaddleOCR
- **Speech**: iFlytek Speech API
- **Object Storage**: MinIO
- **Service Registry**: Nacos
- **Database**: PostgreSQL

**Functional Modules (Blueprint Architecture):**

| Blueprint | Lines of Code | Function |
|-----------|---------|------|
| llm.py | 1,718 | Large language model inference (multimodal input: RTSP/video/image/audio/text) |
| model.py | 810 | Model management (CRUD, version management) |
| deploy.py | 805 | Model deployment service (cluster inference, load balancing, automatic failover) |
| export.py | 677 | Model export (ONNX/TorchScript/TensorRT/OpenVINO) |
| auto_label.py | 664 | Auto-labeling (AI-assisted annotation) |
| train.py | 1,036 | Model training (YOLO fine-tuning, hyperparameter configuration, training monitoring) |
| inference.py | 613 | Inference service (single image/batch/video inference) |
| plate.py | 1,114 | License plate recognition |
| ocr.py | 385 | OCR text recognition |
| speech.py | 247 | Speech recognition |
| cluster.py | 440 | GPU cluster management |
| train_task.py | 372 | Training task scheduling |

**Core Services:**
- `inference_service.py` (1,241 lines): Core inference engine
- `deploy_service.py` (786 lines): Model deployment management
- `deploy_daemon.py` (417 lines): Deployment daemon process
- `ocr_service.py` (610 lines): OCR service
- `speech_service.py` (609 lines): Speech service
- `minio_service.py` (481 lines): Object storage service

### 3.3 VIDEO Module (Python Video Processing Service)

**Technology Stack:**
- **Framework**: Flask + Flask-CORS
- **Video Processing**: OpenCV + FFmpeg
- **Streaming Media**: SRS (Simple Realtime Server)
- **Object Detection**: YOLO (v8/v11/v26) + ByteTrack (object tracking)
- **Face Recognition**: Milvus vector database
- **Message Queue**: Kafka
- **Object Storage**: MinIO
- **Device Discovery**: ONVIF + Hikvision/Dahua proprietary protocols

**Functional Modules (Blueprint Architecture):**

| Blueprint | Lines of Code | Function |
|-----------|---------|------|
| snap.py | 943 | Snapshot management (scheduled capture, storage, retrieval) |
| stream_forward.py | 529 | Stream forwarding (RTSP/RTMP push/pull) |
| algorithm_task.py | ~500 | Algorithm task management (real-time/snapshot modes) |
| camera.py | ~400 | Camera management (multi-protocol access) |
| alert.py | ~400 | Alert event management |
| record.py | 251 | Recording management |
| playback.py | 304 | Playback management |
| face.py | ~300 | Face recognition |
| device_detection_region.py | ~300 | Detection region drawing |

**Sub-service Architecture (6 independent microservices):**

| Service | Responsibility |
|------|------|
| realtime_algorithm_service | Real-time video stream AI analysis |
| snapshot_algorithm_service | Snapshot image AI analysis |
| frame_extractor_service | Video frame extraction |
| sorter_service | Analysis result sorting |
| pusher_service | Video stream pushing |
| stream_forward_service | Stream forwarding |

**Core Service Capabilities:**
- Multi-protocol camera access (GB28181, ONVIF, RTSP, Hikvision/Dahua proprietary protocols)
- Real-time video stream AI analysis (millisecond-level response)
- Detection region visual drawing (rectangle/polygon)
- Triple-linked alert mechanism (detection region × defense period × event type)
- Face recognition + Milvus vector retrieval
- Recording storage and playback
- NVR batch scan and registration

### 3.4 TASK Module (C++ Edge Inference Engine)

**Technology Stack:**
- **Language**: C++17
- **Build**: CMake + vcpkg
- **Inference Engine**: ONNX Runtime (GPU acceleration)
- **Object Detection**: YOLOv11
- **Video Processing**: OpenCV + FFmpeg (libavcodec/libavformat/libavutil/libswscale)
- **Logging**: glog
- **JSON**: jsoncpp
- **Network**: libcurl (HTTP callback)
- **Platform**: Windows + Linux

**Architecture Design:**
```
main.cpp → Manage (Server) → Config → ConfigParser
         → Yolov11Engine (Inference Engine)
         → Yolov11ThreadPool (Thread Pool)
         → Detech (Detection Logic)
         → Draw (Annotation Drawing)
         → RTMPEncoder (RTMP Encoding & Push)
         → AlarmCallback (Alert Callback)
```

**Core Features:**
- Runs as a standalone process, driven by INI configuration files
- Supports RTSP stream real-time pull + YOLO inference
- Multi-threaded inference thread pool
- RTMP encoding and stream push
- HTTP alert callback mechanism
- Cross-platform support (Windows/Linux)

### 3.5 WEB Module (Vue 3 Frontend)

**Technology Stack:**
- **Framework**: Vue 3.4 + TypeScript
- **Build**: Vite
- **UI Library**: Ant Design Vue 4.0 + Element UI 2.15
- **State Management**: Pinia 2.1
- **Routing**: Vue Router 4.3
- **Internationalization**: Vue I18n 9.6 (Chinese/English)
- **Charts**: ECharts 5.5 + echarts-liquidfill + echarts-wordcloud
- **Video Playback**: EasyPlayer + Jessibuca (WebRTC/WebSocket)
- **Maps**: Amap (Gaode Map) API
- **Rich Text**: TinyMCE 5.10 + Vditor
- **3D**: Three.js 0.145
- **Drag & Drop**: vuedraggable + sortablejs
- **CSS**: UnoCSS + Less + Sass

**Page Modules (14 business domains):**

| Module | Vue File Count | Function |
|------|-----------|------|
| camera | 60 | Camera management (multi-protocol access, stream forwarding, detection regions, recording space) |
| system | 55 | System management (users, roles, departments, menus, dictionaries, logs) |
| train | 34 | Training management (training tasks, model management, deployment services, inference results, model export) |
| infra | 31 | Infrastructure (API logs, code generation, configuration, files, scheduled tasks) |
| dataset | 31 | Dataset management (annotation, import/export, format conversion) |
| notice | 30 | Message notifications (email, SMS, DingTalk, Feishu, WeChat) |
| devices | 30 | Device management (products, devices, thing models, OTA) |
| gb28181 | 21 | GB28181 video surveillance (multi-screen monitoring, device directory) |
| dashboard | 6 | Monitoring dashboard (algorithm alerts, device status, GPU monitoring) |
| alert | 4 | Alert events |
| product | 14 | Product management |
| rulechains | 5 | Rule chains |
| ota | 3 | OTA upgrades |

**Frontend Engineering:**
- 558 Vue components + 610 TypeScript files
- Complete Hooks system (50+ custom Hooks)
- Permission management (route guards + button-level permissions)
- Multi-tab management
- Theme customization (dark/light/custom)
- Internationalization (Chinese/English bilingual)
- Code standards (ESLint + Stylelint + Husky + lint-staged + commitlint)

---

## IV. Middleware and Infrastructure

### 4.1 Middleware Stack

| Component | Version | Purpose |
|------|------|------|
| PostgreSQL | 18 | Primary database (6 business databases: ruoyi-vue-pro, iot-ai, iot-device, iot-gb28181, iot-message, iot-video) |
| Nacos | v2.5.1 | Service registry and configuration center |
| Redis | latest | Cache, distributed locks, session management |
| Kafka | latest | Message queue (device data, alert events) |
| MinIO | latest | Object storage (model files, snapshots, recordings, datasets) |
| TDengine | 3.x | Time-series database (device telemetry data) |
| SRS | latest | Streaming media server (RTSP/RTMP forwarding) |

### 4.2 Deployment Architecture

- **Docker Compose unified orchestration**: each module has its own `docker-compose.yml`
- **Unified installation script**: `.scripts/docker/install_linux.sh` for one-click deployment of all services
- **Two-stage build**: Dockerfile.base (Maven dependency cache) → per-module Dockerfile
- **GPU support**: automatic GPU detection and NVIDIA Container Runtime enablement
- **ARM support**: dedicated ARM64 installation scripts and Dockerfiles
- **Kylin OS**: domestic adaptation scripts provided

### 4.3 Database Design

- **6 PostgreSQL databases**: isolated by business domain
- **SQL initialization scripts**: 7 SQL files under `.scripts/postgresql/`
- **Automatic initialization**: executed via `initdb.d` on Docker startup
- **TDengine super tables**: `.scripts/tdengine/tdengine_super_tables.sql`

---

## V. Project Completion Assessment

### 5.1 Feature Completion

| Feature Domain | Completion | Notes |
|--------|--------|------|
| **Device Access Management** | ★★★★★ | GB28181/ONVIF/RTSP multi-protocol, NVR batch scan, Hikvision/Dahua proprietary protocols |
| **Video Stream Processing** | ★★★★★ | Real-time stream analysis, stream forwarding, recording playback, multi-screen monitoring |
| **AI Algorithm Capabilities** | ★★★★★ | YOLO object detection, face recognition, OCR, speech, license plate, LLM |
| **Model Management** | ★★★★★ | Training, export (ONNX/TensorRT/OpenVINO), deployment, version management, cluster inference |
| **Dataset Management** | ★★★★☆ | Annotation, import/export (YOLO/COCO/ImageFolder), auto-labeling |
| **Alert System** | ★★★★★ | Triple-linked alerts, multi-channel push (email/SMS/DingTalk/Feishu/WeChat) |
| **System Management** | ★★★★★ | Users, roles, permissions, departments, dictionaries, logs, OAuth2, multi-tenant |
| **IoT Protocols** | ★★★★☆ | MQTT/TCP/HTTP/EMQX adaptation, thing models, OTA |
| **Monitoring Dashboard** | ★★★★☆ | GPU monitoring, device status, algorithm alert statistics |
| **Edge Inference** | ★★★☆☆ | C++ edge engine (primarily Windows, Linux adaptation in progress) |
| **Frontend UI** | ★★★★★ | 558 Vue components, feature-complete |

### 5.2 Technical Maturity

| Dimension | Rating | Notes |
|------|------|------|
| **Architecture Design** | ★★★★★ | Clear microservice decomposition, multi-language collaboration, api/biz layering |
| **Code Quality** | ★★★★☆ | Standard package structure, well-documented, but some modules have duplicate code |
| **Engineering** | ★★★★★ | Docker Compose orchestration, one-click deployment, CI/CD scripts, code quality toolchain |
| **Documentation** | ★★★★☆ | Multilingual README (6 languages), module READMEs, troubleshooting documentation |
| **Test Coverage** | ★★☆☆☆ | Few test files, overall test coverage is relatively low |
| **Version Management** | ★★★★★ | 35+ version branches, semantic versioning, standard Git workflow |

### 5.3 Iteration Activity

- **Version Span**: V1.0.0 → V9.17.0 (9 major versions, 17 minor versions)
- **Latest Commit**: May 31, 2026 (actively under development)
- **Recent Focus Areas**:
  - Dataset annotation feature optimization
  - GB28181 circular dependency fix
  - Multi-screen monitoring black screen fix
  - Video processing algorithm optimization
  - Unified multiple device addition methods

---

## VI. Architectural Highlights and Innovations

### 6.1 Multi-Language Microservice Collaboration
- **Java**: Business logic, system management, device management (stability, mature ecosystem)
- **Python**: AI inference, video processing (AI ecosystem advantages)
- **C++**: Edge real-time inference (maximum performance)
- **TypeScript/Vue**: Frontend presentation (user experience)

### 6.2 AI Vision Integration
- **Cloud**: Java microservice cluster + Python AI services
- **Edge**: C++ TASK inference engine (deployable on edge devices)
- **Device**: Cameras, sensors, and other IoT devices

### 6.3 Full AI Pipeline Closed Loop
```
Data Collection → Data Annotation → Model Training → Model Export → Model Deployment → Real-time Inference → Alert Notification
```

### 6.4 Video Processing Pipeline
```
Camera → Stream Pull → Frame Extraction → AI Inference → Result Sorting → Alert/Storage
```

### 6.5 Device Protocol Adaptation Layer
- The iot-sink module implements unified adaptation for four protocols: MQTT/TCP/HTTP/EMQX
- Supports core IoT concepts such as device shadow, thing models, and OTA

---

## VII. Potential Risks and Improvement Recommendations

### 7.1 Risk Points

| Risk | Severity | Description |
|------|--------|------|
| **Single Contributor** | 🔴 High | 95%+ of code by one person, core personnel risk |
| **Insufficient Test Coverage** | 🟡 Medium | Lack of systematic unit and integration tests |
| **Spring Boot 2.7** | 🟡 Medium | EOL reached, recommend upgrading to Spring Boot 3.x |
| **Java 21 + Spring Boot 2.7** | 🟡 Medium | Non-standard combination, potential compatibility issues |
| **Dependency Version Management** | 🟡 Medium | Some dependency versions are outdated (e.g., FastJSON 1.x) |

### 7.2 Improvement Recommendations

1. **Test System Development**: Add unit tests, integration tests, establish CI/CD pipeline
2. **Framework Upgrade**: Gradually migrate to Spring Boot 3.x + Java 21 LTS
3. **Documentation Enhancement**: Add API documentation, deployment documentation, developer guides
4. **Code Review**: Establish PR review mechanism to reduce single-person risk
5. **Monitoring Enhancement**: Add Prometheus + Grafana monitoring system
6. **Security Hardening**: Dependency vulnerability scanning, security audits

---

## VIII. Summary

yFeiEye is a **feature-complete, architecturally clear, and technologically diverse** AIoT platform. In less than two years, the project completed rapid iteration from V1.0 to V9.17, covering the full business loop of device access, video processing, AI inference, model management, and alert notifications. The multi-language microservice architecture design (Java + Python + C++) is commendable, fully leveraging the strengths of each language.

The project's greatest strengths are its **full AI pipeline capabilities** (complete closed loop from data annotation to model deployment) and **multi-protocol device access capabilities** (GB28181/ONVIF/RTSP/MQTT/TCP), which are relatively rare among similar projects.

**Overall Completion: ★★★★☆ (85%)** — Core features are complete; the project is in a phase of continuous optimization and refinement.
