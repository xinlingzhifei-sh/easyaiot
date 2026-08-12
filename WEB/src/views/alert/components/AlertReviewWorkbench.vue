<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import moment from 'moment'
import { Button } from '@/components/Button'
import { Icon } from '@/components/Icon'
import { useMessage } from '@/hooks/web/useMessage'
import { usePermission } from '@/hooks/web/usePermission'
import DeviceRegionDrawer from '@/views/camera/components/DeviceRegionDrawer/index.vue'
import type { DeviceDetectionRegion } from '@/api/device/device_detection_region'
import {
  type AlertReviewAiSummary,
  type AlertReviewCase,
  type AlertReviewCaseTimelineItem,
  type AlertReviewCoverageSegment,
  type AlertReviewDetailStreamItem,
  type AlertReviewEvidence,
  type AlertReviewEvidenceAuditEntry,
  type AlertReviewEvidenceExportJob,
  type AlertReviewEvidenceExportPackage,
  type AlertReviewEvidenceVerification,
  type AlertReviewIntegrationSmokeResult,
  type AlertReviewItem,
  type AlertReviewOperationsReport,
  type AlertReviewReportAcknowledgement,
  type AlertReviewReconciliationResult,
  type AlertReviewRuleGeometryEvaluation,
  type AlertReviewRuleReplayResult,
  type AlertReviewRuleSuggestionStatus,
  type AlertReviewRuleSuggestionPreview,
  type AlertReviewRuntimeHealth,
  type AlertReviewRuntimePatrolResult,
  type AlertReviewSegment,
  type AlertReviewSemanticHit,
  type AlertReviewSemanticIndexEvaluation,
  type AlertReviewSemanticTriggerResult,
  type AlertReviewSummary,
  addAlertReviewItemToCase,
  acknowledgeAlertReviewOperationsReport,
  assignAlertReviewCaseOwner,
  auditAlertReviewItemMediaAccess,
  auditAlertReviewMediaAccess,
  convertAlertReviewToEvent,
  closeAlertReviewCase,
  createAlertReviewCase,
  createAlertReviewEvidenceExportJob,
  evaluateAlertReviewRuleGeometry,
  evaluateAlertReviewSemanticIndex,
  evaluateAlertReviewSemanticTrigger,
  generateAlertReviewOperationsReport,
  getAlertReviewCaseTimeline,
  getAlertReviewItemCase,
  getAlertReviewDetailStream,
  getAlertReviewEvidenceAudit,
  getAlertReviewRecordCoverage,
  getAlertReviewRuntimeHealth,
  getAlertReviewSemanticTrigger,
  getAlertReviewSegment,
  getAlertReviewSummary,
  getAlertReviewTimeline,
  ignoreAlertReviewItem,
  listAlertReviewItems,
  markAlertReviewFalsePositive,
  markAlertReviewReviewed,
  markAlertReviewUserStatus,
  mergeAlertReviewCases,
  prepareAlertReviewPlaybackUrl,
  previewAlertReviewRuleSuggestion,
  replayAlertReviewRule,
  reconcileAlertReviewRuntime,
  retryAlertReviewRecordEvidence,
  revertAlertReviewRuleSuggestion,
  runAlertReviewIntegrationSmoke,
  runAlertReviewRuntimePatrol,
  saveAlertReviewRule,
  semanticSearchAlertReview,
  confirmAlertReviewSemanticTrigger,
  suggestAlertReviewCaseCandidates,
  splitAlertReviewCase,
  summarizeAlertReviewCase,
  updateAlertReviewRuleSuggestionStatus,
  verifyAlertReviewEvidencePackage,
} from '@/api/supervision/alertReview'

defineOptions({ name: 'AlertReviewWorkbench' })

const emit = defineEmits<{
  viewImage: [record: Record<string, any>]
  viewVideo: [record: Record<string, any>]
  converted: [item: AlertReviewItem]
}>()

const { createMessage, createConfirm } = useMessage()
const { hasPermission } = usePermission()

const RULE_SUGGESTION_UPDATE_PERMISSION = 'system:supervision-alert-review:rule-suggestion:update'
const RULE_SUGGESTION_REVERT_PERMISSION = 'system:supervision-alert-review:rule-suggestion:revert'
const RULE_REPLAY_PERMISSION = 'system:supervision-alert-review:rules:replay'
const SEMANTIC_TRIGGER_EVALUATE_PERMISSION = 'system:supervision-alert-review:semantic-trigger:evaluate'
const SEMANTIC_TRIGGER_CONFIRM_PERMISSION = 'system:supervision-alert-review:semantic-trigger:confirm'
const SEMANTIC_TRIGGER_EVALUATION_STORAGE_KEY = 'yfeieye.alert-review.semantic-trigger-evaluation-id'
const canUpdateRuleSuggestion = computed(() => hasPermission(RULE_SUGGESTION_UPDATE_PERMISSION))
const canRevertRuleSuggestion = computed(() => hasPermission(RULE_SUGGESTION_REVERT_PERMISSION))
const canReplayRule = computed(() => hasPermission(RULE_REPLAY_PERMISSION))
const canEvaluateSemanticTrigger = computed(() => hasPermission(SEMANTIC_TRIGGER_EVALUATE_PERMISSION))
const canConfirmSemanticTrigger = computed(() => hasPermission(SEMANTIC_TRIGGER_CONFIRM_PERMISSION))

const statusOptions = [
  { value: 'pending_review', label: '待复核' },
  { value: 'reviewed', label: '已复核' },
  { value: 'ignored', label: '已忽略' },
  { value: 'false_positive', label: '误报' },
  { value: 'converted', label: '已转事件' },
]

const filters = reactive({
  reviewStatus: 'pending_review',
  cameraId: '',
  zoneCode: '',
  objectLabel: '',
  recordEvidenceStatus: '',
  inReviewCase: '',
  reviewerUserId: undefined as number | undefined,
})

const caseLifecycleForm = reactive({
  ownerUserId: undefined as number | undefined,
  sourceReviewCaseId: undefined as number | undefined,
  splitTitle: '',
})

const loading = ref(false)
const timelineLoading = ref(false)
const detailStreamLoading = ref(false)
const reviewSegmentLoading = ref(false)
const coverageLoading = ref(false)
const caseLoading = ref(false)
const caseLifecycleLoading = ref(false)
const recordRetryLoading = ref(false)
const userStatusLoading = ref(false)
const semanticLoading = ref(false)
const semanticTriggerLoading = ref(false)
const semanticTriggerDecisionLoading = ref(false)
const rulePreviewLoading = ref(false)
const ruleReplayLoading = ref(false)
const aiSummaryLoading = ref(false)
const evidenceExportLoading = ref(false)
const evidenceAuditLoading = ref(false)
const opsLoading = ref(false)
const items = ref<AlertReviewItem[]>([])
const timeline = ref<AlertReviewEvidence[]>([])
const detailStream = ref<AlertReviewDetailStreamItem[]>([])
const reviewSegment = ref<AlertReviewSegment | null>(null)
const coverage = ref<AlertReviewCoverageSegment[]>([])
const activeCase = ref<AlertReviewCase | null>(null)
const caseTimeline = ref<AlertReviewCaseTimelineItem[]>([])
const evidenceAudit = ref<AlertReviewEvidenceAuditEntry[]>([])
const caseCandidates = ref<AlertReviewItem[]>([])
const semanticQuery = ref('')
const semanticHits = ref<AlertReviewSemanticHit[]>([])
const semanticTriggerResult = ref<AlertReviewSemanticTriggerResult | null>(null)
const semanticTriggerForm = reactive({
  triggerName: '',
  data: '',
  threshold: 0.5,
  actions: ['notification'] as string[],
  notes: '',
})
const rulePreview = ref<AlertReviewRuleSuggestionPreview | null>(null)
const ruleReplay = ref<AlertReviewRuleReplayResult | null>(null)
const aiSummary = ref<AlertReviewAiSummary | null>(null)
const evidenceExport = ref<AlertReviewEvidenceExportPackage | null>(null)
const evidenceExportJob = ref<AlertReviewEvidenceExportJob | null>(null)
const opsHealth = ref<AlertReviewRuntimeHealth | null>(null)
const semanticIndexEvaluation = ref<AlertReviewSemanticIndexEvaluation | null>(null)
const opsReconciliation = ref<AlertReviewReconciliationResult | null>(null)
const opsPatrol = ref<AlertReviewRuntimePatrolResult | null>(null)
const opsSmoke = ref<AlertReviewIntegrationSmokeResult | null>(null)
const opsVerification = ref<AlertReviewEvidenceVerification | null>(null)
const opsGeometry = ref<AlertReviewRuleGeometryEvaluation | null>(null)
const opsReport = ref<AlertReviewOperationsReport | null>(null)
const opsReportAck = ref<AlertReviewReportAcknowledgement | null>(null)
const opsReportAcknowledgement = computed(() => opsReportAck.value || opsReport.value?.acknowledgement || null)
const opsReportAcknowledgementText = computed(() => {
  const acknowledgement = opsReportAcknowledgement.value as Record<string, any> | null
  if (acknowledgement?.status === 'acknowledged')
    return `acknowledged by ${acknowledgement.acknowledgedBy || '-'}`
  return `${acknowledgement?.status || 'pending'} acknowledgement`
})
const summary = ref<AlertReviewSummary>({
  total: 0,
  pendingReview: 0,
  reviewedByMe: 0,
  missingRecord: 0,
  converted: 0,
  inReviewCase: 0,
})
const selectedItem = ref<AlertReviewItem | null>(null)
const ruleDrawerVisible = ref(false)
const ruleDrawerItem = ref<AlertReviewItem | null>(null)
const ruleDrawerDeviceId = computed(() => ruleDrawerItem.value?.cameraId || ruleDrawerItem.value?.deviceId || '')

type UnifiedTimelineKind = 'object' | 'coverage' | 'evidence' | 'case' | 'export'

interface UnifiedTimelineEntry {
  key: string
  kind: UnifiedTimelineKind
  icon: string
  className: string
  title: string
  startTime?: string
  endTime?: string
  subtitle?: string
  uri?: string
  status?: string
  meta: string[]
  actionLabel?: string
  payload?: unknown
}

const unifiedTimeline = computed<UnifiedTimelineEntry[]>(() => {
  const entries: UnifiedTimelineEntry[] = []

  detailStream.value.forEach(entry => {
    const title = entry.label || entry.objectId || entry.lifecycleEvent || 'object lifecycle'
    entries.push({
      key: `object-${detailStreamKey(entry)}`,
      kind: 'object',
      icon: entry.materialType === 'snapshot' ? 'ion:image-sharp' : 'icon-park-outline:target',
      className: `object ${entry.lifecycleEvent || ''}`,
      title,
      startTime: entry.seekTime || entry.happenedAt,
      subtitle: `${entry.cameraId || selectedItem.value?.cameraId || selectedItem.value?.deviceId || '-'} / ${entry.zoneCode || '-'}`,
      uri: entry.materialUri,
      meta: [
        entry.objectId ? `object ${entry.objectId}` : '',
        entry.lifecycleEvent ? `event ${entry.lifecycleEvent}` : '',
      ].filter(Boolean),
      actionLabel: entry.materialUri ? 'Seek' : undefined,
      payload: entry,
    })
  })

  coverage.value.forEach(segment => {
    entries.push({
      key: `coverage-${coverageKey(segment)}`,
      kind: 'coverage',
      icon: segment.status === 'missing' ? 'ant-design:warning-outlined' : 'icon-park-outline:timeline',
      className: `coverage ${segment.status || ''}`,
      title: coverageStatusText(segment.status),
      startTime: segment.startTime,
      endTime: segment.endTime,
      subtitle: selectedItem.value?.cameraId || selectedItem.value?.deviceId || '-',
      uri: segment.recordUri,
      status: segment.status,
      meta: [
        segment.motion !== undefined ? `motion ${segment.motion}` : '',
        segment.objects !== undefined ? `objects ${segment.objects}` : '',
      ].filter(Boolean),
      actionLabel: segment.recordUri ? 'Open record' : undefined,
      payload: segment,
    })
  })

  timeline.value.forEach(evidence => {
    entries.push({
      key: `evidence-${evidenceKey(evidence)}`,
      kind: 'evidence',
      icon: evidence.materialType === 'snapshot' ? 'ion:image-sharp' : 'icon-park-outline:video',
      className: `evidence ${evidence.materialType || ''}`,
      title: evidence.materialType === 'snapshot' ? 'snapshot' : 'record',
      startTime: evidence.happenedAt,
      subtitle: evidence.sourceAlertId || '-',
      uri: evidence.materialUri,
      meta: [evidence.materialType].filter(Boolean),
      actionLabel: evidence.materialUri ? 'Open' : undefined,
      payload: evidence,
    })
  })

  caseTimeline.value.forEach(evidence => {
    entries.push({
      key: `case-${caseTimelineKey(evidence)}`,
      kind: 'case',
      icon: evidence.materialType === 'snapshot' ? 'ion:image-sharp' : 'icon-park-outline:play-cycle',
      className: `case ${evidence.materialType || ''}`,
      title: evidence.materialType || 'case event',
      startTime: evidence.happenedAt,
      subtitle: `${evidence.cameraId || '-'} / ${evidence.sourceAlertId || '-'}`,
      uri: evidence.materialUri,
      meta: [evidence.actionNote || ''].filter(Boolean),
      actionLabel: evidence.materialUri ? 'Open' : undefined,
      payload: evidence,
    })
  })

  exportTimelineEntries().forEach(entry => entries.push(entry))

  return entries.sort((left, right) => {
    const leftTime = moment(left.startTime || left.endTime || 0).valueOf()
    const rightTime = moment(right.startTime || right.endTime || 0).valueOf()
    return leftTime - rightTime
  })
})

