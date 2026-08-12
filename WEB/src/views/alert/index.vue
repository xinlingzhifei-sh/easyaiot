<template>
  <div class="alert-page">
    <div class="alert-tab">
      <Tabs
        :activeKey="state.activeKey"
        :animated="{ inkBar: true, tabPane: true }"
        :destroyInactiveTabPane="true"
        :tabBarGutter="60"
        @tabClick="handleTabClick"
      >
        <TabPane key="1" tab="地图分布">
          <AlertMapView
            ref="alertMapViewRef"
            @view-image="handleCardViewImage"
            @view-video="handleCardViewVideo"
            @set-location="openDeviceLocationDrawer"
            @play="handleMapDevicePlay"
            @view="handleMapDeviceView"
            @edit="handleMapDeviceEdit"
          />
        </TabPane>
        <TabPane key="2" tab="告警事件">
          <!-- 表格模式 -->
          <BasicTable v-if="viewMode === 'table'" @register="registerTable">
            <template #toolbar>
              <AlertListToolbar
                @toggle-view="handleToggleViewMode"
                @clear-all="handleClearAllAlerts"
              />
            </template>
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'device_id'">
                <span style="cursor: pointer" @click="handleCopy(record['device_id'])"><Icon
                  icon="tdesign:copy-filled" color="#4287FCFF"/> {{ formatDeviceId(record['device_id']) }}</span>
              </template>
              <template v-if="column.key === 'device_name'">
                <span style="cursor: pointer" @click="handleCopy(record['device_name'])"><Icon
                  icon="tdesign:copy-filled" color="#4287FCFF"/> {{ record['device_name'] }}</span>
              </template>
              <template v-if="column.key === 'task_type'">
                <a-tag :color="getTaskTypeColor(record['task_type'])">
                  {{ getTaskTypeText(record['task_type']) }}
                </a-tag>
              </template>
              <template v-if="column.dataIndex === 'action'">
                <TableAction
                  :actions="[
                    {
                      icon: 'ion:image-sharp',
                      tooltip: { title: '查看告警图片', placement: 'top' },
                      onClick: handleViewImage.bind(null, record),
                    },
                    {
                      icon: 'icon-park-outline:video',
                      tooltip: { title: '查看告警录像', placement: 'top' },
                      onClick: handleViewVideo.bind(null, record),
                    },
                    {
                      icon: 'ant-design:audit-outlined',
                      tooltip: { title: '加入线索复核', placement: 'top' },
                      onClick: handleCreateReviewClue.bind(null, record),
                    },
                  ]"
                />
              </template>
            </template>
          </BasicTable>

          <!-- 卡片模式（默认） -->
          <div v-else class="card-mode-wrapper">
            <AlertCards
              :api="queryAlarmList"
              :params="params"
              @getMethod="getMethod"
              @viewImage="handleCardViewImage"
              @viewVideo="handleCardViewVideo"
              @createReviewClue="handleCreateReviewClue"
            >
              <template #header>
                <AlertListToolbar
                  @toggle-view="handleToggleViewMode"
                  @clear-all="handleClearAllAlerts"
                />
              </template>
            </AlertCards>
          </div>
        </TabPane>
        <TabPane key="3" tab="线索复核">
          <AlertReviewWorkbench
            ref="alertReviewWorkbenchRef"
            @view-image="handleCardViewImage"
            @view-video="handleCardViewVideo"
          />
        </TabPane>
      </Tabs>
    </div>

    <ImageModal @register="registerImageModal" />
    <DialogPlayer @register="registerVideoModal" />
    <DeviceLocationDrawer @register="registerLocationDrawer" @success="handleLocationDrawerSuccess" />
  </div>
