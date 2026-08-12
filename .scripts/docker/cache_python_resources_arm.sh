#!/bin/bash
# ============================================
# yFeiEye ARM Python 离线 wheel（按模块隔离）
# 目录: .build-cache/arm/{ai,video}/pip-wheels
# 下载方式: ARM 容器内 pip download + 清华 PyPI 源（与 build-runtime 一致）
# ============================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

check_command() { command -v "$1" >/dev/null 2>&1; }

image_to_tar_name() {
    echo "$1" | sed 's#[/:]#_#g'
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YFEIEYE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=init-build-cache-dirs.sh
source "${SCRIPT_DIR}/init-build-cache-dirs.sh"

AI_DIR="${YFEIEYE_ROOT}/AI"
VIDEO_DIR="${YFEIEYE_ROOT}/VIDEO"

ARM_BASE_IMAGE="${ARM_BASE_IMAGE:-pytorch/manylinuxaarch64-builder:cuda12.9}"
DOCKER_IMAGES_DIR="$(arm_docker_images_dir "$YFEIEYE_ROOT")"
PIP_INDEX_URL="https://pypi.tuna.tsinghua.edu.cn/simple"
PIP_DOWNLOAD_TIMEOUT="${PIP_DOWNLOAD_TIMEOUT:-600}"
PIP_DOWNLOAD_RETRIES="${PIP_DOWNLOAD_RETRIES:-15}"
PIP_RESUME_RETRIES="${PIP_RESUME_RETRIES:-30}"
CACHE_DOWNLOAD_MAX_ATTEMPTS="${CACHE_DOWNLOAD_MAX_ATTEMPTS:-5}"
CACHE_DOWNLOAD_RETRY_DELAY="${CACHE_DOWNLOAD_RETRY_DELAY:-15}"

# 参数: AI | VIDEO（模块名，映射到 ai / video 缓存目录）
MODULE_ARG="${1:-AI}"
case "${MODULE_ARG^^}" in
    AI) CACHE_MODULE="ai"; REQ_SOURCE="${AI_DIR}/requirements.txt" ;;
    VIDEO) CACHE_MODULE="video"; REQ_SOURCE="${VIDEO_DIR}/requirements.txt" ;;
    *)
        print_error "未知模块: $MODULE_ARG（支持 AI、VIDEO）"
        exit 1
        ;;
esac

PIP_WHEELS_DIR="$(arm_pip_wheels_build_context_dir_for "$YFEIEYE_ROOT" "$CACHE_MODULE")"
ARM_REQUIREMENTS_FILE="$(yfeieye_build_cache_base "$YFEIEYE_ROOT")/arm/${CACHE_MODULE}/requirements.arm.txt"

init_yfeieye_build_cache_dirs "$YFEIEYE_ROOT"

if ! check_command docker; then
    print_error "未检测到 docker，请先安装 Docker"
    exit 1
fi

download_docker_image() {
    local image="$1"
    local tar_file="${DOCKER_IMAGES_DIR}/$(image_to_tar_name "$image").tar"

    print_info "拉取镜像: $image"
    docker pull --platform linux/arm64 "$image"
    print_info "保存镜像到: $tar_file"
    docker save -o "$tar_file" "$image"
    print_success "镜像已保存: $image"
}

if [ "${CACHE_DOCKER_IMAGE:-0}" = "1" ]; then
    print_info "下载并保存 Docker 基础镜像..."
    download_docker_image "$ARM_BASE_IMAGE"
else
    print_info "跳过 Docker 镜像缓存（启用: CACHE_DOCKER_IMAGE=1 $0）"
fi

