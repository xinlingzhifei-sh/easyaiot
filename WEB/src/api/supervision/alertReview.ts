import { defHttp } from '@/utils/http/axios'

const Api = {
  Items: '/system/supervision/alert-review/items',
  Ingest: '/system/supervision/alert-review/clues/ingest',
  Rules: '/system/supervision/alert-review/rules',
  Cases: '/system/supervision/alert-review/cases',
}

export interface AlertReviewItem {
  id: number
  reviewItemNo: string
  sourceSystem: string
  ruleCode: string
  sourceAlertType?: string
  deviceId?: string
  cameraId?: string
  zoneCode?: string
  objectLabel?: string
  firstAlertTime: string
  lastAlertTime: string
  alertCount: number
  sourceAlertIds: string[]
  reviewStatus: string
  reviewerUserId?: number
  reviewedAt?: string
  ignoreReason?: string
  eventId?: number
  convertedAt?: string
  recordEvidenceStatus?: 'not_required' | 'pending' | 'found' | 'missing' | 'failed' | string
  recordEvidenceCheckedAt?: string
  recordEvidenceMessage?: string
  eventStatus?: string
  closeCheckStatus?: string
  evidenceStatus?: string
  eventReviewStatus?: string
  inReviewCase?: boolean
  ruleSuggestionStatus?: AlertReviewRuleSuggestionLifecycleStatus
  ruleSuggestionUpdatedAt?: string
  reviewData?: Record<string, any>
  ruleSuggestion?: Record<string, any>
}

export type AlertReviewRuleSuggestionLifecycleStatus =
  | 'pending'
  | 'shadow_evaluated'
  | 'accepted'
  | 'rejected'
  | 'applied'
  | 'reverted'

export interface AlertReviewEvidence {
  reviewItemId: number
  sourceAlertId: string
  materialType: 'snapshot' | 'record' | string
  materialUri?: string
  happenedAt: string
}

export interface AlertReviewDetailStreamItem {
  reviewItemId: number
  sourceAlertId?: string
  cameraId?: string
  zoneCode?: string
  objectId?: string
  label?: string
  lifecycleEvent?: string
  happenedAt?: string
  seekTime?: string
  bbox?: number[]
  path?: Record<string, any>[]
  materialType?: string
  materialUri?: string
  metadata?: Record<string, any>
  recordStartTime?: string | null
  playbackOffsetSeconds?: number | null
}

export interface AlertReviewSegment {
  reviewItemId: number
  segmentId: string
  cameraId?: string
  severity?: string
  status?: string
  startTime?: string
  endTime?: string
  objectIds: string[]
  zones: string[]
  sourceAlertIds: string[]
  events: Record<string, any>[]
  metadata?: Record<string, any>
}

export interface AlertReviewQuery {
  reviewStatus?: string
  cameraId?: string
  zoneCode?: string
  objectLabel?: string
  recordEvidenceStatus?: string
  converted?: boolean
  inReviewCase?: boolean
  reviewerUserId?: number
  beginTime?: string
  endTime?: string
}

export interface AlertReviewClueIngest {
  sourceSystem: string
  sourceAlertId: string
  ruleCode?: string
  sourceAlertType?: string
  alertTime: string
  deviceId?: string
  cameraId?: string
  zoneCode?: string
  objectLabel?: string
  staySeconds?: number
  snapshotUri?: string
  recordUri?: string
  sourcePayloadHash?: string
  labels?: string[]
  zones?: string[]
  objectIds?: string[]
  confidence?: number
  bbox?: number[]
  correlationId?: string
  verifiedObjects?: string[]
  thumbTime?: string
  audioLabels?: string[]
  motionMetadata?: Record<string, any>
}

export interface AlertReviewOperation {
  reviewerUserId?: number
  reason?: string
}

export interface AlertReviewMediaReadQuery {
  reviewCaseId?: number
  operatorUserId?: number
  allowedCameraIds?: string[]
}

export interface AlertReviewUserStatusInput {
  userId: number
  hasBeenReviewed: boolean
}

