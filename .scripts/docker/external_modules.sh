#!/bin/bash
# 外部模块（已拆为独立仓库的周边模块：HARNESS / IDEA / TERMINAL / TWIN / SITE）
#
# 仓库地址与 ref 来自 deploy-profiles.json 的 externalModules（经生成的 deploy-profiles.generated.sh 读取）。
# 安装/构建入口在执行模块前调用 ensure_external_module_checkout：模块目录缺失时把外部仓库检出到
# <平台根>/<模块>，模块脚本对 ../.scripts/docker、../WEB/conf 的相对引用因此保持不变。
#
# 环境变量（<模块> 为大写目录名，如 HARNESS）：
#   YFEIEYE_<模块>_REPO          覆盖仓库地址（可填本地裸仓库路径）
#   YFEIEYE_<模块>_REF           覆盖分支 / tag（commit id 也可，此时做完整克隆）
#   YFEIEYE_<模块>_ARCHIVE       用 release 压缩包（本地 .tar.gz 路径或 http(s) URL）代替 git 克隆
#   YFEIEYE_EXTERNAL_MODULES_FETCH=0  不联网拉取；目录缺失即报错，需手工放置
#
# 本文件由 deploy_profile.sh 加载；单独 source 时需先 source deploy-profiles.generated.sh。

_external_module_log() {
    echo "[外部模块] $*" >&2
}

external_module_is_external() {
    [ -n "$(deploy_profile_manifest_external_module_repository "${1:-}")" ]
}

external_module_repository() {
    local module="${1:-}" var
    var="YFEIEYE_${module}_REPO"
    if [ -n "${!var:-}" ]; then
        echo "${!var}"
    else
        deploy_profile_manifest_external_module_repository "$module"
    fi
}

external_module_ref() {
    local module="${1:-}" var
    var="YFEIEYE_${module}_REF"
    if [ -n "${!var:-}" ]; then
        echo "${!var}"
    else
        deploy_profile_manifest_external_module_ref "$module"
    fi
}

# 只有会用到模块文件的命令才拉取；status / logs / stop / clean 等交给调用方原有的"目录不存在"分支。
_external_module_command_fetches() {
    case "${1:-install}" in
        install|update|upgrade|build|build-runtime|start|restart) return 0 ;;
        *) return 1 ;;
    esac
}

