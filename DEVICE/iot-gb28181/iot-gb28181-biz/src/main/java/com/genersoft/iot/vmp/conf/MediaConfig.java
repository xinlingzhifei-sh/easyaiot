package com.genersoft.iot.vmp.conf;

import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.utils.DateUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.util.ObjectUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.regex.Pattern;

@Slf4j
@Configuration("mediaConfig")
@Order(0)
@Data
public class MediaConfig{

    // 修改必须配置，不再支持自动获取
    @Value("${media.id}")
    private String id;

    @Value("${media.ip}")
    private String ip;

    @Value("${media.wan_ip:}")
    private String wanIp;

    @Value("${media.hook-ip:}")
    private String hookIp;

    /** 缓存自动探测到的宿主机 IP，保证 sdp/stream/hook 使用同一地址 */
    private volatile String cachedHostIp;

    @Value("${sip.domain}")
    private String sipDomain;

    @Value("${media.sdp-ip:${media.wan_ip:}}")
    private String sdpIp;

    @Value("${media.stream-ip:${media.wan_ip:}}")
    private String streamIp;

    @Value("${media.http-port:0}")
    private Integer httpPort;

    @Value("${media.auto-config:true}")
    private boolean autoConfig = true;

    @Value("${media.secret}")
    private String secret;

    @Value("${media.rtp.enable}")
    private boolean rtpEnable;

    @Value("${media.rtp.public-port:0}")
    private int rtpPublicPort;

    @Value("${media.rtp.port-range}")
    private String rtpPortRange;

    @Value("${media.rtp.send-port-range}")
    private String rtpSendPortRange;

    @Value("${media.record-assist-port:0}")
    private Integer recordAssistPort = 0;

    @Value("${media.record-day:7}")
    private Integer recordDay;

    @Value("${media.record-path:}")
    private String recordPath;

    @Value("${media.type:zlm}")
    private String type;


    public String getSdpIp() {
        if (ObjectUtils.isEmpty(sdpIp)) {
            return detectHostIp("sdp-ip");
        }
        if (isValidIPAddress(sdpIp)) {
            return resolveHostIp(sdpIp, "sdp-ip");
        }
        // 按照域名解析
        try {
            return InetAddress.getByName(sdpIp).getHostAddress();
        } catch (UnknownHostException e) {
            log.error("[获取SDP IP]: 域名解析失败");
            return detectHostIp("sdp-ip");
        }
    }

    public String getStreamIp() {
        return resolveHostIp(streamIp, "stream-ip");
    }

    /**
     * 获取 hook IP，如果配置为空或为默认值 127.0.0.1，则自动获取宿主机 IP
     */
    public String getHookIp() {
        return resolveHostIp(hookIp, "hook-ip");
    }

    /**
     * 若配置了有效宿主机 IP 则直接使用，否则自动探测
     */
    private String resolveHostIp(String configuredIp, String label) {
        if (!ObjectUtils.isEmpty(configuredIp) && !configuredIp.equals("127.0.0.1")) {
            return configuredIp;
        }
        return detectHostIp(label);
    }

    /**
     * 将自动解析的网络 IP 写入媒体节点（用于刷新 DB/Redis 中残留的错误 hook-ip）
     */
    public void applyNetworkIpTo(MediaServer mediaServer) {
        if (mediaServer == null) {
            return;
        }
        mediaServer.setHookIp(getHookIp());
        mediaServer.setSdpIp(getSdpIp());
        mediaServer.setStreamIp(getStreamIp());
    }

    public int resolveRtpPublicPort(MediaServer mediaServer) {
        if (rtpPublicPort > 0) {
            return rtpPublicPort;
        }
        return mediaServer.getRtpProxyPort();
    }

    /**
     * 自动获取宿主机 IP（排除 127.0.0.1、docker/br 等虚拟网卡和链路本地地址）
     */
    private String detectHostIp(String label) {
        if (cachedHostIp != null) {
            return cachedHostIp;
        }
        try {
            Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
            while (nifs.hasMoreElements()) {
                NetworkInterface nif = nifs.nextElement();
                if (!nif.isUp() || nif.isLoopback() || isVirtualInterface(nif.getName())) {
                    continue;
                }

                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        String hostAddress = addr.getHostAddress();
                        if (!hostAddress.equals("127.0.0.1") && !hostAddress.startsWith("169.254.")) {
                            cachedHostIp = hostAddress;
                            log.info("[自动获取 {}] 检测到宿主机 IP: {}", label, hostAddress);
                            return hostAddress;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[自动获取 {}] 获取宿主机 IP 失败，使用默认值 127.0.0.1", label, e);
        }

        log.warn("[自动获取 {}] 未能自动获取宿主机 IP，使用默认值 127.0.0.1", label);
        return "127.0.0.1";
    }

    private boolean isVirtualInterface(String name) {
        return name.startsWith("docker")
                || name.startsWith("br-")
                || name.startsWith("veth")
                || name.startsWith("virbr");
    }

    public MediaServer buildMediaSer(){
        MediaServer mediaServer = new MediaServer();
        mediaServer.setId(id);
        mediaServer.setIp(ip);
        mediaServer.setDefaultServer(true);
        mediaServer.setHookIp(getHookIp());
        mediaServer.setSdpIp(getSdpIp());
        mediaServer.setStreamIp(getStreamIp());
        mediaServer.setHttpPort(httpPort);
        mediaServer.setAutoConfig(autoConfig);
        mediaServer.setSecret(secret);
        mediaServer.setRtpEnable(rtpEnable);
        mediaServer.setRtpPortRange(rtpPortRange);
        mediaServer.setSendRtpPortRange(rtpSendPortRange);
        mediaServer.setRecordAssistPort(recordAssistPort);
        mediaServer.setHookAliveInterval(10f);
        mediaServer.setRecordDay(recordDay);
        mediaServer.setStatus(false);
        mediaServer.setType(type);
        if (recordPath != null) {
            mediaServer.setRecordPath(recordPath);
        }
        mediaServer.setCreateTime(DateUtil.getNow());
        mediaServer.setUpdateTime(DateUtil.getNow());

        return mediaServer;
    }

    private boolean isValidIPAddress(String ipAddress) {
        if ((ipAddress != null) && (!ipAddress.isEmpty())) {
            return Pattern.matches("^([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}$", ipAddress);
        }
        return false;
    }
}
