package com.basiclab.iot.system.service.supervision;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "yfeieye.review.runtime-outbox.notify")
public class ReviewRuntimeOutboxNotifyProperties {

    public static final String DEFAULT_RUNTIME_ALERT_TEMPLATE_CODE = "YFEIEYE_REVIEW_RUNTIME_ALERT";
    public static final String DEFAULT_OPERATIONS_REPORT_TEMPLATE_CODE = "YFEIEYE_REVIEW_OPERATIONS_REPORT";

    private boolean enabled;
    private String tenantAdminUserRoutes = "";
    private String runtimeAlertTemplateCode = DEFAULT_RUNTIME_ALERT_TEMPLATE_CODE;
    private String operationsReportTemplateCode = DEFAULT_OPERATIONS_REPORT_TEMPLATE_CODE;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantAdminUserRoutes() {
        return tenantAdminUserRoutes;
    }

    public void setTenantAdminUserRoutes(String tenantAdminUserRoutes) {
        this.tenantAdminUserRoutes = tenantAdminUserRoutes == null ? "" : tenantAdminUserRoutes;
    }

    public String getRuntimeAlertTemplateCode() {
        return runtimeAlertTemplateCode;
    }

    public void setRuntimeAlertTemplateCode(String runtimeAlertTemplateCode) {
        this.runtimeAlertTemplateCode = runtimeAlertTemplateCode;
    }

    public String getOperationsReportTemplateCode() {
        return operationsReportTemplateCode;
    }

    public void setOperationsReportTemplateCode(String operationsReportTemplateCode) {
        this.operationsReportTemplateCode = operationsReportTemplateCode;
    }
}