onMounted(() => {
  loadItems()
  loadOpsHealth()
  restoreSemanticTriggerPreview()
})

async function loadItems() {
  loading.value = true
  try {
    items.value = await listAlertReviewItems({
      reviewStatus: filters.reviewStatus || undefined,
      cameraId: filters.cameraId || undefined,
      zoneCode: filters.zoneCode || undefined,
      objectLabel: filters.objectLabel || undefined,
      recordEvidenceStatus: filters.recordEvidenceStatus || undefined,
      inReviewCase: filters.inReviewCase ? filters.inReviewCase === 'true' : undefined,
      reviewerUserId: filters.reviewerUserId || undefined,
    })
    summary.value = await getAlertReviewSummary({
      reviewerUserId: filters.reviewerUserId || undefined,
    })
    if (selectedItem.value) {
      const nextSelected = items.value.find(item => item.id === selectedItem.value?.id) ?? null
      selectedItem.value = nextSelected
    }
  }
  catch (error: any) {
    createMessage.error(error?.message || '加载复核线索失败')
  }
  finally {
    loading.value = false
  }
}

function currentReviewQuery() {
  return {
    reviewStatus: filters.reviewStatus || undefined,
    cameraId: filters.cameraId || undefined,
    zoneCode: filters.zoneCode || undefined,
    objectLabel: filters.objectLabel || undefined,
    recordEvidenceStatus: filters.recordEvidenceStatus || undefined,
    inReviewCase: filters.inReviewCase ? filters.inReviewCase === 'true' : undefined,
    reviewerUserId: filters.reviewerUserId || undefined,
  }
}

function currentOperationsReportRequest() {
  return {
    ...currentReviewQuery(),
    reportType: 'shift',
    operatorUserId: filters.reviewerUserId,
  }
}

async function loadOpsHealth() {
  opsLoading.value = true
  try {
    const query = currentReviewQuery()
    const [health, semanticEvaluation, report] = await Promise.all([
      getAlertReviewRuntimeHealth(query),
      evaluateAlertReviewSemanticIndex(query),
      generateAlertReviewOperationsReport(currentOperationsReportRequest()),
    ])
    opsHealth.value = health
    semanticIndexEvaluation.value = semanticEvaluation
    opsReport.value = report
    opsReportAck.value = null
  }
  catch (error: any) {
    createMessage.error(error?.message || 'runtime health failed')
  }
  finally {
    opsLoading.value = false
  }
}

async function runOpsReportAcknowledgement() {
  opsLoading.value = true
  try {
    const acknowledgement = await acknowledgeAlertReviewOperationsReport({
      ...currentOperationsReportRequest(),
      note: 'workbench_acknowledged',
    })
    opsReportAck.value = acknowledgement
    if (opsReport.value) {
      opsReport.value = {
        ...opsReport.value,
        acknowledgement: {
          ...opsReport.value.acknowledgement,
          ...acknowledgement,
        },
      }
    }
    createMessage.success('operations report acknowledged')
  }
  catch (error: any) {
    createMessage.error(error?.message || 'operations report acknowledgement failed')
  }
  finally {
    opsLoading.value = false
  }
}

async function runOpsReconcile() {
  opsLoading.value = true
  try {
    const result = await reconcileAlertReviewRuntime({
      ...currentReviewQuery(),
      repair: true,
    })
    opsReconciliation.value = result
    opsHealth.value = result.healthReport
    semanticIndexEvaluation.value = await evaluateAlertReviewSemanticIndex(currentReviewQuery())
  }
  catch (error: any) {
    createMessage.error(error?.message || 'runtime reconcile failed')
  }
  finally {
    opsLoading.value = false
  }
}

async function runOpsPatrol() {
  opsLoading.value = true
  try {
    opsPatrol.value = await runAlertReviewRuntimePatrol({
      ...currentReviewQuery(),
      repair: true,
      maxAttempts: 2,
      scheduled: true,
    })
    opsHealth.value = opsPatrol.value.healthReport
    semanticIndexEvaluation.value = await evaluateAlertReviewSemanticIndex(currentReviewQuery())
  }
  catch (error: any) {
    createMessage.error(error?.message || 'runtime patrol failed')
  }
  finally {
    opsLoading.value = false
  }
}

async function runOpsSmoke() {
  opsLoading.value = true
  try {
    opsSmoke.value = await runAlertReviewIntegrationSmoke({
      operatorUserId: filters.reviewerUserId,
      includeVideoExport: true,
      profile: 'device-video-web',
    })
  }
  catch (error: any) {
    createMessage.error(error?.message || 'integration smoke failed')
  }
  finally {
    opsLoading.value = false
  }
}

async function verifyOpsEvidencePackage() {
  if (!evidenceExportJob.value?.jobNo) {
    createMessage.warn('create evidence export first')
    return
  }
  opsLoading.value = true
  try {
    opsVerification.value = await verifyAlertReviewEvidencePackage(evidenceExportJob.value.jobNo, filters.reviewerUserId)
  }
  catch (error: any) {
    createMessage.error(error?.message || 'manifest verify failed')
  }
  finally {
    opsLoading.value = false
  }
}

async function evaluateOpsRuleGeometry() {
  const target = selectedItem.value || items.value[0]
  if (!target) {
    createMessage.warn('select review item first')
    return
  }
  opsLoading.value = true
  try {
    const reviewData = target.reviewData || {}
    const bbox = Array.isArray(reviewData.bbox) ? reviewData.bbox as number[] : [0, 0, 1, 1]
    opsGeometry.value = await evaluateAlertReviewRuleGeometry({
      ruleCode: target.ruleCode,
      cameraId: target.cameraId,
      zoneCode: target.zoneCode,
      objectLabel: target.objectLabel,
      bbox,
      polygon: [[0, 0], [1, 0], [1, 1], [0, 1]],
      operatorUserId: filters.reviewerUserId,
    })
  }
  catch (error: any) {
    createMessage.error(error?.message || 'rule geometry failed')
  }
  finally {
    opsLoading.value = false
  }
}

async function searchReviewItems() {
  const query = semanticQuery.value.trim()
  if (!query) {
    createMessage.warn('请输入语义检索条件')
    return
  }
  semanticLoading.value = true
  try {
    semanticHits.value = await semanticSearchAlertReview({
      q: query,
      limit: 8,
      reviewStatus: filters.reviewStatus || undefined,
      cameraId: filters.cameraId || undefined,
      zoneCode: filters.zoneCode || undefined,
      objectLabel: filters.objectLabel || undefined,
      recordEvidenceStatus: filters.recordEvidenceStatus || undefined,
      inReviewCase: filters.inReviewCase ? filters.inReviewCase === 'true' : undefined,
      reviewerUserId: filters.reviewerUserId || undefined,
    })
  }
  catch (error: any) {
    createMessage.error(error?.message || '语义检索失败')
  }
  finally {
    semanticLoading.value = false
  }
}

async function evaluateSemanticTriggerPreview() {
  const triggerName = semanticTriggerForm.triggerName.trim()
  const data = semanticTriggerForm.data.trim()
  if (!triggerName || !data) {
    createMessage.warn('请填写触发器名称和语义条件')
    return
  }
  semanticTriggerLoading.value = true
  try {
    semanticTriggerResult.value = await evaluateAlertReviewSemanticTrigger({
      ...currentReviewQuery(),
      triggerName,
      triggerType: 'description',
      data,
      threshold: semanticTriggerForm.threshold,
      actions: semanticTriggerForm.actions.length ? [...semanticTriggerForm.actions] : ['notification'],
      cameraId: selectedItem.value?.cameraId || filters.cameraId || undefined,
    })
    sessionStorage.setItem(
      SEMANTIC_TRIGGER_EVALUATION_STORAGE_KEY,
      semanticTriggerResult.value.evaluationId,
    )
    createMessage.success('语义触发评估已保存，等待人工确认')
  }
  catch (error: any) {
    createMessage.error(error?.message || '语义触发评估失败')
  }
  finally {
    semanticTriggerLoading.value = false
  }
}

async function restoreSemanticTriggerPreview() {
  const evaluationId = sessionStorage.getItem(SEMANTIC_TRIGGER_EVALUATION_STORAGE_KEY)
  if (!evaluationId)
    return
  if (!/^sem-[0-9a-f-]{36}$/.test(evaluationId)) {
    sessionStorage.removeItem(SEMANTIC_TRIGGER_EVALUATION_STORAGE_KEY)
    return
  }
  try {
    semanticTriggerResult.value = await getAlertReviewSemanticTrigger(evaluationId)
  }
  catch {
    sessionStorage.removeItem(SEMANTIC_TRIGGER_EVALUATION_STORAGE_KEY)
  }
}

async function confirmSemanticTriggerPreview(confirmationStatus: 'confirmed' | 'rejected') {
  const evaluation = semanticTriggerResult.value
  if (!evaluation || evaluation.humanConfirmationStatus !== 'pending')
    return
  semanticTriggerDecisionLoading.value = true
  try {
    semanticTriggerResult.value = await confirmAlertReviewSemanticTrigger(evaluation.evaluationId, {
      confirmationStatus,
      notes: semanticTriggerForm.notes.trim() || undefined,
    })
    createMessage.success(confirmationStatus === 'confirmed' ? '动作预览已确认' : '动作预览已驳回')
  }
  catch (error: any) {
    createMessage.error(error?.message || '语义触发确认失败')
  }
  finally {
    semanticTriggerDecisionLoading.value = false
  }
}

async function openItem(item: AlertReviewItem) {
  selectReviewItem(item)
  const reviewItemId = item.id
  await loadItemCase(item)
  if (!isSelectedReviewItem(reviewItemId))
    return
  await loadTimeline(item)
  await loadDetailStream(item)
  await loadReviewSegment(item)
  await loadRecordCoverage(item)
  await loadCaseCandidates(item)
}

function selectReviewItem(item: AlertReviewItem) {
  const changed = selectedItem.value?.id !== item.id
  selectedItem.value = item
  if (!changed)
    return
  activeCase.value = null
  caseTimeline.value = []
  evidenceAudit.value = []
  timeline.value = []
  detailStream.value = []
  coverage.value = []
  caseCandidates.value = []
  rulePreview.value = null
  ruleReplay.value = null
  reviewSegment.value = null
  timelineLoading.value = false
  detailStreamLoading.value = false
  coverageLoading.value = false
  caseLoading.value = false
  evidenceAuditLoading.value = false
}

function isSelectedReviewItem(reviewItemId: number) {
  return selectedItem.value?.id === reviewItemId
}

async function loadItemCase(item: AlertReviewItem) {
  const reviewItemId = item.id
  if (isSelectedReviewItem(reviewItemId)) {
    activeCase.value = null
    caseTimeline.value = []
    evidenceAudit.value = []
  }
  if (!item.inReviewCase)
    return
  try {
    const reviewCase = await getAlertReviewItemCase(reviewItemId)
    if (!reviewCase || !isSelectedReviewItem(reviewItemId))
      return
    applyActiveCase(reviewCase)
    await loadCaseTimeline()
  }
  catch (error: any) {
    if (!isSelectedReviewItem(reviewItemId))
      return
    createMessage.error(error?.message || '加载复盘组失败')
  }
}

async function loadTimeline(item: AlertReviewItem | null = selectedItem.value) {
  const target = item
  if (!target)
    return
  const reviewItemId = target.id
  timelineLoading.value = true
  try {
    const nextTimeline = await getAlertReviewTimeline(target.id, mediaReadScope(target))
    if (isSelectedReviewItem(reviewItemId))
      timeline.value = nextTimeline
  }
  catch (error: any) {
    if (!isSelectedReviewItem(reviewItemId))
      return
    createMessage.error(error?.message || '加载证据时间轴失败')
  }
  finally {
    if (isSelectedReviewItem(reviewItemId))
      timelineLoading.value = false
  }
}

async function loadDetailStream(item?: AlertReviewItem | null) {
  const target = item || selectedItem.value
  if (!target)
    return
  const reviewItemId = target.id
  detailStreamLoading.value = true
  try {
    const nextDetailStream = await getAlertReviewDetailStream(target.id, mediaReadScope(target))
    if (isSelectedReviewItem(reviewItemId))
      detailStream.value = nextDetailStream
  }
  catch (error: any) {
    if (!isSelectedReviewItem(reviewItemId))
      return
    createMessage.error(error?.message || 'detail stream load failed')
  }
  finally {
    if (isSelectedReviewItem(reviewItemId))
      detailStreamLoading.value = false
  }
}

async function loadReviewSegment(item?: AlertReviewItem | null) {
  const target = item || selectedItem.value
  if (!target)
    return
  reviewSegmentLoading.value = true
  try {
    reviewSegment.value = await getAlertReviewSegment(target.id)
  }
  catch (error: any) {
    createMessage.error(error?.message || 'review segment load failed')
  }
  finally {
    reviewSegmentLoading.value = false
  }
}

async function loadRecordCoverage(item?: AlertReviewItem | null) {
  const target = item || selectedItem.value
  if (!target)
    return
  const reviewItemId = target.id
  coverageLoading.value = true
  try {
    const nextCoverage = await getAlertReviewRecordCoverage(target.id, mediaReadScope(target))
    if (isSelectedReviewItem(reviewItemId))
      coverage.value = nextCoverage
  }
  catch (error: any) {
    if (!isSelectedReviewItem(reviewItemId))
      return
    createMessage.error(error?.message || '加载录像覆盖度失败')
  }
  finally {
    if (isSelectedReviewItem(reviewItemId))
      coverageLoading.value = false
  }
}