export interface AlertReviewUserStatus {
  reviewItemId: number
  userId: number
  hasBeenReviewed: boolean
  reviewedAt?: string
}

export interface AlertReviewToEventResult {
  reviewItemId: number
  reviewStatus: string
  eventId: number
  reused: boolean
}

export interface AlertReviewCoverageSegment {
  status: 'available' | 'missing' | 'motion' | string
  startTime: string
  endTime: string
  motion?: number
  recordUri?: string
  objects?: number
  metadata?: Record<string, any>
}

export interface AlertReviewLifecycleUpdate {
  lifecycleState?: string
  happenedAt?: string
  objectIds?: string[]
  labels?: string[]
  zones?: string[]
  bbox?: number[]
  motionMetadata?: Record<string, any>
  recordUri?: string
}

export interface AlertReviewRecordStorageSyncRequest {
  operatorUserId?: number
  coverageSegments?: AlertReviewCoverageSegment[]
}

export interface AlertReviewRecordStorageSyncResult {
  reviewItemId: number
  syncStatus: string
  availableSegmentCount: number
  missingSegmentCount: number
  motionSegmentCount: number
  availableSeconds: number
  missingSeconds: number
  motionSeconds: number
  coverage: AlertReviewCoverageSegment[]
  syncedAt: string
  operatorUserId?: number
}

export interface AlertReviewCase {
  id: number
  caseNo: string
  title: string
  status: string
  primaryReviewItemId?: number
  reviewItemIds: number[]
  cameraIds: string[]
  startTime?: string
  endTime?: string
  ownerUserId?: number
  notes?: string
  version?: number
}

export interface AlertReviewCaseTimelineItem {
  reviewCaseId: number
  reviewItemId: number
  cameraId?: string
  sourceAlertId?: string
  materialType: 'snapshot' | 'record' | string
  materialUri?: string
  happenedAt: string
  recordStartTime?: string | null
  playbackOffsetSeconds?: number | null
  actionNote?: string
}

export interface AlertReviewCaseCreate {
  title?: string
  primaryReviewItemId?: number
  reviewItemIds: number[]
  ownerUserId?: number
  notes?: string
}

export interface AlertReviewCaseOwnerUpdate {
  ownerUserId?: number
  operatorUserId?: number
  notes?: string
  expectedVersion?: number
  operationId?: string
}

export interface AlertReviewCaseCloseOperation {
  operatorUserId?: number
  notes?: string
  expectedVersion?: number
  operationId?: string
}

export interface AlertReviewCaseMergeRequest {
  sourceReviewCaseId: number
  operatorUserId?: number
  notes?: string
  targetExpectedVersion?: number
  sourceExpectedVersion?: number
  operationId?: string
}

export interface AlertReviewCaseSplitRequest {
  reviewItemIds: number[]
  title?: string
  ownerUserId?: number
  operatorUserId?: number
  notes?: string
  sourceExpectedVersion?: number
  operationId?: string
}

export interface AlertReviewCaseMergeResult {
  targetCase: AlertReviewCase
  sourceCase: AlertReviewCase
}

export interface AlertReviewCaseSplitResult {
  sourceCase: AlertReviewCase
  newCase: AlertReviewCase
}

export interface AlertReviewSummary {
  total: number
  pendingReview: number
  reviewedByMe: number
  missingRecord: number
  converted: number
  inReviewCase: number
}

export interface AlertReviewRuleSuggestionStatus {
  reviewerUserId?: number
  status: 'pending' | 'shadow_evaluated' | 'accepted' | 'rejected' | 'applied' | 'reverted'
  note?: string
}

export interface AlertReviewRuleSuggestionPreview {
  reviewItemId: number
  currentRule: Record<string, any>
  proposedRule: Record<string, any>
  diff: string[]
  affectedReviewItemNos: string[]
}

export interface AlertReviewRuleSuggestionStat {
  cameraId?: string
  zoneCode?: string
  objectLabel?: string
  action: string
  falsePositiveCount: number
  totalCount: number
  falsePositiveRate: number
  candidateActions: string[]
  lastSeenAt?: string
}

