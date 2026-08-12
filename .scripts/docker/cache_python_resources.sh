#!/bin/bash
# ============================================
# yFeiEye Python 离线 wheel 预下载（按模块隔离）
# 目录: .build-cache/{ai,video}/pip-wheels
# Docker 构建使用各模块 requirements-docker.txt，与镜像内 pip install 一致
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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YFEIEYE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=init-build-cache-dirs.sh
source "${SCRIPT_DIR}/init-build-cache-dirs.sh"

AI_DIR="${YFEIEYE_ROOT}/AI"
VIDEO_DIR="${YFEIEYE_ROOT}/VIDEO"
DEFAULT_PYTORCH_DEVEL_IMAGE="pytorch/pytorch:2.9.0-cuda12.8-cudnn9-devel"
PYTORCH_BASE_IMAGE="${BASE_IMAGE:-$DEFAULT_PYTORCH_DEVEL_IMAGE}"

# PyPI 仅提供 sdist、需在 devel 镜像中预编译为 wheel（runtime 无 gcc）
SDIST_WHEEL_SPECS=(
    "netifaces==0.11.0"
)

# install_linux 等脚本会把 BASE_IMAGE 设为 runtime 供最终部署；sdist 编译须单独解析 devel 镜像
resolve_sdist_wheel_build_image() {
    if [ -n "${SDIST_WHEEL_BUILD_IMAGE:-}" ]; then
        echo "$SDIST_WHEEL_BUILD_IMAGE"
        return
    fi
    local base="$PYTORCH_BASE_IMAGE"
    if [[ "$base" == *-runtime ]]; then
        echo "${base%-runtime}-devel"
        return
    fi
    if [[ "$base" == *-devel ]] || [[ "$base" == *builder* ]]; then
        echo "$base"
        return
    fi
    echo "$DEFAULT_PYTORCH_DEVEL_IMAGE"
}

# 参数: all（默认）| ai | video
TARGET_MODULE="${1:-all}"

module_req_docker_file() {
    case "$(yfeieye_normalize_python_cache_module "$1")" in
        ai) echo "${AI_DIR}/requirements-docker.txt" ;;
        video) echo "${VIDEO_DIR}/requirements-docker.txt" ;;
    esac
}

module_base_image() {
    echo "$PYTORCH_BASE_IMAGE"
}