async function loadRecordCoverageForItem(item: AlertReviewItem) {
  selectReviewItem(item)
  const reviewItemId = item.id
  await loadItemCase(item)
  if (!isSelectedReviewItem(reviewItemId))
    return
  await loadRecordCoverage(item)
}

function mediaReadScope(item: AlertReviewItem) {
  const cameraId = item.cameraId || item.deviceId
  return {
    reviewCaseId: activeCase.value?.id,
    operatorUserId: filters.reviewerUserId || undefined,
    allowedCameraIds: cameraId ? [cameraId] : undefined,
  }
}

async function loadCaseCandidates(item?: AlertReviewItem | null) {
  if (!item)
    return
  try {
    caseCandidates.value = await suggestAlertReviewCaseCandidates(item.id)
  }
  catch {
    caseCandidates.value = []
  }
}

function markFalsePositive(item: AlertReviewItem) {
  createConfirm({
    title: '标记误报',
    iconType: 'warning',
    content: '确认将这条线索标记为误报，并沉淀为规则建议吗？',
    async onOk() {
      try {
        replaceItem(await markAlertReviewFalsePositive(item.id, { reason: 'manual_false_positive' }))
        createMessage.success('已标记误报')
      }
      catch (error: any) {
        createMessage.error(error?.message || '标记误报失败')
      }
    },
  })
}

async function updateRuleSuggestion(item: AlertReviewItem, status: AlertReviewRuleSuggestionStatus['status']) {
  if (!canUpdateRuleSuggestion.value) {
    createMessage.error('缺少规则建议治理权限')
    return
  }
  if ((status === 'shadow_evaluated' || status === 'accepted' || status === 'applied') && !ruleSuggestionSampleReady(item)) {
    createMessage.error('样本数不足，不能进入影子评估、审批或应用阶段')
    return
  }
  if ((status === 'accepted' || status === 'applied') && !ruleSuggestionHasShadowEvidence(item)) {
    createMessage.error('缺少影子评估与规则回放报告，不能审批或应用')
    return
  }
  try {
    replaceItem(await updateAlertReviewRuleSuggestionStatus(item.id, {
      status,
      note: `manual_${status}`,
    }))
    const successMessages: Record<AlertReviewRuleSuggestionStatus['status'], string> = {
      pending: '规则建议已恢复待评估',
      shadow_evaluated: '影子评估完成，等待审批',
      accepted: '规则建议审批通过，尚未应用到线上规则',
      rejected: '规则建议已拒绝，未修改线上规则',
      applied: '规则建议已应用到线上规则',
      reverted: '规则建议已回滚',
    }
    createMessage.success(successMessages[status])
  }
  catch (error: any) {
    createMessage.error(error?.message || '规则建议状态更新失败')
  }
}

async function previewRuleSuggestion(item: AlertReviewItem) {
  rulePreviewLoading.value = true
  selectedItem.value = item
  try {
    rulePreview.value = await previewAlertReviewRuleSuggestion(item.id)
  }
  catch (error: any) {
    createMessage.error(error?.message || '规则建议预览失败')
  }
  finally {
    rulePreviewLoading.value = false
  }
}

async function replayRuleForItem(item?: AlertReviewItem | null) {
  if (!canReplayRule.value) {
    createMessage.error('rule replay permission required')
    return
  }
  const target = item || selectedItem.value
  if (!target?.ruleCode) {
    createMessage.warn('ruleCode is required')
    return
  }
  selectedItem.value = target
  ruleReplayLoading.value = true
  try {
    const proposedRule = (target.ruleSuggestion?.proposedRule || target.ruleSuggestion?.proposed || {}) as Record<string, any>
    ruleReplay.value = await replayAlertReviewRule({
      ruleCode: target.ruleCode,
      sourceSystem: target.sourceSystem,
      cameraId: target.cameraId || target.deviceId,
      zoneCode: target.zoneCode,
      objectLabel: target.objectLabel,
      minStaySeconds: toOptionalNumber(proposedRule.minStaySeconds ?? target.ruleSuggestion?.minStaySeconds),
      beginTime: target.firstAlertTime
        ? moment(target.firstAlertTime).subtract(1, 'hour').format('YYYY-MM-DDTHH:mm:ss')
        : undefined,
      endTime: target.lastAlertTime
        ? moment(target.lastAlertTime).add(1, 'hour').format('YYYY-MM-DDTHH:mm:ss')
        : undefined,
      operatorUserId: filters.reviewerUserId || undefined,
    })
  }
  catch (error: any) {
    createMessage.error(error?.message || 'rule replay failed')
  }
  finally {
    ruleReplayLoading.value = false
  }
}

function revertRuleSuggestion(item: AlertReviewItem) {
  if (!canRevertRuleSuggestion.value) {
    createMessage.error('rule suggestion rollback permission required')
    return
  }
  createConfirm({
    title: '回滚规则建议',
    iconType: 'warning',
    content: '确认将这条规则建议回滚，并保留复核审计记录吗？',
    async onOk() {
      try {
        const next = await revertAlertReviewRuleSuggestion(item.id, {
          status: 'reverted',
          reviewerUserId: filters.reviewerUserId || undefined,
          note: 'manual_revert_from_workbench',
        })
        replaceItem(next)
        rulePreview.value = null
        createMessage.success('规则建议已回滚')
      }
      catch (error: any) {
        createMessage.error(error?.message || '规则建议回滚失败')
      }
    },
  })
}

async function markReviewedByCurrentUser(item: AlertReviewItem, hasBeenReviewed: boolean) {
  if (!filters.reviewerUserId) {
    createMessage.warn('请先输入复核人ID')
    return
  }
  userStatusLoading.value = true
  try {
    await markAlertReviewUserStatus(item.id, {
      userId: filters.reviewerUserId,
      hasBeenReviewed,
    })
    await loadItems()
    createMessage.success(hasBeenReviewed ? '已记录我的复核状态' : '已取消我的复核状态')
  }
  catch (error: any) {
    createMessage.error(error?.message || '复核人状态更新失败')
  }
  finally {
    userStatusLoading.value = false
  }
}

function applyActiveCase(next: AlertReviewCase) {
  activeCase.value = next
  caseLifecycleForm.ownerUserId = next.ownerUserId || filters.reviewerUserId || undefined
}

function currentCaseOperatorUserId() {
  return filters.reviewerUserId || undefined
}

function caseOperationId(action: string, reviewCase: AlertReviewCase, discriminator?: number) {
  const suffix = discriminator === undefined ? '' : `:${discriminator}`
  return `workbench:${action}:${reviewCase.id}:v${reviewCase.version ?? 0}${suffix}`
}

async function createCaseFromItem(item: AlertReviewItem) {
  try {
    applyActiveCase(await createAlertReviewCase({
      title: `${item.cameraId || item.deviceId || 'review'} 复盘`,
      primaryReviewItemId: item.id,
      reviewItemIds: [item.id],
      ownerUserId: filters.reviewerUserId || undefined,
      notes: item.reviewData?.correlationId ? `correlationId=${item.reviewData.correlationId}` : undefined,
    }))
    caseTimeline.value = []
    evidenceAudit.value = []
    createMessage.success('复盘组已创建')
  }
  catch (error: any) {
    createMessage.error(error?.message || '创建复盘组失败')
  }
}

async function addItemToActiveCase(item: AlertReviewItem) {
  if (!activeCase.value)
    return
  try {
    applyActiveCase(await addAlertReviewItemToCase(activeCase.value.id, item.id))
    await loadCaseTimeline()
    createMessage.success('已加入复盘组')
  }
  catch (error: any) {
    createMessage.error(error?.message || '加入复盘组失败')
  }
}

async function assignActiveCaseOwner() {
  if (!activeCase.value)
    return
  const ownerUserId = toOptionalNumber(caseLifecycleForm.ownerUserId) || filters.reviewerUserId || activeCase.value.ownerUserId
  if (!ownerUserId) {
    createMessage.warn('owner user id is required')
    return
  }
  caseLifecycleLoading.value = true
  try {
    applyActiveCase(await assignAlertReviewCaseOwner(activeCase.value.id, {
      ownerUserId,
      operatorUserId: currentCaseOperatorUserId(),
      notes: 'workbench_owner_handoff',
      expectedVersion: activeCase.value.version,
      operationId: caseOperationId('owner', activeCase.value, ownerUserId),
    }))
    createMessage.success('case owner updated')
  }
  catch (error: any) {
    createMessage.error(error?.message || 'case owner update failed')
  }
  finally {
    caseLifecycleLoading.value = false
  }
}

async function closeActiveCase() {
  if (!activeCase.value)
    return
  caseLifecycleLoading.value = true
  try {
    applyActiveCase(await closeAlertReviewCase(activeCase.value.id, {
      operatorUserId: currentCaseOperatorUserId(),
      notes: 'workbench_close_case',
      expectedVersion: activeCase.value.version,
      operationId: caseOperationId('close', activeCase.value),
    }))
    createMessage.success('case closed')
  }
  catch (error: any) {
    createMessage.error(error?.message || 'case close failed')
  }
  finally {
    caseLifecycleLoading.value = false
  }
}

async function mergeActiveCase() {
  if (!activeCase.value)
    return
  const sourceReviewCaseId = toOptionalNumber(caseLifecycleForm.sourceReviewCaseId)
  if (!sourceReviewCaseId) {
    createMessage.warn('source case id is required')
    return
  }
  caseLifecycleLoading.value = true
  try {
    const result = await mergeAlertReviewCases(activeCase.value.id, {
      sourceReviewCaseId,
      operatorUserId: currentCaseOperatorUserId(),
      notes: 'workbench_merge_case',
      targetExpectedVersion: activeCase.value.version,
      operationId: caseOperationId('merge', activeCase.value, sourceReviewCaseId),
    })
    applyActiveCase(result.targetCase)
    caseLifecycleForm.sourceReviewCaseId = undefined
    await loadCaseTimeline()
    createMessage.success('case merged')
  }
  catch (error: any) {
    createMessage.error(error?.message || 'case merge failed')
  }
  finally {
    caseLifecycleLoading.value = false
  }
}

async function splitSelectedItemFromCase() {
  if (!activeCase.value || !selectedItem.value)
    return
  const ownerUserId = toOptionalNumber(caseLifecycleForm.ownerUserId) || filters.reviewerUserId || activeCase.value.ownerUserId
  caseLifecycleLoading.value = true
  try {
    const result = await splitAlertReviewCase(activeCase.value.id, {
      reviewItemIds: [selectedItem.value.id],
      title: caseLifecycleForm.splitTitle || `${selectedItem.value.cameraId || selectedItem.value.deviceId || 'review'} follow-up`,
      ownerUserId,
      operatorUserId: currentCaseOperatorUserId(),
      notes: 'workbench_split_case',
      sourceExpectedVersion: activeCase.value.version,
      operationId: caseOperationId('split', activeCase.value, selectedItem.value.id),
    })
    applyActiveCase(result.newCase)
    caseTimeline.value = []
    evidenceAudit.value = []
    createMessage.success('case split')
  }
  catch (error: any) {
    createMessage.error(error?.message || 'case split failed')
  }
  finally {
    caseLifecycleLoading.value = false
  }
}

async function loadCaseTimeline() {
  if (!activeCase.value)
    return
  const reviewCaseId = activeCase.value.id
  caseLoading.value = true
  try {
    const nextCaseTimeline = await getAlertReviewCaseTimeline(reviewCaseId)
    if (activeCase.value?.id !== reviewCaseId)
      return
    caseTimeline.value = nextCaseTimeline
    await loadEvidenceAudit(reviewCaseId)
  }
  catch (error: any) {
    if (activeCase.value?.id !== reviewCaseId)
      return
    createMessage.error(error?.message || '加载复盘时间线失败')
  }
  finally {
    if (activeCase.value?.id === reviewCaseId)
      caseLoading.value = false
  }
}

async function loadEvidenceAudit(reviewCaseId = activeCase.value?.id) {
  if (!reviewCaseId)
    return
  evidenceAuditLoading.value = true
  try {
    const nextEvidenceAudit = await getAlertReviewEvidenceAudit(reviewCaseId)
    if (activeCase.value?.id === reviewCaseId)
      evidenceAudit.value = nextEvidenceAudit
  }
  catch (error: any) {
    if (activeCase.value?.id !== reviewCaseId)
      return
    createMessage.error(error?.message || 'evidence audit load failed')
  }
  finally {
    if (activeCase.value?.id === reviewCaseId)
      evidenceAuditLoading.value = false
  }
}

async function generateAiSummary() {
  if (!activeCase.value) {
    createMessage.warn('请先创建或选择复盘组')
    return
  }
  aiSummaryLoading.value = true
  try {
    aiSummary.value = await summarizeAlertReviewCase(activeCase.value.id, filters.reviewerUserId || undefined)
  }
  catch (error: any) {
    createMessage.error(error?.message || 'AI 摘要生成失败')
  }
  finally {
    aiSummaryLoading.value = false
  }
}