export interface AlertReviewRule {
  id?: number
  ruleCode: string
  ruleName: string
  sourceSystem?: string
  cameraId?: string
  zoneCode?: string
  objectLabel?: string
  minStaySeconds?: number
  inertiaFrames?: number
  loiteringSeconds?: number
  activeStart?: string
  activeEnd?: string
  enabled?: boolean
}

export interface AlertReviewSemanticHit {
  item: AlertReviewItem
  score: number
  matchedTerms: string[]
  snippet: string
}

export interface AlertReviewSemanticIndexEntry {
  reviewItemId: number
  cameraId?: string
  firstAlertTime?: string
  lastAlertTime?: string
  embeddingKey?: string
  embeddingModel?: string
  embeddingVectorHash?: string
  indexStatus: string
  retryCount: number
  lastError?: string
  indexedAt?: string
}

export interface AlertReviewSemanticReindexJob {
  jobNo: string
  status: string
  queuedReviewItemIds: number[]
  queuedAt: string
  operatorUserId?: number
}

export interface AlertReviewSemanticIndexEvaluation {
  totalCount: number
  pendingCount: number
  indexedCount: number
  failedCount: number
  coverageRate: number
  staleReviewItemIds: number[]
  rebuildProgressRate?: number
  backlogAlarmLevel?: string
  recommendedActions: string[]
  evaluatedAt: string
  operatorUserId?: number
}

export interface AlertReviewSemanticTriggerEvaluation extends AlertReviewQuery {
  triggerName: string
  triggerType?: string
  data: string
  threshold?: number
  actions?: string[]
}

export interface AlertReviewSemanticTriggerConfirmation {
  confirmationStatus: 'confirmed' | 'rejected'
  notes?: string
}

export interface AlertReviewSemanticTriggerResult {
  triggerName: string
  triggerType?: string
  data: string
  matchedReviewItemIds: number[]
  actionPayloads: Record<string, any>[]
  evaluatedAt: string
  inputVersion: string
  latestIndexVersion: number
  hitExplanations: Record<string, any>[]
  actionPreviews: Record<string, any>[]
  humanConfirmationStatus: 'pending' | 'confirmed' | 'rejected' | string
  evaluationId: string
  confirmedBy?: number
  confirmedAt?: string
  duplicate?: boolean
}

export interface AlertReviewRecordGapReasonDefinition {
  code: string
  category: string
  labelZh: string
  retryable: boolean
  aliases?: string[]
}

export interface AlertReviewRuntimeHealth {
  totalCount: number
  missingRecordCount: number
  staleSemanticIndexCount: number
  failedExportJobCount: number
  missingRecordRate: number
  exportFailureRate: number
  semanticBacklogCount: number
  recordGapReasons?: Record<string, number>
  recordGapReasonCatalog?: Record<string, AlertReviewRecordGapReasonDefinition>
  repairableCount: number
  alerts: string[]
  measuredAt: string
  operatorUserId?: number
}

export interface AlertReviewReconciliationResult {
  scannedCount: number
  repairedRecordCount: number
  repairedSemanticIndexCount: number
  failedExportJobCount: number
  findings: string[]
  healthReport: AlertReviewRuntimeHealth
  reconciledAt: string
  operatorUserId?: number
}

export interface AlertReviewRuntimePatrolRequest extends AlertReviewQuery {
  operatorUserId?: number
  repair?: boolean
  maxAttempts?: number
  scheduled?: boolean
}

export interface AlertReviewRuntimePatrolResult {
  status: string
  lockAcquired: boolean
  maxAttempts: number
  attemptCount: number
  healthReport: AlertReviewRuntimeHealth
  reconciliationResult?: AlertReviewReconciliationResult
  alerts: string[]
  notifications: string[]
  recommendedActions: string[]
  executedAt: string
  operatorUserId?: number
  metadata: Record<string, any>
}

export interface AlertReviewOperationsReportRequest extends AlertReviewQuery {
  reportType?: 'shift' | 'daily' | string
  periodStart?: string
  periodEnd?: string
  operatorUserId?: number
}

