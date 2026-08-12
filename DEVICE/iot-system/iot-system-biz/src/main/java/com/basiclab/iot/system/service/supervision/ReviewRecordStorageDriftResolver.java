package com.basiclab.iot.system.service.supervision;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ReviewRecordStorageDriftResolver {

    RecordStorageDriftReport inspect(RecordStorageDriftRequest request);

    static ReviewRecordStorageDriftResolver unavailable() {
        return request -> new RecordStorageDriftReport(
                request == null ? null : request.deviceId(),
                request == null ? null : request.cameraId(),
                null,
                true,
                0,
                0,
                Map.of(),
                List.of(),
                "not_inspected",
                LocalDateTime.now()
        );
    }

    record RecordStorageDriftRequest(String deviceId,
                                     String cameraId,
                                     Integer retentionHours) {
    }

    record RecordStorageDriftReport(String deviceId,
                                    String cameraId,
                                    Long spaceId,
                                    boolean healthy,
                                    int recordCount,
                                    int issueCount,
                                    Map<String, Integer> issueReasons,
                                    List<String> standardReasonKeys,
                                    String message,
                                    LocalDateTime checkedAt) {
    }
}
