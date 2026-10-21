# syntax=docker/dockerfile:1
# 运行时基础镜像：gcr.io 在国内不可达时由 install_linux.sh 改传镜像站地址（--build-arg RUNTIME_IMAGE=...）
ARG RUNTIME_IMAGE=gcr.io/distroless/static-debian12:nonroot

FROM golang:1.25-bookworm AS build
ARG TARGETARCH
# 与 RTC 一致走华为云 Go 模块代理（proxy.golang.org 国内不可达），可用 --build-arg GOPROXY=... 覆盖
ARG GOPROXY=https://repo.huaweicloud.com/repository/goproxy/,direct
ENV GOPROXY=${GOPROXY}
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=${TARGETARCH} go build -o /out/post ./cmd/post \
 && CGO_ENABLED=0 GOOS=linux GOARCH=${TARGETARCH} go build -o /out/post-replay ./cmd/post-replay

FROM ${RUNTIME_IMAGE}
COPY --from=build /out/post /usr/local/bin/post
COPY --from=build /out/post-replay /usr/local/bin/post-replay
COPY schemas /schemas
USER nonroot:nonroot
EXPOSE 8089
ENTRYPOINT ["/usr/local/bin/post"]
