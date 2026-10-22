# shellcheck shell=bash
# 宿主机防火墙：让 bridge 网络里的容器能访问 host 网络模式的服务。
#
# 背景：网关、VIDEO、AI、SRS、go2rtc 等以 host 网络运行，WEB 容器在 yfeieye-network
# 里经 host-gateway 访问它们，流量走宿主机 INPUT 链。Ubuntu 开启 ufw（默认拒绝入站）
# 时这些请求被丢弃，WEB 代理全部超时。这里只对该 Docker 网络的子网放行，不对公网开放。
#
# EASYAIOT_SKIP_FIREWALL=1 跳过（自行管理防火墙时使用）。
# 调用方可提供 print_info / print_warning；未提供时本文件给出简易回退。

if ! declare -f print_info >/dev/null 2>&1; then
    print_info() { echo "[INFO] $1"; }
fi
if ! declare -f print_warning >/dev/null 2>&1; then
    print_warning() { echo "[WARN] $1"; }
fi

# WEB nginx 经 host-gateway 反代的 host 网络端口：
# 网关 48080、iot-system 48099（mini）、VIDEO 6000、AI 5000、SRS 1985/8080、go2rtc 1984
YFEIEYE_HOST_SERVICE_PORTS="48080,48099,6000,5000,1985,8080,1984"

# 用法: allow_docker_network_to_host_ports <docker 网络名> [逗号分隔端口]
allow_docker_network_to_host_ports() {
    local network="$1" ports="${2:-$YFEIEYE_HOST_SERVICE_PORTS}" subnets subnet
    [ "${EASYAIOT_SKIP_FIREWALL:-0}" = "1" ] && return 0
    command -v ufw >/dev/null 2>&1 || return 0
    if [ "$(id -u)" -ne 0 ]; then
        print_warning "非 root，未检查 ufw；若容器访问宿主机服务超时，请执行: sudo ufw allow proto tcp from <${network} 子网> to any port ${ports}"
        return 0
    fi
    ufw status 2>/dev/null | grep -q '^Status: active' || return 0

    subnets="$(docker network inspect "$network" --format '{{range .IPAM.Config}}{{.Subnet}} {{end}}' 2>/dev/null)"
    if [ -z "${subnets// /}" ]; then
        print_warning "未取到 Docker 网络 ${network} 的子网，跳过 ufw 放行"
        return 0
    fi
    for subnet in $subnets; do
        # ufw 规则按地址族区分，这里只处理 IPv4 子网
        case "$subnet" in *:*) continue ;; esac
        if ufw allow proto tcp from "$subnet" to any port "$ports" comment "yfeieye: ${network} to host services" >/dev/null 2>&1; then
            print_info "ufw 已放行 ${subnet} -> 宿主机 TCP ${ports}（仅该 Docker 网络，不对公网开放）"
        else
            print_warning "ufw 放行 ${subnet} -> ${ports} 失败，WEB 访问宿主机服务可能超时"
        fi
    done
}
