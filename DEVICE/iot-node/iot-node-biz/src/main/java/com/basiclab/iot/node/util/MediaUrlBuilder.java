package com.basiclab.iot.node.util;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import lombok.Data;

import java.util.Locale;
import java.util.Map;

/**
 * 根据媒体节点 tags 生成设备推流/播放 URL。
 */
public final class MediaUrlBuilder {

    private MediaUrlBuilder() {
    }

    @Data
    public static class StreamUrls {
        private String rtmpStream;
        private String httpStream;
        private String aiRtmpStream;
        private String aiHttpStream;
        private String zlmHost;
        private Integer zlmHttpPort;
        private Integer zlmRtmpPort;
    }

    public static StreamUrls build(ComputeNodeDO srsLiveNode, ComputeNodeDO srsAiNode,
                                   ComputeNodeDO zlmNode, String deviceId, String httpPlayHost) {
        StreamUrls urls = new StreamUrls();
        if (srsLiveNode != null) {
            int rtmpPort = tagInt(srsLiveNode, "srs_rtmp_port", 1935);
            int httpPort = tagInt(srsLiveNode, "srs_http_port", 8080);
            urls.setRtmpStream(String.format("rtmp://%s:%d/live/%s", srsLiveNode.getHost(), rtmpPort, deviceId));
            urls.setHttpStream(buildHttpFlvUrl(httpPlayHost, srsLiveNode.getHost(), httpPort,
                    String.format("live/%s.flv", deviceId)));
        }
        if (srsAiNode != null) {
            int rtmpPort = tagInt(srsAiNode, "srs_rtmp_port", 1935);
            int httpPort = tagInt(srsAiNode, "srs_http_port", 8080);
            urls.setAiRtmpStream(String.format("rtmp://%s:%d/ai/%s", srsAiNode.getHost(), rtmpPort, deviceId));
            urls.setAiHttpStream(buildHttpFlvUrl(httpPlayHost, srsAiNode.getHost(), httpPort,
                    String.format("ai/%s.flv", deviceId)));
        }
        if (zlmNode != null) {
            urls.setZlmHost(zlmNode.getHost());
            urls.setZlmHttpPort(tagInt(zlmNode, "zlm_http_port", 6080));
            urls.setZlmRtmpPort(tagInt(zlmNode, "zlm_rtmp_port", 10935));
        }
        return urls;
    }

    private static String buildHttpFlvUrl(String httpPlayHost, String nodeHost, int httpPort, String path) {
        String publicOrigin = StrUtil.trimToEmpty(httpPlayHost);
        String lowerOrigin = publicOrigin.toLowerCase(Locale.ROOT);
        if (lowerOrigin.startsWith("http://") || lowerOrigin.startsWith("https://")) {
            return StrUtil.removeSuffix(publicOrigin, "/") + "/" + path;
        }

        String playHost = StrUtil.blankToDefault(publicOrigin, nodeHost);
        return String.format("http://%s:%d/%s", playHost, httpPort, path);
    }

    private static int tagInt(ComputeNodeDO node, String key, int defaultValue) {
        Map<String, String> tags = node.getTags();
        if (tags == null || !tags.containsKey(key)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(tags.get(key).trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

}
