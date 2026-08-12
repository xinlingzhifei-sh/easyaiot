<template>
  <div>
    <ClusterSwimlane
      v-if="state.viewMode === 'swimlane'"
      ref="swimlaneRef"
      :on-create="handleCreate"
      @view="handleView"
      @edit="handleEdit"
      @refresh="handleSuccess"
    />

    <BasicTable v-else-if="state.viewMode === 'table'" @register="registerTable">
      <template #toolbar>
        <Button type="primary" :preIcon="IconEnum.ADD" @click="handleCreate">{{ NODE_TERM.addNode }}</Button>
        <Button type="default" preIcon="ant-design:swap-outlined" @click="cycleViewMode">
          切换视图
        </Button>
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'name'">
          <a class="node-link" @click="handleView(record)">{{ record.name }}</a>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <TableAction :actions="getTableActions(record)" />
        </template>
      </template>
    </BasicTable>

    <div v-else>
      <NodeGridPanel
        :params="params"
        :api="getNodePage"
        @get-method="getMethod"
        @view="handleView"
        @edit="handleEdit"
        @delete="handleDel"
        @continue-setup="handleContinueOnboard"
      >
        <template #header>
          <Button type="primary" :preIcon="IconEnum.ADD" @click="handleCreate">{{ NODE_TERM.addNode }}</Button>
          <Button type="default" preIcon="ant-design:swap-outlined" @click="cycleViewMode">
            切换视图
          </Button>
        </template>
      </NodeGridPanel>
    </div>

    <NodeModal
      @register="registerNodeDrawer"
      @success="handleSuccess"
      @created="handleCreated"
      @host-exists="handleHostExists"
    />
    <NodeDetailDrawer
      ref="detailDrawerRef"
      @register="registerDetailDrawer"
      @edit="handleEdit"
      @maintenance="handleMaintenance"
      @continue-setup="handleContinueOnboard"
      @refresh="handleSuccess"
      @closed="handleDrawerClosed"
    />
  </div>
</template>

<script lang="ts" setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { columns, getNodeFormConfig } from '../../Data';
import NodeModal from '../NodeModal/index.vue';
import NodeDetailDrawer from '../NodeDetailDrawer/index.vue';
import NodeGridPanel from '../NodeGridPanel/index.vue';
import ClusterSwimlane from '../ClusterSwimlane/index.vue';
import { useMessage } from '@/hooks/web/useMessage';
import { useDrawer } from '@/components/Drawer';
import { IconEnum } from '@/enums/appEnum';
import { BasicTable, TableAction, useTable, type ActionItem } from '@/components/Table';
import { Button } from '@/components/Button';
import {
  deleteNode,
  getNodePage,
  setNodeMaintenance,
  type ComputeNodeVO,
} from '@/api/device/node';
import { NODE_STATUS_MAP, NODE_TERM } from '../../utils/constants';
import { navigateToOnboardService } from '../../utils/nodeNavigation';
import { isPlatformNode } from '../../utils/platformNode';

defineOptions({ name: 'ComputeNodeManage' });

type ViewMode = 'swimlane' | 'card' | 'table';

const router = useRouter();
const { createMessage, createConfirm } = useMessage();
const detailDrawerRef = ref<InstanceType<typeof NodeDetailDrawer> | null>(null);
const swimlaneRef = ref<InstanceType<typeof ClusterSwimlane> | null>(null);
const [registerNodeDrawer, { openDrawer: openNodeDrawer }] = useDrawer();
const [registerDetailDrawer, { openDrawer: openDetailDrawer }] = useDrawer();
const hiddenCancelButtonProps = { style: { display: 'none' } } as any;

const state = reactive<{ viewMode: ViewMode }>({
  viewMode: 'swimlane',
});

const params = {};
let cardListReload = () => {};

function getMethod(m: () => void) {
  cardListReload = m;
}

function cycleViewMode() {
  if (state.viewMode === 'swimlane') {
    state.viewMode = 'card';
    return;
  }
  if (state.viewMode === 'card') {
    state.viewMode = 'table';
    return;
  }
  state.viewMode = 'swimlane';
}

async function handleSuccess() {
  reload();
  cardListReload();
  await swimlaneRef.value?.loadLanes?.();
  await detailDrawerRef.value?.reloadDetail?.();
}

function handleDrawerClosed() {
  reload();
  cardListReload();
  swimlaneRef.value?.loadLanes?.();
}

function handleCreate() {
  openNodeDrawer(true, { isUpdate: false });
}