async function exportCaseEvidence() {
  if (!activeCase.value) {
    createMessage.warn('请先创建或选择复盘组')
    return
  }
  evidenceExportLoading.value = true
  try {
    evidenceExportJob.value = await createAlertReviewEvidenceExportJob(activeCase.value.id, {
      operatorUserId: filters.reviewerUserId || undefined,
      format: 'mp4',
      reason: 'review_case_export',
    })
    evidenceExport.value = evidenceExportJob.value.exportPackage
    await loadEvidenceAudit()
    createMessage.success('evidence export job ready')
  }
  catch (error: any) {
    createMessage.error(error?.message || '证据导出失败')
  }
  finally {
    evidenceExportLoading.value = false
  }
}

async function markReviewed(item: AlertReviewItem) {
  try {
    replaceItem(await markAlertReviewReviewed(item.id))
    createMessage.success('线索已复核')
  }
  catch (error: any) {
    createMessage.error(error?.message || '复核失败')
  }
}

async function retryRecordEvidenceForItem(item?: AlertReviewItem | null) {
  if (!item)
    return
  recordRetryLoading.value = true
  try {
    const next = await retryAlertReviewRecordEvidence(item.id)
    replaceItem(next)
    if (selectedItem.value?.id === item.id) {
      await loadTimeline()
      await loadRecordCoverage(next)
    }
    createMessage.success('录像补证已更新')
  }
  catch (error: any) {
    createMessage.error(error?.message || '录像补证失败')
  }
  finally {
    recordRetryLoading.value = false
  }
}

function openRuleDrawer(item: AlertReviewItem) {
  if (!item.cameraId && !item.deviceId) {
    createMessage.warn('未绑定摄像头')
    return
  }
  ruleDrawerItem.value = item
  ruleDrawerVisible.value = true
}

async function handleRegionRuleSave(regions: DeviceDetectionRegion[]) {
  const item = ruleDrawerItem.value
  if (!item)
    return
  const region = pickRuleRegion(item, regions)
  if (!region) {
    createMessage.warn('未选择区域')
    return
  }
  try {
    await saveAlertReviewRule({
      ruleCode: item.ruleCode,
      ruleName: `${item.ruleCode}-${region.region_name || region.id}`,
      sourceSystem: item.sourceSystem,
      cameraId: item.cameraId || item.deviceId,
      zoneCode: item.zoneCode || region.region_name || String(region.id),
      objectLabel: item.objectLabel,
      minStaySeconds: toOptionalNumber(region.minStaySeconds ?? item.ruleSuggestion?.proposedRule?.minStaySeconds),
      inertiaFrames: toOptionalNumber(region.inertiaFrames),
      loiteringSeconds: toOptionalNumber(region.loiteringSeconds),
      enabled: true,
    })
    createMessage.success('区域规则已保存')
    ruleDrawerVisible.value = false
    await loadItems()
  }
  catch (error: any) {
    createMessage.error(error?.message || '区域规则保存失败')
  }
}

function ignoreItem(item: AlertReviewItem) {
  createConfirm({
    title: '忽略线索',
    iconType: 'warning',
    content: '确认忽略这条复核线索吗？',
    async onOk() {
      try {
        replaceItem(await ignoreAlertReviewItem(item.id, { reason: 'manual_ignore' }))
        createMessage.success('线索已忽略')
      }
      catch (error: any) {
        createMessage.error(error?.message || '忽略失败')
      }
    },
  })
}

function convertToEvent(item: AlertReviewItem) {
  createConfirm({
    title: '转监管事件',
    iconType: 'warning',
    content: '确认将这条线索转为监管事件并进入处置闭环吗？',
    async onOk() {
      try {
        const result = await convertAlertReviewToEvent(item.id)
        const converted = { ...item, reviewStatus: result.reviewStatus, eventId: result.eventId }
        replaceItem(converted)
        await loadItems()
        emit('converted', converted)
        createMessage.success(`已转监管事件 #${result.eventId}`)
      }
      catch (error: any) {
        createMessage.error(error?.message || '转监管事件失败')
      }
    },
  })
}

async function openEvidence(evidence: AlertReviewEvidence) {
  if (!evidence.materialUri) {
    createMessage.warn('证据地址为空')
    return
  }
  if (evidence.materialType === 'snapshot') {
    if (!(await guardWorkbenchMediaAccess({
      reviewItemId: evidence.reviewItemId,
      materialUri: evidence.materialUri,
      actionType: 'snapshot',
    })))
      return
    emit('viewImage', { image_url: evidence.materialUri })
    return
  }
  const prepared = await prepareWorkbenchPlayback({
    reviewItemId: evidence.reviewItemId,
    materialUri: evidence.materialUri,
  })
  if (!prepared)
    return
  emit('viewVideo', {
    id: evidence.sourceAlertId,
    device_id: selectedItem.value?.cameraId || selectedItem.value?.deviceId,
    time: evidence.happenedAt,
    record_path: prepared.recordPath,
  })
}

async function openListPlayback(item: AlertReviewItem) {
  await openItem(item)
  const evidence = timeline.value.find(candidate => candidate.materialType !== 'snapshot' && candidate.materialUri)
  if (evidence?.materialUri) {
    const seekTime = evidence.happenedAt || item.firstAlertTime
    const prepared = await prepareWorkbenchPlayback({
      reviewItemId: item.id,
      materialUri: evidence.materialUri,
    })
    if (!prepared)
      return
    emit('viewVideo', {
      id: evidence.sourceAlertId,
      device_id: item.cameraId || item.deviceId,
      time: seekTime,
      seek_time: seekTime,
      record_path: prepared.recordPath,
    })
    return
  }

  const segment = coverage.value.find(candidate => candidate.recordUri)
  if (segment) {
    await openCoverageSegment(segment)
    return
  }

  createMessage.warn('record uri is empty')
}

async function openDetailStreamEntry(entry: AlertReviewDetailStreamItem) {
  if (!entry.materialUri) {
    createMessage.warn('material uri is empty')
    return
  }
  if (entry.materialType === 'snapshot') {
    if (!(await guardWorkbenchMediaAccess({
      reviewItemId: entry.reviewItemId,
      cameraId: entry.cameraId,
      materialUri: entry.materialUri,
      actionType: 'snapshot',
    })))
      return
    emit('viewImage', { image_url: entry.materialUri })
    return
  }
  const prepared = await prepareWorkbenchPlayback({
    reviewItemId: entry.reviewItemId,
    cameraId: entry.cameraId,
    materialUri: entry.materialUri,
  })
  if (!prepared)
    return
  emit('viewVideo', {
    id: entry.sourceAlertId,
    device_id: entry.cameraId || selectedItem.value?.deviceId || selectedItem.value?.cameraId,
    time: entry.seekTime || entry.happenedAt,
    seek_time: entry.seekTime || entry.happenedAt,
    record_start_time: entry.recordStartTime,
    playback_offset_seconds: entry.playbackOffsetSeconds,
    record_path: prepared.recordPath,
  })
}

async function openUnifiedTimelineEntry(entry: UnifiedTimelineEntry) {
  if (entry.kind === 'object') {
    await openDetailStreamEntry(entry.payload as AlertReviewDetailStreamItem)
    return
  }
  if (entry.kind === 'evidence') {
    await openEvidence(entry.payload as AlertReviewEvidence)
    return
  }
  if (entry.kind === 'coverage') {
    await openCoverageSegment(entry.payload as AlertReviewCoverageSegment)
    return
  }
  if (entry.kind === 'case') {
    await openCaseTimelineEntry(entry.payload as AlertReviewCaseTimelineItem)
    return
  }
  if (!entry.uri) {
    createMessage.warn('record uri is empty')
    return
  }
  const prepared = await prepareWorkbenchPlayback({
    reviewItemId: selectedItem.value?.id,
    materialUri: entry.uri,
  })
  if (!prepared)
    return
  emit('viewVideo', {
    device_id: selectedItem.value?.deviceId || selectedItem.value?.cameraId,
    time: entry.startTime,
    seek_time: entry.startTime,
    record_path: prepared.recordPath,
  })
}

async function openCaseTimelineEntry(entry: AlertReviewCaseTimelineItem) {
  if (!entry.materialUri) {
    createMessage.warn('material uri is empty')
    return
  }
  if (entry.materialType === 'snapshot') {
    if (!(await guardCaseTimelineMediaAccess(entry)))
      return
    emit('viewImage', { image_url: entry.materialUri })
    return
  }
  const prepared = await prepareWorkbenchPlayback({
    reviewCaseId: entry.reviewCaseId,
    reviewItemId: entry.reviewItemId,
    cameraId: entry.cameraId,
    materialUri: entry.materialUri,
  })
  if (!prepared)
    return
  emit('viewVideo', {
    id: entry.sourceAlertId,
    device_id: entry.cameraId || selectedItem.value?.deviceId || selectedItem.value?.cameraId,
    time: entry.happenedAt,
    seek_time: entry.happenedAt,
    record_start_time: entry.recordStartTime,
    playback_offset_seconds: entry.playbackOffsetSeconds,
    record_path: prepared.recordPath,
  })
}

async function guardCaseTimelineMediaAccess(entry: AlertReviewCaseTimelineItem) {
  return guardWorkbenchMediaAccess({
    reviewCaseId: entry.reviewCaseId,
    reviewItemId: entry.reviewItemId,
    cameraId: entry.cameraId,
    materialUri: entry.materialUri,
    actionType: 'snapshot',
  })
}

async function guardWorkbenchMediaAccess(target: {
  reviewCaseId?: number
  reviewItemId?: number
  cameraId?: string
  materialUri?: string
  actionType?: 'snapshot' | 'playback'
}) {
  const reviewCaseId = target.reviewCaseId || activeCase.value?.id
  const reviewItemId = target.reviewItemId || selectedItem.value?.id
  const cameraId = target.cameraId || selectedItem.value?.cameraId || selectedItem.value?.deviceId
  if (!reviewItemId || !cameraId || !target.materialUri)
    return true
  try {
    const payload = {
      reviewItemId,
      cameraId,
      materialUri: target.materialUri,
      actionType: target.actionType || 'playback',
      reason: 'workbench playback',
    }
    if (reviewCaseId)
      await auditAlertReviewMediaAccess(reviewCaseId, payload)
    else
      await auditAlertReviewItemMediaAccess(reviewItemId, payload)
    return true
  }
  catch (error: any) {
    createMessage.error(error?.message || 'Media access denied')
    return false
  }
}

async function prepareWorkbenchPlayback(target: {
  reviewCaseId?: number
  reviewItemId?: number
  cameraId?: string
  materialUri?: string
}) {
  const reviewCaseId = target.reviewCaseId || activeCase.value?.id
  const reviewItemId = target.reviewItemId || selectedItem.value?.id
  if (!reviewItemId || !target.materialUri)
    return null
  try {
    const playback = await prepareAlertReviewPlaybackUrl(reviewItemId, {
      reviewCaseId,
      materialUri: target.materialUri,
      reason: 'workbench playback',
    })
    if (playback.decision !== 'granted' || !playback.playbackUrl) {
      createMessage.error(playback.deniedReasons?.join(', ') || 'Media access denied')
      return null
    }
    const prepared = { recordPath: playback.playbackUrl }
    target.materialUri = prepared.recordPath
    return prepared
  }
  catch (error: any) {
    createMessage.error(error?.message || 'Media access denied')
    return null
  }
}

async function openCoverageSegment(segment: AlertReviewCoverageSegment) {
  if (!segment.recordUri) {
    createMessage.warn('record uri is empty')
    return
  }
  const prepared = await prepareWorkbenchPlayback({
    reviewItemId: selectedItem.value?.id,
    materialUri: segment.recordUri,
  })
  if (!prepared)
    return
  emit('viewVideo', {
    device_id: selectedItem.value?.cameraId || selectedItem.value?.deviceId,
    time: segment.startTime,
    seek_time: segment.startTime,
    record_start_time: segment.startTime,
    record_path: prepared.recordPath,
  })
}

function replaceItem(next: AlertReviewItem) {
  items.value = items.value.map(item => (item.id === next.id ? next : item))
  if (selectedItem.value?.id === next.id)
    selectedItem.value = next
}

function statusText(status: string) {
  return statusOptions.find(option => option.value === status)?.label || status || '-'
}

function recordStatusText(status?: string) {
  const map: Record<string, string> = {
    not_required: '无需录像',
    pending: '待补证',
    found: '录像已补',
    missing: '缺录像/待手动补证',
    failed: '补证失败',
  }
  return (status && map[status]) || status || '-'
}

function recordReasonText(reason?: string) {
  const map: Record<string, string> = {
    video_url_not_configured: 'VIDEO URL 未配置',
    record_not_found: 'record not found',
    missing_lookup_fields: 'missing lookup fields',
    service_unavailable: 'video service unavailable',
    video_service_unavailable: 'video service unavailable',
    permission_denied: 'permission denied',
    retention_expired: 'retention expired',
    stream_interrupted: 'stream interrupted',
    recording_disabled: 'recording disabled',
    record_space_not_found: 'record space missing',
    probe_failed: 'probe failed',
    file_missing: 'file missing',
  }
  return (reason && map[reason]) || reason || '-'
}

function recordGapReasonSummary(reasons?: Record<string, number>) {
  const entries = Object.entries(reasons || {})
  if (!entries.length)
    return 'gap reasons -'
  return entries.map(([reason, count]) => `${recordReasonText(reason)} ${count}`).join(' / ')
}

function percentText(value?: number) {
  if (value === undefined || value === null || Number.isNaN(Number(value)))
    return '-'
  return `${Math.round(Number(value) * 100)}%`
}

function semanticProgressText(evaluation?: AlertReviewSemanticIndexEvaluation | null) {
  const progress = evaluation?.rebuildProgressRate ?? evaluation?.coverageRate
  return percentText(progress)
}

