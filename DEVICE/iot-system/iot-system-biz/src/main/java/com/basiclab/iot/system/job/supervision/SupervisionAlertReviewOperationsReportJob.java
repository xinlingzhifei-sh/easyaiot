package com.basiclab.iot.system.job.supervision;

import com.basiclab.iot.common.core.handler.JobHandler;
import com.basiclab.iot.common.core.job.TenantJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewOperationsReportDelivery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewOperationsReport;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewQuery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReportCommand;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component("supervisionAlertReviewOperationsReportJob")
public class SupervisionAlertReviewOperationsReportJob implements JobHandler {

    private static final String REPORT_TYPE_DAILY = "daily";
    private static final String REPORT_TYPE_SHIFT = "shift";
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int SHIFT_HOURS = 8;

    private final SupervisionAlertReviewService supervisionAlertReviewService;

    public SupervisionAlertReviewOperationsReportJob(SupervisionAlertReviewService supervisionAlertReviewService) {
        this.supervisionAlertReviewService = supervisionAlertReviewService;
    }

    @Override
    @SupervisionAlertReviewRuntimeJob("supervisionAlertReviewOperationsReportJob")
    @TenantJob
    public String execute(String param) {
        String reportType = parseReportType(param);
        LocalDateTime periodEnd = previousCompletePeriodEnd(reportType, LocalDateTime.now(REPORT_ZONE));
        LocalDateTime periodStart = REPORT_TYPE_DAILY.equals(reportType)
                ? periodEnd.minusDays(1)
                : periodEnd.minusHours(SHIFT_HOURS);
        ReviewOperationsReportDelivery delivery = supervisionAlertReviewService.scheduleReviewReportDelivery(
                new ReviewReportCommand(reportType, new ReviewQuery(null, null, periodStart, periodEnd),
                        periodStart, periodEnd, null)
        );
        ReviewOperationsReport report = delivery.report();
        Object deliveryStatus = report.deliveryPlan() == null ? null : report.deliveryPlan().get("deliveryStatus");
        Object acknowledgement = report.acknowledgement() == null ? null : report.acknowledgement().get("status");
        return "reportType=" + report.reportType()
                + ", scheduled=true"
                + ", items=" + report.reviewItemIds().size()
                + ", evidenceGaps=" + report.evidenceGaps().size()
                + ", recommendedActions=" + report.recommendedActions().size()
                + ", deliveryStatus=" + deliveryStatus
                + ", acknowledgement=" + acknowledgement
                + ", deliveryOutbox=" + delivery.outboxEventCount();
    }

    private static String parseReportType(String param) {
        if (param == null || param.isBlank()) {
            return REPORT_TYPE_SHIFT;
        }
        String normalized = param.trim().toLowerCase();
        return REPORT_TYPE_DAILY.equals(normalized) ? REPORT_TYPE_DAILY : REPORT_TYPE_SHIFT;
    }

    private static LocalDateTime previousCompletePeriodEnd(String reportType, LocalDateTime now) {
        if (REPORT_TYPE_DAILY.equals(reportType)) {
            return now.toLocalDate().atStartOfDay();
        }
        int boundaryHour = (now.getHour() / SHIFT_HOURS) * SHIFT_HOURS;
        return now.toLocalDate().atTime(boundaryHour, 0);
    }

}
