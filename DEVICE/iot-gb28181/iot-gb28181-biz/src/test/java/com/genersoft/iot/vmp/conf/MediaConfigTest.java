package com.genersoft.iot.vmp.conf;

import com.genersoft.iot.vmp.media.bean.MediaServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaConfigTest {

    @Test
    void configuredPublicRtpPortOverridesMediaServerLocalPort() {
        MediaConfig mediaConfig = new MediaConfig();
        mediaConfig.setRtpPublicPort(443);
        MediaServer mediaServer = new MediaServer();
        mediaServer.setRtpProxyPort(10000);

        assertEquals(443, mediaConfig.resolveRtpPublicPort(mediaServer));
    }

    @Test
    void localRtpProxyPortRemainsDefaultWithoutPublicOverride() {
        MediaConfig mediaConfig = new MediaConfig();
        mediaConfig.setRtpPublicPort(0);
        MediaServer mediaServer = new MediaServer();
        mediaServer.setRtpProxyPort(10000);

        assertEquals(10000, mediaConfig.resolveRtpPublicPort(mediaServer));
    }
}