# 展开 -r includes；-r 相对路径按原 requirements 所在目录解析
append_requirements_file() {
    local req_file="$1"
    local out_file="$2"
    local include_base_dir="${3:-}"
    local req_dir line include_path

    [ -f "$req_file" ] || return 0
    if [ -n "$include_base_dir" ]; then
        req_dir="$(cd "$include_base_dir" && pwd)"
    else
        req_dir="$(cd "$(dirname "$req_file")" && pwd)"
    fi

    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%%#*}"
        line="$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
        [ -z "$line" ] && continue
        case "$line" in
            --index-url*) ;;
            -r*)
                include_path="${line#-r }"
                include_path="${line#-r}"
                include_path="$(echo "$include_path" | sed 's/^[[:space:]]*//')"
                if [[ "$include_path" != /* ]]; then
                    include_path="${req_dir}/${include_path}"
                fi
                append_requirements_file "$include_path" "$out_file" "$req_dir"
                ;;
            *)
                echo "$line" >> "$out_file"
                ;;
        esac
    done < "$req_file"
}

prepare_flattened_requirements_arm() {
    local req_file="$1"  # requirements.txt (ARM 版含 torch)
    local out_file out_dir tmp_req req_dir line_count
    # ★ 在项目 .build-cache 下创建临时文件，确保 Docker 挂载时路径可达
    out_dir="$(yfeieye_build_cache_base "$YFEIEYE_ROOT")/tmp"
    mkdir -p "$out_dir"
    out_file="${out_dir}/requirements-${CACHE_MODULE}-arm-flat.txt"
    tmp_req="${out_dir}/requirements-${CACHE_MODULE}-arm-src.txt"
    req_dir="$(cd "$(dirname "$req_file")" && pwd)"
    echo "--index-url ${PIP_INDEX_URL}" > "$out_file"
    if [ ! -f "$req_file" ]; then
        print_error "requirements 不存在: $req_file"
        rm -f "$out_file"
        return 1
    fi
    sed 's/onnxruntime-gpu/onnxruntime/g' "$req_file" > "$tmp_req"
    append_requirements_file "$tmp_req" "$out_file" "$req_dir"
    rm -f "$tmp_req"
    sed -i 's/onnxruntime-gpu/onnxruntime/g' "$out_file"
    line_count=$(grep -cve '^[[:space:]]*$' -- "$out_file" || echo 0)
    if [ "$line_count" -lt 8 ]; then
        print_error "requirements 扁平化结果异常（仅 ${line_count} 行），-r 引用可能未展开: ${req_file}"
        rm -f "$out_file"
        return 1
    fi
    sync -f "$out_file" 2>/dev/null || true
    echo "$out_file"
}

if [ "${CLEAR_PIP_WHEELS:-0}" = "1" ]; then
    print_info "[${CACHE_MODULE}] 清理旧 ARM pip wheel..."
    find "$PIP_WHEELS_DIR" -maxdepth 1 -type f -delete 2>/dev/null || true
    rm -f "$(arm_pip_wheels_stamp_file_for "$EASYAIOT_ROOT" "$CACHE_MODULE")"
fi

count_arm_wheels() {
    find "$PIP_WHEELS_DIR" -maxdepth 1 -type f \( -name "*.whl" -o -name "*.tar.gz" -o -name "*.zip" \) 2>/dev/null | wc -l
}

cleanup_broken_arm_wheels() {
    find "$PIP_WHEELS_DIR" -maxdepth 1 -type f -empty -delete 2>/dev/null || true
}

run_docker_pip_download() {
    local flat_req="$1"
    print_info "[${CACHE_MODULE}] ARM 容器内下载（清华源，当前 $(count_arm_wheels) 个 wheel）..."
    docker run --rm \
        --platform linux/arm64 \
        -e "PIP_INDEX_URL=${PIP_INDEX_URL}" \
        -e "PIP_DOWNLOAD_TIMEOUT=${PIP_DOWNLOAD_TIMEOUT}" \
        -e "PIP_DOWNLOAD_RETRIES=${PIP_DOWNLOAD_RETRIES}" \
        -e "PIP_RESUME_RETRIES=${PIP_RESUME_RETRIES}" \
        -e "http_proxy=" \
        -e "https_proxy=" \
        -e "HTTP_PROXY=" \
        -e "HTTPS_PROXY=" \
        -e "ALL_PROXY=" \
        -e "no_proxy=*" \
        -e "NO_PROXY=*" \
        -v "${flat_req}:/tmp/requirements-arm.flat:ro" \
        -v "${PIP_WHEELS_DIR}:/wheels" \
        "$ARM_BASE_IMAGE" \
        /bin/bash -lc '
set -e
if [ -x /opt/python/cp311-cp311/bin/pip3.11 ]; then
    PIP_BIN=/opt/python/cp311-cp311/bin/pip3.11
elif [ -x /opt/python/cp310-cp310/bin/pip3.10 ]; then
    PIP_BIN=/opt/python/cp310-cp310/bin/pip3.10
elif command -v pip3 >/dev/null 2>&1; then
    PIP_BIN=$(command -v pip3)
else
    echo "未找到可用 pip3"
    exit 1
fi
"$PIP_BIN" --version
"$PIP_BIN" download -r /tmp/requirements-arm.flat -d /wheels \
    --timeout "${PIP_DOWNLOAD_TIMEOUT}" \
    --retries "${PIP_DOWNLOAD_RETRIES}" \
    --resume-retries "${PIP_RESUME_RETRIES}" \
    -i "${PIP_INDEX_URL}"
'
}

download_pip_packages() {
    local flat_req attempt existing_count
    flat_req="$(prepare_flattened_requirements_arm "$REQ_SOURCE")"

    print_info "[${CACHE_MODULE}] ARM pip wheel → ${PIP_WHEELS_DIR}"
    print_info "[${CACHE_MODULE}] requirements 扁平化: $(wc -l < "$flat_req") 行"
    print_info "[${CACHE_MODULE}] PyPI 源: ${PIP_INDEX_URL}"

    if [ ! -f "$flat_req" ] || [ ! -s "$flat_req" ]; then
        print_error "[${CACHE_MODULE}] 扁平化 requirements 无效: ${flat_req}"
        return 1
    fi

    existing_count=$(count_arm_wheels)
    if [ "$existing_count" -gt 0 ]; then
        print_info "[${CACHE_MODULE}] 已有 ${existing_count} 个 wheel，断点续传（已下载的自动跳过）"
        print_info "[${CACHE_MODULE}] 全量重下请加 CLEAR_PIP_WHEELS=1"
    fi

    attempt=1
    while [ "$attempt" -le "$CACHE_DOWNLOAD_MAX_ATTEMPTS" ]; do
        cleanup_broken_arm_wheels
        print_info "[${CACHE_MODULE}] 下载尝试 ${attempt}/${CACHE_DOWNLOAD_MAX_ATTEMPTS}..."

        set +e
        run_docker_pip_download "$flat_req"
        local status=$?
        set -e

        if [ $status -eq 0 ]; then
            grep -cve '^[[:space:]]*$' -- "$flat_req" > "$(arm_pip_wheels_stamp_file_for "$EASYAIOT_ROOT" "$CACHE_MODULE")"
            print_success "[${CACHE_MODULE}] pip wheel 下载完成（共 $(count_arm_wheels) 个）"
            return 0
        fi

        if [ "$attempt" -lt "$CACHE_DOWNLOAD_MAX_ATTEMPTS" ]; then
            print_warning "[${CACHE_MODULE}] 下载失败，${CACHE_DOWNLOAD_RETRY_DELAY}s 后重试（已下载的 wheel 会保留）..."
            sleep "$CACHE_DOWNLOAD_RETRY_DELAY"
        fi
        attempt=$((attempt + 1))
    done

    print_error "[${CACHE_MODULE}] 下载失败（已重试 ${CACHE_DOWNLOAD_MAX_ATTEMPTS} 次）"
    print_info "断点续传: bash ${EASYAIOT_ROOT}/$(echo "$CACHE_MODULE" | tr '[:lower:]' '[:upper:]')/cache_resources_arm.sh"
    return 1
}

download_pip_packages

du -sh "$(yfeieye_build_cache_base "$YFEIEYE_ROOT")/arm" 2>/dev/null || true
print_success "ARM 预下载完成: ${PIP_WHEELS_DIR}"
