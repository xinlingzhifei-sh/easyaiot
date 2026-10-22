# shellcheck shell=bash
# daemon.json 变更后让配置生效，但不在共享主机上误伤其它项目的容器。
#
# 背景：未启用 live-restore 时，systemctl restart docker 会停掉宿主机上所有容器。
# 安装脚本经常只是补了 registry-mirrors / runtimes，这类配置 SIGHUP 热加载即可生效，
# 没必要为此重启守护进程。
#
# 策略（apply_docker_daemon_config）：
#   - Docker 未运行 / 无 systemd：不做处理（下次启动自然生效）
#   - 显式 EASYAIOT_ALLOW_DOCKER_RESTART=1、宿主机没有运行中的容器、或已启用 live-restore：重启
#   - 否则只热加载（systemctl reload docker），并提示 dns 等不可热加载项需在维护窗口手动重启
#
# 调用方可提供 print_info / print_success / print_warning；未提供时本文件给出简易回退。

if ! declare -f print_info >/dev/null 2>&1; then
    print_info() { echo "[INFO] $1"; }
fi
if ! declare -f print_success >/dev/null 2>&1; then
    print_success() { echo "[OK] $1"; }
fi
if ! declare -f print_warning >/dev/null 2>&1; then
    print_warning() { echo "[WARN] $1"; }
fi

# 非 root 且有 sudo 时经 sudo 执行
_docker_guard_run() {
    if [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1; then
        sudo "$@"
    else
        "$@"
    fi
}

# 查询失败时输出空串（调用方按"有容器"处理，不冒险重启）
docker_running_container_count() {
    local ids
    ids="$(_docker_guard_run docker ps -q 2>/dev/null)" || return 0
    printf '%s\n' "$ids" | grep -c . || true
}

docker_live_restore_enabled() {
    [ "$(_docker_guard_run docker info --format '{{.LiveRestoreEnabled}}' 2>/dev/null)" = "true" ]
}

# 用法: apply_docker_daemon_config [变更说明]
# 返回 0 表示已重启或已热加载（或无需处理）；1 表示未能生效，已提示手动操作
apply_docker_daemon_config() {
    local reason="${1:-daemon.json 已变更}"
    if ! command -v systemctl >/dev/null 2>&1; then
        print_warning "未找到 systemctl，跳过重启 Docker（容器或非 systemd 环境）"
        return 0
    fi
    if ! _docker_guard_run systemctl is-active --quiet docker 2>/dev/null; then
        return 0
    fi
    _docker_guard_run systemctl daemon-reload 2>/dev/null || true

    local running
    running="$(docker_running_container_count)"
    if [ "${EASYAIOT_ALLOW_DOCKER_RESTART:-0}" = "1" ] || [ "$running" = "0" ] || docker_live_restore_enabled; then
        print_info "正在重启 Docker 服务以使配置生效（${reason}）..."
        if _docker_guard_run systemctl restart docker 2>/dev/null; then
            print_success "Docker 服务已重启"
            return 0
        fi
        print_warning "Docker 重启失败，请稍后手动执行: sudo systemctl restart docker"
        return 1
    fi

    if _docker_guard_run systemctl reload docker 2>/dev/null; then
        print_warning "宿主机有 ${running:-未知数量的} 个运行中的容器且未启用 live-restore，未重启 Docker，已热加载配置（${reason}）"
        print_warning "registry-mirrors / runtimes / default-runtime 已生效；dns 等需重启的配置请在维护窗口执行 sudo systemctl restart docker（或设 EASYAIOT_ALLOW_DOCKER_RESTART=1 重跑）"
        return 0
    fi
    print_warning "热加载 Docker 配置失败，且宿主机有 ${running:-未知数量的} 个运行中的容器，未自动重启；请在维护窗口执行: sudo systemctl restart docker"
    return 1
}