</template>
<script lang="ts" setup name="noticeSetting">
import { nextTick, reactive, ref, onMounted, onActivated } from 'vue';
import { TabPane, Tabs } from 'ant-design-vue';
import { BasicTable, TableAction, useTable } from '@/components/Table';
import { useMessage } from '@/hooks/web/useMessage';
import { getBasicColumns, getFormConfig } from './Data';
import { useRouter } from 'vue-router';
import { queryAlarmList, clearAllAlerts } from '@/api/device/calculate';
import { Icon } from '@/components/Icon';
import AlertCards from '@/views/alert/components/AlertCards/index.vue';
import AlertMapView from '@/views/alert/components/AlertMapView/index.vue';
import AlertListToolbar from '@/views/alert/components/AlertListToolbar.vue';
import AlertReviewWorkbench from '@/views/alert/components/AlertReviewWorkbench.vue';
import ImageModal from '@/views/alert/components/ImageModal/index.vue';
import DialogPlayer from '@/components/VideoPlayer/DialogPlayer.vue';
import { useModal } from '@/components/Modal';
import {
  mergeAlertFetchParams,
  normalizeAlertQueryParams,
  snapshotAlertFilters,
} from '@/views/alert/utils/alertQueryParams';
import DeviceLocationDrawer from '@/views/camera/components/DeviceLocationDrawer/index.vue';
import { canSetDeviceLocation } from '@/views/camera/utils/deviceLocation';
import { getDeviceInfo } from '@/api/device/camera';
import { openDeviceInDialogPlayer } from '@/views/camera/utils/devicePlay';
import { playAlertRecordInModal } from '@/utils/alertRecordPlayback';
import { isSnapAlertTask } from '@/views/alert/alertDisplay';
import { ingestAlertReviewClue } from '@/api/supervision/alertReview';

const router = useRouter();
const [registerImageModal, { openModal: openImageModal }] = useModal();
const [registerVideoModal, { openModal: openVideoModal, closeModal: closeVideoModal, setModalProps: setVideoModalProps }] = useModal();
const [registerLocationDrawer, { openModal: openLocationModal }] = useModal();

defineOptions({ name: 'Alarm' });

const ALERT_TAB_KEYS = {
  MAP: '1',
  EVENTS: '2',
  REVIEW: '3',
} as const;

const ALERT_TAB_ID_SET = new Set<string>(Object.values(ALERT_TAB_KEYS));

const viewMode = ref<'table' | 'card'>('card');

const state = reactive<{ activeKey: string }>({
  activeKey: ALERT_TAB_KEYS.MAP,
});

const params = ref<Record<string, any>>({});
const alertMapViewRef = ref<InstanceType<typeof AlertMapView>>();
const alertReviewWorkbenchRef = ref<InstanceType<typeof AlertReviewWorkbench>>();

let cardListReload = () => {};

const lastTableFilterParams = ref<Record<string, any>>({});

function normalizeAlertRouteTab(tab: unknown): string {
  if (tab === 'map') return ALERT_TAB_KEYS.MAP;
  if (tab === 'events') return ALERT_TAB_KEYS.EVENTS;
  if (tab === 'review') return ALERT_TAB_KEYS.REVIEW;
  const tabStr = String(tab);
  if (ALERT_TAB_ID_SET.has(tabStr)) return tabStr;
  return ALERT_TAB_KEYS.MAP;
}

async function activateMapTab() {
  state.activeKey = ALERT_TAB_KEYS.MAP;
  await nextTick();
  const filters = lastTableFilterParams.value;
  if (Object.keys(filters).length) {
    await alertMapViewRef.value?.applyFilters?.(filters);
  } else {
    await alertMapViewRef.value?.init?.();
  }
  await alertMapViewRef.value?.resizeMap?.();
}

function handleTabClick(activeKey: string) {
  state.activeKey = activeKey;
  if (activeKey === ALERT_TAB_KEYS.MAP) {
    void activateMapTab();
  } else if (activeKey === ALERT_TAB_KEYS.REVIEW) {
    alertReviewWorkbenchRef.value?.refresh?.();
  }
}

const refreshData = () => {
  const route = router.currentRoute.value;
  const rawTab = route.query.tab ?? (route.query.view === 'map' ? ALERT_TAB_KEYS.MAP : undefined);
  const tab = rawTab ? normalizeAlertRouteTab(rawTab) : ALERT_TAB_KEYS.MAP;
  if (tab === ALERT_TAB_KEYS.MAP) {
    void activateMapTab();
    return;
  }
  if (tab === ALERT_TAB_KEYS.REVIEW) {
    state.activeKey = ALERT_TAB_KEYS.REVIEW;
    nextTick(() => alertReviewWorkbenchRef.value?.refresh?.());
    return;
  }
  state.activeKey = ALERT_TAB_KEYS.EVENTS;
  if (route.query.task_name) {
    params.value = { task_name: route.query.task_name };
    setTimeout(() => {
      const form = getForm();
      if (form) {
        form.setFieldsValue({ task_name: route.query.task_name });
      }
      reload();
      cardListReload();
    }, 100);
  } else {
    reload();
    cardListReload();
  }
};

