package com.basiclab.iot.system.service.supervision;

@FunctionalInterface
public interface ReviewPlaybackUrlSigner {

    String signPlaybackUrl(String rawUrl, String cameraId);

    static ReviewPlaybackUrlSigner passthrough() {
        return (rawUrl, cameraId) -> rawUrl;
    }
}
