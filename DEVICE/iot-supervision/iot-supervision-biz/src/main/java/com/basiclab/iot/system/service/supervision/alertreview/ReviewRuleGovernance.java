package com.basiclab.iot.system.service.supervision.alertreview;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.collectStringValues;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstNonNull;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.intersects;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.matchesText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.max;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.min;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requireText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.roundRate;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toInteger;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toLocalDateTime;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toLong;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toMapList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toStringObjectMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;

/**
 * 规则治理：规则保存与回放、几何评估、规则建议的影子评估/采纳/回滚及统计。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewRuleGovernance {

    private final AlertReviewDependencies deps;
    private final ReviewItemQuery reviewItemQuery;

    ReviewRuleGovernance(AlertReviewDependencies deps, ReviewItemQuery reviewItemQuery) {
        this.deps = deps;
        this.reviewItemQuery = reviewItemQuery;
    }

    private static final int MIN_RULE_SUGGESTION_SAMPLE_COUNT = 3;
    static final List<String> DEFAULT_RULE_CANDIDATE_ACTIONS = List.of(
            "narrow_zone",
            "raise_confidence",
            "increase_min_stay",
            "require_zone",
            "suppress_label_zone"
    );

    public ReviewItemAggregate updateRuleSuggestionStatus(RuleSuggestionOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        String status = requireRuleSuggestionStatus(command.status());
        ReviewItemAggregate item = deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        requireRuleSuggestionTransition(item, status);
        Map<String, Object> suggestion = updateRuleSuggestionLifecycle(item.ruleSuggestion(), status, command.note());
        if (RULE_SUGGESTION_SHADOW_EVALUATED.equals(status)) {
            suggestion = withCurrentRuleSuggestionSafety(item, suggestion);
            requireRuleSuggestionSampleReady(suggestion);
            suggestion = withRuleGovernanceEvidence(item, suggestion);
        }
        if (RULE_SUGGESTION_ACCEPTED.equals(status)) {
            requireRuleSuggestionSampleReady(suggestion);
            requireRuleGovernanceEvidence(suggestion);
        }
        if (RULE_SUGGESTION_APPLIED.equals(status)) {
            requireRuleSuggestionSampleReady(suggestion);
            requireRuleGovernanceEvidence(suggestion);
            suggestion = applyRuleSuggestion(item, suggestion);
        }
        return reviewItemQuery.withEventProjection(deps.reviewItemStore.updateRuleSuggestionStatus(
                command.reviewItemId(),
                command.reviewerUserId(),
                status,
                suggestion,
                LocalDateTime.now()
        ));
    }

    public RuleSuggestionPreview previewRuleSuggestion(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        ReviewItemAggregate item = deps.reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        Map<String, Object> proposedRule = buildProposedRule(item);
        Map<String, Object> currentRule = deps.reviewRuleStore.listAll().stream()
                .filter(rule -> sameRuleScope(rule, item))
                .findFirst()
                .map(ReviewRuleGovernance::ruleToMap)
                .orElse(Map.of());
        List<String> diff = buildRuleSuggestionDiff(currentRule, proposedRule);
        List<String> affectedReviewItemNos = reviewItemQuery.listWorkbench(new ReviewQuery(null, item.cameraId(), null, null))
                .stream()
                .filter(candidate -> Objects.equals(candidate.zoneCode(), item.zoneCode()))
                .filter(candidate -> Objects.equals(candidate.objectLabel(), item.objectLabel()))
                .map(ReviewItemAggregate::reviewItemNo)
                .toList();
        return new RuleSuggestionPreview(item.id(), currentRule, proposedRule, diff, affectedReviewItemNos);
    }

    public ReviewItemAggregate revertRuleSuggestion(RuleSuggestionOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        String status = requireRuleSuggestionStatus(command.status());
        if (!RULE_SUGGESTION_REVERTED.equals(status)) {
            throw new IllegalArgumentException("rule suggestion revert requires status: " + RULE_SUGGESTION_REVERTED);
        }
        ReviewItemAggregate item = deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        requireRuleSuggestionTransition(item, status);
        Map<String, Object> suggestion = updateRuleSuggestionLifecycle(item.ruleSuggestion(), status, command.note());
        suggestion = rollbackRuleSuggestion(item, suggestion);
        return reviewItemQuery.withEventProjection(deps.reviewItemStore.updateRuleSuggestionStatus(
                command.reviewItemId(),
                command.reviewerUserId(),
                status,
                suggestion,
                LocalDateTime.now()
        ));
    }

    public List<RuleSuggestionStat> listRuleSuggestionStats(ReviewQuery query) {
        ReviewQuery scopedQuery = query == null
                ? new ReviewQuery(null, null, null, null)
                : new ReviewQuery(null, query.cameraId(), query.zoneCode(), query.objectLabel(), null,
                        null, null, null, query.beginTime(), query.endTime());
        Map<RuleSuggestionGroupKey, List<ReviewItemAggregate>> grouped = new LinkedHashMap<>();
        for (ReviewItemAggregate item : reviewItemQuery.listWorkbench(scopedQuery)) {
            RuleSuggestionGroupKey key = new RuleSuggestionGroupKey(item.cameraId(), item.zoneCode(), item.objectLabel());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        List<RuleSuggestionStat> stats = new ArrayList<>();
        for (Map.Entry<RuleSuggestionGroupKey, List<ReviewItemAggregate>> entry : grouped.entrySet()) {
            List<ReviewItemAggregate> falsePositiveItems = entry.getValue().stream()
                    .filter(item -> STATUS_FALSE_POSITIVE.equals(item.reviewStatus()))
                    .toList();
            if (falsePositiveItems.isEmpty()) {
                continue;
            }
            ReviewItemAggregate sample = falsePositiveItems.get(0);
            Map<String, Object> suggestion = sample.ruleSuggestion() == null ? Map.of() : sample.ruleSuggestion();
            String action = toText(suggestion.get("action"));
            LocalDateTime lastSeenAt = falsePositiveItems.stream()
                    .map(ReviewItemAggregate::lastAlertTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            stats.add(new RuleSuggestionStat(
                    entry.getKey().cameraId(),
                    entry.getKey().zoneCode(),
                    entry.getKey().objectLabel(),
                    hasText(action) ? action : "suppress_label_zone",
                    falsePositiveItems.size(),
                    entry.getValue().size(),
                    roundRate(falsePositiveItems.size(), entry.getValue().size()),
                    ruleCandidateActions(suggestion),
                    lastSeenAt
            ));
        }
        return stats.stream()
                .sorted(Comparator
                        .comparing(RuleSuggestionStat::falsePositiveCount, Comparator.reverseOrder())
                        .thenComparing(RuleSuggestionStat::lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public ReviewRuleReplayResult replayRule(ReviewRuleReplayCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.ruleCode(), "ruleCode");
        ReviewQuery query = new ReviewQuery(
                null,
                command.cameraId(),
                command.zoneCode(),
                command.objectLabel(),
                null,
                null,
                null,
                null,
                command.beginTime(),
                command.endTime()
        );
        List<ReviewItemAggregate> evaluatedItems = reviewItemQuery.listWorkbench(query).stream()
                .filter(item -> Objects.equals(command.ruleCode(), item.ruleCode()))
                .filter(item -> matchesText(command.sourceSystem(), item.sourceSystem()))
                .toList();
        Optional<ReviewRuleView> currentRule = deps.reviewRuleStore.listAll().stream()
                .filter(rule -> Objects.equals(command.ruleCode(), rule.ruleCode()))
                .filter(rule -> matchesText(rule.sourceSystem(), command.sourceSystem()))
                .filter(rule -> matchesText(rule.cameraId(), command.cameraId()))
                .filter(rule -> matchesText(rule.zoneCode(), command.zoneCode()))
                .filter(rule -> matchesText(rule.objectLabel(), command.objectLabel()))
                .findFirst();
        Map<String, Object> ruleVersion = currentRule
                .map(ReviewRuleGovernance::ruleVersionFromRule)
                .orElseGet(() -> ruleVersionFromReplayCommand(command));
        int matchBeforeCount = currentRule
                .map(rule -> (int) evaluatedItems.stream().filter(item -> matchesReplayRule(item, rule)).count())
                .orElse(0);
        int matchAfterCount = (int) evaluatedItems.stream()
                .filter(item -> matchesReplayRule(item, command))
                .count();
        int falsePositiveBeforeCount = (int) evaluatedItems.stream()
                .filter(item -> STATUS_FALSE_POSITIVE.equals(item.reviewStatus()))
                .count();
        int matchedFalsePositiveAfterCount = (int) evaluatedItems.stream()
                .filter(item -> STATUS_FALSE_POSITIVE.equals(item.reviewStatus()))
                .filter(item -> matchesReplayRule(item, command))
                .count();
        List<String> actions = new ArrayList<>();
        double beforeRate = roundRate(falsePositiveBeforeCount, evaluatedItems.size());
        double afterRate = roundRate(matchedFalsePositiveAfterCount, matchAfterCount);
        if (afterRate <= beforeRate) {
            actions.add("safe_to_apply");
        } else {
            actions.add("manual_review_required");
        }
        if (matchAfterCount == 0) {
            actions.add("check_recall_risk");
        }
        Map<String, Object> report = buildReplayReport(
                evaluatedItems,
                matchBeforeCount,
                matchAfterCount,
                falsePositiveBeforeCount,
                matchedFalsePositiveAfterCount,
                actions,
                ruleVersion
        );
        return new ReviewRuleReplayResult(
                command.ruleCode(),
                evaluatedItems.stream().map(ReviewItemAggregate::id).toList(),
                evaluatedItems.size(),
                matchBeforeCount,
                matchAfterCount,
                falsePositiveBeforeCount,
                beforeRate,
                afterRate,
                List.copyOf(actions),
                replayScope(command),
                report,
                LocalDateTime.now()
        );
    }

    public ReviewRuleGeometryEvaluation evaluateRuleGeometry(ReviewRuleGeometryCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.ruleCode(), "ruleCode");
        List<Double> evaluatedPoint = bottomCenterPoint(command.bbox());
        boolean inside = !evaluatedPoint.isEmpty() && pointInPolygon(evaluatedPoint, command.polygon());
        ReviewQuery query = command.query() == null
                ? new ReviewQuery(null, command.cameraId(), command.zoneCode(), command.objectLabel(),
                        null, null, null, null, null, null)
                : command.query();
        List<ReviewItemAggregate> replayedItems = reviewItemQuery.listWorkbench(query).stream()
                .filter(item -> Objects.equals(command.ruleCode(), item.ruleCode()))
                .filter(item -> matchesText(command.cameraId(), item.cameraId()))
                .filter(item -> matchesText(command.zoneCode(), item.zoneCode()))
                .filter(item -> matchesText(command.objectLabel(), item.objectLabel()))
                .toList();
        List<Long> replayedReviewItemIds = replayedItems.stream()
                .map(ReviewItemAggregate::id)
                .toList();
        Integer minStaySeconds = replayedItems.stream()
                .map(ReviewRuleGovernance::inferredStaySeconds)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        Optional<ReviewRuleView> matchedRule = matchingRuleForGeometry(command);
        Integer zoneInertiaFrames = matchedRule
                .map(ReviewRuleView::inertiaFrames)
                .map(ReviewRuleGovernance::normalizeZoneInertiaFrames)
                .orElse(1);
        Integer loiteringSeconds = matchedRule
                .map(ReviewRuleView::loiteringSeconds)
                .filter(value -> value != null && value > 0)
                .orElseGet(() -> matchedRule
                        .map(ReviewRuleView::minStaySeconds)
                        .filter(value -> value != null && value > 0)
                        .orElse(minStaySeconds));
        Integer observedConsecutiveFrames = replayedItems.stream()
                .map(ReviewRuleGovernance::inferredConsecutiveZoneFrames)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        Integer observedStaySeconds = replayedItems.stream()
                .map(ReviewRuleGovernance::inferredStaySeconds)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        boolean inertiaSatisfied = observedConsecutiveFrames == null
                ? zoneInertiaFrames <= 1
                : observedConsecutiveFrames >= zoneInertiaFrames;
        boolean loiteringSatisfied = loiteringSeconds == null || loiteringSeconds <= 0
                || (observedStaySeconds != null && observedStaySeconds >= loiteringSeconds);
        Map<String, Object> ruleVersion = new LinkedHashMap<>();
        ruleVersion.put("ruleCode", command.ruleCode());
        ruleVersion.put("cameraId", command.cameraId());
        ruleVersion.put("zoneCode", command.zoneCode());
        ruleVersion.put("objectLabel", command.objectLabel());
        ruleVersion.put("minStaySeconds", minStaySeconds);
        ruleVersion.put("zoneInertiaFrames", zoneInertiaFrames);
        ruleVersion.put("loiteringSeconds", loiteringSeconds);
        ruleVersion.put("geometryType", "bottom_center");
        ruleVersion.put("semanticEngine", "yfeieye-rule-geometry-v1");
        ruleVersion.put("pointStrategy", "bottom_center");
        ruleVersion.put("coordinateSpace", "image");
        ruleVersion.put("applicationMode", "shadow");
        ruleVersion.put("operatorUserId", command.operatorUserId());
        ruleVersion.put("evaluatedAt", LocalDateTime.now().toString());
        List<String> consistencyChecks = new ArrayList<>(List.of(
                "front_back_replay_use_bottom_center",
                "bbox_order_x1_y1_x2_y2"
        ));
        if (zoneInertiaFrames > 1) {
            consistencyChecks.add("zone_inertia_applied");
        }
        if (loiteringSeconds != null && loiteringSeconds > 0) {
            consistencyChecks.add("loitering_threshold_applied");
        }
        return new ReviewRuleGeometryEvaluation(
                "bottom_center",
                inside,
                evaluatedPoint,
                command.zoneCode(),
                replayedReviewItemIds,
                immutableNonNullMap(ruleVersion),
                List.copyOf(consistencyChecks),
                LocalDateTime.now(),
                List.of(ruleMatchTrace(command, evaluatedPoint, inside, minStaySeconds, zoneInertiaFrames,
                        observedConsecutiveFrames, inertiaSatisfied, loiteringSeconds, observedStaySeconds,
                        loiteringSatisfied))
        );
    }

    public ReviewRuleView saveRule(ReviewRuleCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.ruleCode(), "ruleCode");
        return deps.reviewRuleStore.save(command);
    }

    public List<ReviewRuleView> listRules() {
        return deps.reviewRuleStore.listAll();
    }

    private static boolean matchesReplayRule(ReviewItemAggregate item, ReviewRuleView rule) {
        return matchesText(rule.sourceSystem(), item.sourceSystem())
                && matchesText(rule.cameraId(), item.cameraId())
                && matchesText(rule.zoneCode(), item.zoneCode())
                && matchesText(rule.objectLabel(), item.objectLabel())
                && matchesReplayStay(rule.minStaySeconds(), item)
                && matchesReplayInertia(rule.inertiaFrames(), item);
    }

    private static boolean matchesReplayRule(ReviewItemAggregate item, ReviewRuleReplayCommand command) {
        return matchesText(command.sourceSystem(), item.sourceSystem())
                && matchesText(command.cameraId(), item.cameraId())
                && matchesText(command.zoneCode(), item.zoneCode())
                && matchesText(command.objectLabel(), item.objectLabel())
                && matchesReplayStay(command.minStaySeconds(), item);
    }

    private static boolean matchesReplayStay(Integer minStaySeconds, ReviewItemAggregate item) {
        if (minStaySeconds == null || minStaySeconds <= 0) {
            return true;
        }
        Integer staySeconds = inferredStaySeconds(item);
        return staySeconds != null && staySeconds >= minStaySeconds;
    }

    private static boolean matchesReplayInertia(Integer inertiaFrames, ReviewItemAggregate item) {
        int requiredFrames = normalizeZoneInertiaFrames(inertiaFrames);
        if (requiredFrames <= 1) {
            return true;
        }
        Integer observedFrames = inferredConsecutiveZoneFrames(item);
        return observedFrames != null && observedFrames >= requiredFrames;
    }

    private static List<Double> bottomCenterPoint(List<Double> bbox) {
        if (bbox == null || bbox.size() < 4) {
            return List.of();
        }
        Double x1 = bbox.get(0);
        Double y2 = bbox.get(3);
        Double x2 = bbox.get(2);
        if (x1 == null || x2 == null || y2 == null) {
            return List.of();
        }
        return List.of((x1 + x2) / 2D, y2);
    }

    private static boolean pointInPolygon(List<Double> point, List<List<Double>> polygon) {
        if (point == null || point.size() < 2 || polygon == null || polygon.size() < 3) {
            return false;
        }
        double x = point.get(0);
        double y = point.get(1);
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            List<Double> current = polygon.get(i);
            List<Double> previous = polygon.get(j);
            if (current == null || previous == null || current.size() < 2 || previous.size() < 2) {
                continue;
            }
            double xi = current.get(0);
            double yi = current.get(1);
            double xj = previous.get(0);
            double yj = previous.get(1);
            boolean intersects = ((yi > y) != (yj > y))
                    && x < (xj - xi) * (y - yi) / (yj - yi) + xi;
            if (intersects) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static Integer inferredStaySeconds(ReviewItemAggregate item) {
        Map<String, Object> reviewData = item.reviewData() == null ? Map.of() : item.reviewData();
        Integer staySeconds = toInteger(reviewData.get("staySeconds"));
        if (staySeconds != null) {
            return staySeconds;
        }
        for (Map<String, Object> detection : toMapList(reviewData.get("detections"))) {
            staySeconds = toInteger(detection.get("staySeconds"));
            if (staySeconds != null) {
                return staySeconds;
            }
        }
        return null;
    }

    private Optional<ReviewRuleView> matchingRuleForGeometry(ReviewRuleGeometryCommand command) {
        return deps.reviewRuleStore.listAll().stream()
                .filter(rule -> Objects.equals(command.ruleCode(), rule.ruleCode()))
                .filter(rule -> matchesText(rule.cameraId(), command.cameraId()))
                .filter(rule -> matchesText(rule.zoneCode(), command.zoneCode()))
                .filter(rule -> matchesText(rule.objectLabel(), command.objectLabel()))
                .findFirst();
    }

    private static Integer inferredConsecutiveZoneFrames(ReviewItemAggregate item) {
        Map<String, Object> reviewData = item.reviewData() == null ? Map.of() : item.reviewData();
        Integer frames = toInteger(reviewData.get("consecutiveZoneFrames"));
        if (frames != null) {
            return frames;
        }
        Map<String, Object> motion = toStringObjectMap(reviewData.get("motion"));
        frames = toInteger(firstNonNull(motion.get("consecutiveZoneFrames"), motion.get("consecutive_zone_frames")));
        if (frames != null) {
            return frames;
        }
        for (Map<String, Object> detection : toMapList(reviewData.get("detections"))) {
            frames = toInteger(firstNonNull(detection.get("consecutiveZoneFrames"), detection.get("consecutive_zone_frames")));
            if (frames != null) {
                return frames;
            }
        }
        return null;
    }

    private static Map<String, Object> ruleMatchTrace(ReviewRuleGeometryCommand command,
                                                      List<Double> bottomCenter,
                                                      boolean inside,
                                                      Integer minStaySeconds,
                                                      Integer zoneInertiaFrames,
                                                      Integer observedConsecutiveFrames,
                                                      boolean inertiaSatisfied,
                                                      Integer loiteringSeconds,
                                                      Integer observedStaySeconds,
                                                      boolean loiteringSatisfied) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("ruleCode", command.ruleCode());
        trace.put("cameraId", command.cameraId());
        trace.put("zoneCode", command.zoneCode());
        trace.put("objectLabel", command.objectLabel());
        trace.put("bbox", command.bbox());
        trace.put("bottomCenter", bottomCenter);
        trace.put("inside", inside);
        trace.put("minStaySeconds", minStaySeconds);
        trace.put("threshold", minStaySeconds);
        trace.put("zoneInertiaFrames", zoneInertiaFrames);
        trace.put("observedConsecutiveFrames", observedConsecutiveFrames);
        trace.put("inertiaSatisfied", inertiaSatisfied);
        trace.put("loiteringSeconds", loiteringSeconds);
        trace.put("observedStaySeconds", observedStaySeconds);
        trace.put("loiteringSatisfied", loiteringSatisfied);
        trace.put("qualifiedInside", inside && inertiaSatisfied && loiteringSatisfied);
        trace.put("ruleVersion", "shadow");
        trace.put("geometryType", "bottom_center");
        trace.put("semanticEngine", "yfeieye-rule-geometry-v1");
        trace.put("pointStrategy", "bottom_center");
        trace.put("coordinateSpace", "image");
        trace.put("operatorUserId", command.operatorUserId());
        return immutableNonNullMap(trace);
    }

    private static Map<String, Object> replayScope(ReviewRuleReplayCommand command) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("ruleCode", command.ruleCode());
        scope.put("sourceSystem", command.sourceSystem());
        scope.put("cameraId", command.cameraId());
        scope.put("zoneCode", command.zoneCode());
        scope.put("objectLabel", command.objectLabel());
        scope.put("minStaySeconds", command.minStaySeconds());
        scope.put("beginTime", command.beginTime() == null ? null : command.beginTime().toString());
        scope.put("endTime", command.endTime() == null ? null : command.endTime().toString());
        scope.put("operatorUserId", command.operatorUserId());
        return immutableNonNullMap(scope);
    }

    private static Map<String, Object> ruleVersionFromRule(ReviewRuleView rule) {
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("ruleId", rule.id());
        version.put("ruleCode", rule.ruleCode());
        version.put("sourceSystem", rule.sourceSystem());
        version.put("cameraId", rule.cameraId());
        version.put("zoneCode", rule.zoneCode());
        version.put("objectLabel", rule.objectLabel());
        version.put("minStaySeconds", rule.minStaySeconds());
        version.put("inertiaFrames", normalizeZoneInertiaFrames(rule.inertiaFrames()));
        version.put("loiteringSeconds", firstNonNull(rule.loiteringSeconds(), rule.minStaySeconds()));
        version.put("semanticEngine", "yfeieye-rule-geometry-v1");
        version.put("source", "saved_rule");
        return immutableNonNullMap(version);
    }

    private static Map<String, Object> ruleVersionFromReplayCommand(ReviewRuleReplayCommand command) {
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("ruleCode", command.ruleCode());
        version.put("sourceSystem", command.sourceSystem());
        version.put("cameraId", command.cameraId());
        version.put("zoneCode", command.zoneCode());
        version.put("objectLabel", command.objectLabel());
        version.put("minStaySeconds", command.minStaySeconds());
        version.put("inertiaFrames", 1);
        version.put("loiteringSeconds", command.minStaySeconds());
        version.put("semanticEngine", "yfeieye-rule-geometry-v1");
        version.put("source", "replay_command");
        return immutableNonNullMap(version);
    }

    static Integer normalizeZoneInertiaFrames(Integer inertiaFrames) {
        if (inertiaFrames == null || inertiaFrames <= 0) {
            return 1;
        }
        return inertiaFrames;
    }

    private static Map<String, Object> buildReplayReport(List<ReviewItemAggregate> evaluatedItems,
                                                         int matchBeforeCount,
                                                         int matchAfterCount,
                                                         int falsePositiveBeforeCount,
                                                         int matchedFalsePositiveAfterCount,
                                                         List<String> recommendedActions) {
        return buildReplayReport(evaluatedItems, matchBeforeCount, matchAfterCount, falsePositiveBeforeCount,
                matchedFalsePositiveAfterCount, recommendedActions, Map.of());
    }

    private static Map<String, Object> buildReplayReport(List<ReviewItemAggregate> evaluatedItems,
                                                         int matchBeforeCount,
                                                         int matchAfterCount,
                                                         int falsePositiveBeforeCount,
                                                         int matchedFalsePositiveAfterCount,
                                                         List<String> recommendedActions,
                                                         Map<String, Object> ruleVersion) {
        int hitReduction = Math.max(0, matchBeforeCount - matchAfterCount);
        int falsePositiveReduction = Math.max(0, falsePositiveBeforeCount - matchedFalsePositiveAfterCount);
        int possibleMissedCount = matchAfterCount == 0
                ? evaluatedItems.size()
                : Math.max(0, matchBeforeCount - matchAfterCount);
        boolean recallRisk = possibleMissedCount > 0 && recommendedActions.contains("check_recall_risk");
        boolean shouldApply = recommendedActions.contains("safe_to_apply") && !recallRisk;

        Map<String, Object> impactScope = new LinkedHashMap<>();
        impactScope.put("cameraIds", distinctValues(evaluatedItems.stream().map(ReviewItemAggregate::cameraId).toList()));
        impactScope.put("zoneCodes", distinctValues(evaluatedItems.stream().map(ReviewItemAggregate::zoneCode).toList()));
        impactScope.put("objectLabels", distinctValues(evaluatedItems.stream().map(ReviewItemAggregate::objectLabel).toList()));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("shouldApply", shouldApply);
        report.put("decision", shouldApply ? "apply" : "review_before_apply");
        report.put("hitReduction", hitReduction);
        report.put("matchBeforeCount", matchBeforeCount);
        report.put("matchAfterCount", matchAfterCount);
        report.put("falsePositiveReduction", falsePositiveReduction);
        report.put("possibleMissedCount", possibleMissedCount);
        report.put("impactScope", impactScope);
        report.put("ruleVersion", immutableNonNullMap(ruleVersion));
        Map<String, Object> geometrySemantics = new LinkedHashMap<>();
        geometrySemantics.put("objectPoint", "bottom_center");
        geometrySemantics.put("bboxOrder", "x1_y1_x2_y2");
        geometrySemantics.put("zoneInertiaFrames", ruleVersion.getOrDefault("inertiaFrames", 1));
        geometrySemantics.put("loiteringSeconds", ruleVersion.get("loiteringSeconds"));
        geometrySemantics.put("singleSourceRuleVersion", !ruleVersion.isEmpty());
        geometrySemantics.put("sharedBy", List.of("rule_editor", "backend_replay", "evidence_verifier"));
        report.put("geometrySemantics", immutableNonNullMap(geometrySemantics));
        report.put("ruleLifecyclePolicy", Map.of(
                "directApplyAllowed", false,
                "shadowEvaluationRequired", true,
                "approvalRequired", true,
                "versionedRollbackRequired", true,
                "rollbackSupported", true
        ));
        return immutableNonNullMap(report);
    }

    private static List<String> distinctValues(List<String> values) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (hasText(value)) {
                distinct.add(value);
            }
        }
        return List.copyOf(distinct);
    }

    private Map<String, Object> withRuleGovernanceEvidence(ReviewItemAggregate item,
                                                           Map<String, Object> suggestion) {
        Map<String, Object> updated = new LinkedHashMap<>(suggestion == null ? Map.of() : suggestion);
        Map<String, Object> shadowEvaluation = toStringObjectMap(updated.get("shadowEvaluation"));
        if (shadowEvaluation.isEmpty()) {
            shadowEvaluation = buildRuleShadowEvaluation(item);
            updated.put("shadowEvaluation", shadowEvaluation);
        }
        if (toStringObjectMap(updated.get("replayReport")).isEmpty()) {
            updated.put("replayReport", buildRuleSuggestionReplayReport(item, shadowEvaluation));
        }
        return immutableNonNullMap(updated);
    }

    private static void requireRuleGovernanceEvidence(Map<String, Object> suggestion) {
        if (toStringObjectMap(suggestion == null ? null : suggestion.get("shadowEvaluation")).isEmpty()
                || toStringObjectMap(suggestion == null ? null : suggestion.get("replayReport")).isEmpty()) {
            throw new IllegalStateException(
                    "rule suggestion requires existing shadowEvaluation and replayReport before approval or apply"
            );
        }
    }

    private Map<String, Object> buildRuleShadowEvaluation(ReviewItemAggregate item) {
        List<ReviewItemAggregate> scopedItems = reviewItemQuery.listWorkbench(new ReviewQuery(
                null,
                item.cameraId(),
                item.zoneCode(),
                item.objectLabel(),
                null,
                null,
                null,
                null,
                null,
                null
        )).stream()
                .filter(candidate -> Objects.equals(candidate.ruleCode(), item.ruleCode()))
                .filter(candidate -> sameRuleScope(candidate, item))
                .toList();
        long totalCount = scopedItems.size();
        long falsePositiveCount = scopedItems.stream()
                .filter(candidate -> STATUS_FALSE_POSITIVE.equals(candidate.reviewStatus()))
                .count();
        long estimatedSuppressedCount = falsePositiveCount;
        long afterFalsePositiveCount = Math.max(0L, falsePositiveCount - estimatedSuppressedCount);
        Map<String, Object> shadow = new LinkedHashMap<>();
        shadow.put("evaluatedReviewItemCount", (int) totalCount);
        shadow.put("estimatedSuppressedCount", (int) estimatedSuppressedCount);
        shadow.put("beforeFalsePositiveRate", roundRate(falsePositiveCount, totalCount));
        shadow.put("afterFalsePositiveRate", roundRate(afterFalsePositiveCount, totalCount));
        shadow.put("scopeCameraId", item.cameraId());
        shadow.put("scopeZoneCode", item.zoneCode());
        shadow.put("scopeObjectLabel", item.objectLabel());
        shadow.put("evaluatedAt", LocalDateTime.now().toString());
        return immutableNonNullMap(shadow);
    }

    private Map<String, Object> buildRuleSuggestionReplayReport(ReviewItemAggregate item,
                                                                Map<String, Object> shadowEvaluation) {
        List<ReviewItemAggregate> scopedItems = reviewItemQuery.listWorkbench(new ReviewQuery(
                null,
                item.cameraId(),
                item.zoneCode(),
                item.objectLabel(),
                null,
                null,
                null,
                null,
                null,
                null
        )).stream()
                .filter(candidate -> Objects.equals(candidate.ruleCode(), item.ruleCode()))
                .filter(candidate -> sameRuleScope(candidate, item))
                .toList();
        int falsePositiveCount = (int) scopedItems.stream()
                .filter(candidate -> STATUS_FALSE_POSITIVE.equals(candidate.reviewStatus()))
                .count();
        List<String> recommendedActions = List.of("safe_to_apply", "check_recall_risk");
        Map<String, Object> ruleVersion = ruleVersionFromRuleSuggestionItem(item);
        Map<String, Object> report = new LinkedHashMap<>(buildReplayReport(
                scopedItems,
                scopedItems.size(),
                0,
                falsePositiveCount,
                0,
                recommendedActions,
                ruleVersion
        ));
        report.put("evaluatedReviewItemIds", scopedItems.stream().map(ReviewItemAggregate::id).toList());
        report.put("evaluatedCount", scopedItems.size());
        report.put("sampleWindow", replaySampleWindow(scopedItems, item));
        report.put("hitComparison", replayHitComparison(scopedItems.size(), 0, falsePositiveCount, 0));
        report.put("falseNegativeEstimate", replayFalseNegativeEstimate(toInteger(report.get("possibleMissedCount"))));
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("sourceSystem", item.sourceSystem());
        scope.put("ruleCode", item.ruleCode());
        scope.put("cameraId", item.cameraId());
        scope.put("zoneCode", item.zoneCode());
        scope.put("objectLabel", item.objectLabel());
        report.put("scope", immutableNonNullMap(scope));
        report.put("recommendedActions", recommendedActions);
        report.put("shadowEvaluation", shadowEvaluation);
        report.put("replayedAt", LocalDateTime.now().toString());
        return immutableNonNullMap(report);
    }

    private static Map<String, Object> ruleVersionFromRuleSuggestionItem(ReviewItemAggregate item) {
        Map<String, Object> suggestion = item.ruleSuggestion() == null ? Map.of() : item.ruleSuggestion();
        Integer minStaySeconds = firstNonNull(toInteger(suggestion.get("minStaySeconds")), 15);
        Integer loiteringSeconds = firstNonNull(toInteger(suggestion.get("loiteringSeconds")), minStaySeconds);
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("ruleCode", item.ruleCode());
        version.put("sourceSystem", item.sourceSystem());
        version.put("cameraId", item.cameraId());
        version.put("zoneCode", item.zoneCode());
        version.put("objectLabel", item.objectLabel());
        version.put("minStaySeconds", minStaySeconds);
        version.put("inertiaFrames", normalizeZoneInertiaFrames(toInteger(suggestion.get("inertiaFrames"))));
        version.put("loiteringSeconds", loiteringSeconds);
        version.put("semanticEngine", "yfeieye-rule-geometry-v1");
        version.put("source", "rule_suggestion");
        version.put("suggestionAction", suggestion.get("action"));
        version.put("lifecycleStatus", firstText(suggestion.get("lifecycleStatus"), item.ruleSuggestionStatus()));
        return immutableNonNullMap(version);
    }

    private static Map<String, Object> replaySampleWindow(List<ReviewItemAggregate> scopedItems,
                                                          ReviewItemAggregate fallbackItem) {
        LocalDateTime startTime = scopedItems.stream()
                .map(candidate -> firstNonNull(candidate.firstAlertTime(), candidate.lastAlertTime()))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(firstNonNull(fallbackItem.firstAlertTime(), fallbackItem.lastAlertTime()));
        LocalDateTime endTime = scopedItems.stream()
                .map(candidate -> firstNonNull(candidate.lastAlertTime(), candidate.firstAlertTime()))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(firstNonNull(fallbackItem.lastAlertTime(), fallbackItem.firstAlertTime()));
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("startTime", startTime == null ? null : startTime.toString());
        window.put("endTime", endTime == null ? null : endTime.toString());
        window.put("sampleCount", scopedItems.size());
        window.put("reviewItemIds", scopedItems.stream().map(ReviewItemAggregate::id).toList());
        return immutableNonNullMap(window);
    }

    private static Map<String, Object> replayHitComparison(int beforeCount,
                                                           int afterCount,
                                                           int falsePositiveBeforeCount,
                                                           int falsePositiveAfterCount) {
        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("beforeCount", beforeCount);
        comparison.put("afterCount", afterCount);
        comparison.put("difference", Math.max(0, beforeCount - afterCount));
        comparison.put("falsePositiveBeforeCount", falsePositiveBeforeCount);
        comparison.put("falsePositiveAfterCount", falsePositiveAfterCount);
        comparison.put("falsePositiveReduction", Math.max(0, falsePositiveBeforeCount - falsePositiveAfterCount));
        return immutableNonNullMap(comparison);
    }

    private static Map<String, Object> replayFalseNegativeEstimate(Integer possibleMissedCount) {
        int missedCount = possibleMissedCount == null ? 0 : Math.max(0, possibleMissedCount);
        Map<String, Object> estimate = new LinkedHashMap<>();
        estimate.put("possibleMissedCount", missedCount);
        estimate.put("riskLevel", missedCount > 0 ? "review_required" : "low");
        estimate.put("recommendedAction", missedCount > 0 ? "check_recall_risk" : "none");
        return immutableNonNullMap(estimate);
    }

    private static boolean sameRuleScope(ReviewItemAggregate left, ReviewItemAggregate right) {
        return Objects.equals(left.sourceSystem(), right.sourceSystem())
                && Objects.equals(left.ruleCode(), right.ruleCode())
                && Objects.equals(left.cameraId(), right.cameraId())
                && Objects.equals(left.zoneCode(), right.zoneCode())
                && Objects.equals(left.objectLabel(), right.objectLabel());
    }

    private static Map<String, Object> buildProposedRule(ReviewItemAggregate item) {
        Map<String, Object> suggestion = item.ruleSuggestion() == null ? Map.of() : item.ruleSuggestion();
        Map<String, Object> proposedRule = new LinkedHashMap<>();
        proposedRule.put("action", hasText(toText(suggestion.get("action")))
                ? suggestion.get("action")
                : "suppress_label_zone");
        proposedRule.put("sourceSystem", item.sourceSystem());
        proposedRule.put("ruleCode", item.ruleCode());
        proposedRule.put("cameraId", item.cameraId());
        proposedRule.put("zoneCode", item.zoneCode());
        proposedRule.put("objectLabel", item.objectLabel());
        if (hasText(toText(suggestion.get("reason")))) {
            proposedRule.put("reason", suggestion.get("reason"));
        }
        return Map.copyOf(proposedRule);
    }

    private Map<String, Object> applyRuleSuggestion(ReviewItemAggregate item, Map<String, Object> suggestion) {
        Map<String, Object> previousRule = deps.reviewRuleStore.listAll().stream()
                .filter(rule -> sameRuleScope(rule, item))
                .findFirst()
                .map(ReviewRuleGovernance::ruleToMap)
                .orElse(Map.of());
        ReviewRuleView appliedRule = deps.reviewRuleStore.save(buildAppliedRuleCommand(item, suggestion, previousRule));
        Map<String, Object> updated = new LinkedHashMap<>(suggestion);
        if (!previousRule.isEmpty()) {
            updated.put("previousRule", previousRule);
        }
        updated.put("appliedRuleId", appliedRule.id());
        updated.put("appliedRule", ruleToMap(appliedRule));
        updated.put("configVersion", ruleConfigVersion(appliedRule.id(), suggestion, "configVersion"));
        return immutableNonNullMap(updated);
    }

    private Map<String, Object> rollbackRuleSuggestion(ReviewItemAggregate item, Map<String, Object> suggestion) {
        Long appliedRuleId = toLong(suggestion.get("appliedRuleId"));
        if (appliedRuleId == null) {
            return suggestion;
        }
        Map<String, Object> previousRule = toStringObjectMap(suggestion.get("previousRule"));
        ReviewRuleCommand rollbackCommand = previousRule.isEmpty()
                ? buildDisabledAppliedRuleCommand(item, suggestion, appliedRuleId)
                : ruleCommandFromMap(previousRule, appliedRuleId);
        ReviewRuleView rollbackRule = deps.reviewRuleStore.save(rollbackCommand);
        Map<String, Object> updated = new LinkedHashMap<>(suggestion);
        updated.put("rollbackRule", ruleToMap(rollbackRule));
        updated.put("rollbackVersion", ruleConfigVersion(appliedRuleId, suggestion, "configVersion"));
        return immutableNonNullMap(updated);
    }

    private static ReviewRuleCommand buildAppliedRuleCommand(ReviewItemAggregate item,
                                                             Map<String, Object> suggestion,
                                                             Map<String, Object> previousRule) {
        Long ruleId = toLong(suggestion.get("appliedRuleId"));
        if (ruleId == null) {
            ruleId = toLong(previousRule.get("id"));
        }
        return new ReviewRuleCommand(
                ruleId,
                item.ruleCode(),
                firstText(suggestion.get("suggestedRuleName"), "false_positive_" + item.cameraId() + "_" + item.zoneCode()),
                item.sourceSystem(),
                item.cameraId(),
                item.zoneCode(),
                item.objectLabel(),
                toInteger(suggestion.get("minStaySeconds")),
                null,
                null,
                true,
                toInteger(firstNonNull(suggestion.get("inertiaFrames"), previousRule.get("inertiaFrames"))),
                toInteger(firstNonNull(suggestion.get("loiteringSeconds"), previousRule.get("loiteringSeconds")))
        );
    }

    private static ReviewRuleCommand buildDisabledAppliedRuleCommand(ReviewItemAggregate item,
                                                                     Map<String, Object> suggestion,
                                                                     Long appliedRuleId) {
        Map<String, Object> appliedRule = toStringObjectMap(suggestion.get("appliedRule"));
        return new ReviewRuleCommand(
                appliedRuleId,
                firstText(appliedRule.get("ruleCode"), item.ruleCode()),
                firstText(appliedRule.get("ruleName"), "false_positive_" + item.cameraId() + "_" + item.zoneCode()),
                firstText(appliedRule.get("sourceSystem"), item.sourceSystem()),
                firstText(appliedRule.get("cameraId"), item.cameraId()),
                firstText(appliedRule.get("zoneCode"), item.zoneCode()),
                firstText(appliedRule.get("objectLabel"), item.objectLabel()),
                toInteger(appliedRule.get("minStaySeconds")),
                toLocalDateTime(appliedRule.get("activeStart")),
                toLocalDateTime(appliedRule.get("activeEnd")),
                false,
                toInteger(appliedRule.get("inertiaFrames")),
                toInteger(appliedRule.get("loiteringSeconds"))
        );
    }

    private static ReviewRuleCommand ruleCommandFromMap(Map<String, Object> rule, Long fallbackRuleId) {
        return new ReviewRuleCommand(
                toLong(rule.get("id")) == null ? fallbackRuleId : toLong(rule.get("id")),
                toText(rule.get("ruleCode")),
                toText(rule.get("ruleName")),
                toText(rule.get("sourceSystem")),
                toText(rule.get("cameraId")),
                toText(rule.get("zoneCode")),
                toText(rule.get("objectLabel")),
                toInteger(rule.get("minStaySeconds")),
                toLocalDateTime(rule.get("activeStart")),
                toLocalDateTime(rule.get("activeEnd")),
                toBoolean(rule.get("enabled")),
                toInteger(rule.get("inertiaFrames")),
                toInteger(rule.get("loiteringSeconds"))
        );
    }

    private static boolean sameRuleScope(ReviewRuleView rule, ReviewItemAggregate item) {
        return Objects.equals(rule.ruleCode(), item.ruleCode())
                && matchesText(rule.sourceSystem(), item.sourceSystem())
                && matchesText(rule.cameraId(), item.cameraId())
                && matchesText(rule.zoneCode(), item.zoneCode())
                && matchesText(rule.objectLabel(), item.objectLabel());
    }

    private static Map<String, Object> ruleToMap(ReviewRuleView rule) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", rule.id());
        values.put("ruleCode", rule.ruleCode());
        values.put("ruleName", rule.ruleName());
        values.put("sourceSystem", rule.sourceSystem());
        values.put("cameraId", rule.cameraId());
        values.put("zoneCode", rule.zoneCode());
        values.put("objectLabel", rule.objectLabel());
        values.put("minStaySeconds", rule.minStaySeconds());
        values.put("inertiaFrames", rule.inertiaFrames());
        values.put("loiteringSeconds", rule.loiteringSeconds());
        values.put("activeStart", rule.activeStart());
        values.put("activeEnd", rule.activeEnd());
        values.put("enabled", rule.enabled());
        values.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(values);
    }

    private static List<String> buildRuleSuggestionDiff(Map<String, Object> currentRule,
                                                        Map<String, Object> proposedRule) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(currentRule.keySet());
        keys.addAll(proposedRule.keySet());
        List<String> diff = new ArrayList<>();
        for (String key : keys) {
            Object currentValue = currentRule.get(key);
            Object proposedValue = proposedRule.get(key);
            if (!Objects.equals(currentValue, proposedValue)) {
                diff.add(key + ": " + currentValue + " -> " + proposedValue);
            }
        }
        return List.copyOf(diff);
    }

    Map<String, Object> buildRuleSuggestionSafetySummary(ReviewItemAggregate item) {
        List<ReviewItemAggregate> scopedItems = reviewItemQuery.listWorkbench(new ReviewQuery(
                null,
                item.cameraId(),
                item.zoneCode(),
                item.objectLabel(),
                null,
                null,
                null,
                null,
                null,
                null
        )).stream()
                .filter(candidate -> Objects.equals(candidate.ruleCode(), item.ruleCode()))
                .filter(candidate -> sameRuleScope(candidate, item))
                .toList();
        int beforeHitCount = Math.max(1, scopedItems.size());
        int existingFalsePositiveCount = (int) scopedItems.stream()
                .filter(candidate -> !Objects.equals(candidate.id(), item.id()))
                .filter(candidate -> STATUS_FALSE_POSITIVE.equals(candidate.reviewStatus()))
                .count();
        int currentSampleCount = existingFalsePositiveCount + 1;
        int afterEstimatedHitCount = 0;
        int possibleMissedCount = Math.max(0, beforeHitCount - currentSampleCount);

        Map<String, Object> impactScope = new LinkedHashMap<>();
        impactScope.put("cameraIds", distinctValues(withScopeValue(scopedItems.stream()
                .map(ReviewItemAggregate::cameraId)
                .toList(), item.cameraId())));
        impactScope.put("zoneCodes", distinctValues(withScopeValue(scopedItems.stream()
                .map(ReviewItemAggregate::zoneCode)
                .toList(), item.zoneCode())));
        impactScope.put("objectLabels", distinctValues(withScopeValue(scopedItems.stream()
                .map(ReviewItemAggregate::objectLabel)
                .toList(), item.objectLabel())));

        Map<String, Object> beforeAfter = new LinkedHashMap<>();
        beforeAfter.put("beforeHitCount", beforeHitCount);
        beforeAfter.put("afterEstimatedHitCount", afterEstimatedHitCount);
        beforeAfter.put("falsePositiveBeforeCount", currentSampleCount);
        beforeAfter.put("falsePositiveAfterCount", 0);
        beforeAfter.put("possibleMissedCount", possibleMissedCount);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("minimumSampleCount", MIN_RULE_SUGGESTION_SAMPLE_COUNT);
        summary.put("currentSampleCount", currentSampleCount);
        summary.put("sampleRequirementMet", currentSampleCount >= MIN_RULE_SUGGESTION_SAMPLE_COUNT);
        summary.put("riskNote", currentSampleCount < MIN_RULE_SUGGESTION_SAMPLE_COUNT
                ? "low_sample_requires_more_review"
                : possibleMissedCount > 0
                ? "possible_recall_loss_requires_replay"
                : "ready_for_shadow_evaluation");
        summary.put("impactScope", immutableNonNullMap(impactScope));
        summary.put("beforeAfterComparison", immutableNonNullMap(beforeAfter));
        return immutableNonNullMap(summary);
    }

    private Map<String, Object> withCurrentRuleSuggestionSafety(ReviewItemAggregate item,
                                                                Map<String, Object> suggestion) {
        Map<String, Object> refreshed = new LinkedHashMap<>(suggestion == null ? Map.of() : suggestion);
        refreshed.putAll(buildRuleSuggestionSafetySummary(item));
        return immutableNonNullMap(refreshed);
    }

    private static void requireRuleSuggestionSampleReady(Map<String, Object> suggestion) {
        if (Boolean.TRUE.equals(suggestion.get("sampleRequirementMet"))) {
            return;
        }
        Integer currentSampleCount = toInteger(suggestion.get("currentSampleCount"));
        Integer minimumSampleCount = toInteger(suggestion.get("minimumSampleCount"));
        throw new IllegalStateException("rule suggestion minimum sample requirement not met: "
                + (currentSampleCount == null ? "-" : currentSampleCount)
                + "/"
                + (minimumSampleCount == null ? "-" : minimumSampleCount));
    }

    private static List<String> withScopeValue(List<String> values, String value) {
        List<String> merged = new ArrayList<>(values == null ? List.of() : values);
        if (hasText(value)) {
            merged.add(value);
        }
        return merged;
    }

    private static List<String> ruleCandidateActions(Map<String, Object> suggestion) {
        Set<String> values = new LinkedHashSet<>();
        collectStringValues(values, suggestion == null ? null : suggestion.get("candidateActions"));
        if (values.isEmpty()) {
            return DEFAULT_RULE_CANDIDATE_ACTIONS;
        }
        return List.copyOf(values);
    }

    private static Map<String, Object> updateRuleSuggestionLifecycle(Map<String, Object> current,
                                                                     String status,
                                                                     String note) {
        Map<String, Object> suggestion = new LinkedHashMap<>(current == null ? Map.of() : current);
        suggestion.put("lifecycleStatus", status);
        if (hasText(note)) {
            suggestion.put("lifecycleNote", note);
        }
        if (RULE_SUGGESTION_ACCEPTED.equals(status) && hasText(note)) {
            suggestion.put("approvalNote", note);
        }
        if (RULE_SUGGESTION_APPLIED.equals(status) && hasText(note)) {
            suggestion.put("applyNote", note);
        }
        suggestion.put("lifecycleUpdatedAt", LocalDateTime.now().toString());
        return Map.copyOf(suggestion);
    }

    private static void requireRuleSuggestionTransition(ReviewItemAggregate item, String nextStatus) {
        String lifecycleStatus = item.ruleSuggestion() == null
                ? null
                : toText(item.ruleSuggestion().get("lifecycleStatus"));
        String currentStatus = hasText(item.ruleSuggestionStatus())
                ? item.ruleSuggestionStatus()
                : lifecycleStatus;
        Set<String> requiredCurrentStatuses = switch (nextStatus) {
            case RULE_SUGGESTION_SHADOW_EVALUATED -> Set.of(RULE_SUGGESTION_PENDING);
            case RULE_SUGGESTION_ACCEPTED -> Set.of(RULE_SUGGESTION_SHADOW_EVALUATED);
            case RULE_SUGGESTION_REJECTED -> Set.of(
                    RULE_SUGGESTION_PENDING,
                    RULE_SUGGESTION_SHADOW_EVALUATED
            );
            case RULE_SUGGESTION_APPLIED -> Set.of(RULE_SUGGESTION_ACCEPTED);
            case RULE_SUGGESTION_REVERTED -> Set.of(RULE_SUGGESTION_APPLIED);
            default -> Set.of();
        };
        if (requiredCurrentStatuses.contains(currentStatus)) {
            return;
        }
        throw new IllegalStateException("rule suggestion transition requires "
                + requiredCurrentStatuses + " -> " + nextStatus + ", current: " + currentStatus);
    }

    private static String ruleConfigVersion(Long ruleId, Map<String, Object> suggestion, String versionKey) {
        int version = toInteger(suggestion.get(versionKey)) == null ? 1 : toInteger(suggestion.get(versionKey)) + 1;
        Object current = suggestion.get(versionKey);
        if (current instanceof String value && value.startsWith("rule-" + ruleId + "-v")) {
            String raw = value.substring(("rule-" + ruleId + "-v").length());
            try {
                version = Integer.parseInt(raw) + 1;
            } catch (NumberFormatException ignored) {
                version = 1;
            }
        }
        return "rule-" + ruleId + "-v" + version;
    }

    private static String requireRuleSuggestionStatus(String status) {
        requireText(status, "status");
        if (List.of(RULE_SUGGESTION_PENDING, RULE_SUGGESTION_SHADOW_EVALUATED,
                RULE_SUGGESTION_ACCEPTED, RULE_SUGGESTION_REJECTED,
                RULE_SUGGESTION_APPLIED, RULE_SUGGESTION_REVERTED).contains(status)) {
            return status;
        }
        throw new IllegalArgumentException("unsupported rule suggestion status: " + status);
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = toText(value);
        return hasText(text) ? Boolean.parseBoolean(text) : null;
    }

    private record RuleSuggestionGroupKey(String cameraId,
                                          String zoneCode,
                                          String objectLabel) {
    }
}
