package com.basiclab.iot.system.service.supervision;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@ConfigurationProperties(prefix = "yfeieye.review.ai-summary.redaction")
public class ReviewAiSummaryRedactionPolicy {

    private static final String DEFAULT_POLICY_VERSION = "review-ai-redaction-policy-v1";
    private static final String DEFAULT_REDACTED_VALUE = "[REDACTED]";
    private static final List<String> DEFAULT_SENSITIVE_KEYS = List.of(
            "personname",
            "realname",
            "username",
            "phone",
            "phonenumber",
            "mobile",
            "mobilenumber",
            "tel",
            "telephone",
            "idcard",
            "identityno",
            "idnumber",
            "residentid",
            "citizenid"
    );
    private static final List<String> DEFAULT_SENSITIVE_VALUE_PATTERNS = List.of(
            "(?s).*(?<!\\d)1[3-9]\\d{9}(?!\\d).*",
            "(?s).*(?<!\\d)\\d{17}[0-9Xx](?![0-9Xx]).*"
    );

    private String policyVersion = DEFAULT_POLICY_VERSION;
    private String redactedValue = DEFAULT_REDACTED_VALUE;
    private List<String> sensitiveKeys = DEFAULT_SENSITIVE_KEYS;
    private List<String> sensitiveValuePatterns = DEFAULT_SENSITIVE_VALUE_PATTERNS;

    public boolean shouldRedact(String key, Object value) {
        if (sensitiveKeys.stream().map(ReviewAiSummaryRedactionPolicy::normalizeKey)
                .anyMatch(normalizeKey(key)::equals)) {
            return true;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value);
        if (text.trim().isEmpty()) {
            return false;
        }
        return sensitiveValuePatterns.stream()
                .anyMatch(pattern -> Pattern.compile(pattern).matcher(text).matches());
    }

    private static String normalizeKey(String key) {
        return key == null
                ? ""
                : key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion == null || policyVersion.trim().isEmpty()
                ? DEFAULT_POLICY_VERSION
                : policyVersion.trim();
    }

    public String getRedactedValue() {
        return redactedValue;
    }

    public void setRedactedValue(String redactedValue) {
        this.redactedValue = redactedValue == null || redactedValue.trim().isEmpty()
                ? DEFAULT_REDACTED_VALUE
                : redactedValue;
    }

    public List<String> getSensitiveKeys() {
        return sensitiveKeys;
    }

    public void setSensitiveKeys(List<String> sensitiveKeys) {
        this.sensitiveKeys = sensitiveKeys == null ? List.of() : List.copyOf(sensitiveKeys);
    }

    public List<String> getSensitiveValuePatterns() {
        return sensitiveValuePatterns;
    }

    public void setSensitiveValuePatterns(List<String> sensitiveValuePatterns) {
        this.sensitiveValuePatterns = sensitiveValuePatterns == null ? List.of() : List.copyOf(sensitiveValuePatterns);
    }
}