export interface AlertReviewReportAcknowledgementRequest extends AlertReviewOperationsReportRequest {
  note?: string
}

export interface AlertReviewReportAcknowledgement {
  reportKey: string
  reportType: string
  status: string
  acknowledgedBy?: number
  acknowledgedAt?: string
  note?: string
  duplicate: boolean
  metadata: Record<string, any>
}

export interface AlertReviewOperationsReport {
  reportType: string
  reviewItemIds: number[]
  title: string
  summary: string
  evidenceGaps: string[]
  recommendedActions: string[]
  generatedAt: string
  operatorUserId?: number
  structuredData: Record<string, any>
  deliveryPlan: Record<string, any>
  acknowledgement: Record<string, any>
}

export interface AlertReviewAiSummary {
  reviewCaseId: number
  reviewItemIds: number[]
  title: string
  summary: string
  keyFacts: string[]
  evidenceGaps: string[]
  recommendedActions: string[]
  generatedAt: string
  generatedBy: string
  structuredData: Record<string, any>
}

export interface AlertReviewEvidenceExportRequest {
  reviewItemIds?: number[]
  operatorUserId?: number
  format?: string
  reason?: string
  approverUserId?: number
  approvalNote?: string
}

export interface AlertReviewEvidenceExportPackage {
  packageNo: string
  format: string
  reviewCaseId: number
  reviewItemIds: number[]
  evidenceUris: string[]
  timeline: AlertReviewCaseTimelineItem[]
  manifest: Record<string, any>
  generatedAt: string
}

export interface AlertReviewEvidenceExportJob {
  jobNo: string
  status: string
  exportPackage: AlertReviewEvidenceExportPackage
  fileHash: string
  expiresAt: string
  operatorUserId?: number
  reason?: string
  boundEventIds: number[]
  createdAt: string
}

export interface AlertReviewManifestVerification {
  jobNo: string
  valid: boolean
  expectedManifestHash?: string
  actualManifestHash?: string
  packageChecksum?: string
  violations: string[]
  verifiedAt: string
}

export interface AlertReviewEvidenceVerification {
  jobNo: string
  valid: boolean
  manifestVerification: AlertReviewManifestVerification
  manifestV2: Record<string, any>
  decisionTrail: Record<string, any>[]
  replayableReasons: string[]
  auditTrail: AlertReviewEvidenceAuditEntry[]
  verifiedAt: string
  operatorUserId?: number
}

export interface AlertReviewEvidenceAuditEntry {
  reviewCaseId?: number
  reviewItemId?: number
  actionType: string
  jobNo?: string
  fileHash?: string
  operatorUserId?: number
  actionNote?: string
  evidenceUris: string[]
  boundEventIds: number[]
  happenedAt: string
  metadata: Record<string, any>
}

export interface AlertReviewEvidenceAuditQuery {
  eventId?: number
  reviewCaseId?: number
  reviewItemId?: number
  exportJobNo?: string
}

export interface AlertReviewMediaAccessAuditRequest {
  reviewItemId?: number
  operatorUserId?: number
  cameraId?: string
  materialUri?: string
  actionType?: string
  allowedCameraIds?: string[]
  reason?: string
}

export interface AlertReviewMediaAccessAuditEntry {
  reviewCaseId?: number
  reviewItemId?: number
  operatorUserId?: number
  cameraId?: string
  materialUri?: string
  actionType?: string
  decision: string
  deniedReasons: string[]
  happenedAt: string
  metadata: Record<string, any>
}

export interface AlertReviewPlaybackUrlRequest {
  reviewCaseId?: number
  operatorUserId?: number
  materialUri?: string
  allowedCameraIds?: string[]
  reason?: string
}

export interface AlertReviewPlaybackAccess {
  reviewCaseId?: number
  reviewItemId?: number
  operatorUserId?: number
  cameraId?: string
  materialUri?: string
  playbackUrl?: string
  decision: string
  deniedReasons: string[]
  audit?: AlertReviewMediaAccessAuditEntry
}