function semanticBacklogSummary(evaluation?: AlertReviewSemanticIndexEvaluation | null) {
  if (!evaluation)
    return 'stale - / failed -'
  return `stale ${evaluation.staleReviewItemIds?.length || 0} / failed ${evaluation.failedCount || 0}`
}

function ruleSuggestionSafetyRows(item: AlertReviewItem) {
  const suggestion = item.ruleSuggestion || {}
  const rows: string[] = []
  const currentSampleCount = toOptionalNumber(suggestion.currentSampleCount)
  const minimumSampleCount = toOptionalNumber(suggestion.minimumSampleCount)
  if (currentSampleCount !== undefined || minimumSampleCount !== undefined)
    rows.push(`sample ${currentSampleCount ?? '-'}/${minimumSampleCount ?? '-'}`)
  if (suggestion.riskNote)
    rows.push(`risk ${String(suggestion.riskNote)}`)
  const impactScope = suggestion.impactScope as Record<string, unknown> | undefined
  const impactText = [
    compactValueList(impactScope?.cameraIds),
    compactValueList(impactScope?.zoneCodes),
    compactValueList(impactScope?.objectLabels),
  ].filter(value => value !== '-')
  if (impactText.length)
    rows.push(`impact ${impactText.join(' / ')}`)
  const beforeAfter = suggestion.beforeAfterComparison as Record<string, unknown> | undefined
  const beforeHitCount = toOptionalNumber(beforeAfter?.beforeHitCount)
  const afterEstimatedHitCount = toOptionalNumber(beforeAfter?.afterEstimatedHitCount)
  if (beforeHitCount !== undefined || afterEstimatedHitCount !== undefined)
    rows.push(`hits ${beforeHitCount ?? '-'} -> ${afterEstimatedHitCount ?? '-'}`)
  const possibleMissedCount = toOptionalNumber(beforeAfter?.possibleMissedCount)
  if (possibleMissedCount !== undefined)
    rows.push(`possible missed ${possibleMissedCount}`)
  return rows
}

function ruleSuggestionSampleReady(item: AlertReviewItem) {
  const suggestion = item.ruleSuggestion || {}
  if (suggestion.sampleRequirementMet === true)
    return true
  if (suggestion.sampleRequirementMet === false)
    return false
  const currentSampleCount = toOptionalNumber(suggestion.currentSampleCount)
  const minimumSampleCount = toOptionalNumber(suggestion.minimumSampleCount)
  if (minimumSampleCount === undefined)
    return true
  return (currentSampleCount ?? 0) >= minimumSampleCount
}

function ruleSuggestionHasShadowEvidence(item: AlertReviewItem) {
  const suggestion = item.ruleSuggestion || {}
  const shadowEvaluation = suggestion.shadowEvaluation
  const replayReport = suggestion.replayReport
  return shadowEvaluation !== null
    && typeof shadowEvaluation === 'object'
    && Object.keys(shadowEvaluation).length > 0
    && replayReport !== null
    && typeof replayReport === 'object'
    && Object.keys(replayReport).length > 0
}

function canShadowEvaluateRuleSuggestion(item: AlertReviewItem) {
  return item.ruleSuggestionStatus === 'pending' && canUpdateRuleSuggestion.value && ruleSuggestionSampleReady(item)
}

function canRejectRuleSuggestion(item: AlertReviewItem) {
  return (item.ruleSuggestionStatus === 'pending' || item.ruleSuggestionStatus === 'shadow_evaluated')
    && canUpdateRuleSuggestion.value
}

function canAcceptRuleSuggestion(item: AlertReviewItem) {
  return item.ruleSuggestionStatus === 'shadow_evaluated'
    && canUpdateRuleSuggestion.value
    && ruleSuggestionSampleReady(item)
    && ruleSuggestionHasShadowEvidence(item)
}

function canApplyRuleSuggestion(item: AlertReviewItem) {
  return item.ruleSuggestionStatus === 'accepted'
    && canUpdateRuleSuggestion.value
    && ruleSuggestionSampleReady(item)
    && ruleSuggestionHasShadowEvidence(item)
}

function ruleSuggestionStatusText(status?: AlertReviewItem['ruleSuggestionStatus']) {
  return {
    pending: '待影子评估',
    shadow_evaluated: '影子评估完成',
    accepted: '已审批，待应用',
    rejected: '已拒绝',
    applied: '已应用',
    reverted: '已回滚',
  }[status || 'pending']
}

