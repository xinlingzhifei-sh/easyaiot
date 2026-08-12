package com.basiclab.iot.system.job.supervision;

import com.basiclab.iot.common.core.handler.JobHandler;
import com.basiclab.iot.common.core.job.TenantJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewQuery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticWorkerCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticWorkerRun;
import org.springframework.stereotype.Component;

@Component("supervisionAlertReviewSemanticIndexJob")
public class SupervisionAlertReviewSemanticIndexJob implements JobHandler {

    private final SupervisionAlertReviewService supervisionAlertReviewService;

    public SupervisionAlertReviewSemanticIndexJob(SupervisionAlertReviewService supervisionAlertReviewService) {
        this.supervisionAlertReviewService = supervisionAlertReviewService;
    }

    @Override
    @SupervisionAlertReviewRuntimeJob("supervisionAlertReviewSemanticIndexJob")
    @TenantJob
    public String execute(String param) {
        ReviewSemanticWorkerRun result = supervisionAlertReviewService.processSemanticIndexQueue(
                new ReviewSemanticWorkerCommand(
                        new ReviewQuery(null, null, null, null),
                        parseLimit(param),
                        null
                )
        );
        return "status=" + result.status()
                + ", scanned=" + result.scannedCount()
                + ", processed=" + result.processedCount()
                + ", failed=" + result.failedCount()
                + ", remaining=" + result.remainingBacklogCount()
                + ", progress=" + result.progressRate();
    }

    private static Integer parseLimit(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(param.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

}
