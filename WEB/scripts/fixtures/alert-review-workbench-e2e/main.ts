import { createApp, defineComponent, h, nextTick } from 'vue'
import { getAlertReviewE2EMessages } from './messageStub'
import AlertReviewWorkbench from '@/views/alert/components/AlertReviewWorkbench.vue'
import { useModal } from '@/components/Modal'
import { playAlertRecordInModal } from '@/utils/alertRecordPlayback'

declare global {
  interface Window {
    __alertReviewE2EApiCalls?: Array<{ name: string; payload?: unknown }>
  }
}

const viewVideoEvents: unknown[] = []
const viewImageEvents: unknown[] = []
const convertedEvents: unknown[] = []
const modalPayloads: unknown[] = []
const nativeSeekAssignments: number[] = []
const e2eMode = new URLSearchParams(window.location.search).get('mode') || 'all'

const nativeCurrentTimes = new WeakMap<HTMLMediaElement, number>()
Object.defineProperty(HTMLMediaElement.prototype, 'currentTime', {
  configurable: true,
  get() {
    return nativeCurrentTimes.get(this) ?? 0
  },
  set(value: number) {
    nativeCurrentTimes.set(this, value)
    nativeSeekAssignments.push(value)
  },
})
Object.defineProperty(HTMLMediaElement.prototype, 'play', {
  configurable: true,
  value: () => Promise.resolve(),
})
Object.defineProperty(HTMLMediaElement.prototype, 'pause', {
  configurable: true,
  value: () => undefined,
})
Object.defineProperty(HTMLMediaElement.prototype, 'load', {
  configurable: true,
  value: () => undefined,
})

if (e2eMode === 'dev-api-real-drawer') {
  const NativeImage = window.Image
  const loadedImageSrc = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="320" height="180" viewBox="0 0 320 180"%3E%3Crect width="320" height="180" fill="%23f6f7f9"/%3E%3C/svg%3E'
  window.Image = class AlertReviewE2EImage extends NativeImage {
    private originalSrc = ''

    set src(value: string) {
      this.originalSrc = value
      super.src = loadedImageSrc
    }

    get src() {
      return this.originalSrc || super.src
    }
  } as typeof Image
}

function resultElement() {
  const element = document.querySelector<HTMLPreElement>('#alert-review-e2e-result')
  if (!element)
    throw new Error('missing result element')
  return element
}

function writeResult(status: 'passed' | 'failed', payload: unknown) {
  const element = resultElement()
  element.dataset.status = status
  element.textContent = JSON.stringify(payload, null, 2)
}

function text() {
  return document.body.textContent || ''
}

function wait(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

async function waitFor(assertion: () => boolean, label: string, timeoutMs = 5000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    await nextTick()
    if (assertion())
      return
    await wait(25)
  }
  throw new Error(`timed out waiting for ${label}`)
}

function requiredElement<T extends HTMLElement>(selector: string): T {
  const element = document.querySelector<T>(selector)
  if (!element)
    throw new Error(`missing ${selector}`)
  return element
}

function click(selector: string) {
  requiredElement<HTMLElement>(selector).click()
}

async function clickAndWaitForVideo(selector: string, label: string) {
  const before = viewVideoEvents.length
  click(selector)
  await waitFor(() => viewVideoEvents.length > before, label)
}

async function clickAndAssertNativeSeek(selector: string, label: string, expectedOffset: number) {
  const before = nativeSeekAssignments.length
  await clickAndWaitForVideo(selector, `${label} viewVideo event`)
  await waitFor(() => !!document.querySelector('video.native-video-player'), `${label} native player`)
  requiredElement<HTMLVideoElement>('video.native-video-player')
    .dispatchEvent(new Event('loadedmetadata'))
  await waitFor(() => nativeSeekAssignments.length > before, `${label} native currentTime assignment`)
  const actual = nativeSeekAssignments[nativeSeekAssignments.length - 1]
  if (actual !== expectedOffset)
    throw new Error(`${label} expected native currentTime ${expectedOffset}, got ${String(actual)}`)
}