function compactValueList(value: unknown) {
  if (Array.isArray(value))
    return value.map(item => String(item)).filter(Boolean).join(',') || '-'
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function replayRuleVersionText(replay?: AlertReviewRuleReplayResult | null) {
  const ruleVersion = replay?.report?.ruleVersion as Record<string, unknown> | undefined
  return `${compactValueList(ruleVersion?.applicationMode)} / ${compactValueList(ruleVersion?.semanticEngine)}`
}

function replaySampleWindowText(replay?: AlertReviewRuleReplayResult | null) {
  const sampleWindow = replay?.report?.sampleWindow as Record<string, unknown> | undefined
  return `${compactValueList(sampleWindow?.startTime)} -> ${compactValueList(sampleWindow?.endTime)} / ${compactValueList(sampleWindow?.sampleCount)}`
}

function replayHitComparisonText(replay?: AlertReviewRuleReplayResult | null) {
  const hitComparison = replay?.report?.hitComparison as Record<string, unknown> | undefined
  return `${compactValueList(hitComparison?.beforeCount)} -> ${compactValueList(hitComparison?.afterCount)} / diff ${compactValueList(hitComparison?.difference)}`
}

function replayFalseNegativeText(replay?: AlertReviewRuleReplayResult | null) {
  const estimate = replay?.report?.falseNegativeEstimate as Record<string, unknown> | undefined
  return `${compactValueList(estimate?.riskLevel)} / possible missed ${compactValueList(estimate?.possibleMissedCount)}`
}

function eventStatusText(status?: string) {
  const map: Record<string, string> = {
    created: '已创建',
    dispatched: '已派发',
    accepted: '已接收',
    handling: '处置中',
    pending_recheck: '待复核',
    rework_required: '需返工',
    pending_close_check: '待审核',
    exception_review: '异常复核',
    transferred_major: '重大转办',
    closed: '已闭环',
  }
  return (status && map[status]) || status || '-'
}

function coverageStatusText(status?: string) {
  const map: Record<string, string> = {
    available: '有录像',
    missing: '缺录像',
    motion: '有运动',
  }
  return (status && map[status]) || status || '-'
}

function canRetryRecordEvidence(item?: AlertReviewItem | null) {
  return item?.recordEvidenceStatus === 'missing' || item?.recordEvidenceStatus === 'failed'
}

function canMarkFalsePositive(item?: AlertReviewItem | null) {
  return !!item && item.reviewStatus !== 'converted' && !item.eventId
}

function pickRuleRegion(item: AlertReviewItem, regions: DeviceDetectionRegion[]) {
  if (!regions.length)
    return null
  return (
    regions.find(region => item.zoneCode && (region.region_name === item.zoneCode || String(region.id) === item.zoneCode))
    || regions[0]
  )
}

function formatTime(value?: string) {
  if (!value)
    return '-'
  return moment(value).format('YYYY-MM-DD HH:mm:ss')
}

function evidenceKey(evidence: AlertReviewEvidence) {
  return `${evidence.sourceAlertId}-${evidence.materialType}-${evidence.materialUri}`
}

function detailStreamKey(entry: AlertReviewDetailStreamItem) {
  return `${entry.reviewItemId}-${entry.sourceAlertId || ''}-${entry.objectId || ''}-${entry.lifecycleEvent || ''}-${entry.happenedAt || ''}`
}

function evidenceAuditKey(entry: AlertReviewEvidenceAuditEntry) {
  return `${entry.reviewCaseId}-${entry.reviewItemId || ''}-${entry.actionType}-${entry.jobNo || ''}-${entry.happenedAt}`
}

function coverageKey(segment: AlertReviewCoverageSegment) {
  return `${segment.status}-${segment.startTime}-${segment.endTime}-${segment.recordUri || ''}`
}

function caseTimelineKey(evidence: AlertReviewCaseTimelineItem) {
  return `${evidence.reviewCaseId}-${evidence.reviewItemId}-${evidence.sourceAlertId}-${evidence.materialType}-${evidence.materialUri || ''}`
}

function manifestChecksum(pkg?: AlertReviewEvidenceExportPackage | null) {
  return pkg?.manifest?.checksum || '-'
}

function exportTimelineEntries(): UnifiedTimelineEntry[] {
  const manifest = evidenceExport.value?.manifest || {}
  const videoExports = Array.isArray(manifest.videoExports) ? manifest.videoExports : []
  return videoExports.map((videoExport: Record<string, any>, index: number) => ({
    key: `export-${evidenceExport.value?.packageNo || 'pkg'}-${videoExport.exportId || index}`,
    kind: 'export',
    icon: 'ant-design:export-outlined',
    className: `export ${videoExport.status || ''}`,
    title: 'export clip',
    startTime: evidenceExport.value?.generatedAt,
    subtitle: videoExport.status || evidenceExportJob.value?.status || '-',
    uri: videoExport.exportUri,
    status: videoExport.status,
    meta: [
      videoExport.exportId ? `id ${videoExport.exportId}` : '',
      videoExport.message || '',
      evidenceExportJob.value?.fileHash ? `hash ${evidenceExportJob.value.fileHash}` : '',
    ].filter(Boolean),
    actionLabel: videoExport.exportUri ? 'Open export' : undefined,
    payload: videoExport,
  }))
}

function unifiedTimelineKey(entry: UnifiedTimelineEntry) {
  return entry.key
}

function timelineRange(entry: UnifiedTimelineEntry) {
  if (entry.endTime)
    return `${formatTime(entry.startTime)} - ${formatTime(entry.endTime)}`
  return formatTime(entry.startTime)
}

const topologyAreaKeys = ['regulatoryArea', 'regulatory_area', 'regulatoryAreaCode', 'regulatory_area_code', 'supervisionArea', 'supervision_area']
const adjacentCameraKeys = ['adjacentCameras', 'adjacent_cameras']
const objectIdKeys = ['objectIds', 'object_ids']
const correlationKeys = ['correlationId', 'correlation_id']

function reviewDataValue(item: AlertReviewItem | null | undefined, keys: string[]): unknown {
  const reviewData = item?.reviewData || {}
  for (const key of keys) {
    const value = reviewData[key]
    if (value !== undefined && value !== null && value !== '')
      return value
  }
  const caseCandidateMatch = reviewData.caseCandidateMatch as Record<string, unknown> | undefined
  if (caseCandidateMatch) {
    for (const key of keys) {
      const value = caseCandidateMatch[key]
      if (value !== undefined && value !== null && value !== '')
        return value
    }
  }
  const motion = reviewData.motion as Record<string, unknown> | undefined
  if (motion) {
    for (const key of keys) {
      const value = motion[key]
      if (value !== undefined && value !== null && value !== '')
        return value
    }
  }
  return undefined
}

function textValues(value: unknown): string[] {
  if (Array.isArray(value))
    return value.map(item => String(item).trim()).filter(Boolean)
  if (value === undefined || value === null || value === '')
    return []
  return [String(value).trim()].filter(Boolean)
}

function firstText(value: unknown): string | undefined {
  return textValues(value)[0]
}

function candidateMatchReasons(candidate: AlertReviewItem) {
  const base = selectedItem.value
  const reasons: string[] = []
  const area = firstText(reviewDataValue(candidate, topologyAreaKeys)) || firstText(reviewDataValue(base, topologyAreaKeys))
  if (area)
    reasons.push(`topology area ${area}`)

  const baseCamera = base?.cameraId || base?.deviceId
  const candidateCamera = candidate.cameraId || candidate.deviceId
  const candidateAdjacent = textValues(reviewDataValue(candidate, adjacentCameraKeys))
  const baseAdjacent = textValues(reviewDataValue(base, adjacentCameraKeys))
  if ((baseCamera && candidateAdjacent.includes(baseCamera)) || (candidateCamera && baseAdjacent.includes(candidateCamera)))
    reasons.push(`adjacent ${baseCamera || '-'} -> ${candidateCamera || '-'}`)

  const baseObjects = textValues(reviewDataValue(base, objectIdKeys))
  const candidateObjects = textValues(reviewDataValue(candidate, objectIdKeys))
  const sharedObject = candidateObjects.find(objectId => baseObjects.includes(objectId))
  if (sharedObject)
    reasons.push(`shared object ${sharedObject}`)

  const correlationId = firstText(reviewDataValue(candidate, correlationKeys)) || firstText(reviewDataValue(base, correlationKeys))
  if (correlationId)
    reasons.push(`correlation ${correlationId}`)

  return reasons.length ? reasons : ['time window match']
}

function toOptionalNumber(value: unknown): number | undefined {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : undefined
}

defineExpose({
  refresh: loadItems,
})
</script>

<template>
  <div class="review-workbench" data-testid="alert-review-workbench">
    <div class="review-toolbar">
      <div class="review-filters">
        <select v-model="filters.reviewStatus" class="review-input" @change="loadItems">
          <option value="">
            全部状态
          </option>
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
        <input
          v-model.trim="filters.cameraId"
          class="review-input"
          placeholder="摄像头/设备ID"
          @keyup.enter="loadItems"
        >
        <input
          v-model.trim="filters.zoneCode"
          class="review-input"
          placeholder="zone"
          @keyup.enter="loadItems"
        >
        <input
          v-model.trim="filters.objectLabel"
          class="review-input"
          placeholder="label"
          @keyup.enter="loadItems"
        >
        <select v-model="filters.recordEvidenceStatus" class="review-input" @change="loadItems">
          <option value="">
            record
          </option>
          <option value="missing">
            missing
          </option>
          <option value="found">
            found
          </option>
          <option value="failed">
            failed
          </option>
        </select>
        <select v-model="filters.inReviewCase" class="review-input" @change="loadItems">
          <option value="">
            case
          </option>
          <option value="true">
            in case
          </option>
          <option value="false">
            no case
          </option>
        </select>
        <input
          v-model.number="filters.reviewerUserId"
          class="review-input reviewer-input"
          type="number"
          placeholder="复核人ID"
          @keyup.enter="loadItems"
        >
      </div>
      <Button type="primary" pre-icon="ant-design:reload-outlined" :loading="loading" @click="loadItems">
        刷新
      </Button>
    </div>

    <div class="semantic-search-bar">
      <input
        v-model.trim="semanticQuery"
        class="review-input semantic-input"
        placeholder="语义检索：人员、区域、物体、correlationId"
        @keyup.enter="searchReviewItems"
      >
      <Button size="small" :loading="semanticLoading" @click="searchReviewItems">
        语义检索
      </Button>
    </div>

    <div v-if="semanticHits.length" class="semantic-result-list">
      <div v-for="hit in semanticHits" :key="hit.item.id" class="semantic-result-item" @click="openItem(hit.item)">
        <div>
          <div class="strong">
            {{ hit.item.reviewItemNo }} / score {{ hit.score }}
          </div>
          <div class="muted">
            {{ hit.snippet || hit.matchedTerms.join(', ') }}
          </div>
        </div>
        <Button size="small" type="link" @click.stop="openItem(hit.item)">
          打开
        </Button>
      </div>
    </div>

    <section class="semantic-trigger-panel" data-testid="alert-review-semantic-trigger-panel">
      <div class="semantic-trigger-heading">
        <div>
          <strong>语义触发器</strong>
          <p>评估只生成命中解释和动作预览，确认前不会执行任何动作。</p>
        </div>
        <span v-if="semanticTriggerResult" class="status-pill">
          {{ semanticTriggerResult.humanConfirmationStatus }}
        </span>
      </div>
      <div class="semantic-trigger-form">
        <input
          v-model.trim="semanticTriggerForm.triggerName"
          data-testid="alert-review-semantic-trigger-name"
          class="review-input"
          placeholder="触发器名称"
        >
        <input
          v-model.trim="semanticTriggerForm.data"
          data-testid="alert-review-semantic-trigger-data"
          class="review-input semantic-trigger-data"
          placeholder="语义条件，例如：未戴安全帽进入门厅"
        >
        <input
          v-model.number="semanticTriggerForm.threshold"
          class="review-input semantic-trigger-threshold"
          type="number"
          min="0"
          max="1"
          step="0.05"
          aria-label="语义阈值"
        >
        <label class="semantic-trigger-action-option">
          <input v-model="semanticTriggerForm.actions" type="checkbox" value="notification">
          通知
        </label>
        <label class="semantic-trigger-action-option">
          <input v-model="semanticTriggerForm.actions" type="checkbox" value="sub_label">
          子标签
        </label>
        <Button
          v-if="canEvaluateSemanticTrigger"
          data-testid="alert-review-semantic-trigger-evaluate"
          size="small"
          type="primary"
          :loading="semanticTriggerLoading"
          @click="evaluateSemanticTriggerPreview"
        >
          生成预览
        </Button>
      </div>

      <template v-if="semanticTriggerResult">
        <div class="semantic-trigger-result-meta">
          <span>evaluationId {{ semanticTriggerResult.evaluationId }}</span>
          <span>input {{ semanticTriggerResult.inputVersion }}</span>
          <span>index v{{ semanticTriggerResult.latestIndexVersion }}</span>
          <span>命中 {{ semanticTriggerResult.matchedReviewItemIds.length }} 条</span>
          <span v-if="semanticTriggerResult.confirmedBy">确认人 {{ semanticTriggerResult.confirmedBy }}</span>
          <span v-if="semanticTriggerResult.confirmedAt">确认时间 {{ semanticTriggerResult.confirmedAt }}</span>
        </div>
        <div class="semantic-trigger-result-grid">
          <div>
            <strong>命中解释</strong>
            <div
              v-for="(explanation, index) in semanticTriggerResult.hitExplanations"
              :key="`semantic-hit-${index}`"
              class="semantic-trigger-result-row"
            >
              #{{ explanation.reviewItemId || '-' }} / {{ explanation.cameraId || '-' }} /
              hit index v{{ explanation.indexVersion ?? '-' }} /
              score {{ explanation.score ?? '-' }} /
              {{ Array.isArray(explanation.matchedTerms) ? explanation.matchedTerms.join(', ') : '-' }}
            </div>
          </div>
          <div>
            <strong>动作预览</strong>
            <div
              v-for="(preview, index) in semanticTriggerResult.actionPreviews"
              :key="`semantic-action-${index}`"
              class="semantic-trigger-result-row"
            >
              {{ preview.action || '-' }} → review item #{{ preview.reviewItemId || '-' }}
            </div>
          </div>
        </div>
        <div class="semantic-trigger-confirmation">
          <input
            v-model.trim="semanticTriggerForm.notes"
            data-testid="alert-review-semantic-trigger-notes"
            class="review-input semantic-trigger-data"
            placeholder="审批备注（可选）"
          >
          <span>确认只记录审批状态，不会执行动作</span>
          <Button
            v-if="semanticTriggerResult.humanConfirmationStatus === 'pending' && canConfirmSemanticTrigger"
            data-testid="alert-review-semantic-trigger-confirm"
            size="small"
            type="primary"
            :loading="semanticTriggerDecisionLoading"
            @click="confirmSemanticTriggerPreview('confirmed')"
          >
            确认预览
          </Button>
          <Button
            v-if="semanticTriggerResult.humanConfirmationStatus === 'pending' && canConfirmSemanticTrigger"
            data-testid="alert-review-semantic-trigger-reject"
            size="small"
            :loading="semanticTriggerDecisionLoading"
            @click="confirmSemanticTriggerPreview('rejected')"
          >
            驳回预览
          </Button>
        </div>
      </template>
    </section>

    <div class="summary-strip">
      <span>total {{ summary.total }}</span>
      <span>pending {{ summary.pendingReview }}</span>
      <span>reviewed {{ summary.reviewedByMe }}</span>
      <span>missing {{ summary.missingRecord }}</span>
      <span>event {{ summary.converted }}</span>
      <span>case {{ summary.inReviewCase }}</span>
    </div>

    <div class="ops-panel" data-testid="alert-review-ops-panel">
      <div class="ops-cell" data-testid="alert-review-ops-health">
        <span>health</span>
        <strong>{{ opsHealth?.alerts?.length ? opsHealth.alerts.join(' / ') : 'unknown' }}</strong>
        <small>{{ recordGapReasonSummary(opsHealth?.recordGapReasons) }} / repairable {{ opsHealth?.repairableCount ?? '-' }}</small>
        <Button size="small" :loading="opsLoading" @click="loadOpsHealth">
          check
        </Button>
      </div>
      <div class="ops-cell" data-testid="alert-review-ops-reconcile">
        <span>reconcile</span>
        <strong>{{ opsPatrol?.status || (opsReconciliation ? 'reconciled' : '-') }}</strong>
        <small>{{ opsReconciliation?.findings?.join(' / ') || opsPatrol?.recommendedActions?.join(' / ') || 'no patrol yet' }}</small>
        <Button size="small" :loading="opsLoading" @click="runOpsReconcile">
          repair
        </Button>
        <Button size="small" :loading="opsLoading" @click="runOpsPatrol">
          patrol
        </Button>
      </div>
      <div class="ops-cell" data-testid="alert-review-ops-semantic">
        <span>semantic</span>
        <strong>{{ semanticIndexEvaluation?.backlogAlarmLevel || 'unknown' }}</strong>
        <small>{{ semanticProgressText(semanticIndexEvaluation) }} / {{ semanticBacklogSummary(semanticIndexEvaluation) }}</small>
        <Button size="small" :loading="opsLoading" @click="loadOpsHealth">
          index
        </Button>
      </div>
      <div class="ops-cell" data-testid="alert-review-ops-report-ack">
        <span>report</span>
        <strong>{{ opsReportAcknowledgementText }}</strong>
        <small>{{ opsReport?.deliveryPlan?.reportKey || opsReport?.title || 'shift report' }}</small>
        <Button
          size="small"
          data-testid="alert-review-ops-report-ack-action"
          :disabled="opsReportAcknowledgement?.status === 'acknowledged'"
          :loading="opsLoading"
          @click="runOpsReportAcknowledgement"
        >
          ack
        </Button>
      </div>
      <div class="ops-cell" data-testid="alert-review-ops-smoke">
        <span>smoke</span>
        <strong>{{ opsSmoke?.status || '-' }}</strong>
        <small>{{ opsSmoke?.profile || 'device-video-web' }}</small>
        <Button size="small" :loading="opsLoading" @click="runOpsSmoke">
          run
        </Button>
      </div>
      <div class="ops-cell" data-testid="alert-review-ops-manifest-verify">
        <span>manifest</span>
        <strong>{{ opsVerification?.valid === undefined ? '-' : (opsVerification.valid ? 'valid' : 'invalid') }}</strong>
        <small>{{ opsVerification?.replayableReasons?.join(' / ') || evidenceExportJob?.jobNo || 'no job' }}</small>
        <Button size="small" :loading="opsLoading" @click="verifyOpsEvidencePackage">
          verify
        </Button>
      </div>
      <div class="ops-cell" data-testid="alert-review-ops-rule-geometry">
        <span>rule</span>
        <strong>{{ opsGeometry?.geometryType || 'bottom_center' }}</strong>
        <small>{{ opsGeometry?.matchTraces?.[0]?.zoneCode || selectedItem?.zoneCode || '-' }}</small>
        <Button size="small" :loading="opsLoading" @click="evaluateOpsRuleGeometry">
          trace
        </Button>
      </div>
    </div>

    <div class="review-layout">
      <div class="review-list">
        <table class="review-table">
          <thead>
            <tr>
              <th>线索</th>
              <th>摄像头</th>
              <th>规则/区域</th>
              <th>时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in items"
              :key="item.id"
              :class="{ selected: selectedItem?.id === item.id }"
              @click="openItem(item)"
            >
              <td>
                <div class="strong">
                  {{ item.reviewItemNo }}
                </div>
                <div class="muted">
                  {{ item.alertCount || 0 }} 条告警
                </div>
              </td>
              <td>
                <div>{{ item.cameraId || item.deviceId || '-' }}</div>
                <div class="muted">
                  {{ item.objectLabel || '-' }}
                </div>
              </td>
              <td>
                <div>{{ item.ruleCode }}</div>
                <div class="muted">
                  {{ item.zoneCode || '未绑定区域' }}
                </div>
              </td>
              <td>
                <div>{{ formatTime(item.firstAlertTime) }}</div>
                <div class="muted">
                  {{ formatTime(item.lastAlertTime) }}
                </div>
              </td>
              <td>
                <div class="status-stack">
                  <span class="status-pill" :class="item.reviewStatus">
                    {{ statusText(item.reviewStatus) }}
                  </span>
                  <span v-if="item.recordEvidenceStatus" class="record-status-pill" :class="item.recordEvidenceStatus">
                    {{ recordStatusText(item.recordEvidenceStatus) }}
                  </span>
                  <span v-if="item.recordEvidenceMessage" class="record-reason">
                    {{ recordReasonText(item.recordEvidenceMessage) }}
                  </span>
                  <span v-if="item.eventStatus" class="event-status-pill">
                    {{ eventStatusText(item.eventStatus) }}
                  </span>
                  <span v-if="item.eventReviewStatus" class="event-status-pill">
                    {{ item.eventReviewStatus }}
                  </span>
                  <span v-if="item.ruleSuggestionStatus" class="event-status-pill">
                    {{ ruleSuggestionStatusText(item.ruleSuggestionStatus) }}
                  </span>
                  <span v-if="ruleSuggestionSafetyRows(item).length" class="rule-suggestion-safety">
                    <span
                      v-for="row in ruleSuggestionSafetyRows(item)"
                      :key="row"
                      class="rule-suggestion-safety-line"
                    >
                      {{ row }}
                    </span>
                  </span>
                </div>
              </td>
              <td>
                <div class="row-actions" @click.stop>
                  <Button
                    v-if="canRetryRecordEvidence(item)"
                    size="small"
                    type="link"
                    :loading="recordRetryLoading"
                    @click="retryRecordEvidenceForItem(item)"
                  >
                    补证
                  </Button>
                  <Button size="small" type="link" data-testid="alert-review-open-rule-drawer" @click="openRuleDrawer(item)">
                    区域规则
                  </Button>
                  <Button size="small" type="link" @click="openItem(item)">
                    证据
                  </Button>
                  <Button size="small" type="link" data-testid="alert-review-list-playback" @click="openListPlayback(item)">
                    录像
                  </Button>
                  <Button
                    size="small"
                    type="link"
                    data-testid="alert-review-list-coverage"
                    @click="loadRecordCoverageForItem(item)"
                  >
                    覆盖度
                  </Button>
                  <Button size="small" type="link" @click="markReviewed(item)">
                    复核
                  </Button>
                  <Button v-if="canMarkFalsePositive(item)" size="small" type="link" @click="markFalsePositive(item)">
                    误报
                  </Button>
                  <Button
                    v-if="canShadowEvaluateRuleSuggestion(item)"
                    size="small"
                    type="link"
                    @click="updateRuleSuggestion(item, 'shadow_evaluated')"
                  >
                    影子评估
                  </Button>
                  <Button
                    v-if="canAcceptRuleSuggestion(item)"
                    size="small"
                    type="link"
                    @click="updateRuleSuggestion(item, 'accepted')"
                  >
                    审批通过
                  </Button>
                  <Button
                    v-if="canRejectRuleSuggestion(item)"
                    size="small"
                    type="link"
                    @click="updateRuleSuggestion(item, 'rejected')"
                  >
                    拒绝建议
                  </Button>
                  <Button
                    v-if="canApplyRuleSuggestion(item)"
                    size="small"
                    type="link"
                    @click="updateRuleSuggestion(item, 'applied')"
                  >
                    应用规则
                  </Button>
                  <Button
                    v-if="item.ruleSuggestionStatus"
                    size="small"
                    type="link"
                    :loading="rulePreviewLoading && selectedItem?.id === item.id"
                    @click="previewRuleSuggestion(item)"
                  >
                    预览
                  </Button>
                  <Button
                    v-if="item.ruleSuggestionStatus === 'applied' && canRevertRuleSuggestion"
                    size="small"
                    type="link"
                    @click="revertRuleSuggestion(item)"
                  >
                    回滚
                  </Button>
                  <Button
                    v-if="item.ruleSuggestionStatus && canReplayRule"
                    size="small"
                    type="link"
                    :loading="ruleReplayLoading && selectedItem?.id === item.id"
                    @click="replayRuleForItem(item)"
                  >
                    回放分析
                  </Button>
                  <Button size="small" type="link" data-testid="alert-review-create-case" @click="createCaseFromItem(item)">
                    新建复盘
                  </Button>
                  <Button v-if="activeCase" size="small" type="link" @click="addItemToActiveCase(item)">
                    加入复盘
                  </Button>
                  <Button size="small" type="link" @click="ignoreItem(item)">
                    忽略
                  </Button>
                  <Button size="small" type="link" @click="convertToEvent(item)">
                    转事件
                  </Button>
                </div>
              </td>
            </tr>
            <tr v-if="!loading && items.length === 0">
              <td colspan="6" class="empty-cell">
                暂无复核线索
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <aside class="timeline-panel">
        <template v-if="selectedItem">
          <div class="timeline-header">
            <div>
              <div class="strong">
                {{ selectedItem.reviewItemNo }}
              </div>
              <div class="muted">
                {{ selectedItem.sourceAlertIds?.join(' / ') || '-' }}
              </div>
              <div class="timeline-meta">
                <span v-if="selectedItem.recordEvidenceStatus" class="record-status-pill" :class="selectedItem.recordEvidenceStatus">
                  {{ recordStatusText(selectedItem.recordEvidenceStatus) }}
                </span>
                <span v-if="selectedItem.recordEvidenceMessage" class="record-reason">
                  {{ recordReasonText(selectedItem.recordEvidenceMessage) }}
                </span>
                <span v-if="selectedItem.eventStatus" class="event-status-pill">
                  {{ eventStatusText(selectedItem.eventStatus) }}
                </span>
                <span v-if="selectedItem.closeCheckStatus" class="event-status-pill">
                  {{ selectedItem.closeCheckStatus }}
                </span>
                <span v-if="selectedItem.evidenceStatus" class="event-status-pill">
                  {{ selectedItem.evidenceStatus }}
                </span>
              </div>
            </div>
            <Button
              v-if="canRetryRecordEvidence(selectedItem)"
              size="small"
              :loading="recordRetryLoading"
              @click="retryRecordEvidenceForItem(selectedItem)"
            >
              补证
            </Button>
            <Button size="small" pre-icon="ant-design:reload-outlined" :loading="timelineLoading" @click="loadTimeline()">
              更新证据
            </Button>
            <Button size="small" :loading="coverageLoading" @click="loadRecordCoverage(selectedItem)">
              覆盖度
            </Button>
            <Button size="small" :loading="detailStreamLoading" @click="loadDetailStream(selectedItem)">
              Detail
            </Button>
          </div>

          <div class="reviewer-status-panel">
            <span>{{ filters.reviewerUserId ? `复核人 #${filters.reviewerUserId}` : '未选择复核人' }}</span>
            <Button
              size="small"
              :loading="userStatusLoading"
              :disabled="!filters.reviewerUserId"
              @click="markReviewedByCurrentUser(selectedItem, true)"
            >
              我已复核
            </Button>
            <Button
              size="small"
              :loading="userStatusLoading"
              :disabled="!filters.reviewerUserId"
              @click="markReviewedByCurrentUser(selectedItem, false)"
            >
              取消我的复核
            </Button>
          </div>

          <div v-if="rulePreview" class="rule-preview-panel">
            <div class="panel-heading">
              <span>规则变更预览</span>
              <Button size="small" type="link" @click="rulePreview = null">
                关闭
              </Button>
            </div>
            <div class="muted">
              affected {{ rulePreview.affectedReviewItemNos.join(' / ') || '-' }}
            </div>
            <div class="diff-list">
              <div v-for="line in rulePreview.diff" :key="line" class="diff-line">
                {{ line }}
              </div>
            </div>
          </div>

          <div v-if="unifiedTimeline.length" class="unified-timeline-panel" data-testid="alert-review-unified-timeline">
            <div class="panel-heading">
              <span>Review video timeline</span>
              <span>{{ unifiedTimeline.length }}</span>
            </div>
            <div class="unified-timeline-list">
              <div
                v-for="entry in unifiedTimeline"
                :key="unifiedTimelineKey(entry)"
                class="unified-timeline-item"
                :class="[entry.kind, entry.status, entry.className]"
              >
                <div class="timeline-dot">
                  <Icon :icon="entry.icon" />
                </div>
                <div class="timeline-body">
                  <div class="timeline-title">
                    {{ entry.title }}
                    <span>{{ timelineRange(entry) }}</span>
                  </div>
                  <div class="muted">
                    {{ entry.subtitle || '-' }}
                  </div>
                  <div v-if="entry.meta.length" class="unified-meta">
                    <span v-for="meta in entry.meta" :key="meta">{{ meta }}</span>
                  </div>
                  <div v-if="entry.uri" class="timeline-uri">
                    {{ entry.uri }}
                  </div>
                  <Button
                    v-if="entry.actionLabel"
                    size="small"
                    type="link"
                    data-testid="alert-review-unified-action"
                    @click="openUnifiedTimelineEntry(entry)"
                  >
                    {{ entry.actionLabel }}
                  </Button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="detailStream.length" class="timeline-list detail-stream-list" data-testid="alert-review-detail-stream">
            <div v-for="entry in detailStream" :key="detailStreamKey(entry)" class="timeline-item">
              <div class="timeline-dot">
                <Icon :icon="entry.materialType === 'snapshot' ? 'ion:image-sharp' : 'icon-park-outline:video'" />
              </div>
              <div class="timeline-body">
                <div class="timeline-title">
                  {{ entry.label || entry.objectId || entry.lifecycleEvent || 'object' }}
                  <span>{{ formatTime(entry.seekTime || entry.happenedAt) }}</span>
                </div>
                <div class="muted">
                  {{ entry.cameraId || selectedItem.cameraId || selectedItem.deviceId || '-' }} / {{ entry.zoneCode || '-' }}
                </div>
                <div class="timeline-uri">
                  {{ entry.materialUri || '-' }}
                </div>
                <Button size="small" type="link" data-testid="alert-review-detail-seek" @click="openDetailStreamEntry(entry)">
                  Seek
                </Button>
              </div>
            </div>
          </div>

          <div v-if="reviewSegment" class="rule-preview-panel" data-testid="alert-review-review-segment">
            <div class="panel-heading">
              <span>Review segment</span>
              <span class="muted">{{ reviewSegment.status || '-' }}</span>
            </div>
            <div class="muted">
              {{ reviewSegment.segmentId || '-' }} | {{ formatTime(reviewSegment.startTime) }} - {{ formatTime(reviewSegment.endTime) }}
            </div>
            <div class="muted">
              camera {{ reviewSegment.cameraId || selectedItem.cameraId || '-' }} | severity {{ reviewSegment.severity || '-' }}
            </div>
            <div class="muted">
              objects {{ (reviewSegment.objectIds || []).join(' / ') || '-' }} | zones {{ (reviewSegment.zones || []).join(' / ') || '-' }}
            </div>
            <div class="muted">
              events {{ (reviewSegment.events || []).map(event => event.event || event.lifecycleEvent || '-').join(' -> ') || '-' }}
            </div>
            <Button size="small" type="link" :loading="reviewSegmentLoading" @click="loadReviewSegment()">
              Refresh
            </Button>
          </div>

          <div v-if="ruleReplay" class="rule-preview-panel">
            <div class="panel-heading">
              <span>Rule replay report</span>
              <Button size="small" type="link" @click="ruleReplay = null">
                Close
              </Button>
            </div>
            <div class="muted">
              decision {{ ruleReplay.report?.decision || '-' }} | apply {{ ruleReplay.report?.shouldApply ? 'yes' : 'no' }}
            </div>
            <div class="muted">
              hits {{ ruleReplay.matchBeforeCount }} -> {{ ruleReplay.matchAfterCount }}
            </div>
            <div class="muted">
              false positive reduction {{ ruleReplay.report?.falsePositiveReduction ?? '-' }} | possible missed {{ ruleReplay.report?.possibleMissedCount ?? '-' }}
            </div>
            <div class="muted">
              scope {{ (ruleReplay.report?.impactScope?.cameraIds || []).join(' / ') || '-' }} / {{ (ruleReplay.report?.impactScope?.zoneCodes || []).join(' / ') || '-' }}
            </div>
            <div class="muted">
              rule version {{ replayRuleVersionText(ruleReplay) }}
            </div>
            <div class="muted">
              sample window {{ replaySampleWindowText(ruleReplay) }}
            </div>
            <div class="muted">
              hit comparison {{ replayHitComparisonText(ruleReplay) }}
            </div>
            <div class="muted">
              false negative {{ replayFalseNegativeText(ruleReplay) }}
            </div>
          </div>

          <div v-if="coverage.length" class="coverage-list" data-testid="alert-review-record-coverage">
            <div v-for="segment in coverage" :key="coverageKey(segment)" class="coverage-segment" :class="segment.status">
              <span>{{ coverageStatusText(segment.status) }}</span>
              <span>{{ formatTime(segment.startTime) }} - {{ formatTime(segment.endTime) }}</span>
              <span v-if="segment.motion !== undefined">motion {{ segment.motion }}</span>
              <span v-if="segment.objects !== undefined">objects {{ segment.objects }}</span>
              <span v-if="segment.recordUri" class="coverage-uri">{{ segment.recordUri }}</span>
              <Button
                v-if="segment.recordUri"
                size="small"
                type="link"
                data-testid="alert-review-coverage-seek"
                @click="openCoverageSegment(segment)"
              >
                Seek
              </Button>
            </div>
          </div>

          <div v-if="activeCase" class="case-panel" data-testid="alert-review-case-panel">
            <div>
              <div class="strong">
                {{ activeCase.title || activeCase.caseNo }}
              </div>
              <div class="muted">
                {{ activeCase.reviewItemIds.length }} 条线索 / {{ activeCase.cameraIds.join(' / ') || '-' }}
              </div>
            </div>
            <Button size="small" :loading="caseLoading" @click="loadCaseTimeline">
              复盘时间线
            </Button>
          </div>

          <div v-if="activeCase" class="case-ops">
            <span class="case-status-pill">{{ activeCase.status || '-' }}</span>
            <input
              v-model.number="caseLifecycleForm.ownerUserId"
              class="review-input case-id-input"
              data-testid="alert-review-case-owner-input"
              placeholder="owner"
            />
            <Button
              size="small"
              data-testid="alert-review-case-owner"
              :loading="caseLifecycleLoading"
              @click="assignActiveCaseOwner"
            >
              Owner
            </Button>
            <Button
              size="small"
              data-testid="alert-review-case-close"
              :loading="caseLifecycleLoading"
              @click="closeActiveCase"
            >
              Close
            </Button>
            <input
              v-model.number="caseLifecycleForm.sourceReviewCaseId"
              class="review-input case-id-input"
              data-testid="alert-review-case-merge-source"
              placeholder="source case"
            />
            <Button
              size="small"
              data-testid="alert-review-case-merge"
              :loading="caseLifecycleLoading"
              @click="mergeActiveCase"
            >
              Merge
            </Button>
            <Button
              size="small"
              data-testid="alert-review-case-split"
              :loading="caseLifecycleLoading"
              :disabled="!selectedItem"
              @click="splitSelectedItemFromCase"
            >
              Split
            </Button>            <Button size="small" data-testid="alert-review-ai-summary-action" :loading="aiSummaryLoading" @click="generateAiSummary">
              AI 摘要
            </Button>
            <Button size="small" data-testid="alert-review-export-action" :loading="evidenceExportLoading" @click="exportCaseEvidence">
              证据导出
            </Button>
          </div>

          <div v-if="aiSummary" class="ai-summary-panel" data-testid="alert-review-ai-summary">
            <div class="panel-heading">
              <span>{{ aiSummary.title }}</span>
              <span>{{ formatTime(aiSummary.generatedAt) }}</span>
            </div>
            <p>{{ aiSummary.summary }}</p>
            <div v-if="aiSummary.structuredData?.threatLevel" class="muted">
              threat {{ aiSummary.structuredData.threatLevel }}
            </div>
            <div v-if="aiSummary.structuredData?.responsibilityUnit" class="muted">
              unit {{ aiSummary.structuredData.responsibilityUnit }}
            </div>
            <div v-if="aiSummary.structuredData?.convertibleToEvent !== undefined" class="muted">
              convertible {{ aiSummary.structuredData.convertibleToEvent ? 'yes' : 'no' }}
            </div>
            <div v-if="aiSummary.evidenceGaps.length" class="muted">
              gaps: {{ aiSummary.evidenceGaps.join(' / ') }}
            </div>
            <div v-if="aiSummary.recommendedActions.length" class="muted">
              actions: {{ aiSummary.recommendedActions.join(' / ') }}
            </div>
          </div>

          <div v-if="evidenceExport" class="export-panel" data-testid="alert-review-evidence-export">
            <div class="panel-heading">
              <span>{{ evidenceExport.packageNo }}</span>
              <span>{{ evidenceExport.format }}</span>
            </div>
            <div v-if="evidenceExportJob" class="muted">
              job {{ evidenceExportJob.jobNo }} | {{ evidenceExportJob.status }}
            </div>
            <div v-if="evidenceExportJob" class="muted">
              hash {{ evidenceExportJob.fileHash }}
            </div>
            <div v-if="evidenceExportJob" class="muted">
              expires {{ formatTime(evidenceExportJob.expiresAt) }}
            </div>
            <div v-if="evidenceExportJob?.boundEventIds?.length" class="muted">
              events {{ evidenceExportJob.boundEventIds.join(' / ') }}
            </div>
            <div class="muted">
              checksum {{ manifestChecksum(evidenceExport) }}
            </div>
            <div class="export-uri-list">
              <div v-for="uri in evidenceExport.evidenceUris" :key="uri" class="coverage-uri">
                {{ uri }}
              </div>
            </div>
          </div>

          <div v-if="evidenceAudit.length" class="export-panel" data-testid="alert-review-evidence-audit">
            <div class="panel-heading">
              <span>Evidence audit</span>
              <Button size="small" type="link" :loading="evidenceAuditLoading" @click="() => loadEvidenceAudit()">
                Refresh
              </Button>
            </div>
            <div v-for="entry in evidenceAudit" :key="evidenceAuditKey(entry)" class="muted">
              {{ entry.actionType }} | {{ formatTime(entry.happenedAt) }} | {{ entry.fileHash || '-' }} | events {{ entry.boundEventIds?.join(' / ') || '-' }}
            </div>
          </div>

          <div v-if="caseCandidates.length" class="candidate-list">
            <div v-for="candidate in caseCandidates" :key="candidate.id" class="candidate-item" data-testid="alert-review-case-candidate">
              <div class="candidate-main">
                <div class="strong">
                  {{ candidate.reviewItemNo || candidate.id }}
                </div>
                <div class="muted">
                  {{ candidate.cameraId || candidate.deviceId || '-' }} / {{ formatTime(candidate.firstAlertTime) }}
                </div>
                <div class="candidate-reasons">
                  <span v-for="reason in candidateMatchReasons(candidate)" :key="reason">{{ reason }}</span>
                </div>
              </div>
              <Button v-if="activeCase" size="small" type="link" data-testid="alert-review-candidate-add" @click="addItemToActiveCase(candidate)">
                add
              </Button>
            </div>
          </div>

          <div v-if="caseTimeline.length" class="timeline-list case-timeline">
            <div v-for="evidence in caseTimeline" :key="caseTimelineKey(evidence)" class="timeline-item">
              <div class="timeline-dot">
                <Icon :icon="evidence.materialType === 'snapshot' ? 'ion:image-sharp' : 'icon-park-outline:video'" />
              </div>
              <div class="timeline-body">
                <div class="timeline-title">
                  {{ evidence.cameraId || '-' }}
                  <span>{{ formatTime(evidence.happenedAt) }}</span>
                </div>
                <div class="muted">
                  {{ evidence.sourceAlertId }} / {{ evidence.materialType }}
                </div>
                <div class="timeline-uri">
                  {{ evidence.materialUri || '-' }}
                </div>
                <Button
                  v-if="evidence.materialUri"
                  size="small"
                  type="link"
                  data-testid="alert-review-case-timeline-seek"
                  @click="openCaseTimelineEntry(evidence)"
                >
                  Seek
                </Button>
              </div>
            </div>
          </div>

          <div v-if="timeline.length" class="timeline-list">
            <div v-for="evidence in timeline" :key="evidenceKey(evidence)" class="timeline-item">
              <div class="timeline-dot">
                <Icon :icon="evidence.materialType === 'snapshot' ? 'ion:image-sharp' : 'icon-park-outline:video'" />
              </div>
              <div class="timeline-body">
                <div class="timeline-title">
                  {{ evidence.materialType === 'snapshot' ? '抓拍' : '录像' }}
                  <span>{{ formatTime(evidence.happenedAt) }}</span>
                </div>
                <div class="muted">
                  {{ evidence.sourceAlertId }}
                </div>
                <div class="timeline-uri">
                  {{ evidence.materialUri || '-' }}
                </div>
                <Button size="small" type="link" @click="openEvidence(evidence)">
                  {{ evidence.materialType === 'snapshot' ? '查看抓拍' : '查看录像' }}
                </Button>
              </div>
            </div>
          </div>
          <div v-else class="empty-panel">
            暂无证据
          </div>
        </template>
        <div v-else class="empty-panel">
          选择一条线索查看抓拍和录像
        </div>
      </aside>
    </div>

    <a-drawer v-model:visible="ruleDrawerVisible" title="区域规则" width="960" destroy-on-close>
      <DeviceRegionDrawer
        v-if="ruleDrawerDeviceId"
        :device-id="ruleDrawerDeviceId"
        @save="handleRegionRuleSave"
      />
      <div v-else class="empty-panel">
        未选择摄像头
      </div>
    </a-drawer>
  </div>
</template>

<style scoped lang="less">
.review-workbench {
  padding: 12px 16px 16px;
  background: #fff;
}

.review-toolbar,
.review-filters,
.row-actions,
.timeline-header,
.timeline-actions,
.timeline-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-actions {
  flex-wrap: wrap;
}

.review-filters {
  flex-wrap: wrap;
}

.review-toolbar {
  justify-content: space-between;
  margin-bottom: 12px;
}

.summary-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  color: #5f6b7a;
  font-size: 12px;
}

