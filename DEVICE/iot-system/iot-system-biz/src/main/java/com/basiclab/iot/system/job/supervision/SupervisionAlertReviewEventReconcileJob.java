package com.basiclab.iot.system.job.supervision;

import com.basiclab.iot.common.core.handler.JobHandler;
import com.basiclab.iot.common.core.job.TenantJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEventReconciliationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEventReconciliationResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewQuery;
import org.springframework.stereotype.Component;

@Component("supervisionAlertReviewEventReconcileJob")
public class SupervisionAlertReviewEventReconcileJob implements JobHandler {

    private final SupervisionAlertReviewService supervisionAlertReviewService;

    public SupervisionAlertReviewEventReconcileJob(SupervisionAlertReviewService supervisionAlertReviewService) {
        this.supervisionAlertReviewService = supervisionAlertReviewService;
    }

    @Override
    @SupervisionAlertReviewRuntimeJob("supervisionAlertReviewEventReconcileJob")
    @TenantJob
    public String execute(String param) {
        ReviewEventReconciliationResult result = supervisionAlertReviewService.reconcileEventProjections(
                new ReviewEventReconciliationCommand(new ReviewQuery(null, null, null, null), null)
        );
        return "scanned=" + result.scannedCount()
                + ", reconciled=" + result.reconciledCount()
                + ", missingProjection=" + result.missingProjectionCount()
                + ", conflict=" + result.conflictCount();
    }

}