export interface AlertReviewRuleReplayRequest {
  ruleCode: string
  sourceSystem?: string
  cameraId?: string
  zoneCode?: string
  objectLabel?: string
  minStaySeconds?: number
  beginTime?: string
  endTime?: string
  operatorUserId?: number
}

export interface AlertReviewRuleReplayResult {
  ruleCode: string
  evaluatedReviewItemIds: number[]
  evaluatedCount: number
  matchBeforeCount: number
  matchAfterCount: number
  falsePositiveBeforeCount: number
  falsePositiveBeforeRate: number
  falsePositiveAfterRate: number
  recommendedActions: string[]
  scope: Record<string, any>
  report: Record<string, any>
  replayedAt: string
}

export interface AlertReviewRuleGeometryRequest {
  ruleCode: string
  cameraId?: string
  zoneCode?: string
  polygon?: number[][]
  bbox?: number[]
  objectLabel?: string
  beginTime?: string
  endTime?: string
  operatorUserId?: number
}

export interface AlertReviewRuleGeometryEvaluation {
  geometryType: string
  inside: boolean
  evaluatedPoint: number[]
  zoneCode?: string
  replayedReviewItemIds: number[]
  ruleVersion: Record<string, any>
  consistencyChecks: string[]
  evaluatedAt: string
  matchTraces: Record<string, any>[]
}

export interface AlertReviewIntegrationSmokeRequest {
  operatorUserId?: number
  includeVideoExport?: boolean
  alertTime?: string
  profile?: string
}

export interface AlertReviewIntegrationSmokeResult {
  status: string
  reviewItemId: number
  eventId: number
  reviewCaseId: number
  exportJobNo: string
  manifestValid: boolean
  videoExportRequested: boolean
  checkpoints: string[]
  executedAt: string
  operatorUserId?: number
  profile?: string
}

export function listAlertReviewItems(params?: AlertReviewQuery) {
  return defHttp.get<AlertReviewItem[]>({ url: Api.Items, params })
}

export function getAlertReviewSummary(params?: Pick<AlertReviewQuery, 'reviewerUserId' | 'beginTime' | 'endTime'>) {
  return defHttp.get<AlertReviewSummary>({ url: '/system/supervision/alert-review/summary', params })
}

export function ingestAlertReviewClue(data: AlertReviewClueIngest) {
  return defHttp.post<AlertReviewItem>({ url: Api.Ingest, data })
}

export function getAlertReviewTimeline(reviewItemId: number, params?: AlertReviewMediaReadQuery) {
  return defHttp.get<AlertReviewEvidence[]>({ url: `${Api.Items}/${reviewItemId}/timeline`, params: params ?? {} })
}

export function getAlertReviewDetailStream(reviewItemId: number, params?: AlertReviewMediaReadQuery) {
  return defHttp.get<AlertReviewDetailStreamItem[]>({ url: `${Api.Items}/${reviewItemId}/detail-stream`, params: params ?? {} })
}

export function getAlertReviewSegment(reviewItemId: number) {
  return defHttp.get<AlertReviewSegment>({ url: `${Api.Items}/${reviewItemId}/review-segment` })
}

export function getAlertReviewRecordCoverage(reviewItemId: number, params?: AlertReviewMediaReadQuery) {
  return defHttp.get<AlertReviewCoverageSegment[]>({ url: `${Api.Items}/${reviewItemId}/record-coverage`, params: params ?? {} })
}

export function updateAlertReviewLifecycle(reviewItemId: number, data: AlertReviewLifecycleUpdate) {
  return defHttp.post<AlertReviewItem>({ url: `${Api.Items}/${reviewItemId}/lifecycle`, data })
}

export function syncAlertReviewRecordStorage(reviewItemId: number, data?: AlertReviewRecordStorageSyncRequest) {
  return defHttp.post<AlertReviewRecordStorageSyncResult>({
    url: `${Api.Items}/${reviewItemId}/record-storage/sync`,
    data: data ?? {},
  })
}

