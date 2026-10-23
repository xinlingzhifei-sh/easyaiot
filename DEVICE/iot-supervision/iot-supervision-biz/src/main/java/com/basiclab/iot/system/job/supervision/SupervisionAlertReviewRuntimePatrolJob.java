package com.basiclab.iot.system.job.supervision;

import com.basiclab.iot.common.core.handler.JobHandler;
import com.basiclab.iot.common.core.job.TenantJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewQuery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimePatrolCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimePatrolResult;
import org.springframework.stereotype.Component;

@Component("supervisionAlertReviewRuntimePatrolJob")
public class SupervisionAlertReviewRuntimePatrolJob implements JobHandler {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final SupervisionAlertReviewService supervisionAlertReviewService;

    public SupervisionAlertReviewRuntimePatrolJob(SupervisionAlertReviewService supervisionAlertReviewService) {
        this.supervisionAlertReviewService = supervisionAlertReviewService;
    }

    @Override
    @SupervisionAlertReviewRuntimeJob("supervisionAlertReviewRuntimePatrolJob")
    @TenantJob
    public String execute(String param) {
        ReviewRuntimePatrolResult result = supervisionAlertReviewService.runRuntimePatrol(
                new ReviewRuntimePatrolCommand(
                        new ReviewQuery(null, null, null, null),
                        null,
                        true,
                        DEFAULT_MAX_ATTEMPTS,
                        true
                )
        );
        Object outboxEventCount = result.metadata() == null ? 0 : result.metadata().get("outboxEventCount");
        return "status=" + result.status()
                + ", scheduled=true"
                + ", lockAcquired=" + result.lockAcquired()
                + ", alerts=" + result.alerts().size()
                + ", outbox=" + outboxEventCount;
    }

}