function clickButtonByText(label: string) {
  const button = Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
    .find(candidate => candidate.textContent?.trim() === label)
  if (!button)
    throw new Error(`missing button ${label}`)
  button.click()
}

function hasButtonByText(label: string) {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
    .some(candidate => candidate.textContent?.trim() === label)
}

function buttonByText(label: string) {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
    .find(candidate => candidate.textContent?.trim() === label)
}

function setInputValue(selector: string, value: string) {
  const input = requiredElement<HTMLInputElement>(selector)
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  input.dispatchEvent(new Event('change', { bubbles: true }))
}

function reviewRowByNo(reviewItemNo: string) {
  const row = Array.from(document.querySelectorAll<HTMLTableRowElement>('tbody tr'))
    .find(candidate => candidate.textContent?.includes(reviewItemNo))
  if (!row)
    throw new Error(`missing review item row ${reviewItemNo}`)
  return row
}

function clickReviewRowByNo(reviewItemNo: string) {
  const row = reviewRowByNo(reviewItemNo)
  row.click()
}

function clickReviewRowActionByNo(reviewItemNo: string, selector: string) {
  const action = reviewRowByNo(reviewItemNo).querySelector<HTMLElement>(selector)
  if (!action)
    throw new Error(`missing ${selector} for review item ${reviewItemNo}`)
  action.click()
}

function clickReviewRow() {
  clickReviewRowByNo('RV-20260702-001')
}

function assertApiCalled(name: string) {
  const calls = window.__alertReviewE2EApiCalls || []
  if (!calls.some(call => call.name === name))
    throw new Error(`expected API call ${name}`)
}

function apiCall(name: string) {
  return (window.__alertReviewE2EApiCalls || []).find(call => call.name === name)
}

function assertMediaReadScope(
  name: string,
  reviewItemId: number,
  expectedReviewCaseId?: number,
) {
  const call = [...(window.__alertReviewE2EApiCalls || [])].reverse().find(candidate => {
    const payload = candidate.payload as { reviewItemId?: number } | undefined
    return candidate.name === name && payload?.reviewItemId === reviewItemId
  })
  const payload = call?.payload as {
    reviewItemId?: number
    params?: {
      reviewCaseId?: number
      operatorUserId?: number
      allowedCameraIds?: string[]
    }
  } | undefined
  if (payload?.params?.reviewCaseId !== expectedReviewCaseId) {
    throw new Error(
      `${name} expected reviewCaseId ${String(expectedReviewCaseId)}, got ${String(payload?.params?.reviewCaseId)}`,
    )
  }
  if (payload?.params?.operatorUserId !== 9001)
    throw new Error(`${name} expected operatorUserId 9001, got ${String(payload?.params?.operatorUserId)}`)
  if (JSON.stringify(payload?.params?.allowedCameraIds) !== JSON.stringify(['cam-east-gate'])) {
    throw new Error(
      `${name} expected allowedCameraIds [cam-east-gate], got ${JSON.stringify(payload?.params?.allowedCameraIds)}`,
    )
  }
}

function assertCaseMutationConcurrency(
  name: string,
  versionField: string,
  expectedVersion: number,
  expectedOperationId: string,
) {
  const envelope = apiCall(name)?.payload as { payload?: Record<string, unknown> } | undefined
  const payload = envelope?.payload
  if (payload?.[versionField] !== expectedVersion || payload?.operationId !== expectedOperationId) {
    throw new Error(
      `${name} expected ${versionField}=${expectedVersion} and operationId=${expectedOperationId}, got ${JSON.stringify(payload)}`,
    )
  }
}

function lastVideoEvent() {
  const event = viewVideoEvents[viewVideoEvents.length - 1] as Record<string, unknown> | undefined
  if (!event)
    throw new Error('expected viewVideo event')
  return event
}