export function retryAlertReviewRecordEvidence(reviewItemId: number) {
  return defHttp.post<AlertReviewItem>({ url: `${Api.Items}/${reviewItemId}/record-evidence/retry` })
}

export function markAlertReviewReviewed(reviewItemId: number, data?: AlertReviewOperation) {
  return defHttp.post<AlertReviewItem>({ url: `${Api.Items}/${reviewItemId}/review`, data: data ?? {} })
}

export function markAlertReviewUserStatus(reviewItemId: number, data: AlertReviewUserStatusInput) {
  return defHttp.post<AlertReviewUserStatus>({ url: `${Api.Items}/${reviewItemId}/user-status`, data })
}

export function ignoreAlertReviewItem(reviewItemId: number, data?: AlertReviewOperation) {
  return defHttp.post<AlertReviewItem>({ url: `${Api.Items}/${reviewItemId}/ignore`, data: data ?? {} })
}

export function markAlertReviewFalsePositive(reviewItemId: number, data?: AlertReviewOperation) {
  return defHttp.post<AlertReviewItem>({ url: `${Api.Items}/${reviewItemId}/false-positive`, data: data ?? {} })
}

export function updateAlertReviewRuleSuggestionStatus(reviewItemId: number, data: AlertReviewRuleSuggestionStatus) {
  return defHttp.post<AlertReviewItem>({ url: `${Api.Items}/${reviewItemId}/rule-suggestion/status`, data })
}

export function previewAlertReviewRuleSuggestion(reviewItemId: number) {
  return defHttp.get<AlertReviewRuleSuggestionPreview>({
    url: `${Api.Items}/${reviewItemId}/rule-suggestion/preview`,
  })
}

export function revertAlertReviewRuleSuggestion(reviewItemId: number, data: AlertReviewRuleSuggestionStatus) {
  return defHttp.post<AlertReviewItem>({ url: `${Api.Items}/${reviewItemId}/rule-suggestion/revert`, data })
}

export function listAlertReviewRuleSuggestionStats(
  params?: Pick<AlertReviewQuery, 'cameraId' | 'zoneCode' | 'objectLabel' | 'beginTime' | 'endTime'>,
) {
  return defHttp.get<AlertReviewRuleSuggestionStat[]>({
    url: '/system/supervision/alert-review/rule-suggestions/stats',
    params,
  })
}

export function semanticSearchAlertReview(params: AlertReviewQuery & { q: string; limit?: number }) {
  return defHttp.get<AlertReviewSemanticHit[]>({
    url: '/system/supervision/alert-review/semantic-search',
    params,
  })
}

export function evaluateAlertReviewSemanticTrigger(data: AlertReviewSemanticTriggerEvaluation) {
  return defHttp.post<AlertReviewSemanticTriggerResult>({
    url: '/system/supervision/alert-review/semantic-triggers/evaluate',
    data,
  })
}

export function getAlertReviewSemanticTrigger(evaluationId: string) {
  return defHttp.get<AlertReviewSemanticTriggerResult>({
    url: `/system/supervision/alert-review/semantic-triggers/${evaluationId}`,
  })
}

export function confirmAlertReviewSemanticTrigger(
  evaluationId: string,
  data: AlertReviewSemanticTriggerConfirmation,
) {
  return defHttp.post<AlertReviewSemanticTriggerResult>({
    url: `/system/supervision/alert-review/semantic-triggers/${evaluationId}/confirmation`,
    data,
  })
}

export function reindexAlertReviewSemanticIndex(params?: AlertReviewQuery) {
  return defHttp.post<AlertReviewSemanticIndexEntry[]>({
    url: '/system/supervision/alert-review/semantic-index/reindex',
    params,
  })
}

export function queueAlertReviewSemanticReindex(params?: AlertReviewQuery & { operatorUserId?: number }) {
  return defHttp.post<AlertReviewSemanticReindexJob>({
    url: '/system/supervision/alert-review/semantic-index/queue',
    params,
  })
}