onMounted(() => {
  refreshData();
});

onActivated(() => {
  refreshData();
});

function getMethod(m: any) {
  cardListReload = m;
}

function handleToggleViewMode() {
  viewMode.value = viewMode.value === 'card' ? 'table' : 'card';
  if (viewMode.value === 'table') {
    reload();
  } else {
    cardListReload();
  }
}

function handleCardViewImage(record) {
  handleViewImage(record);
}

function handleCardViewVideo(record) {
  handleViewVideo(record);
}

async function handleCreateReviewClue(record: Record<string, any>) {
  const alertTime = normalizeAlertTime(record['time']);
  const sourceAlertId = String(record['id'] ?? record['alert_id'] ?? `${record['device_id'] || 'alert'}-${record['time'] || Date.now()}`);
  if (!alertTime) {
    createMessage.warn('缺少告警时间，无法加入线索复核');
    return;
  }
  try {
    await ingestAlertReviewClue({
      sourceSystem: 'video',
      sourceAlertId,
      ruleCode: mapAlertEventToReviewRule(record['event']),
      sourceAlertType: String(record['event'] || record['task_type'] || ''),
      alertTime,
      deviceId: record['device_id'],
      cameraId: record['device_id'],
      objectLabel: record['object'],
      snapshotUri: record['image_url'],
      recordUri: record['record_path'],
    });
    createMessage.success('已加入线索复核工作台');
    state.activeKey = ALERT_TAB_KEYS.REVIEW;
    await nextTick();
    alertReviewWorkbenchRef.value?.refresh?.();
  } catch (error: any) {
    createMessage.error(error?.message || '加入线索复核失败');
  }
}

function normalizeAlertTime(value: unknown): string {
  if (!value) return '';
  return String(value).trim().replace(' ', 'T');
}

function mapAlertEventToReviewRule(event: unknown): string {
  const value = String(event || '').toLowerCase();
  if (value.includes('fall')) return 'RULE_FALL_DOWN';
  if (value.includes('fight')) return 'RULE_FIGHT';
  if (value.includes('restricted') || value.includes('intrusion') || value.includes('cross')) {
    return 'RULE_RESTRICTED_AREA';
  }
  if (value.includes('gather') || value.includes('crowd')) return 'RULE_ABNORMAL_GATHERING';
  return 'RULE_ABNORMAL_GATHERING';
}

function openDeviceLocationDrawer(record: { id?: string; name?: string; device_kind?: string }) {
  if (!canSetDeviceLocation(record)) return;
  openLocationModal(true, { deviceId: record.id, record });
}

function handleLocationDrawerSuccess() {
  alertMapViewRef.value?.refresh?.();
}

async function handleMapDevicePlay(record: { id?: string }) {
  if (!record?.id) return;
  try {
    const info = await getDeviceInfo(record.id);
    openDeviceInDialogPlayer(openVideoModal, info);
  } catch {
    createMessage.error('加载设备信息失败');
  }
}

function handleMapDeviceView(record: { id?: string }) {
  if (!record?.id) return;
  router.push({ path: '/camera', query: { tab: '3', highlight: record.id } });
}

function handleMapDeviceEdit(record: { id?: string }) {
  if (!record?.id) return;
  router.push({ path: '/camera', query: { tab: '3', edit: record.id } });
}

const { createMessage, createConfirm } = useMessage();
const [
  registerTable,
  {
    reload,
    getForm,
  },
] = useTable({
  canResize: true,
  showIndexColumn: false,
  title: '告警事件列表',
  api: queryAlarmList,
  columns: getBasicColumns(),
  useSearchForm: true,
  showTableSetting: false,
  formConfig: getFormConfig(),
  fetchSetting: {
    listField: 'alert_list',
    totalField: 'total',
    pageField: 'pageNo',
    sizeField: 'pageSize',
  },
  beforeFetch: (p) => {
    const route = router.currentRoute.value;
    const merged = mergeAlertFetchParams(
      p as Record<string, unknown>,
      lastTableFilterParams.value,
    );
    const normalized = normalizeAlertQueryParams(
      merged,
      route.query.task_name as string | undefined,
    );
    lastTableFilterParams.value = snapshotAlertFilters(normalized);
    return normalized;
  },
  rowKey: 'id',
});

