# yFeiEye 프로젝트 심층 기술 아키텍처 분석 보고서

> 분석 일자：2026-05-31 | 저장소：https://gitee.com/volara/yfeieye | 현재 브랜치：main (V9.17.0)

---

## 一、프로젝트 개요

**yFeiEye**（yFei AI Vision Control Platform）는 **클라우드-엣지-디바이스 일체형 지능형 IoT 플랫폼**으로, AI와 IoT의 심층 융합에 집중합니다. 프로젝트 비전은 "AI를 전 세계 누구나 쉽게 접할 수 있게 한다"입니다.

| 지표 | 데이터 |
|------|------|
| 총 커밋 수 | 1,760 |
| 주요 기여자 | reese（3,988 commits，95%+） |
| 버전 반복 | V1.0.0 → V9.17.0（총 35+ 개 버전 브랜치） |
| 코드 규모 | Java 2,374 파일 / Python 173 파일 / Vue 558 파일 / TypeScript 610 파일 / C++ 30 파일 |
| Shell 스크립트 | 79개（배포/운영 자동화） |
| SQL 스크립트 | 7개（다중 데이터베이스 초기화） |

---

## 二、전체 아키텍처 설계

### 2.1 계층형 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    WEB 프론트엔드（Vue 3 + Ant Design Vue）        │
├─────────────────────────────────────────────────────────────┤
│                 API Gateway（Spring Cloud Gateway）           │
├──────────┬──────────┬──────────┬──────────┬─────────────────┤
│ iot-system │ iot-infra │ iot-device │ iot-dataset │ iot-message │
│  시스템 관리  │  인프라  │  디바이스 관리   │  데이터셋 관리  │  메시지 푸시    │
├──────────┴──────────┴──────────┴──────────┴─────────────────┤
│          iot-sink（프로토콜 어댑터 계층：MQTT/TCP/HTTP/EMQX）           │
├──────────┬──────────┬──────────┬─────────────────────────────┤
│  AI 서비스  │ VIDEO 서비스│ TASK 모듈 │ iot-gb28181（영상 감시 프로토콜）  │
│ Flask+YOLO│ Flask+스트림 처리│ C++ 추론  │   Java SIP 시그널링            │
├──────────┴──────────┴──────────┴─────────────────────────────┤
│              미들웨어 계층（Nacos / PostgreSQL / Redis / Kafka / MinIO / TDengine）│
└─────────────────────────────────────────────────────────────┘
```

### 2.2 마이크로서비스 분할

프로젝트는 **다언어 마이크로서비스 아키텍처**를 채택하며, 책임 도메인별로 5대 모듈로 분할합니다：

| 모듈 | 언어/프레임워크 | 책임 | 서비스 수 |
|------|-----------|------|--------|
| **DEVICE** | Java 21 + Spring Boot 2.7 + Spring Cloud | 디바이스 관리、시스템 관리、메시지 푸시、데이터셋、파일 저장 | 8+ 마이크로서비스 |
| **AI** | Python + Flask + PyTorch + YOLO | 모델 학습、추론、배포、OCR、음성、LLM | 1개 메인 서비스 + 하위 서비스 |
| **VIDEO** | Python + Flask + OpenCV + FFmpeg | 영상 스트림 처리、실시간/스냅샷 알고리즘、녹화、알람 | 1개 메인 서비스 + 6개 하위 서비스 |
| **TASK** | C++17 + OpenCV + ONNX Runtime + FFmpeg | 엣지 실시간 추론 엔진 | 독립 프로세스 |
| **WEB** | Vue 3 + TypeScript + Vite + Ant Design Vue | 전 기능 프론트엔드 관리 플랫폼 | SPA |

---

## 三、각 모듈 기술 아키텍처 상세

### 3.1 DEVICE 모듈（Java 마이크로서비스 클러스터）

**기술 스택：**
- **프레임워크**：Spring Boot 2.7.18 + Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.4.0
- **JDK**：Java 21
- **게이트웨이**：Spring Cloud Gateway
- **등록/설정 센터**：Nacos
- **데이터베이스**：PostgreSQL（주 DB）+ TDengine（시계열 데이터）
- **ORM**：MyBatis-Plus 3.5.5 + Dynamic Datasource
- **캐시**：Redis + Redisson 3.18.0
- **메시지 큐**：RocketMQ / Kafka
- **객체 저장**：MinIO
- **워크플로**：Flowable 6.8.0
- **예약 작업**：XXL-Job 2.3.1
- **API 문서**：Knife4j 4.3.0 + SpringDoc
- **모니터링**：SkyWalking 8.12.0 + Spring Boot Admin
- **유틸리티 라이브러리**：Hutool 5.8.25、MapStruct 1.5.5、EasyExcel 3.3.3

**하위 모듈 분할（12개）：**

| 하위 모듈 | Java 파일 수 | 책임 |
|--------|------------|------|
| iot-common | 447 | 공통 기반 라이브러리（보안、캐시、RPC、MQ、MyBatis、테넌트 등 17개 하위 모듈） |
| iot-gb28181 | 569 | GB28181 국가 표준 영상 감시 프로토콜 연동（SIP 시그널링、디바이스 등록、스트림 관리） |
| iot-system | 398 | 시스템 관리（사용자、역할、권한、부서、사전、OAuth2、SMS） |
| iot-device | 272 | 디바이스 관리（제품、디바이스、OTA、사물 모델、프로토콜 관리） |
| iot-sink | 191 | 프로토콜 어댑터 계층（MQTT/TCP/HTTP/EMQX 상하행 메시지 처리） |
| iot-infra | 188 | 인프라（파일、로그、WebSocket、설정、코드 생성） |
| iot-message | 120 | 메시지 푸시（이메일、SMS、딩톡、飞书、WeChat 공중계정/기업 WeChat） |
| iot-dataset | 117 | 데이터셋 관리（어노테이션、가져오기/내보내기、YOLO/COCO/ImageFolder 형식） |
| iot-tdengine | 38 | TDengine 시계열 데이터베이스 통합 |
| iot-file | 19 | 파일 서비스（MinIO/로컬 저장） |
| iot-gateway | 15 | API 게이트웨이 |

**아키텍처 특징：**
- 표준 api/biz 계층：각 비즈니스 모듈을 `xxx-api`（인터페이스 정의）와 `xxx-biz`（구현）로 분할
- OpenFeign을 통한 서비스 간 RPC 호출
- 다중 테넌트 지원（iot-common-tenant）
- 데이터 권한 지원（iot-common-data-permission）

### 3.2 AI 모듈（Python AI 서비스）

**기술 스택：**
- **프레임워크**：Flask + Flask-SQLAlchemy
- **딥러닝**：PyTorch 2.9+ (CUDA 12.8) + Ultralytics YOLO (v8/v11/v26)
- **추론 형식**：PyTorch / ONNX / TorchScript / TensorRT / OpenVINO
- **대형 모델**：QwenVL3 비전 대형 모델、Qwen/DeepSeek LLM
- **OCR**：PaddleOCR
- **음성**：讯飞语音 API
- **객체 저장**：MinIO
- **서비스 등록**：Nacos
- **데이터베이스**：PostgreSQL

**기능 모듈（Blueprint 아키텍처）：**

| Blueprint | 코드 라인 수 | 기능 |
|-----------|---------|------|
| llm.py | 1,718 | 대규모 언어 모델 추론（멀티모달 입력：RTSP/영상/이미지/오디오/텍스트） |
| model.py | 810 | 모델 관리（CRUD、버전 관리） |
| deploy.py | 805 | 모델 배포 서비스（클러스터 추론、로드 밸런싱、자동 장애 조치） |
| export.py | 677 | 모델 내보내기（ONNX/TorchScript/TensorRT/OpenVINO） |
| auto_label.py | 664 | 자동 어노테이션（AI 보조 어노테이션） |
| train.py | 1,036 | 모델 학습（YOLO 미세 조정、하이퍼파라미터 설정、학습 모니터링） |
| inference.py | 613 | 추론 서비스（단일 이미지/배치/영상 추론） |
| plate.py | 1,114 | 번호판 인식 |
| ocr.py | 385 | OCR 문자 인식 |
| speech.py | 247 | 음성 인식 |
| cluster.py | 440 | GPU 클러스터 관리 |
| train_task.py | 372 | 학습 작업 스케줄링 |

**핵심 서비스：**
- `inference_service.py`（1,241 라인）：핵심 추론 엔진
- `deploy_service.py`（786 라인）：모델 배포 관리
- `deploy_daemon.py`（417 라인）：배포 데몬 프로세스
- `ocr_service.py`（610 라인）：OCR 서비스
- `speech_service.py`（609 라인）：음성 서비스
- `minio_service.py`（481 라인）：객체 저장 서비스

### 3.3 VIDEO 모듈（Python 영상 처리 서비스）

**기술 스택：**
- **프레임워크**：Flask + Flask-CORS
- **영상 처리**：OpenCV + FFmpeg
- **스트리밍**：SRS（Simple Realtime Server）
- **객체 탐지**：YOLO (v8/v11/v26) + ByteTrack（객체 추적）
- **얼굴 인식**：Milvus 벡터 데이터베이스
- **메시지 큐**：Kafka
- **객체 저장**：MinIO
- **디바이스 검색**：ONVIF + 海康/大华 프라이빗 프로토콜

**기능 모듈（Blueprint 아키텍처）：**

| Blueprint | 코드 라인 수 | 기능 |
|-----------|---------|------|
| snap.py | 943 | 스냅샷 관리（예약 캡처、저장、검색） |
| stream_forward.py | 529 | 스트림 포워딩（RTSP/RTMP 푸시/풀 스트리밍） |
| algorithm_task.py | ~500 | 알고리즘 작업 관리（실시간/스냅샷 두 가지 모드） |
| camera.py | ~400 | 카메라 관리（다중 프로토콜 연동） |
| alert.py | ~400 | 알람 이벤트 관리 |
| record.py | 251 | 녹화 관리 |
| playback.py | 304 | 재생 관리 |
| face.py | ~300 | 얼굴 인식 |
| device_detection_region.py | ~300 | 탐지 영역 그리기 |

**하위 서비스 아키텍처（6개 독립 마이크로서비스）：**

| 서비스 | 책임 |
|------|------|
| realtime_algorithm_service | 실시간 영상 스트림 AI 분석 |
| snapshot_algorithm_service | 스냅샷 이미지 AI 분석 |
| frame_extractor_service | 영상 프레임 추출 |
| sorter_service | 분석 결과 정렬 |
| pusher_service | 영상 푸시 스트리밍 |
| stream_forward_service | 스트림 포워딩 |

**핵심 서비스 역량：**
- 다중 프로토콜 카메라 연동（GB28181、ONVIF、RTSP、海康/大华 프라이빗 프로토콜）
- 실시간 영상 스트림 AI 분석（밀리초 단위 응답）
- 탐지 영역 시각화 그리기（사각형/다각형）
- 삼중 연동 알람 메커니즘（탐지 영역 × 방어 시간대 × 이벤트 유형）
- 얼굴 인식 + Milvus 벡터 검색
- 녹화 저장 및 재생
- NVR 일괄 스캔 등록

### 3.4 TASK 모듈（C++ 엣지 추론 엔진）

**기술 스택：**
- **언어**：C++17
- **빌드**：CMake + vcpkg
- **추론 엔진**：ONNX Runtime（GPU 가속）
- **객체 탐지**：YOLOv11
- **영상 처리**：OpenCV + FFmpeg（libavcodec/libavformat/libavutil/libswscale）
- **로그**：glog
- **JSON**：jsoncpp
- **네트워크**：libcurl（HTTP 콜백）
- **플랫폼**：Windows + Linux

**아키텍처 설계：**
```
main.cpp → Manage (Server) → Config → ConfigParser
         → Yolov11Engine (推理引擎)
         → Yolov11ThreadPool (线程池)
         → Detech (检测逻辑)
         → Draw (绘制标注)
         → RTMPEncoder (RTMP 编码推流)
         → AlarmCallback (告警回调)