function handleEdit(record: Recordable) {
  if (isPlatformNode(record)) {
    createMessage.warning(NODE_TERM.controlPlaneNodeReadonly);
    return;
  }
  openNodeDrawer(true, { record, isUpdate: true });
}

function handleView(record: Recordable) {
  openDetailDrawer(true, { record });
  reload();
  cardListReload();
}

function handleDel(record: Recordable) {
  handleDelete(record);
}

function handleCreated(record: ComputeNodeVO) {
  handleSuccess();
  navigateToOnboardService(router, record);
}

function handleContinueOnboard(record: Recordable) {
  navigateToOnboardService(router, record);
}

function getTableActions(record: Recordable): ActionItem[] {
  const actions: ActionItem[] = [];
  if (record.status === 'pending') {
    actions.push({
      icon: 'ant-design:rocket-outlined',
      tooltip: { title: NODE_TERM.continueOnboard, placement: 'top' },
      onClick: handleContinueOnboard.bind(null, record),
    });
  }
  actions.push({
    icon: IconEnum.VIEW,
    tooltip: { title: NODE_TERM.viewDetail, placement: 'top' },
    onClick: handleView.bind(null, record),
  });
  if (!isPlatformNode(record)) {
    actions.push({
      icon: IconEnum.EDIT,
      tooltip: { title: NODE_TERM.editNode, placement: 'top' },
      onClick: handleEdit.bind(null, record),
    });
    actions.push({
      icon: IconEnum.DELETE,
      tooltip: { title: '删除', placement: 'top' },
      popConfirm: {
        placement: 'topRight',
        title: '确认删除该节点？',
        confirm: handleDelete.bind(null, record),
      },
    });
  }
  return actions;
}

async function handleHostExists(host: string) {
  let existing: ComputeNodeVO | undefined;
  try {
    const res = await getNodePage({ pageNo: 1, pageSize: 1, host });
    existing = res?.data?.list?.[0];
  } catch {
    // ignore
  }
  handleSuccess();

  if (!existing) {
    createConfirm({
      iconType: 'info',
      title: '该主机地址已存在',
      content: `主机 ${host} 已在系统中注册，请在列表中点击「${NODE_TERM.continueOnboard}」。`,
      okText: '我知道了',
      cancelButtonProps: hiddenCancelButtonProps,
    });
    return;
  }

  const statusLabel = NODE_STATUS_MAP[existing.status || '']?.text || existing.status || '未知';
  const isPending = existing.status === 'pending';

  createConfirm({
    iconType: 'info',
    title: '该主机地址已存在',
    content: isPending
      ? `节点「${existing.name}」(${statusLabel})，是否${NODE_TERM.continueOnboard}？`
      : `节点「${existing.name}」(${statusLabel})，是否${NODE_TERM.viewDetail}？`,
    okText: isPending ? NODE_TERM.continueOnboard : NODE_TERM.viewDetail,
    cancelText: '取消',
    onOk: async () => {
      if (isPending) handleContinueOnboard(existing!);
      else handleView(existing!);
    },
  });
}

async function handleDelete(record: Recordable) {
  if (isPlatformNode(record)) {
    createMessage.warning(`${NODE_TERM.controlPlaneNode}不可删除`);
    return;
  }
  await deleteNode(record.id);
  createMessage.success('删除成功');
  handleSuccess();
}

async function handleMaintenance(record: Recordable, enabled: boolean) {
  if (isPlatformNode(record)) {
    createMessage.warning(NODE_TERM.controlPlaneNodeReadonly);
    return;
  }
  await setNodeMaintenance(record.id, enabled);
  createMessage.success(enabled ? '已进入维护模式' : '已退出维护模式');
  await handleSuccess();
}

const [registerTable, { reload }] = useTable({
  canResize: true,
  showIndexColumn: false,
  title: NODE_TERM.nodeInventory,
  api: getNodePage,
  columns,
  useSearchForm: true,
  showTableSetting: false,
  pagination: true,
  formConfig: getNodeFormConfig(),
  actionColumn: {
    width: 160,
    title: '操作',
    dataIndex: 'action',
    fixed: 'right',
  },
  fetchSetting: {
    pageField: 'pageNo',
    sizeField: 'pageSize',
    listField: 'data.list',
    totalField: 'data.total',
  },
  rowKey: 'id',
});
</script>

<style lang="less" scoped>
.node-link {
  color: #266cfb;
  cursor: pointer;
  font-weight: 500;

  &:hover {
    color: #1a5ae8;
  }
}

:deep(.xingyuv-basic-table-action) {
  .ant-btn {
    padding-inline: 6px;
  }
}
</style>
