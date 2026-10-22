package com.basiclab.iot.system.job.supervision;

import com.basiclab.iot.common.core.handler.JobHandler;
import com.basiclab.iot.common.core.job.TenantJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxPublishCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxPublishResult;
import org.springframework.stereotype.Component;

@Component("supervisionAlertReviewRuntimeOutboxJob")
public class SupervisionAlertReviewRuntimeOutboxJob implements JobHandler {

    private final SupervisionAlertReviewService supervisionAlertReviewService;

    public SupervisionAlertReviewRuntimeOutboxJob(SupervisionAlertReviewService supervisionAlertReviewService) {
        this.supervisionAlertReviewService = supervisionAlertReviewService;
    }

    @Override
    @SupervisionAlertReviewRuntimeJob("supervisionAlertReviewRuntimeOutboxJob")
    @TenantJob
    public String execute(String param) {
        ReviewRuntimeOutboxPublishResult result = supervisionAlertReviewService.publishRuntimeOutbox(
                new ReviewRuntimeOutboxPublishCommand(parseLimit(param), null)
        );
        return "scanned=" + result.scannedCount()
                + ", published=" + result.publishedCount()
                + ", failed=" + result.failedCount();
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