.ops-panel {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.ops-cell {
  display: grid;
  min-width: 0;
  gap: 4px;
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fbfcfe;
  color: #475569;
  font-size: 12px;
}

.ops-cell strong,
.ops-cell small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ops-cell strong {
  color: #1f2937;
}

.review-input {
  height: 32px;
  min-width: 160px;
  padding: 0 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
}

.reviewer-input {
  min-width: 120px;
}

.case-id-input {
  width: 104px;
  min-width: 104px;
}

.semantic-search-bar,
.semantic-result-item,
.reviewer-status-panel,
.case-ops,
.panel-heading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.semantic-search-bar {
  margin-bottom: 8px;
}

.semantic-input {
  flex: 1;
}

.semantic-result-list {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
}

.semantic-result-item {
  justify-content: space-between;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fbfcfe;
  cursor: pointer;
}

.semantic-trigger-panel {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid #dfe5ee;
  border-radius: 6px;
  background: #fbfcfe;
}

.semantic-trigger-heading,
.semantic-trigger-form,
.semantic-trigger-confirmation,
.semantic-trigger-result-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.semantic-trigger-heading {
  justify-content: space-between;

  p {
    margin: 2px 0 0;
    color: #7b8494;
    font-size: 12px;
  }
}

.semantic-trigger-form,
.semantic-trigger-confirmation {
  flex-wrap: wrap;
}

.semantic-trigger-data {
  flex: 1;
}

.semantic-trigger-threshold {
  width: 90px;
  min-width: 90px;
}

.semantic-trigger-action-option {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #5f6570;
  font-size: 12px;
}

.semantic-trigger-result-meta {
  flex-wrap: wrap;
  color: #5f6570;
  font-size: 12px;
}

.semantic-trigger-result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.semantic-trigger-result-row {
  margin-top: 5px;
  color: #5f6570;
  font-size: 12px;
  line-height: 18px;
}

.semantic-trigger-confirmation > span {
  color: #8a5a00;
  font-size: 12px;
}

.review-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 12px;
}