export function evaluateAlertReviewSemanticIndex(params?: AlertReviewQuery & { operatorUserId?: number }) {
  return defHttp.get<AlertReviewSemanticIndexEvaluation>({
    url: '/system/supervision/alert-review/semantic-index/evaluation',
    params,
  })
}

export function getAlertReviewRuntimeHealth(params?: AlertReviewQuery & { operatorUserId?: number }) {
  return defHttp.get<AlertReviewRuntimeHealth>({
    url: '/system/supervision/alert-review/runtime-health',
    params,
  })
}

export function reconcileAlertReviewRuntime(params?: AlertReviewQuery & { operatorUserId?: number; repair?: boolean }) {
  return defHttp.post<AlertReviewReconciliationResult>({
    url: '/system/supervision/alert-review/runtime-reconcile',
    params,
  })
}

export function runAlertReviewRuntimePatrol(data?: AlertReviewRuntimePatrolRequest) {
  return defHttp.post<AlertReviewRuntimePatrolResult>({
    url: '/system/supervision/alert-review/runtime-patrol',
    data: data ?? {},
  })
}

export function generateAlertReviewOperationsReport(data?: AlertReviewOperationsReportRequest) {
  return defHttp.post<AlertReviewOperationsReport>({
    url: '/system/supervision/alert-review/operations-report',
    data: data ?? {},
  })
}

export function acknowledgeAlertReviewOperationsReport(data?: AlertReviewReportAcknowledgementRequest) {
  return defHttp.post<AlertReviewReportAcknowledgement>({
    url: '/system/supervision/alert-review/operations-report/acknowledgement',
    data: data ?? {},
  })
}

export function convertAlertReviewToEvent(reviewItemId: number, data?: AlertReviewOperation) {
  return defHttp.post<AlertReviewToEventResult>({ url: `${Api.Items}/${reviewItemId}/to-event`, data: data ?? {} })
}

export function createAlertReviewCase(data: AlertReviewCaseCreate) {
  return defHttp.post<AlertReviewCase>({ url: Api.Cases, data })
}

export function getAlertReviewItemCase(reviewItemId: number) {
  return defHttp.get<AlertReviewCase | null>({ url: `${Api.Items}/${reviewItemId}/case` })
}

export function addAlertReviewItemToCase(reviewCaseId: number, reviewItemId: number) {
  return defHttp.post<AlertReviewCase>({ url: `${Api.Cases}/${reviewCaseId}/items/${reviewItemId}` })
}

export function assignAlertReviewCaseOwner(reviewCaseId: number, data: AlertReviewCaseOwnerUpdate) {
  return defHttp.post<AlertReviewCase>({ url: `${Api.Cases}/${reviewCaseId}/owner`, data })
}

export function closeAlertReviewCase(reviewCaseId: number, data?: AlertReviewCaseCloseOperation) {
  return defHttp.post<AlertReviewCase>({ url: `${Api.Cases}/${reviewCaseId}/close`, data: data ?? {} })
}

export function mergeAlertReviewCases(targetReviewCaseId: number, data: AlertReviewCaseMergeRequest) {
  return defHttp.post<AlertReviewCaseMergeResult>({ url: `${Api.Cases}/${targetReviewCaseId}/merge`, data })
}

export function splitAlertReviewCase(sourceReviewCaseId: number, data: AlertReviewCaseSplitRequest) {
  return defHttp.post<AlertReviewCaseSplitResult>({ url: `${Api.Cases}/${sourceReviewCaseId}/split`, data })
}

export function getAlertReviewCaseTimeline(reviewCaseId: number) {
  return defHttp.get<AlertReviewCaseTimelineItem[]>({ url: `${Api.Cases}/${reviewCaseId}/timeline` })
}

export function summarizeAlertReviewCase(reviewCaseId: number, operatorUserId?: number) {
  return defHttp.get<AlertReviewAiSummary>({
    url: `${Api.Cases}/${reviewCaseId}/ai-summary`,
    params: { operatorUserId },
  })
}