function assertLastVideoSeek(
  label: string,
  expectedSeekTime: string,
  expectedRecordPath: string,
  expectedRecordStartTime?: string,
) {
  const event = lastVideoEvent()
  if (event.seek_time !== expectedSeekTime)
    throw new Error(`${label} expected seek_time ${expectedSeekTime}, got ${String(event.seek_time)}`)
  if (event.record_path !== expectedRecordPath)
    throw new Error(`${label} expected record_path ${expectedRecordPath}, got ${String(event.record_path)}`)
  if (expectedRecordStartTime && event.record_start_time !== expectedRecordStartTime)
    throw new Error(`${label} expected record_start_time ${expectedRecordStartTime}, got ${String(event.record_start_time)}`)
}

function assertLastPlaybackPreparation(
  label: string,
  expected: {
    reviewCaseId?: number
    reviewItemId: number
    materialUri: string
  },
) {
  const calls = window.__alertReviewE2EApiCalls || []
  const call = calls[calls.length - 1]
  if (call?.name !== 'prepareAlertReviewPlaybackUrl')
    throw new Error(`${label} expected prepareAlertReviewPlaybackUrl before opening media, got ${String(call?.name)}`)
  const payload = call.payload as { reviewItemId?: number; params?: Record<string, unknown> } | undefined
  if (payload?.reviewItemId !== expected.reviewItemId)
    throw new Error(`${label} expected playback reviewItemId ${expected.reviewItemId}, got ${String(payload?.reviewItemId)}`)
  if (payload?.params?.materialUri !== expected.materialUri)
    throw new Error(`${label} expected playback materialUri ${expected.materialUri}, got ${String(payload?.params?.materialUri)}`)
  if (payload?.params?.reviewCaseId !== expected.reviewCaseId)
    throw new Error(`${label} expected playback reviewCaseId ${String(expected.reviewCaseId)}, got ${String(payload?.params?.reviewCaseId)}`)
}

