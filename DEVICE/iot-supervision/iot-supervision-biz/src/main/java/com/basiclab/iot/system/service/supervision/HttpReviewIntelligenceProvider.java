package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewAiSummary;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseTimelineItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemAggregate;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HttpReviewIntelligenceProvider implements ReviewIntelligenceProvider {

    private final RestTemplate restTemplate;
    private final String semanticSearchUrl;
    private final String summaryUrl;

    public HttpReviewIntelligenceProvider(RestTemplate restTemplate,
                                          @Value("${yfeieye.review-intelligence.semantic-search-url:}") String semanticSearchUrl,
                                          @Value("${yfeieye.review-intelligence.summary-url:}") String summaryUrl) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
        this.semanticSearchUrl = semanticSearchUrl;
        this.summaryUrl = summaryUrl;
    }

    @Override
    public Optional<List<ReviewSemanticHit>> semanticSearch(ReviewSemanticSearchRequest request) {
        if (!hasText(semanticSearchUrl) || request == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", request.query());
            body.put("limit", request.limit());
            body.put("candidates", request.candidates().stream().map(this::candidatePayload).toList());
            Map<?, ?> response = restTemplate.postForObject(semanticSearchUrl, body, Map.class);
            Map<?, ?> data = responseData(response);
            Object rows = firstPresent(data, "hits", "items", "results");
            if (!(rows instanceof List<?> list)) {
                return Optional.empty();
            }
            Map<Long, ReviewItemAggregate> candidatesById = request.candidates().stream()
                    .map(ReviewSemanticSearchCandidate::item)
                    .collect(Collectors.toMap(ReviewItemAggregate::id, Function.identity(), (left, right) -> left));
            List<ReviewSemanticHit> hits = new ArrayList<>();
            for (Object row : list) {
                if (row instanceof Map<?, ?> map) {
                    Long reviewItemId = parseLong(firstPresent(map, "reviewItemId", "review_item_id", "id"));
                    ReviewItemAggregate item = candidatesById.get(reviewItemId);
                    if (item != null) {
                        hits.add(new ReviewSemanticHit(
                                item,
                                parseDouble(firstPresent(map, "score", "similarity")),
                                toStringList(firstPresent(map, "matchedTerms", "matched_terms", "terms")),
                                firstText(map.get("snippet"), map.get("reason"), map.get("summary"))
                        ));
                    }
                }
            }
            return Optional.of(hits);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ReviewAiSummary> summarize(ReviewAiSummaryRequest request) {
        if (!hasText(summaryUrl) || request == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reviewCaseId", request.reviewCaseId());
            body.put("operatorUserId", request.operatorUserId());
            body.put("reviewItemIds", request.reviewItemIds());
            body.put("items", request.items());
            body.put("timeline", request.timeline().stream().map(this::timelinePayload).toList());
            Map<?, ?> response = restTemplate.postForObject(summaryUrl, body, Map.class);
            Map<?, ?> data = responseData(response);
            String summary = firstText(data.get("summary"), data.get("text"), data.get("content"));
            if (!hasText(summary)) {
                return Optional.empty();
            }
            return Optional.of(new ReviewAiSummary(
                    request.reviewCaseId(),
                    request.reviewItemIds(),
                    firstText(data.get("title"), "review case " + request.reviewCaseId()),
                    summary,
                    toStringList(firstPresent(data, "keyFacts", "key_facts", "facts")),
                    toStringList(firstPresent(data, "evidenceGaps", "evidence_gaps", "gaps")),
                    toStringList(firstPresent(data, "recommendedActions", "recommended_actions", "actions")),
                    LocalDateTime.now(),
                    firstText(data.get("generatedBy"), data.get("generated_by"), "http-review-intelligence"),
                    toObjectMap(firstPresent(data, "structuredData", "structured_data", "summaryData", "summary_data"))
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Map<String, Object> candidatePayload(ReviewSemanticSearchCandidate candidate) {
        ReviewItemAggregate item = candidate.item();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewItemId", item.id());
        payload.put("reviewItemNo", item.reviewItemNo());
        payload.put("cameraId", item.cameraId());
        payload.put("zoneCode", item.zoneCode());
        payload.put("objectLabel", item.objectLabel());
        payload.put("reviewStatus", item.reviewStatus());
        payload.put("document", candidate.document());
        return payload;
    }

    private Map<String, Object> timelinePayload(ReviewCaseTimelineItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewCaseId", item.reviewCaseId());
        payload.put("reviewItemId", item.reviewItemId());
        payload.put("cameraId", item.cameraId());
        payload.put("sourceAlertId", item.sourceAlertId());
        payload.put("materialType", item.materialType());
        payload.put("materialUri", item.materialUri());
        payload.put("happenedAt", item.happenedAt());
        payload.put("actionNote", item.actionNote());
        return payload;
    }

    private static Map<?, ?> responseData(Map<?, ?> response) {
        if (response == null || response.isEmpty()) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return dataMap;
        }
        return response;
    }

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private static Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null && hasText(String.valueOf(value))) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null && hasText(String.valueOf(value))) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0D;
            }
        }
        return 0D;
    }

    private static List<String> toStringList(Object value) {
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                if (item != null && hasText(String.valueOf(item))) {
                    values.add(String.valueOf(item));
                }
            }
            return List.copyOf(values);
        }
        if (value != null && hasText(String.valueOf(value))) {
            return List.of(String.valueOf(value));
        }
        return List.of();
    }

    private static Map<String, Object> toObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                values.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return Map.copyOf(values);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
