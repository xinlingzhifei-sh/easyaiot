<script lang="ts" setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Empty, Pagination, Spin } from 'ant-design-vue';
import { IconEnum } from '@/enums/appEnum';
import { Button } from '@/components/Button';
import { deleteNode, type ComputeNodeVO } from '@/api/device/node';
import { useMessage } from '@/hooks/web/useMessage';
import ClusterLaneRow from './ClusterLaneRow.vue';
import ControlPlanePeerDrawer from '../ControlPlanePeerModal/index.vue';
import { useClusterLanes } from './useClusterLanes';
import { NODE_TERM } from '../../utils/constants';
import { useDrawer } from '@/components/Drawer';
import { navigateToNodeBatchTab, navigateToOnboardService } from '../../utils/nodeNavigation';
import { isPlatformNode } from '../../utils/platformNode';

defineOptions({ name: 'ClusterSwimlane' });

const emit = defineEmits<{
  view: [node: ComputeNodeVO];
  edit: [node: ComputeNodeVO];
  created: [node: ComputeNodeVO];
  refresh: [];
}>();

const props = defineProps<{
  onCreate?: () => void;
}>();

const router = useRouter();
const { createMessage, createConfirm } = useMessage();
const [registerPeerDrawer, { openDrawer: openPeerDrawer }] = useDrawer();
const { loading, lanes, laneTotal, page, pageSize, loadLanes, changePage } = useClusterLanes();

function handleCreateWorker() {
  props.onCreate?.();
}

function handleAddCentral() {
  openPeerDrawer(true);
}

function handleBatchNavigate(tab: string, nodeIds: number[]) {
  navigateToNodeBatchTab(router, tab, nodeIds);
}

async function handleDelete(node: ComputeNodeVO) {
  if (isPlatformNode(node) || node.isRemote || !node.id) return;
  createConfirm({
    iconType: 'warning',
    title: '确认删除该节点？',
    onOk: async () => {
      await deleteNode(node.id!);
      createMessage.success('删除成功');
      await loadLanes();
      emit('refresh');
    },
  });
}

async function handlePeerSuccess() {
  await loadLanes(1);
  emit('refresh');
}

function handlePageChange(nextPage: number, nextPageSize?: number) {
  changePage(nextPage, nextPageSize);
}

onMounted(() => {
  loadLanes();
});

defineExpose({ loadLanes });
</script>

<template>
  <div class="node-swimlane-wrapper">
    <div class="list-panel">
      <Spin :spinning="loading">
        <div class="list-header">
          <div class="list-header__left">
            <span class="list-title">{{ NODE_TERM.swimlaneView }}</span>
            <span class="list-subtitle">每行左侧为中心节点，右侧横向展示其工作节点；本机泳道可批量操控</span>
          </div>
          <div class="list-actions">
            <Button type="primary" :preIcon="IconEnum.ADD" @click="handleCreateWorker">
              {{ NODE_TERM.addNode }}
            </Button>
            <Button
              type="default"
              preIcon="ant-design:cluster-outlined"
              @click="handleAddCentral"
            >
              {{ NODE_TERM.addCentralNode }}
            </Button>
            <Button :loading="loading" preIcon="ant-design:redo-outlined" @click="() => loadLanes()">
              刷新
            </Button>
          </div>
        </div>

        <div v-if="lanes.length" class="cluster-swimlane__lanes">
          <ClusterLaneRow
            v-for="lane in lanes"
            :key="lane.laneKey"
            :lane="lane"
            @view="(node) => emit('view', node)"
            @edit="(node) => emit('edit', node)"
            @delete="handleDelete"
            @continue-setup="(node) => navigateToOnboardService(router, node)"
            @batch-navigate="handleBatchNavigate"
            @refresh="loadLanes"
          />
        </div>

        <div v-if="laneTotal > pageSize" class="cluster-swimlane__pagination">
          <Pagination
            :current="page"
            :page-size="pageSize"
            :total="laneTotal"
            :show-size-changer="true"
            :show-quick-jumper="true"
            :page-size-options="['5', '10', '20', '50']"
            :show-total="(total) => `共 ${total} 个中心节点`"
            @change="handlePageChange"
            @show-size-change="handlePageChange"
          />
        </div>

        <Empty
          v-if="!loading && !lanes.length"
          class="cluster-swimlane__empty"
          description="暂无泳道数据，请确认中心节点已纳管"
        />
      </Spin>
    </div>

    <ControlPlanePeerDrawer @register="registerPeerDrawer" @success="handlePeerSuccess" />
  </div>
</template>

<style lang="less" scoped>
@import '../../utils/theme.less';
.node-swimlane-wrapper {
  background: #fff;
  min-height: 100%;
}

.list-panel {
  background: #fff;
  padding: 0 8px 20px;

  :deep(.ant-spin-nested-loading),
  :deep(.ant-spin-container) {
    background: transparent;
  }
}

.list-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 12px 16px 20px;
  flex-wrap: wrap;
}

.list-header__left {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.list-title {
  padding-left: 4px;
  font-size: 16px;
  font-weight: 500;
  line-height: 24px;
  color: #181818;
}

.list-subtitle {
  padding-left: 4px;
  font-size: 13px;
  color: #999;
  line-height: 1.5;
}

.list-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.cluster-swimlane__lanes {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0 4px;
}

.cluster-swimlane__lanes > :deep(.cluster-lane + .cluster-lane) {
  border-top: 1px solid @node-border;
}

.cluster-swimlane__empty {
  padding: 48px 16px;
}

.cluster-swimlane__pagination {
  display: flex;
  justify-content: center;
  padding: 24px 16px 8px;
}
</style>