async function runE2E() {
  const DialogPlayer = (await import('@/components/VideoPlayer/DialogPlayer.vue')).default
  const Harness = defineComponent({
    name: 'AlertReviewPlaybackHarness',
    setup() {
      const [registerVideoModal, { openModal, closeModal }] = useModal()
      const handleViewVideo = async (payload: unknown) => {
        viewVideoEvents.push(payload)
        await playAlertRecordInModal({
          openModal(open, data, openOnSet) {
            if (data)
              modalPayloads.push(data)
            openModal(open, data, openOnSet)
          },
          closeModal,
        }, payload as any)
      }

      return () => h('div', [
        h(AlertReviewWorkbench, {
          onViewVideo: handleViewVideo,
          onViewImage: (payload: unknown) => viewImageEvents.push(payload),
          onConverted: (payload: unknown) => convertedEvents.push(payload),
        }),
        h(DialogPlayer, { onRegister: registerVideoModal }),
      ])
    },
  })
  const app = createApp(Harness)

  app.component('ADrawer', defineComponent({
    name: 'ADrawer',
    setup(_, { slots }) {
      return () => h('section', { 'data-testid': 'alert-review-drawer-stub' }, slots.default?.())
    },
  }))
  app.component('AInputNumber', defineComponent({
    name: 'AInputNumber',
    inheritAttrs: false,
    props: { value: Number },
    emits: ['update:value'],
    setup(props, { attrs, emit }) {
      return () => h('label', attrs, [
        h('input', {
          type: 'number',
          value: props.value,
          onInput: (event: Event) => emit('update:value', Number((event.target as HTMLInputElement).value)),
        }),
      ])
    },
  }))

  app.mount('#app')

  await waitFor(() => !!document.querySelector('[data-testid="alert-review-workbench"]'), 'workbench root')
  await waitFor(() => text().includes('RV-20260702-001'), 'initial review clue row')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-ops-semantic"]'), 'semantic ops cell')
  await waitFor(() => text().includes('critical'), 'semantic backlog alarm')
  await waitFor(() => text().includes('50%'), 'semantic rebuild progress')
  await waitFor(() => text().includes('stale 1 / failed 1'), 'semantic stale failed summary')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-ops-report-ack"]'), 'operations report acknowledgement cell')
  await waitFor(() => text().includes('pending acknowledgement'), 'operations report pending acknowledgement')
  document.querySelector<HTMLButtonElement>('[data-testid="alert-review-ops-report-ack-action"]')?.click()
  await waitFor(() => text().includes('acknowledged by 9001'), 'operations report acknowledged state')
  await waitFor(() => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'acknowledgeAlertReviewOperationsReport'), 'operations report acknowledgement call')

  setInputValue('[data-testid="alert-review-semantic-trigger-name"]', 'helmet-doorway')
  setInputValue('[data-testid="alert-review-semantic-trigger-data"]', 'helmet doorway')
  click('[data-testid="alert-review-semantic-trigger-evaluate"]')
  await waitFor(() => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'evaluateAlertReviewSemanticTrigger'), 'semantic trigger evaluation call')
  await waitFor(() => text().includes('sem-123e4567-e89b-42d3-a456-426614174000'), 'semantic trigger persisted evaluation id')
  await waitFor(() => text().includes('input semantic-trigger-input-v1'), 'semantic trigger input version')
  await waitFor(() => text().includes('index v1'), 'semantic trigger latest index version')
  await waitFor(() => text().includes('hit index v1'), 'semantic trigger hit index version')
  await waitFor(() => text().includes('动作预览'), 'semantic trigger action preview')
  setInputValue('[data-testid="alert-review-semantic-trigger-notes"]', 'preview approved')
  click('[data-testid="alert-review-semantic-trigger-confirm"]')
  await waitFor(() => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'confirmAlertReviewSemanticTrigger'), 'semantic trigger confirmation call')
  await waitFor(() => text().includes('confirmed'), 'semantic trigger confirmed state')
  const semanticConfirmation = apiCall('confirmAlertReviewSemanticTrigger')?.payload as {
    payload?: Record<string, unknown>
  } | undefined
  if (semanticConfirmation?.payload?.confirmationStatus !== 'confirmed')
    throw new Error('semantic trigger confirmation must submit confirmed status')
  if ('actionPreviews' in (semanticConfirmation?.payload || {}))
    throw new Error('semantic trigger confirmation must not resubmit or execute client action previews')

  await clickAndWaitForVideo('[data-testid="alert-review-list-playback"]', 'list playback video event')
  assertLastVideoSeek('list playback', '2026-07-02T08:00:00', '/video/record/east-gate-080000.mp4')
  assertLastPlaybackPreparation('list playback', {
    reviewItemId: 101,
    materialUri: '/video/record/east-gate-080000.mp4',
  })

  clickReviewRow()
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-unified-timeline"]'), 'unified timeline')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-detail-stream"]'), 'detail stream')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-review-segment"]'), 'review segment')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-record-coverage"]'), 'record coverage')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-case-candidate"]'), 'topology candidate')

  setInputValue('.reviewer-input', '9001')
  clickReviewRowByNo('RV-20260702-003')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-case-panel"]'), 'existing review case panel')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-case-timeline-seek"]'), 'existing case timeline seek button')
  await waitFor(
    () => (window.__alertReviewE2EApiCalls || []).some(
      call => call.name === 'getAlertReviewItemCase' && call.payload === 103,
    ),
    'existing review item case lookup',
  )
  assertMediaReadScope('getAlertReviewTimeline', 103, 501)
  assertMediaReadScope('getAlertReviewDetailStream', 103, 501)
  assertMediaReadScope('getAlertReviewRecordCoverage', 103, 501)

  const directCoverageCallCount = (window.__alertReviewE2EApiCalls || [])
    .filter(call => call.name === 'getAlertReviewRecordCoverage' && (call.payload as any)?.reviewItemId === 101)
    .length
  clickReviewRowActionByNo('RV-20260702-001', '[data-testid="alert-review-list-coverage"]')
  await waitFor(
    () => (window.__alertReviewE2EApiCalls || []).filter(
      call => call.name === 'getAlertReviewRecordCoverage' && (call.payload as any)?.reviewItemId === 101,
    ).length > directCoverageCallCount,
    'direct pre-case coverage request',
  )
  assertMediaReadScope('getAlertReviewRecordCoverage', 101)
  await waitFor(() => !document.querySelector('[data-testid="alert-review-case-panel"]'), 'direct coverage clears previous row case')

  const raceCallStart = (window.__alertReviewE2EApiCalls || []).length
  clickReviewRowByNo('RV-20260702-003')
  await waitFor(
    () => (window.__alertReviewE2EApiCalls || []).slice(raceCallStart)
      .some(call => call.name === 'getAlertReviewItemCase' && call.payload === 103),
    'delayed existing case lookup starts',
  )
  clickReviewRow()
  await wait(400)
  if (!reviewRowByNo('RV-20260702-001').classList.contains('selected'))
    throw new Error('delayed previous-row requests must not replace the current selected item')
  if (document.querySelector('[data-testid="alert-review-case-panel"]'))
    throw new Error('delayed previous-row case lookup must not restore a stale active case')
  for (const call of (window.__alertReviewE2EApiCalls || []).slice(raceCallStart)) {
    const payload = call.payload as { reviewItemId?: number, params?: { reviewCaseId?: number } } | undefined
    if (
      ['getAlertReviewTimeline', 'getAlertReviewDetailStream', 'getAlertReviewRecordCoverage'].includes(call.name)
      && payload?.reviewItemId === 101
      && payload.params?.reviewCaseId !== undefined
    ) {
      throw new Error(`${call.name} must not reuse stale reviewCaseId ${String(payload.params.reviewCaseId)} for pre-case item 101`)
    }
  }
  for (const staleUri of [
    '/video/record/stale-case-103-timeline.mp4',
    '/video/record/stale-case-103-detail.mp4',
    '/video/record/stale-case-103-coverage.mp4',
  ]) {
    if (text().includes(staleUri))
      throw new Error(`delayed previous-row response leaked into current item: ${staleUri}`)
  }
  assertMediaReadScope('getAlertReviewTimeline', 101)
  assertMediaReadScope('getAlertReviewDetailStream', 101)
  assertMediaReadScope('getAlertReviewRecordCoverage', 101)

  clickReviewRow()
  await waitFor(() => !document.querySelector('[data-testid="alert-review-case-panel"]'), 'non-case review item clears active case')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-detail-stream"]'), 'restored first review detail stream')
  await waitFor(() => text().includes('RV-20260702-002'), 'topology candidate id')
  await waitFor(() => text().includes('topology area yard-east'), 'topology candidate area reason')
  await waitFor(() => text().includes('adjacent cam-east-gate -> cam-yard-east'), 'topology candidate adjacency reason')
  await waitFor(() => text().includes('shared object person-1'), 'topology candidate object reason')
  await waitFor(() => text().includes('待手动补证'), 'manual record evidence fallback label')
  await waitFor(() => text().includes('VIDEO URL 未配置'), 'missing VIDEO URL reason label')
  await waitFor(() => text().includes('sample 1/3'), 'rule suggestion sample safety summary')
  await waitFor(() => text().includes('risk low_sample_requires_more_review'), 'rule suggestion risk note')
  await waitFor(() => text().includes('impact cam-east-gate / gate-zone / person'), 'rule suggestion impact scope')
  await waitFor(() => text().includes('hits 4 -> 0'), 'rule suggestion before-after hit comparison')
  assertMediaReadScope('getAlertReviewTimeline', 101)
  assertMediaReadScope('getAlertReviewDetailStream', 101)
  assertMediaReadScope('getAlertReviewRecordCoverage', 101)
  if (hasButtonByText('accept'))
    throw new Error('low-sample rule suggestion should hide accept action')
  if (hasButtonByText('applied'))
    throw new Error('low-sample rule suggestion should hide apply action')

  await clickAndAssertNativeSeek('[data-testid="alert-review-detail-seek"]', 'detail stream seek', 2)
  assertLastVideoSeek(
    'detail stream seek',
    '2026-07-02T08:00:02',
    '/video/record/east-gate-080000.mp4',
    '2026-07-02T08:00:00',
  )
  assertLastPlaybackPreparation('detail stream seek before active case', {
    reviewItemId: 101,
    materialUri: '/video/record/east-gate-080000.mp4',
  })

  await clickAndWaitForVideo('[data-testid="alert-review-unified-action"]', 'unified timeline video event')
  assertLastVideoSeek('unified timeline seek', '2026-07-02T07:59:45', '/video/record/east-gate-075945.mp4')
  assertLastPlaybackPreparation('unified timeline seek before active case', {
    reviewItemId: 101,
    materialUri: '/video/record/east-gate-075945.mp4',
  })

  await clickAndAssertNativeSeek('[data-testid="alert-review-coverage-seek"]', 'coverage seek', 0)
  assertLastVideoSeek(
    'coverage seek',
    '2026-07-02T07:59:45',
    '/video/record/east-gate-075945.mp4',
    '2026-07-02T07:59:45',
  )
  assertLastPlaybackPreparation('coverage seek before active case', {
    reviewItemId: 101,
    materialUri: '/video/record/east-gate-075945.mp4',
  })

  click('[data-testid="alert-review-open-rule-drawer"]')
  if (e2eMode === 'dev-api-real-drawer') {
    await waitFor(() => text().includes('检测区域 (1)'), 'real DeviceRegionDrawer region list')
    requiredElement<HTMLElement>('.region-item').click()
    await waitFor(() => !!document.querySelector('[data-testid="device-region-inertia-frames"] input'), 'region inertia frames input')
    await waitFor(() => !!document.querySelector('[data-testid="device-region-loitering-seconds"] input'), 'region loitering seconds input')
    const inertiaInput = requiredElement<HTMLInputElement>('[data-testid="device-region-inertia-frames"] input')
    const loiteringInput = requiredElement<HTMLInputElement>('[data-testid="device-region-loitering-seconds"] input')
    if (inertiaInput.value !== '1' || loiteringInput.value !== '5')
      throw new Error(`region drawer expected initial rule 1/5, got ${inertiaInput.value}/${loiteringInput.value}`)
    setInputValue('[data-testid="device-region-inertia-frames"] input', '3')
    setInputValue('[data-testid="device-region-loitering-seconds"] input', '20')
    clickButtonByText('抓拍图片')
    await waitFor(() => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'captureDeviceSnapshot'), 'real DeviceRegionDrawer capture snapshot')
    await waitFor(
      () => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'loadProtectedSnapshotObjectUrl'),
      'real DeviceRegionDrawer protected snapshot load',
    )
    await waitFor(() => {
      const saveButton = buttonByText('保存区域')
      return !!saveButton && !saveButton.disabled
    }, 'real DeviceRegionDrawer save button')
    buttonByText('保存区域')?.click()
    await waitFor(() => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'updateDeviceRegion'), 'real DeviceRegionDrawer writes device region')
    const updateCall = apiCall('updateDeviceRegion')
    const updateData = (updateCall?.payload as { data?: Record<string, unknown> } | undefined)?.data
    if (updateData?.inertiaFrames !== 3 || updateData?.loiteringSeconds !== 20) {
      throw new Error(
        `region update expected inertiaFrames/loiteringSeconds 3/20, got ${String(updateData?.inertiaFrames)}/${String(updateData?.loiteringSeconds)}`,
      )
    }
  }
  else {
    await waitFor(() => !!document.querySelector('[data-testid="alert-review-region-drawer-stub"]'), 'region drawer')
    click('[data-testid="alert-review-region-drawer-save"]')
  }
  await waitFor(() => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'saveAlertReviewRule'), 'region save writes rule')
  const saveRuleCall = apiCall('saveAlertReviewRule')
  const savedRule = saveRuleCall?.payload as Record<string, unknown> | undefined
  if (savedRule?.inertiaFrames !== 3)
    throw new Error(`region save expected inertiaFrames 3, got ${String(savedRule?.inertiaFrames)}`)
  if (savedRule?.loiteringSeconds !== 20)
    throw new Error(`region save expected loiteringSeconds 20, got ${String(savedRule?.loiteringSeconds)}`)

  clickButtonByText('回放分析')
  await waitFor(() => text().includes('Rule replay report'), 'rule replay panel')
  await waitFor(() => text().includes('rule version shadow / yfeieye-rule-geometry-v1'), 'rule replay rule version')
  await waitFor(() => text().includes('sample window 2026-07-02T08:00:00 -> 2026-07-02T08:20:00 / 2'), 'rule replay sample window')
  await waitFor(() => text().includes('hit comparison 2 -> 0 / diff 2'), 'rule replay hit comparison')
  await waitFor(() => text().includes('false negative review_required / possible missed 2'), 'rule replay false negative estimate')

  if (!hasButtonByText('误报'))
    throw new Error('expected false-positive action before conversion')
  clickButtonByText('转事件')
  await waitFor(() => convertedEvents.length === 1, 'converted event emission')
  if (hasButtonByText('误报'))
    throw new Error('converted review item should hide false-positive action')
  if (!hasButtonByText('证据'))
    throw new Error('converted review item should keep evidence action')
  if (!hasButtonByText('覆盖度'))
    throw new Error('converted review item should keep record coverage action')

  click('[data-testid="alert-review-create-case"]')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-case-panel"]'), 'review case panel')
  click('[data-testid="alert-review-candidate-add"]')
  await waitFor(() => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'addAlertReviewItemToCase'), 'candidate add action')

  await clickAndWaitForVideo('[data-testid="alert-review-detail-seek"]', 'detail seek with active case video event')
  assertLastVideoSeek(
    'detail stream seek with active case',
    '2026-07-02T08:00:02',
    '/video/record/east-gate-080000.mp4',
    '2026-07-02T08:00:00',
  )
  assertLastPlaybackPreparation('detail stream seek with active case', {
    reviewCaseId: 501,
    reviewItemId: 101,
    materialUri: '/video/record/east-gate-080000.mp4',
  })

  await clickAndWaitForVideo('[data-testid="alert-review-unified-action"]', 'unified timeline with active case video event')
  assertLastVideoSeek('unified timeline seek with active case', '2026-07-02T07:59:45', '/video/record/east-gate-075945.mp4')
  assertLastPlaybackPreparation('unified timeline seek with active case', {
    reviewCaseId: 501,
    reviewItemId: 101,
    materialUri: '/video/record/east-gate-075945.mp4',
  })

  await clickAndWaitForVideo('[data-testid="alert-review-coverage-seek"]', 'coverage seek with active case video event')
  assertLastVideoSeek(
    'coverage seek with active case',
    '2026-07-02T07:59:45',
    '/video/record/east-gate-075945.mp4',
    '2026-07-02T07:59:45',
  )
  assertLastPlaybackPreparation('coverage seek with active case', {
    reviewCaseId: 501,
    reviewItemId: 101,
    materialUri: '/video/record/east-gate-075945.mp4',
  })

  click('[data-testid="alert-review-case-owner"]')
  await waitFor(() => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'assignAlertReviewCaseOwner'), 'case owner action')
  assertCaseMutationConcurrency('assignAlertReviewCaseOwner', 'expectedVersion', 0, 'workbench:owner:501:v0:9001')

  click('[data-testid="alert-review-case-close"]')
  await waitFor(() => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'closeAlertReviewCase'), 'case close action')
  assertCaseMutationConcurrency('closeAlertReviewCase', 'expectedVersion', 1, 'workbench:close:501:v1')

  setInputValue('[data-testid="alert-review-case-merge-source"]', '502')
  click('[data-testid="alert-review-case-merge"]')
  await waitFor(() => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'mergeAlertReviewCases'), 'case merge action')
  assertCaseMutationConcurrency('mergeAlertReviewCases', 'targetExpectedVersion', 1, 'workbench:merge:501:v1:502')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-case-timeline-seek"]'), 'case timeline seek button')
  await clickAndAssertNativeSeek('[data-testid="alert-review-case-timeline-seek"]', 'case timeline seek', 10)
  assertLastVideoSeek(
    'case timeline seek',
    '2026-07-02T08:00:10',
    '/video/record/east-gate-080000.mp4',
    '2026-07-02T08:00:00',
  )
  assertLastPlaybackPreparation('case timeline seek', {
    reviewCaseId: 501,
    reviewItemId: 101,
    materialUri: '/video/record/east-gate-080000.mp4',
  })
  await waitFor(() => !requiredElement<HTMLButtonElement>('[data-testid="alert-review-case-split"]').disabled, 'case lifecycle idle')

  click('[data-testid="alert-review-case-split"]')
  await waitFor(() => (window.__alertReviewE2EApiCalls || []).some(call => call.name === 'splitAlertReviewCase'), 'case split action')
  assertCaseMutationConcurrency('splitAlertReviewCase', 'sourceExpectedVersion', 1, 'workbench:split:501:v1:101')

  click('[data-testid="alert-review-ai-summary-action"]')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-ai-summary"]'), 'AI summary panel')

  click('[data-testid="alert-review-export-action"]')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-evidence-export"]'), 'evidence export panel')
  await waitFor(() => !!document.querySelector('[data-testid="alert-review-evidence-audit"]'), 'evidence audit panel')

  assertApiCalled('listAlertReviewItems')
  assertApiCalled('getAlertReviewTimeline')
  assertApiCalled('getAlertReviewDetailStream')
  assertApiCalled('getAlertReviewSegment')
  assertApiCalled('getAlertReviewRecordCoverage')
  assertApiCalled('createAlertReviewCase')
  assertApiCalled('addAlertReviewItemToCase')
  assertApiCalled('assignAlertReviewCaseOwner')
  assertApiCalled('closeAlertReviewCase')
  assertApiCalled('mergeAlertReviewCases')
  assertApiCalled('splitAlertReviewCase')
  assertApiCalled('summarizeAlertReviewCase')
  assertApiCalled('createAlertReviewEvidenceExportJob')
  assertApiCalled('getAlertReviewEvidenceAudit')
  assertApiCalled('prepareAlertReviewPlaybackUrl')
  assertApiCalled('evaluateAlertReviewSemanticIndex')

  const errorMessages = getAlertReviewE2EMessages().filter(message => message.type === 'error')
  if (errorMessages.length)
    throw new Error(`unexpected UI error messages: ${JSON.stringify(errorMessages)}`)

  return {
    apiCalls: window.__alertReviewE2EApiCalls?.map(call => call.name) || [],
    viewVideoEvents,
    modalPayloads,
    nativeSeekAssignments,
    viewImageEvents,
    convertedEvents,
    panels: {
      unifiedTimeline: !!document.querySelector('[data-testid="alert-review-unified-timeline"]'),
      detailStream: !!document.querySelector('[data-testid="alert-review-detail-stream"]'),
      reviewSegment: !!document.querySelector('[data-testid="alert-review-review-segment"]'),
      recordCoverage: !!document.querySelector('[data-testid="alert-review-record-coverage"]'),
      reviewCase: !!document.querySelector('[data-testid="alert-review-case-panel"]'),
      aiSummary: !!document.querySelector('[data-testid="alert-review-ai-summary"]'),
      evidenceExport: !!document.querySelector('[data-testid="alert-review-evidence-export"]'),
      evidenceAudit: !!document.querySelector('[data-testid="alert-review-evidence-audit"]'),
    },
  }
}

try {
  writeResult('passed', await runE2E())
}
catch (error) {
  writeResult('failed', {
    message: error instanceof Error ? error.message : String(error),
    stack: error instanceof Error ? error.stack : undefined,
    body: text().slice(0, 5000),
    apiCalls: window.__alertReviewE2EApiCalls || [],
  })
}
