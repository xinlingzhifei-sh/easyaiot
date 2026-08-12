package com.basiclab.iot.system.job.supervision;

import com.basiclab.iot.common.core.handler.JobHandler;
import com.basiclab.iot.common.core.job.TenantJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportWorkerCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportWorkerRun;
import org.springframework.stereotype.Component;

@Component("supervisionAlertReviewEvidenceExportWorkerJob")
public class SupervisionAlertReviewEvidenceExportWorkerJob implements JobHandler {

    private final SupervisionAlertReviewService supervisionAlertReviewService;

    public SupervisionAlertReviewEvidenceExportWorkerJob(SupervisionAlertReviewService supervisionAlertReviewService) {
        this.supervisionAlertReviewService = supervisionAlertReviewService;
    }

    @Override
    @SupervisionAlertReviewRuntimeJob("supervisionAlertReviewEvidenceExportWorkerJob")
    @TenantJob
    public String execute(String param) {
        ReviewEvidenceExportWorkerRun result = supervisionAlertReviewService.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(parseLimit(param), null)
        );
        return "status=" + result.status()
                + ", scanned=" + result.scannedCount()
                + ", processed=" + result.processedCount()
                + ", failed=" + result.failedCount()
                + ", deferred=" + result.deferredCount()
                + ", remaining=" + result.remainingBacklogCount();
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