# 展开 -r includes，避免合并文件中出现无法解析的相对引用
append_requirements_file() {
    local req_file="$1"
    local out_file="$2"
    local req_dir line include_path

    [ -f "$req_file" ] || return 0
    req_dir="$(cd "$(dirname "$req_file")" && pwd)"

    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%%#*}"
        line="$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
        [ -z "$line" ] && continue
        case "$line" in
            --index-url*) ;;
            -r*)
                include_path="${line#-r }"
                include_path="${include_path#-r}"
                include_path="$(echo "$include_path" | sed 's/^[[:space:]]*//')"
                if [[ "$include_path" != /* ]]; then
                    include_path="${req_dir}/${include_path}"
                fi
                append_requirements_file "$include_path" "$out_file"
                ;;
            *)
                echo "$line" >> "$out_file"
                ;;
        esac
    done < "$req_file"
}

prepare_flattened_requirements() {
    local module="$1"
    local req_file out_file out_dir
    req_file="$(module_req_docker_file "$module")"
    # ★ 使用确定性文件名（按模块），避免 mktemp 随机后缀在 Docker 挂载时产生隐式竞态
    #    路径固定在 .build-cache/tmp/ 下，确保 Docker Daemon 可访问
    out_dir="$(yfeieye_build_cache_base "$EASYAIOT_ROOT")/tmp"
    mkdir -p "$out_dir"
    out_file="${out_dir}/requirements-${module}-docker-flat.txt"
    echo "--index-url https://pypi.tuna.tsinghua.edu.cn/simple" > "$out_file"
    if [ ! -f "$req_file" ]; then
        print_error "requirements-docker 不存在: $req_file"
        rm -f "$out_file"
        return 1
    fi
    append_requirements_file "$req_file" "$out_file"
    # ★ 写入完成后 sync 确保数据落盘，避免 Docker 挂载时看到空/不完整文件
    sync -f "$out_file" 2>/dev/null || true
    echo "$out_file"
}

# 将仅有 tar.gz 的 C 扩展预编译为 wheel，供 runtime 基础镜像离线/混合安装
build_required_sdist_wheels() {
    local wheels_dir="$1"
    local build_image spec pkg_name
    build_image="$(resolve_sdist_wheel_build_image)"

    for spec in "${SDIST_WHEEL_SPECS[@]}"; do
        pkg_name="${spec%%==*}"
        if compgen -G "${wheels_dir}/${pkg_name}-"*.whl >/dev/null 2>&1; then
            print_info "[sdist] 已有 ${pkg_name} wheel，跳过"
            continue
        fi
        print_info "[sdist] 使用 ${build_image} 编译 ${spec} wheel..."
        docker run --rm \
            -v "${wheels_dir}:/wheels" \
            "$build_image" \
            /bin/bash -lc "set -e
pip install -q --upgrade pip wheel setuptools -i https://pypi.tuna.tsinghua.edu.cn/simple
find_links=''
if compgen -G '/wheels/${pkg_name}-*.tar.gz' >/dev/null; then
  find_links='--find-links /wheels'
fi
pip wheel '${spec}' -w /wheels --no-deps \${find_links} -i https://pypi.tuna.tsinghua.edu.cn/simple"
        if ! compgen -G "${wheels_dir}/${pkg_name}-"*.whl >/dev/null 2>&1; then
            print_error "[sdist] 未生成 ${pkg_name} wheel"
            return 1
        fi
        print_success "[sdist] ${pkg_name} wheel 已写入 ${wheels_dir}"
    done
}

download_module_wheels() {
    local module="$1"
    local wheels_dir base_image flat_req status

    module="$(yfeieye_normalize_python_cache_module "$module")" || return 1
    wheels_dir="$(pip_wheels_build_context_dir_for "$YFEIEYE_ROOT" "$module")"
    base_image="$(module_base_image "$module")"
    flat_req="$(prepare_flattened_requirements "$module")"
    trap 'rm -f "$flat_req"' RETURN

    if [ "${CLEAR_PIP_WHEELS:-0}" = "1" ]; then
        print_info "[${module}] 清理旧 wheel（CLEAR_PIP_WHEELS=1）..."
        find "$wheels_dir" -maxdepth 1 -type f -delete 2>/dev/null || true
    fi

    print_info "[${module}] requirements-docker → ${flat_req}（$(wc -l < "$flat_req") 行）"
    print_info "[${module}] 目标: ${wheels_dir}"
    print_info "[${module}] 镜像: ${base_image}"

    # ★ 挂载前双重校验：确保 flat_req 是普通文件（非目录/空文件），防止 Docker 创建空目录
    if [ ! -f "$flat_req" ]; then
        print_error "[${module}] 扁平化 requirements 文件无效或为目录: ${flat_req}"
        return 1
    fi
    if [ ! -s "$flat_req" ]; then
        print_error "[${module}] 扁平化 requirements 文件为空: ${flat_req}"
        return 1
    fi
    print_info "[${module}] 挂载 requirements: ${flat_req} (size=$(stat -c%s "$flat_req" 2>/dev/null || wc -c < "$flat_req") bytes)"
    # ★ 容器内预检：确保 Docker 将源文件挂载为普通文件（非目录），避免 Docker 在源路径
    #    不可达/权限不足时自动创建目录的隐式行为
    docker run --rm \
        -v "${flat_req}:/tmp/requirements-docker.flat:ro" \
        --entrypoint /bin/bash \
        "$base_image" \
        -c 'test -f /tmp/requirements-docker.flat' 2>/dev/null || {
        print_error "[${module}] Docker 无法将 ${flat_req} 挂载为普通文件（可能是路径不可达或权限问题）"
        return 1
    }

    set +e
    docker run --rm \
        -e PYTHONUNBUFFERED=1 \
        -e PIP_ROOT_USER_ACTION=ignore \
        -v "${flat_req}:/tmp/requirements-docker.flat:ro" \
        -v "${wheels_dir}:/wheels" \
        "$base_image" \
        /bin/bash -lc 'pip install --upgrade pip -i https://pypi.tuna.tsinghua.edu.cn/simple && pip download -r /tmp/requirements-docker.flat -d /wheels --timeout 120 --retries 3 -i https://pypi.tuna.tsinghua.edu.cn/simple'
    status=$?
    set -e

    if [ $status -ne 0 ]; then
        if [ "${ALLOW_HOST_PIP_FALLBACK:-0}" != "1" ]; then
            print_error "[${module}] 容器内下载失败；回退: ALLOW_HOST_PIP_FALLBACK=1 $0 ${module}"
            return 1
        fi
        print_warning "[${module}] 容器内下载失败，使用本机 python3 回退..."
        export PIP_ROOT_USER_ACTION=ignore
        python3 -m pip download -r "$flat_req" -d "$wheels_dir" --timeout 120 --retries 3 \
            -i https://pypi.tuna.tsinghua.edu.cn/simple
    fi

    build_required_sdist_wheels "$wheels_dir"

    print_success "[${module}] wheel 已保存到 ${wheels_dir}"
}

if ! check_command docker; then
    print_error "未检测到 docker，请先安装 Docker"
    exit 1
fi

init_yfeieye_build_cache_dirs "$YFEIEYE_ROOT"

print_warning "依赖包体积较大，首次按模块下载可能需要 10–30 分钟/模块，请勿中断"

case "${TARGET_MODULE,,}" in
    all)
        for module in "${YFEIEYE_PYTHON_CACHE_MODULES[@]}"; do
            download_module_wheels "$module"
        done
        ;;
    ai|video)
        download_module_wheels "$TARGET_MODULE"
        ;;
    *)
        print_error "未知模块: ${TARGET_MODULE}（支持: all, ai, video）"
        exit 1
        ;;
esac

du -sh "$(yfeieye_build_cache_base "$YFEIEYE_ROOT")" 2>/dev/null || true
print_success "Python 离线 wheel 预下载完成（模块: ${TARGET_MODULE})"
