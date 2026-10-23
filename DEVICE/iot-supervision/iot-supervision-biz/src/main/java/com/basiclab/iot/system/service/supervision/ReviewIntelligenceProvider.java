package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewAiSummary;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseTimelineItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemAggregate;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewQuery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticHit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ReviewIntelligenceProvider {

    Optional<List<ReviewSemanticHit>> semanticSearch(ReviewSemanticSearchRequest request);

    Optional<ReviewAiSummary> summarize(ReviewAiSummaryRequest request);

    static ReviewIntelligenceProvider unavailable() {
        return UnavailableReviewIntelligenceProvider.INSTANCE;
    }

    record ReviewSemanticSearchRequest(String query,
                                       ReviewQuery filters,
                                       Integer limit,
                                       List<ReviewSemanticSearchCandidate> candidates) {
    }

    record ReviewSemanticSearchCandidate(ReviewItemAggregate item,
                                         String document) {
    }

    record ReviewAiSummaryRequest(Long reviewCaseId,
                                  Long operatorUserId,
                                  List<Long> reviewItemIds,
                                  List<ReviewCaseTimelineItem> timeline,
                                  List<ReviewItemSummaryContext> items) {
    }

    record ReviewItemSummaryContext(Long reviewItemId,
                                    String reviewItemNo,
                                    String cameraId,
                                    String zoneCode,
                                    String objectLabel,
                                    String reviewStatus,
                                    String recordEvidenceStatus,
                                    Long eventId,
                                    Map<String, Object> reviewData) {
    }

    final class UnavailableReviewIntelligenceProvider implements ReviewIntelligenceProvider {

        private static final UnavailableReviewIntelligenceProvider INSTANCE = new UnavailableReviewIntelligenceProvider();

        private UnavailableReviewIntelligenceProvider() {
        }

        @Override
        public Optional<List<ReviewSemanticHit>> semanticSearch(ReviewSemanticSearchRequest request) {
            return Optional.empty();
        }

        @Override
        public Optional<ReviewAiSummary> summarize(ReviewAiSummaryRequest request) {
            return Optional.empty();
        }
    }
}
