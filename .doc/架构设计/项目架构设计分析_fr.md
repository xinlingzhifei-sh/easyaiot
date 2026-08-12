# Rapport d'analyse approfondie de l'architecture technique yFeiEye

> Date d'analyse : 2026-05-31 | Dépôt : https://gitee.com/volara/yfeieye | Branche actuelle : main (V9.17.0)

---

## I. Vue d'ensemble du projet

**yFeiEye** (yFei AI Vision Control Platform) est une **plateforme IoT intelligente intégrée cloud-edge-device**, axée sur l'intégration profonde de l'IA et de l'IoT. La vision du projet est « Rendre l'IA accessible au monde entier sans barrière ».

| Indicateur | Données |
|------|------|
| Nombre total de commits | 1 760 |
| Contributeur principal | reese (3 988 commits, 95 %+) |
| Itération des versions | V1.0.0 → V9.17.0 (35+ branches de version) |
| Taille du code | Java 2 374 fichiers / Python 173 fichiers / Vue 558 fichiers / TypeScript 610 fichiers / C++ 30 fichiers |
| Scripts Shell | 79 (automatisation déploiement/exploitation) |
| Scripts SQL | 7 (initialisation multi-bases de données) |

---

## II. Conception architecturale globale

### 2.1 Architecture en couches

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend WEB (Vue 3 + Ant Design Vue)        │
├─────────────────────────────────────────────────────────────┤
│                 API Gateway (Spring Cloud Gateway)           │
├──────────┬──────────┬──────────┬──────────┬─────────────────┤
│ iot-system │ iot-infra │ iot-device │ iot-dataset │ iot-message │
│  Gestion système  │  Infrastructure  │  Gestion appareils   │  Gestion jeux de données  │  Push messages    │
├──────────┴──────────┴──────────┴──────────┴─────────────────┤
│          iot-sink (Couche d'adaptation protocoles : MQTT/TCP/HTTP/EMQX)           │
├──────────┬──────────┬──────────┬─────────────────────────────┤
│  Service AI  │ Service VIDEO│ Module TASK │ iot-gb28181 (Protocole vidéosurveillance)  │
│ Flask+YOLO│ Flask+Traitement flux│ Inférence C++  │   Signalisation SIP Java            │
├──────────┴──────────┴──────────┴─────────────────────────────┤
│              Couche middleware (Nacos / PostgreSQL / Redis / Kafka / MinIO / TDengine)│
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Décomposition en microservices

Le projet adopte une **architecture microservices multilingue**, décomposée en 5 modules majeurs selon les domaines de responsabilité :

| Module | Langage/Framework | Responsabilité | Nombre de services |
|------|-----------|------|--------|
| **DEVICE** | Java 21 + Spring Boot 2.7 + Spring Cloud | Gestion des appareils, gestion système, push de messages, jeux de données, stockage de fichiers | 8+ microservices |
| **AI** | Python + Flask + PyTorch + YOLO | Entraînement, inférence, déploiement de modèles, OCR, voix, LLM | 1 service principal + sous-services |
| **VIDEO** | Python + Flask + OpenCV + FFmpeg | Traitement des flux vidéo, algorithmes temps réel/instantané, enregistrement, alertes | 1 service principal + 6 sous-services |
| **TASK** | C++17 + OpenCV + ONNX Runtime + FFmpeg | Moteur d'inférence temps réel en périphérie | Processus autonome |
| **WEB** | Vue 3 + TypeScript + Vite + Ant Design Vue | Plateforme de gestion frontend complète | SPA |

---

## III. Architecture technique détaillée par module

### 3.1 Module DEVICE (Cluster de microservices Java)

**Stack technologique :**
- **Framework** : Spring Boot 2.7.18 + Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.4.0
- **JDK** : Java 21
- **Passerelle** : Spring Cloud Gateway
- **Registre/Centre de configuration** : Nacos
- **Base de données** : PostgreSQL (principale) + TDengine (données temporelles)
- **ORM** : MyBatis-Plus 3.5.5 + Dynamic Datasource
- **Cache** : Redis + Redisson 3.18.0
- **File de messages** : RocketMQ / Kafka
- **Stockage objet** : MinIO
- **Workflow** : Flowable 6.8.0
- **Tâches planifiées** : XXL-Job 2.3.1
- **Documentation API** : Knife4j 4.3.0 + SpringDoc
- **Monitoring** : SkyWalking 8.12.0 + Spring Boot Admin
- **Bibliothèques utilitaires** : Hutool 5.8.25, MapStruct 1.5.5, EasyExcel 3.3.3

**Décomposition en sous-modules (12 modules) :**

| Sous-module | Nombre de fichiers Java | Responsabilité |
|--------|------------|------|
| iot-common | 447 | Bibliothèque de base commune (17 sous-modules : sécurité, cache, RPC, MQ, MyBatis, tenant, etc.) |
| iot-gb28181 | 569 | Intégration protocole vidéosurveillance GB28181 (signalisation SIP, enregistrement appareils, gestion des flux) |
| iot-system | 398 | Gestion système (utilisateurs, rôles, permissions, départements, dictionnaires, OAuth2, SMS) |
| iot-device | 272 | Gestion des appareils (produits, appareils, OTA, modèles de choses, gestion des protocoles) |
| iot-sink | 191 | Couche d'adaptation protocoles (traitement messages montants/descendants MQTT/TCP/HTTP/EMQX) |
| iot-infra | 188 | Infrastructure (fichiers, journaux, WebSocket, configuration, génération de code) |
| iot-message | 120 | Push de messages (e-mail, SMS, DingTalk, Feishu, compte officiel WeChat/WeChat Entreprise) |
| iot-dataset | 117 | Gestion des jeux de données (annotation, import/export, formats YOLO/COCO/ImageFolder) |
| iot-tdengine | 38 | Intégration base de données temporelle TDengine |
| iot-file | 19 | Service de fichiers (MinIO/stockage local) |
| iot-gateway | 15 | Passerelle API |

**Caractéristiques architecturales :**
- Couche api/biz standard : chaque module métier est divisé en `xxx-api` (définition d'interface) et `xxx-biz` (implémentation)
- Appels RPC inter-services via OpenFeign
- Support multi-tenant (iot-common-tenant)
- Support des permissions de données (iot-common-data-permission)

### 3.2 Module AI (Service IA Python)

**Stack technologique :**
- **Framework** : Flask + Flask-SQLAlchemy
- **Apprentissage profond** : PyTorch 2.9+ (CUDA 12.8) + Ultralytics YOLO (v8/v11/v26)
- **Formats d'inférence** : PyTorch / ONNX / TorchScript / TensorRT / OpenVINO
- **Grands modèles** : QwenVL3 modèle de vision, Qwen/DeepSeek LLM
- **OCR** : PaddleOCR
- **Voix** : API vocale iFlytek
- **Stockage objet** : MinIO
- **Registre de services** : Nacos
- **Base de données** : PostgreSQL

**Modules fonctionnels (Architecture Blueprint) :**

| Blueprint | Lignes de code | Fonction |
|-----------|---------|------|
| llm.py | 1 718 | Inférence de grand modèle de langage (entrée multimodale : RTSP/vidéo/image/audio/texte) |
| model.py | 810 | Gestion des modèles (CRUD, gestion des versions) |
| deploy.py | 805 | Service de déploiement de modèles (inférence cluster, équilibrage de charge, basculement automatique) |
| export.py | 677 | Export de modèles (ONNX/TorchScript/TensorRT/OpenVINO) |
| auto_label.py | 664 | Annotation automatique (annotation assistée par IA) |
| train.py | 1 036 | Entraînement de modèles (fine-tuning YOLO, configuration hyperparamètres, suivi d'entraînement) |
| inference.py | 613 | Service d'inférence (inférence image unique/lot/vidéo) |
| plate.py | 1 114 | Reconnaissance de plaques d'immatriculation |
| ocr.py | 385 | Reconnaissance de texte OCR |
| speech.py | 247 | Reconnaissance vocale |
| cluster.py | 440 | Gestion de cluster GPU |
| train_task.py | 372 | Planification des tâches d'entraînement |

**Services principaux :**
- `inference_service.py` (1 241 lignes) : Moteur d'inférence principal
- `deploy_service.py` (786 lignes) : Gestion du déploiement de modèles
- `deploy_daemon.py` (417 lignes) : Processus démon de déploiement
- `ocr_service.py` (610 lignes) : Service OCR
- `speech_service.py` (609 lignes) : Service vocal
- `minio_service.py` (481 lignes) : Service de stockage objet

### 3.3 Module VIDEO (Service de traitement vidéo Python)

**Stack technologique :**
- **Framework** : Flask + Flask-CORS
- **Traitement vidéo** : OpenCV + FFmpeg
- **Streaming** : SRS (Simple Realtime Server)
- **Détection d'objets** : YOLO (v8/v11/v26) + ByteTrack (suivi d'objets)
- **Reconnaissance faciale** : Base de données vectorielle Milvus
- **File de messages** : Kafka
- **Stockage objet** : MinIO
- **Découverte d'appareils** : ONVIF + protocoles propriétaires Hikvision/Dahua

**Modules fonctionnels (Architecture Blueprint) :**

| Blueprint | Lignes de code | Fonction |
|-----------|---------|------|
| snap.py | 943 | Gestion des instantanés (capture planifiée, stockage, recherche) |
| stream_forward.py | 529 | Transfert de flux (push/pull RTSP/RTMP) |
| algorithm_task.py | ~500 | Gestion des tâches algorithmiques (modes temps réel/instantané) |
| camera.py | ~400 | Gestion des caméras (accès multi-protocoles) |
| alert.py | ~400 | Gestion des événements d'alerte |
| record.py | 251 | Gestion des enregistrements |
| playback.py | 304 | Gestion de la relecture |
| face.py | ~300 | Reconnaissance faciale |
| device_detection_region.py | ~300 | Dessin des zones de détection |

**Architecture des sous-services (6 microservices indépendants) :**

| Service | Responsabilité |
|------|------|
| realtime_algorithm_service | Analyse IA de flux vidéo en temps réel |
| snapshot_algorithm_service | Analyse IA d'images instantanées |
| frame_extractor_service | Extraction de trames vidéo |
| sorter_service | Tri des résultats d'analyse |
| pusher_service | Push de flux vidéo |
| stream_forward_service | Transfert de flux |

**Capacités principales du service :**
- Accès caméra multi-protocoles (GB28181, ONVIF, RTSP, protocoles propriétaires Hikvision/Dahua)
- Analyse IA de flux vidéo en temps réel (réponse au niveau milliseconde)
- Dessin visuel des zones de détection (rectangle/polygone)
- Mécanisme d'alerte triple (zone de détection × période de défense × type d'événement)
- Reconnaissance faciale + recherche vectorielle Milvus
- Stockage et relecture des enregistrements
- Scan et enregistrement par lot NVR

### 3.4 Module TASK (Moteur d'inférence en périphérie C++)

**Stack technologique :**
- **Langage** : C++17
- **Build** : CMake + vcpkg
- **Moteur d'inférence** : ONNX Runtime (accélération GPU)
- **Détection d'objets** : YOLOv11
- **Traitement vidéo** : OpenCV + FFmpeg (libavcodec/libavformat/libavutil/libswscale)
- **Journalisation** : glog
- **JSON** : jsoncpp
- **Réseau** : libcurl (callback HTTP)
- **Plateforme** : Windows + Linux

**Conception architecturale :**
```
main.cpp → Manage (Server) → Config → ConfigParser
         → Yolov11Engine (Moteur d'inférence)
         → Yolov11ThreadPool (Pool de threads)
         → Detech (Logique de détection)
         → Draw (Dessin d'annotations)
         → RTMPEncoder (Encodage et push RTMP)
         → AlarmCallback (Callback d'alerte)
```

**Caractéristiques principales :**
- Exécution en processus autonome, piloté par fichiers de configuration INI
- Prise en charge du pull RTSP en temps réel + inférence YOLO
- Pool de threads d'inférence multi-thread
- Encodage et push RTMP
- Mécanisme de callback d'alerte HTTP
- Support multi-plateforme (Windows/Linux)

### 3.5 Module WEB (Frontend Vue 3)

**Stack technologique :**
- **Framework** : Vue 3.4 + TypeScript
- **Build** : Vite
- **Bibliothèque UI** : Ant Design Vue 4.0 + Element UI 2.15
- **Gestion d'état** : Pinia 2.1
- **Routage** : Vue Router 4.3
- **Internationalisation** : Vue I18n 9.6 (chinois/anglais)
- **Graphiques** : ECharts 5.5 + echarts-liquidfill + echarts-wordcloud
- **Lecture vidéo** : EasyPlayer + Jessibuca (WebRTC/WebSocket)
- **Cartes** : API Amap (Gaode Map)
- **Texte enrichi** : TinyMCE 5.10 + Vditor
- **3D** : Three.js 0.145
- **Glisser-déposer** : vuedraggable + sortablejs
- **CSS** : UnoCSS + Less + Sass

**Modules de pages (14 domaines métier) :**

| Module | Nombre de fichiers Vue | Fonction |
|------|-----------|------|
| camera | 60 | Gestion des caméras (accès multi-protocoles, transfert de flux, zones de détection, espace d'enregistrement) |
| system | 55 | Gestion système (utilisateurs, rôles, départements, menus, dictionnaires, journaux) |
| train | 34 | Gestion de l'entraînement (tâches d'entraînement, gestion des modèles, services de déploiement, résultats d'inférence, export de modèles) |
| infra | 31 | Infrastructure (journaux API, génération de code, configuration, fichiers, tâches planifiées) |
| dataset | 31 | Gestion des jeux de données (annotation, import/export, conversion de format) |
| notice | 30 | Notifications (e-mail, SMS, DingTalk, Feishu, WeChat) |
| devices | 30 | Gestion des appareils (produits, appareils, modèles de choses, OTA) |
| gb28181 | 21 | Vidéosurveillance GB28181 (monitoring multi-écrans, répertoire des appareils) |
| dashboard | 6 | Tableau de bord (alertes algorithmiques, état des appareils, monitoring GPU) |
| alert | 4 | Événements d'alerte |
| product | 14 | Gestion des produits |
| rulechains | 5 | Chaînes de règles |
| ota | 3 | Mises à jour OTA |

**Ingénierie frontend :**
- 558 composants Vue + 610 fichiers TypeScript
- Système Hooks complet (50+ Hooks personnalisés)
- Gestion des permissions (gardes de route + permissions au niveau bouton)
- Gestion multi-onglets
- Personnalisation du thème (sombre/clair/personnalisé)
- Internationalisation (bilingue chinois/anglais)
- Normes de code (ESLint + Stylelint + Husky + lint-staged + commitlint)

---

## IV. Middleware et infrastructure

### 4.1 Stack middleware

| Composant | Version | Usage |
|------|------|------|
| PostgreSQL | 18 | Base de données principale (6 bases métier : ruoyi-vue-pro, iot-ai, iot-device, iot-gb28181, iot-message, iot-video) |
| Nacos | v2.5.1 | Registre de services et centre de configuration |
| Redis | latest | Cache, verrous distribués, gestion des sessions |
| Kafka | latest | File de messages (données appareils, événements d'alerte) |
| MinIO | latest | Stockage objet (fichiers de modèles, instantanés, enregistrements, jeux de données) |
| TDengine | 3.x | Base de données temporelle (télémétrie des appareils) |
| SRS | latest | Serveur de streaming (transfert RTSP/RTMP) |

### 4.2 Architecture de déploiement

- **Orchestration unifiée Docker Compose** : chaque module possède son propre `docker-compose.yml`
- **Script d'installation unifié** : `.scripts/docker/install_linux.sh` pour déploiement en un clic de tous les services
- **Build en deux étapes** : Dockerfile.base (cache des dépendances Maven) → Dockerfile par module
- **Support GPU** : détection automatique GPU et activation de NVIDIA Container Runtime
- **Support ARM** : scripts d'installation et Dockerfiles dédiés ARM64
- **Kylin OS** : scripts d'adaptation pour l'écosystème domestique chinois

### 4.3 Conception de la base de données

- **6 bases PostgreSQL** : isolées par domaine métier
- **Scripts d'initialisation SQL** : 7 fichiers SQL sous `.scripts/postgresql/`
- **Initialisation automatique** : exécution via `initdb.d` au démarrage Docker
- **Super tables TDengine** : `.scripts/tdengine/tdengine_super_tables.sql`

---

## V. Évaluation du niveau d'achèvement du projet

### 5.1 Achèvement fonctionnel

| Domaine fonctionnel | Achèvement | Description |
|--------|--------|------|
| **Gestion de l'accès aux appareils** | ★★★★★ | Multi-protocoles GB28181/ONVIF/RTSP, scan par lot NVR, protocoles propriétaires Hikvision/Dahua |
| **Traitement des flux vidéo** | ★★★★★ | Analyse de flux temps réel, transfert de flux, relecture d'enregistrements, monitoring multi-écrans |
| **Capacités algorithmiques IA** | ★★★★★ | Détection d'objets YOLO, reconnaissance faciale, OCR, voix, plaques, LLM |
| **Gestion des modèles** | ★★★★★ | Entraînement, export (ONNX/TensorRT/OpenVINO), déploiement, gestion des versions, inférence cluster |
| **Gestion des jeux de données** | ★★★★☆ | Annotation, import/export (YOLO/COCO/ImageFolder), annotation automatique |
| **Système d'alertes** | ★★★★★ | Alertes triples, push multi-canal (e-mail/SMS/DingTalk/Feishu/WeChat) |
| **Gestion système** | ★★★★★ | Utilisateurs, rôles, permissions, départements, dictionnaires, journaux, OAuth2, multi-tenant |
| **Protocoles IoT** | ★★★★☆ | Adaptation MQTT/TCP/HTTP/EMQX, modèles de choses, OTA |
| **Tableau de bord de monitoring** | ★★★★☆ | Monitoring GPU, état des appareils, statistiques d'alertes algorithmiques |
| **Inférence en périphérie** | ★★★☆☆ | Moteur edge C++ (principalement Windows, adaptation Linux en cours) |
| **Interface frontend** | ★★★★★ | 558 composants Vue, fonctionnalités complètes |

### 5.2 Maturité technique

| Dimension | Évaluation | Description |
|------|------|------|
| **Conception architecturale** | ★★★★★ | Décomposition microservices claire, collaboration multilingue, couche api/biz |
| **Qualité du code** | ★★★★☆ | Structure de packages standard, bien documenté, mais duplication de code dans certains modules |
| **Ingénierie** | ★★★★★ | Orchestration Docker Compose, déploiement en un clic, scripts CI/CD, chaîne d'outils qualité code |
| **Documentation** | ★★★★☆ | README multilingue (6 langues), README par module, documentation de dépannage |
| **Couverture de tests** | ★★☆☆☆ | Peu de fichiers de test, couverture globale relativement faible |
| **Gestion des versions** | ★★★★★ | 35+ branches de version, versionnement sémantique, workflow Git standard |

### 5.3 Activité d'itération

- **Étendue des versions** : V1.0.0 → V9.17.0 (9 versions majeures, 17 versions mineures)
- **Dernier commit** : 31 mai 2026 (développement actif en cours)
- **Priorités récentes** :
  - Optimisation des fonctionnalités d'annotation de jeux de données
  - Correction de la dépendance circulaire GB28181
  - Correction de l'écran noir en monitoring multi-écrans
  - Optimisation des algorithmes de traitement vidéo
  - Unification des méthodes d'ajout d'appareils

---

## VI. Points forts et innovations architecturales

### 6.1 Collaboration microservices multilingue
- **Java** : Logique métier, gestion système, gestion des appareils (stabilité, écosystème mature)
- **Python** : Inférence IA, traitement vidéo (avantages de l'écosystème IA)
- **C++** : Inférence temps réel en périphérie (performance maximale)
- **TypeScript/Vue** : Présentation frontend (expérience utilisateur)

### 6.2 Intégration cloud-edge-device
- **Cloud** : Cluster de microservices Java + services IA Python
- **Edge** : Moteur d'inférence TASK C++ (déployable sur appareils edge)
- **Device** : Caméras, capteurs et autres appareils IoT

### 6.3 Boucle fermée complète de la chaîne IA
```
Collecte de données → Annotation de données → Entraînement de modèles → Export de modèles → Déploiement de modèles → Inférence temps réel → Notification d'alertes
```

### 6.4 Pipeline de traitement vidéo
```
Caméra → Pull de flux → Extraction de trames → Inférence IA → Tri des résultats → Alertes/Stockage
```

### 6.5 Couche d'adaptation des protocoles appareils
- Le module iot-sink implémente l'adaptation unifiée de quatre protocoles : MQTT/TCP/HTTP/EMQX
- Prise en charge des concepts IoT fondamentaux : ombre d'appareil, modèles de choses, OTA

---

## VII. Risques potentiels et recommandations d'amélioration

### 7.1 Points de risque

| Risque | Gravité | Description |
|------|--------|------|
| **Contributeur unique** | 🔴 Élevée | 95 %+ du code par une seule personne, risque de dépendance aux personnes clés |
| **Couverture de tests insuffisante** | 🟡 Moyenne | Absence de tests unitaires et d'intégration systématiques |
| **Spring Boot 2.7** | 🟡 Moyenne | Fin de vie atteinte, migration vers Spring Boot 3.x recommandée |
| **Java 21 + Spring Boot 2.7** | 🟡 Moyenne | Combinaison non standard, problèmes de compatibilité possibles |
| **Gestion des versions de dépendances** | 🟡 Moyenne | Certaines dépendances sont obsolètes (ex. FastJSON 1.x) |

### 7.2 Recommandations d'amélioration

1. **Construction du système de tests** : Ajouter tests unitaires et d'intégration, établir un pipeline CI/CD
2. **Mise à niveau du framework** : Migration progressive vers Spring Boot 3.x + Java 21 LTS
3. **Enrichissement de la documentation** : Compléter la documentation API, de déploiement et le guide développeur
4. **Revue de code** : Mettre en place un mécanisme de revue de PR pour réduire le risque lié à une seule personne
5. **Amélioration du monitoring** : Ajouter un système de monitoring Prometheus + Grafana
6. **Renforcement de la sécurité** : Scan de vulnérabilités des dépendances, audits de sécurité

---

## VIII. Synthèse

yFeiEye est une plateforme AIoT **fonctionnellement complète, architecturalement claire et technologiquement riche**. En moins de deux ans, le projet a accompli une itération rapide de V1.0 à V9.17, couvrant la boucle métier complète : accès aux appareils, traitement vidéo, inférence IA, gestion des modèles et notifications d'alertes. L'architecture microservices multilingue (Java + Python + C++) est remarquable et exploite pleinement les atouts de chaque langage.

Les atouts majeurs du projet sont ses **capacités de chaîne IA complète** (boucle fermée de l'annotation des données au déploiement des modèles) et ses **capacités d'accès multi-protocoles aux appareils** (GB28181/ONVIF/RTSP/MQTT/TCP), relativement rares parmi les projets similaires.

**Achèvement global : ★★★★☆ (85 %)** — Les fonctionnalités principales sont complètes ; le projet est en phase d'optimisation et de perfectionnement continu.
