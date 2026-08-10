FROM ubuntu:22.04 AS build

ARG ONNXRUNTIME_VERSION=1.23.1

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        cmake \
        curl \
        g++ \
        libavcodec-dev \
        libavformat-dev \
        libavutil-dev \
        libcurl4-openssl-dev \
        libgoogle-glog-dev \
        libinih-dev \
        libjsoncpp-dev \
        libopencv-dev \
        libpng-dev \
        libpugixml-dev \
        libssl-dev \
        libswscale-dev \
        make \
        pkg-config \
    && rm -rf /var/lib/apt/lists/*

RUN for attempt in 1 2 3 4 5; do \
        curl -4 --retry 4 --retry-all-errors --retry-delay 5 \
            --connect-timeout 30 --max-time 900 -fL -C - \
            "https://github.com/microsoft/onnxruntime/releases/download/v${ONNXRUNTIME_VERSION}/onnxruntime-linux-x64-${ONNXRUNTIME_VERSION}.tgz" \
            -o /tmp/onnxruntime.tgz \
            && break; \
        test "${attempt}" -lt 5 || exit 1; \
        sleep 5; \
    done \
    && tar -tzf /tmp/onnxruntime.tgz >/dev/null \
    && mkdir -p /tmp/onnxruntime /usr/local/include/onnxruntime \
    && tar -xzf /tmp/onnxruntime.tgz -C /tmp/onnxruntime --strip-components=1 \
    && cp -a /tmp/onnxruntime/include/. /usr/local/include/onnxruntime/ \
    && cp -a /tmp/onnxruntime/lib/. /usr/local/lib/ \
    && ldconfig

WORKDIR /src
COPY . .

RUN cmake -S . -B build \
        -DBUILD_TASK_MANAGER=ON \
        -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build --target TaskManager RUNTIME -- -j2

FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        libavcodec58 \
        libavformat58 \
        libavutil56 \
        libgoogle-glog0v5 \
        libinih1 \
        libjsoncpp25 \
        libopencv-core4.5d \
        libopencv-dnn4.5d \
        libopencv-highgui4.5d \
        libopencv-imgcodecs4.5d \
        libopencv-imgproc4.5d \
        libopencv-videoio4.5d \
        libpng16-16 \
        libpugixml1v5 \
        libswscale5 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/config/generated /app/models

COPY --from=build /src/build/TaskManager /usr/local/bin/TaskManager
COPY --from=build /src/build/RUNTIME /usr/local/bin/RUNTIME
COPY --from=build /usr/local/lib/libonnxruntime.so* /usr/local/lib/
COPY --from=build /src/models/yolov11n.onnx /app/models/yolov11n.onnx
COPY --from=build /src/models/coco.names /app/models/coco.names

RUN ldconfig \
    && test -x /usr/local/bin/TaskManager \
    && test -x /usr/local/bin/RUNTIME \
    && ! ldd /usr/local/bin/RUNTIME | grep -q "not found"

WORKDIR /app
EXPOSE 7000

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -fsS http://127.0.0.1:7000/health || exit 1

CMD ["TaskManager", "--host", "0.0.0.0", "--port", "7000", "--task-bin", "/usr/local/bin/RUNTIME", "--config-dir", "/app/config/generated"]