.review-list {
  min-width: 0;
  overflow: auto;
}

.review-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 13px;

  th,
  td {
    padding: 10px 8px;
    border-bottom: 1px solid #f0f0f0;
    text-align: left;
    vertical-align: top;
  }

  th {
    color: #5f6570;
    font-weight: 600;
    background: #fafafa;
  }

  tr {
    cursor: pointer;
  }

  tr:hover td,
  tr.selected td {
    background: #f5f8ff;
  }
}

.strong {
  color: #1f2937;
  font-weight: 600;
}

.muted {
  color: #7b8494;
  font-size: 12px;
  line-height: 18px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  min-width: 64px;
  height: 22px;
  justify-content: center;
  padding: 0 8px;
  border-radius: 11px;
  font-size: 12px;
  background: #edf2ff;
  color: #3154b8;

  &.reviewed {
    background: #edf7ed;
    color: #287a3e;
  }

  &.ignored {
    background: #f4f4f5;
    color: #5f6570;
  }

  &.false_positive {
    background: #fff1f0;
    color: #b42318;
  }

  &.converted {
    background: #fff2e8;
    color: #b85c00;
  }
}

.status-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.record-status-pill,
.event-status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 7px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 20px;
}

.record-reason {
  max-width: 180px;
  overflow: hidden;
  color: #b42318;
  font-size: 12px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rule-suggestion-safety {
  display: grid;
  gap: 2px;
  max-width: 220px;
  color: #475467;
  font-size: 12px;
  line-height: 16px;
}

.rule-suggestion-safety-line {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-status-pill {
  background: #eef6ff;
  color: #2364aa;

  &.found,
  &.not_required {
    background: #edf7ed;
    color: #287a3e;
  }

  &.missing,
  &.failed {
    background: #fff1f0;
    color: #b42318;
  }
}

.event-status-pill {
  background: #f5f3ff;
  color: #5b3db2;
}

.timeline-panel {
  min-height: 420px;
  padding: 12px;
  border: 1px solid #edf0f5;
  border-radius: 6px;
  background: #fbfcfe;
}

.timeline-header {
  justify-content: space-between;
  margin-bottom: 12px;
}

.timeline-meta {
  flex-wrap: wrap;
  margin-top: 6px;
}

.reviewer-status-panel,
.rule-preview-panel,
.ai-summary-panel,
.export-panel {
  margin-bottom: 12px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

.reviewer-status-panel {
  flex-wrap: wrap;
}

.coverage-list,
.case-panel,
.case-ops {
  margin-bottom: 12px;
}

.case-ops {
  flex-wrap: wrap;
}

.case-status-pill {
  padding: 4px 8px;
  border: 1px solid #c7d2fe;
  border-radius: 4px;
  color: #3730a3;
  font-size: 12px;
  background: #eef2ff;
}

.panel-heading {
  justify-content: space-between;
  margin-bottom: 6px;
  color: #1f2937;
  font-weight: 600;
}

.diff-list,
.export-uri-list {
  display: grid;
  gap: 4px;
  margin-top: 8px;
}

.diff-line {
  overflow: hidden;
  padding: 4px 6px;
  border-radius: 4px;
  color: #475467;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: #f8fafc;
}

.coverage-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.unified-timeline-panel {
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fbfcfe;
}

.unified-timeline-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.unified-timeline-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 8px;

  &.missing .timeline-dot {
    color: #b42318;
    background: #fff1f0;
  }

  &.motion .timeline-dot {
    color: #ad6800;
    background: #fff7e6;
  }

  &.export .timeline-dot {
    color: #067647;
    background: #ecfdf3;
  }
}

.unified-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;

  span {
    padding: 2px 6px;
    border-radius: 4px;
    color: #475467;
    background: #f2f4f7;
    font-size: 12px;
  }
}

.coverage-segment {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  background: #eef6ff;
  color: #1f3f68;
  font-size: 12px;

  &.missing {
    background: #fff1f0;
    color: #b42318;
  }

  &.motion {
    background: #fff7e6;
    color: #ad6800;
  }
}

.coverage-uri {
  grid-column: 1 / -1;
  overflow: hidden;
  color: #667085;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.case-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
  border: 1px solid #d8e2ff;
  border-radius: 6px;
  background: #f6f8ff;
}

.case-timeline {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #edf0f5;
}

.candidate-list {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
}

.candidate-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid #eef1f5;
  border-radius: 4px;
  font-size: 12px;
}

.candidate-main {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.candidate-reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.candidate-reasons span {
  padding: 1px 6px;
  border-radius: 4px;
  color: #3154b8;
  background: #edf2ff;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 8px;
}

.timeline-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #2f65d9;
  background: #eaf1ff;
}

.timeline-body {
  min-width: 0;
  padding-bottom: 10px;
  border-bottom: 1px solid #edf0f5;
}

.timeline-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: #1f2937;
  font-weight: 600;
}

.timeline-uri {
  overflow: hidden;
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-cell,
.empty-panel {
  padding: 48px 16px;
  color: #8b95a5;
  text-align: center;
}

@media (max-width: 1200px) {
  .review-layout {
    grid-template-columns: 1fr;
  }
}
</style>
