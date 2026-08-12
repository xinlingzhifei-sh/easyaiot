# yFeiEye APP Module

yFeiEye APP is a cross-platform mobile management console built with uni-app 3. A single codebase compiles to H5, WeChat Mini Program, and native App, sharing the same backend API (`/admin-api`) as the PC (WEB) module to enable on-the-go control for operations and management staff.

## Features

- 🌐 Cross-Platform: Built on uni-app 3, one codebase for H5, WeChat Mini Program, and native App; CLI-based development without HBuilderX
- 🔗 API Reuse: Shares `/admin-api` with the WEB module; OAuth2 dual-token auth with auto token refresh and multi-tenant support
- 📹 Device Management: Unified list for direct cameras, GB28181, and NVR; channel browsing and one-tap live preview in device details
- 📡 Stream Forwarding: Create, start/stop stream forwarding tasks; view cluster node status and multi-stream URLs
- 🤖 Algorithm Tasks: Real-time and snapshot algorithm task lists with start/stop control and detection/frame statistics
- 🚨 Alert Center: Alert event search, snapshot preview, and on-demand alert video playback
- 🧠 Model Management: Model list and deployment status monitoring
- 🔮 Model Inference: Mobile image inference workspace — pick a model, upload an image, view results
- 🏋️ Model Training: Training task progress monitoring and one-click stop
- 🎬 Video Monitoring: Jessibuca low-latency player on H5 for FLV/HLS live streams and alert video playback
- 👤 Personal Center: Profile, account security, FAQ, feedback, privacy settings, and app configuration
- 💬 Notifications: In-app message search and detail view

## Tech Stack

- **Framework**: uni-app 3 / Vue 3
- **Language**: TypeScript
- **Build Tool**: Vite 5
- **UI Framework**: Wot Design Uni
- **Styling**: UnoCSS
- **State Management**: Pinia (with persistence)
- **Routing**: Convention-based routing (uni-pages)
- **HTTP Client**: Custom request wrapper (with API encryption support)
- **Video Playback**: Jessibuca / flv.js
- **Charts**: ECharts
- **Code Standards**: ESLint
