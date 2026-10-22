package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCameraTopology;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCameraTopologyResolver;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConfigurationProperties(prefix = "yfeieye.review.camera-topology")
public class ConfiguredReviewCameraTopologyResolver implements ReviewCameraTopologyResolver {

    private Map<String, CameraTopologyProperties> cameras = new LinkedHashMap<>();

    @Override
    public ReviewCameraTopology resolveCameraTopology(String cameraId) {
        if (cameraId == null || cameraId.trim().isEmpty()) {
            return ReviewCameraTopology.empty();
        }
        CameraTopologyProperties topology = cameras.get(cameraId.trim());
        if (topology == null) {
            return ReviewCameraTopology.empty();
        }
        return new ReviewCameraTopology(topology.regulatoryArea, topology.adjacentCameraIds);
    }

    public Map<String, CameraTopologyProperties> getCameras() {
        return cameras;
    }

    public void setCameras(Map<String, CameraTopologyProperties> cameras) {
        this.cameras = cameras == null ? new LinkedHashMap<>() : cameras;
    }

    public static class CameraTopologyProperties {
        private String regulatoryArea;
        private List<String> adjacentCameraIds = List.of();

        public String getRegulatoryArea() {
            return regulatoryArea;
        }

        public void setRegulatoryArea(String regulatoryArea) {
            this.regulatoryArea = regulatoryArea;
        }

        public List<String> getAdjacentCameraIds() {
            return adjacentCameraIds;
        }

        public void setAdjacentCameraIds(List<String> adjacentCameraIds) {
            this.adjacentCameraIds = adjacentCameraIds == null ? List.of() : adjacentCameraIds;
        }
    }
}