export function exportAlertReviewEvidence(reviewCaseId: number, data?: AlertReviewEvidenceExportRequest) {
  return defHttp.post<AlertReviewEvidenceExportPackage>({
    url: `${Api.Cases}/${reviewCaseId}/evidence-export`,
    data: data ?? {},
  })
}

export function createAlertReviewEvidenceExportJob(reviewCaseId: number, data?: AlertReviewEvidenceExportRequest) {
  return defHttp.post<AlertReviewEvidenceExportJob>({
    url: `${Api.Cases}/${reviewCaseId}/evidence-export-jobs`,
    data: data ?? {},
  })
}

export function getAlertReviewEvidenceAudit(reviewCaseId: number) {
  return defHttp.get<AlertReviewEvidenceAuditEntry[]>({ url: `${Api.Cases}/${reviewCaseId}/evidence-audit` })
}

export function getAlertReviewItemEvidenceAudit(reviewItemId: number) {
  return defHttp.get<AlertReviewEvidenceAuditEntry[]>({ url: `${Api.Items}/${reviewItemId}/evidence-audit` })
}

export function queryAlertReviewEvidenceAudit(query: AlertReviewEvidenceAuditQuery) {
  return defHttp.get<AlertReviewEvidenceAuditEntry[]>({
    url: '/system/supervision/alert-review/evidence-audit',
    params: query,
  })
}

export function verifyAlertReviewManifest(jobNo: string) {
  return defHttp.get<AlertReviewManifestVerification>({
    url: `/system/supervision/alert-review/evidence-export-jobs/${jobNo}/manifest/verify`,
  })
}

export function verifyAlertReviewEvidencePackage(jobNo: string, operatorUserId?: number) {
  return defHttp.get<AlertReviewEvidenceVerification>({
    url: `/system/supervision/alert-review/evidence-export-jobs/${jobNo}/verify`,
    params: { operatorUserId },
  })
}

export function auditAlertReviewMediaAccess(reviewCaseId: number, data?: AlertReviewMediaAccessAuditRequest) {
  return defHttp.post<AlertReviewMediaAccessAuditEntry>({
    url: `${Api.Cases}/${reviewCaseId}/media-access/audit`,
    data: data ?? {},
  })
}

export function auditAlertReviewItemMediaAccess(reviewItemId: number, data?: AlertReviewMediaAccessAuditRequest) {
  return defHttp.post<AlertReviewMediaAccessAuditEntry>({
    url: `${Api.Items}/${reviewItemId}/media-access/audit`,
    data: data ?? {},
  })
}

export function prepareAlertReviewPlaybackUrl(reviewItemId: number, params?: AlertReviewPlaybackUrlRequest) {
  return defHttp.get<AlertReviewPlaybackAccess>({
    url: `${Api.Items}/${reviewItemId}/playback-url`,
    params: params ?? {},
  })
}

export function replayAlertReviewRule(data: AlertReviewRuleReplayRequest) {
  return defHttp.post<AlertReviewRuleReplayResult>({
    url: '/system/supervision/alert-review/rules/replay',
    data,
  })
}

export function evaluateAlertReviewRuleGeometry(data: AlertReviewRuleGeometryRequest) {
  return defHttp.post<AlertReviewRuleGeometryEvaluation>({
    url: '/system/supervision/alert-review/rules/geometry-evaluate',
    data,
  })
}

export function runAlertReviewIntegrationSmoke(data?: AlertReviewIntegrationSmokeRequest) {
  return defHttp.post<AlertReviewIntegrationSmokeResult>({
    url: '/system/supervision/alert-review/integration-smoke',
    data: data ?? {},
  })
}

export function suggestAlertReviewCaseCandidates(reviewItemId: number) {
  return defHttp.get<AlertReviewItem[]>({ url: `${Api.Items}/${reviewItemId}/case-candidates` })
}

export function saveAlertReviewRule(data: AlertReviewRule) {
  return defHttp.post<AlertReviewRule>({ url: Api.Rules, data })
}

export function listAlertReviewRules() {
  return defHttp.get<AlertReviewRule[]>({ url: Api.Rules })
}