_external_module_extract_archive() {
    local archive="$1" target="$2" tmp_extract src entries
    tmp_extract="${target}.extract.$$"
    rm -rf "$tmp_extract"
    mkdir -p "$tmp_extract" || return 1
    case "$archive" in
        http://*|https://*)
            if ! curl -fsSL "$archive" | tar -xzf - -C "$tmp_extract"; then
                rm -rf "$tmp_extract"
                return 1
            fi
            ;;
        *)
            # 经 stdin 读入：GNU tar 会把 "E:/..." 这类带冒号的 -f 参数当成远程主机
            if [ ! -f "$archive" ] || ! tar -xzf - -C "$tmp_extract" < "$archive"; then
                rm -rf "$tmp_extract"
                return 1
            fi
            ;;
    esac
    # release 包通常带一层顶层目录（如 yfeieye-site-1.0.0/），剥掉它
    src="$tmp_extract"
    entries=("$tmp_extract"/* "$tmp_extract"/.[!.]*)
    local real=()
    local e
    for e in "${entries[@]}"; do
        [ -e "$e" ] && real+=("$e")
    done
    if [ "${#real[@]}" -eq 1 ] && [ -d "${real[0]}" ]; then
        src="${real[0]}"
    fi
    mv "$src" "$target" || { rm -rf "$tmp_extract"; return 1; }
    rm -rf "$tmp_extract"
}

_external_module_clone() {
    local repo="$1" ref="$2" target="$3"
    if git clone -q --depth 1 --branch "$ref" "$repo" "$target" 2>/dev/null; then
        return 0
    fi
    rm -rf "$target"
    # ref 是 commit id 时 --branch 不可用，退回完整克隆后检出
    if printf '%s' "$ref" | grep -Eq '^[0-9a-fA-F]{7,40}$'; then
        git clone -q "$repo" "$target" && git -C "$target" checkout -q --detach "$ref" && return 0
        rm -rf "$target"
    fi
    return 1
}

_external_module_update() {
    local module="$1" dir="$2" ref
    git -C "$dir" config --get yfeieye.externalModule >/dev/null 2>&1 || return 0
    ref="$(external_module_ref "$module")"
    if [ -n "$(git -C "$dir" status --porcelain 2>/dev/null)" ]; then
        _external_module_log "${module} 检出目录有本地改动，跳过更新: ${dir}"
        return 0
    fi
    if git -C "$dir" fetch -q --depth 1 origin "$ref" 2>/dev/null \
        && git -C "$dir" checkout -q --detach FETCH_HEAD 2>/dev/null; then
        _external_module_log "${module} 已更新到 ${ref}（$(git -C "$dir" rev-parse --short HEAD)）"
    else
        _external_module_log "${module} 更新失败（${ref}），继续使用当前检出"
    fi
    return 0
}

# ensure_external_module_checkout <模块> [命令] [平台根]
# 返回 0：模块不是外部模块、命令无需拉取，或目录已就绪；返回 1：需要拉取但失败。
ensure_external_module_checkout() {
    local module="${1:-}" command="${2:-install}" root
    root="$(_deploy_profile_repo_root "${3:-${PROJECT_ROOT:-}}")"
    external_module_is_external "$module" || return 0
    _external_module_command_fetches "$command" || return 0

    local dir="${root}/${module}"
    if [ -d "$dir" ]; then
        case "$command" in
            update|upgrade) _external_module_update "$module" "$dir" ;;
        esac
        return 0
    fi

    local repo ref archive_var archive tmp
    repo="$(external_module_repository "$module")"
    ref="$(external_module_ref "$module")"
    archive_var="YFEIEYE_${module}_ARCHIVE"
    archive="${!archive_var:-}"
    tmp="${dir}.fetch.$$"
    rm -rf "$tmp"

    if [ -n "$archive" ]; then
        _external_module_log "${module} 已拆为独立仓库，从压缩包展开: ${archive}"
        if ! _external_module_extract_archive "$archive" "$tmp"; then
            rm -rf "$tmp"
            _external_module_log "${module} 压缩包展开失败: ${archive}"
            return 1
        fi
    else
        if [ "${YFEIEYE_EXTERNAL_MODULES_FETCH:-1}" = "0" ]; then
            _external_module_log "${module} 已拆为独立仓库，且已禁止自动拉取（YFEIEYE_EXTERNAL_MODULES_FETCH=0）"
            _external_module_log "请把 ${repo}（${ref}）检出到 ${dir}，或设置 ${archive_var}"
            return 1
        fi
        if ! command -v git >/dev/null 2>&1; then
            _external_module_log "${module} 需要 git 才能拉取 ${repo}；也可设置 ${archive_var} 使用压缩包"
            return 1
        fi
        _external_module_log "${module} 已拆为独立仓库，拉取 ${repo}（${ref}）→ ${dir}"
        if ! _external_module_clone "$repo" "$ref" "$tmp"; then
            rm -rf "$tmp"
            _external_module_log "${module} 拉取失败；可用 YFEIEYE_${module}_REPO / YFEIEYE_${module}_REF 指定其他地址，或 ${archive_var} 使用压缩包"
            return 1
        fi
        git -C "$tmp" config yfeieye.externalModule "$module"
    fi

    if ! mv "$tmp" "$dir"; then
        rm -rf "$tmp"
        _external_module_log "${module} 无法放置到 ${dir}"
        return 1
    fi
    return 0
}
