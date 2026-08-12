package com.basiclab.iot.node.util;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaUrlBuilderTest {

    @Test
    void buildsPublicHttpsHttpFlvUrlsWhenPlayHostIsOrigin() {
        ComputeNodeDO srsNode = new ComputeNodeDO();
        srsNode.setHost("192.168.0.88");
        srsNode.setTags(Map.of(
                "srs_rtmp_port", "1935",
                "srs_http_port", "8080"));

        MediaUrlBuilder.StreamUrls urls = MediaUrlBuilder.build(
                srsNode, srsNode, null, "gb28181_demo", "https://eye.yfeiai.com");

        assertEquals("rtmp://192.168.0.88:1935/live/gb28181_demo", urls.getRtmpStream());
        assertEquals("https://eye.yfeiai.com/live/gb28181_demo.flv", urls.getHttpStream());
        assertEquals("rtmp://192.168.0.88:1935/ai/gb28181_demo", urls.getAiRtmpStream());
        assertEquals("https://eye.yfeiai.com/ai/gb28181_demo.flv", urls.getAiHttpStream());
    }
}