```

**핵심 특성：**
- 독립 프로세스 실행, INI 설정 파일로 구동
- RTSP 스트림 실시간 풀 + YOLO 추론 지원
- 멀티스레드 추론 스레드 풀
- RTMP 인코딩 푸시 스트리밍
- HTTP 알람 콜백 메커니즘
- 크로스 플랫폼 지원（Windows/Linux）

### 3.5 WEB 모듈（Vue 3 프론트엔드）

**기술 스택：**
- **프레임워크**：Vue 3.4 + TypeScript
- **빌드**：Vite
- **UI 라이브러리**：Ant Design Vue 4.0 + Element UI 2.15
- **상태 관리**：Pinia 2.1
- **라우팅**：Vue Router 4.3
- **국제화**：Vue I18n 9.6（중/영）
- **차트**：ECharts 5.5 + echarts-liquidfill + echarts-wordcloud
- **영상 재생**：EasyPlayer + Jessibuca（WebRTC/WebSocket）
- **지도**：高德地图 API
- **리치 텍스트**：TinyMCE 5.10 + Vditor
- **3D**：Three.js 0.145
- **드래그**：vuedraggable + sortablejs
- **CSS**：UnoCSS + Less + Sass

**페이지 모듈（14개 비즈니스 도메인）：**

| 모듈 | Vue 파일 수 | 기능 |
|------|-----------|------|
| camera | 60 | 카메라 관리（다중 프로토콜 연동、스트림 포워딩、탐지 영역、녹화 공간） |
| system | 55 | 시스템 관리（사용자、역할、부서、메뉴、사전、로그） |
| train | 34 | 학습 관리（학습 작업、모델 관리、배포 서비스、추론 결과、모델 내보내기） |
| infra | 31 | 인프라（API 로그、코드 생성、설정、파일、예약 작업） |
| dataset | 31 | 데이터셋 관리（어노테이션、가져오기/내보내기、형식 변환） |
| notice | 30 | 메시지 알림（이메일、SMS、딩톡、飞书、WeChat） |
| devices | 30 | 디바이스 관리（제품、디바이스、사물 모델、OTA） |
| gb28181 | 21 | GB28181 영상 감시（분할 화면 모니터링、디바이스 디렉터리） |
| dashboard | 6 | 모니터링 대시보드（알고리즘 알람、디바이스 상태、GPU 모니터링） |
| alert | 4 | 알람 이벤트 |
| product | 14 | 제품 관리 |
| rulechains | 5 | 규칙 체인 |
| ota | 3 | OTA 업그레이드 |

**프론트엔드 엔지니어링：**
- 558개 Vue 컴포넌트 + 610개 TypeScript 파일
- 완전한 Hooks 체계（50+ 커스텀 Hook）
- 권한 관리（라우트 가드 + 버튼 수준 권한）
- 다중 탭 관리
- 테마 커스터마이징（다크/라이트/사용자 정의）
- 국제화（중/영 이중 언어）
- 코드 규범（ESLint + Stylelint + Husky + lint-staged + commitlint）

---

## 四、미들웨어 및 인프라

### 4.1 미들웨어 스택

| 컴포넌트 | 버전 | 용도 |
|------|------|------|
| PostgreSQL | 18 | 주 데이터베이스（6개 비즈니스 DB：ruoyi-vue-pro、iot-ai、iot-device、iot-gb28181、iot-message、iot-video） |
| Nacos | v2.5.1 | 서비스 등록 및 설정 센터 |
| Redis | latest | 캐시、분산 락、세션 관리 |
| Kafka | latest | 메시지 큐（디바이스 데이터、알람 이벤트） |
| MinIO | latest | 객체 저장（모델 파일、스냅샷、녹화、데이터셋） |
| TDengine | 3.x | 시계열 데이터베이스（디바이스 원격 측정 데이터） |
| SRS | latest | 스트리밍 미디어 서버（RTSP/RTMP 포워딩） |

### 4.2 배포 아키텍처

- **Docker Compose 통합 오케스트레이션**：각 모듈별 독립 `docker-compose.yml`
- **통합 설치 스크립트**：`.scripts/docker/install_linux.sh` 원클릭 전체 서비스 배포
- **2단계 빌드**：Dockerfile.base（Maven 의존성 캐시）→ 각 모듈 Dockerfile
- **GPU 지원**：GPU 자동 감지 및 NVIDIA Container Runtime 활성화
- **ARM 지원**：ARM64 전용 설치 스크립트 및 Dockerfile 제공
- **银河麒麟**：국산화 적응 스크립트 제공

### 4.3 데이터베이스 설계

- **6개 PostgreSQL DB**：비즈니스 도메인별 격리
- **SQL 초기화 스크립트**：`.scripts/postgresql/` 하위 7개 SQL 파일
- **자동 초기화**：Docker 시작 시 `initdb.d`를 통해 자동 실행
- **TDengine 슈퍼 테이블**：`.scripts/tdengine/tdengine_super_tables.sql`

---

## 五、프로젝트 완성도 평가

### 5.1 기능 완성도

| 기능 도메인 | 완성도 | 설명 |
|--------|--------|------|
| **디바이스 연동 관리** | ★★★★★ | GB28181/ONVIF/RTSP 다중 프로토콜、NVR 일괄 스캔、海康/大华 프라이빗 프로토콜 |
| **영상 스트림 처리** | ★★★★★ | 실시간 스트림 분석、스트림 포워딩、녹화 재생、분할 화면 모니터링 |
| **AI 알고리즘 역량** | ★★★★★ | YOLO 객체 탐지、얼굴 인식、OCR、음성、번호판、LLM |
| **모델 관리** | ★★★★★ | 학습、내보내기（ONNX/TensorRT/OpenVINO）、배포、버전 관리、클러스터 추론 |
| **데이터셋 관리** | ★★★★☆ | 어노테이션、가져오기/내보내기（YOLO/COCO/ImageFolder）、자동 어노테이션 |
| **알람 시스템** | ★★★★★ | 삼중 연동 알람、다중 채널 푸시（이메일/SMS/딩톡/飞书/WeChat） |
| **시스템 관리** | ★★★★★ | 사용자、역할、권한、부서、사전、로그、OAuth2、다중 테넌트 |
| **IoT 프로토콜** | ★★★★☆ | MQTT/TCP/HTTP/EMQX 어댑터、사물 모델、OTA |
| **모니터링 대시보드** | ★★★★☆ | GPU 모니터링、디바이스 상태、알고리즘 알람 통계 |
| **엣지 추론** | ★★★☆☆ | C++ 엣지 엔진（Windows 위주, Linux 적응 중） |
| **프론트엔드 UI** | ★★★★★ | 558개 Vue 컴포넌트, 기능 완비 |

### 5.2 기술 성숙도

| 차원 | 평점 | 설명 |
|------|------|------|
| **아키텍처 설계** | ★★★★★ | 명확한 마이크로서비스 분할、다언어 협업、api/biz 계층 |
| **코드 품질** | ★★★★☆ | 규범적인 패키지 구조、주석 충실, 일부 모듈 중복 코드 존재 |
| **엔지니어링** | ★★★★★ | Docker Compose 오케스트레이션、원클릭 배포、CI/CD 스크립트、코드 규범 도구 체인 |
| **문서** | ★★★★☆ | README 다국어 버전（6개 언어）、모듈 README、장애排查 문서 |
| **테스트 커버리지** | ★★☆☆☆ | 소량 테스트 파일 존재, 전체 테스트 커버리지 낮음 |
| **버전 관리** | ★★★★★ | 35+ 버전 브랜치, 시맨틱 버전, 규범적인 Git 워크플로 |

### 5.3 반복 활성도

- **버전 범위**：V1.0.0 → V9.17.0（9개 메이저 버전, 17개 마이너 버전）
- **최근 커밋**：2026년 5월 31일（지속적인 활발한 개발 중）
- **최근 중점：**
  - 데이터셋 어노테이션 기능 최적화
  - GB28181 순환 의존성 수정
  - 분할 화면 모니터링 검은 화면 문제 수정
  - 영상 처리 알고리즘 최적화
  - 다양한 디바이스 추가 방식 통합

---

## 六、아키텍처 하이라이트 및 혁신 포인트

### 6.1 다언어 마이크로서비스 협업
- **Java**：비즈니스 로직、시스템 관리、디바이스 관리 담당（안정성、성숙한 생태계）
- **Python**：AI 추론、영상 처리 담당（AI 생태계 우위）
- **C++**：엣지 실시간 추론 담당（극한 성능）
- **TypeScript/Vue**：프론트엔드 표시 담당（사용자 경험）

### 6.2 클라우드-엣지-디바이스 일체화
- **클라우드**：Java 마이크로서비스 클러스터 + Python AI 서비스
- **엣지**：C++ TASK 추론 엔진（엣지 디바이스에 배포 가능）
- **디바이스**：카메라、센서 등 IoT 디바이스

### 6.3 AI 전체 체인 폐루프
```
数据采集 → 数据标注 → 模型训练 → 模型导出 → 模型部署 → 实时推理 → 告警通知
```

### 6.4 영상 처리 파이프라인
```
摄像头 → 流拉取 → 帧提取 → AI 推理 → 结果排序 → 告警/存储
```

### 6.5 디바이스 프로토콜 어댑터 계층
- iot-sink 모듈이 MQTT/TCP/HTTP/EMQX 네 가지 프로토콜의 통합 어댑터 구현
- 디바이스 섀도우、사물 모델、OTA 등 IoT 핵심 개념 지원

---

## 七、잠재적 위험 및 개선 제안

### 7.1 위험 요소

| 위험 | 심각도 | 설명 |
|------|--------|------|
| **단일 기여자** | 🔴 높음 | 95%+ 코드가 한 명에 의해 작성, 핵심 인력 위험 존재 |
| **테스트 커버리지 부족** | 🟡 중간 | 체계적인 단위 테스트 및 통합 테스트 부재 |
| **Spring Boot 2.7** | 🟡 중간 | EOL 상태, Spring Boot 3.x 업그레이드 권장 |
| **Java 21 + Spring Boot 2.7** | 🟡 중간 | 비표준 조합, 호환성 문제 가능 |
| **의존성 버전 관리** | 🟡 중간 | 일부 의존성 버전 구식（예：FastJSON 1.x） |

### 7.2 개선 제안

1. **테스트 체계 구축**：단위 테스트、통합 테스트 보완, CI/CD 파이프라인 구축
2. **프레임워크 업그레이드**：Spring Boot 3.x + Java 21 LTS로 점진적 마이그레이션
3. **문서 보완**：API 문서、배포 문서、개발자 가이드 보충
4. **코드 리뷰**：PR 리뷰 메커니즘 구축, 단일 인력 위험 감소
5. **모니터링 보완**：Prometheus + Grafana 모니터링 체계 보충
6. **보안 강화**：의존성 취약점 스캔、보안 감사

---

## 八、요약

yFeiEye는 **기능이 완비되고, 아키텍처가 명확하며, 기술 스택이 풍부한** AIoT 플랫폼입니다. 프로젝트는 2년 미만 만에 V1.0에서 V9.17까지 빠른 반복을 완료했으며, 디바이스 연동、영상 처리、AI 추론、모델 관리、알람 알림 등 완전한 비즈니스 폐루프를 포괄합니다. 다언어 마이크로서비스 아키텍처（Java + Python + C++）의 설계 방향은 각 언어의 장점을 충분히 발휘할 수 있어 긍정적입니다.

프로젝트의 가장 큰 특징은 **AI 전체 체인 역량**（데이터 어노테이션부터 모델 배포까지의 완전한 폐루프）과 **다중 프로토콜 디바이스 연동 역량**（GB28181/ONVIF/RTSP/MQTT/TCP）으로, 동종 프로젝트에서는 드문 편입니다.

**종합 완성도：★★★★☆（85%）** — 핵심 기능이 완비되었으며, 지속적인 최적화 및 다듬기 단계에 있습니다.