const handleViewImage = (record: Record<string, any>) => {
  const minioUrl = record['image_url'];
  if (minioUrl == null || String(minioUrl).trim() === '') {
    createMessage.warn('告警图片不存在');
    return;
  }
  openImageModal(true, {
    image_url: minioUrl,
    information: record.information,
    event: record.event,
  });
};

let lastVideoErrorTime = 0;
let lastVideoErrorMsg = '';

const handleViewVideo = async (record) => {
  if (isSnapAlertTask(record)) {
    createMessage.warn('抓拍任务无告警录像');
    return;
  }
  if (!record['device_id'] || !record['time']) {
    createMessage.warn('缺少必要信息：设备ID或告警时间');
    return;
  }

  try {
    const ok = await playAlertRecordInModal(
      { openModal: openVideoModal, closeModal: closeVideoModal, setModalProps: setVideoModalProps },
      {
        id: record['id'],
        device_id: record['device_id'],
        time: record['time'],
        seek_time: record['seek_time'],
        record_start_time: record['record_start_time'],
        playback_offset_seconds: record['playback_offset_seconds'],
        record_path: record['record_path'],
        video_url: record['video_url'],
        url: record['url'],
      },
    );
    if (ok) {
      lastVideoErrorTime = 0;
      lastVideoErrorMsg = '';
    } else {
      showVideoErrorOnce('暂未找到该时间段的录像文件，请稍后再试');
    }
  } catch (error: any) {
    console.error('查询录像失败:', error);
    const errorData = error?.response?.data || error?.data;
    const errorMsg = errorData?.message || error?.message || '查询录像失败，请稍后重试';
    showVideoErrorOnce(errorMsg);
  }
};

function showVideoErrorOnce(message: string) {
  const now = Date.now();
  if (now - lastVideoErrorTime < 3000 && lastVideoErrorMsg === message) {
    return;
  }
  lastVideoErrorTime = now;
  lastVideoErrorMsg = message;
  createMessage.warn(message);
}

function formatDeviceId(deviceId: string | null | undefined): string {
  if (!deviceId) return '-';
  if (deviceId.length <= 8) return deviceId;
  return deviceId.substring(0, 8) + '...';
}

function getTaskTypeText(taskType: string | null | undefined): string {
  if (!taskType) return '实时';
  if (taskType === 'snap' || taskType === 'snapshot') {
    return '抓拍';
  } else if (taskType === 'realtime') {
    return '实时';
  }
  return taskType;
}

function getTaskTypeColor(taskType: string | null | undefined): string {
  if (!taskType) return 'blue';
  if (taskType === 'snap' || taskType === 'snapshot') {
    return 'green';
  } else if (taskType === 'realtime') {
    return 'blue';
  }
  return 'default';
}

async function handleCopy(record: unknown) {
  const text = typeof record === 'string' ? record : JSON.stringify(record, null, 2);
  if (navigator.clipboard) {
    await navigator.clipboard.writeText(text);
  } else {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
  }
  createMessage.success('复制成功');
}

const handleClearAllAlerts = () => {
  createConfirm({
    title: '清空告警',
    iconType: 'warning',
    content: '确定要清空所有告警记录吗？此操作不可恢复！',
    async onOk() {
      try {
        await clearAllAlerts();
        createMessage.success('清空告警成功');
        reload();
        cardListReload();
        alertMapViewRef.value?.refresh?.();
      } catch (error: any) {
        const errorMsg =
          error?.response?.data?.msg ||
          error?.response?.data?.message ||
          error?.message ||
          '清空告警失败，请稍后重试';
        createMessage.error(errorMsg);
      }
    },
  });
};
</script>

<style scoped lang="less">
.alert-page {
  min-height: 0;
  background: #ffffff;
}

.alert-tab {
  padding: 0;

  :deep(.ant-tabs-nav) {
    padding: 5px 0 0 25px;
  }

  :deep(.ant-tabs) {
    background-color: #ffffff;
  }
}

.card-mode-wrapper {
  min-height: 0;
}
</style>
