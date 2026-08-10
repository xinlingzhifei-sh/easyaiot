# RUNTIME Module

High-performance **C++ frame worker** for yFeiEye (evolved from TASK). Handles pull / decode / infer / emit. It does **not** replace VIDEO.

## Role vs VIDEO

- **VIDEO**: orchestration, preview forward, task lifecycle, alert hook, Kafka, DB
- **RUNTIME**: hot path executor for `executor=cpp` realtime tasks

## Capabilities

- Optimized C++ execution for compute-intensive workloads
- Real-time AI inference on edge devices
- Concurrent task processing and scheduling through TaskManager compatibility
- Low-memory operation for resource-constrained edge nodes
- ONNX model inference with runtime health and version checks

The default is `executor=cpp`; use `executor=python` when the Python worker is preferred. See the Chinese README for pipeline, build, and VIDEO integration details: [README.md](README.md).
