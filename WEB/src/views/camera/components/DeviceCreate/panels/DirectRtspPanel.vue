<template>
  <DeviceCreatePanelLayout>
    <template #form>
      <BasicForm @register="registerForm" />
    </template>
    <template #actions>
      <Button type="primary" :loading="submitting" @click="handleSubmit">注册设备</Button>
      <Button
        v-if="edgeCommandStatus && edgeCommandContext"
        size="small"
        :loading="edgeCommandLoading"
        @click="handleCheckEdgeCommand"
      >
        检查 Edge 命令
      </Button>
      <div v-if="edgeCommandStatus || edgeCommandError" class="edge-command-status">
        <span v-if="edgeCommandStatus" class="edge-command-status__ok">
          Edge 命令已下发
          <span v-if="edgeCommandStatus.commandId">#{{ edgeCommandStatus.commandId }}</span>
          <span v-if="edgeCommandStatus.commandStatus">({{ edgeCommandStatus.commandStatus }})</span>
          <span v-if="edgeCommandStatus.action">- {{ edgeCommandStatus.action }}</span>
        </span>
        <span v-if="edgeCommandError" class="edge-command-status__error">
          Edge 命令失败：{{ edgeCommandError }}
        </span>
      </div>
    </template>
  </DeviceCreatePanelLayout>
</template>

<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { BasicForm, useForm } from '@/components/Form';
import { Button } from '@/components/Button';
import { useMessage } from '@/hooks/web/useMessage';
import { registerDevice } from '@/api/device/camera';
import {
  ensureDeviceStreamForwardTask,
  ensureEdgeStreamForwardTask,
  reconcileEdgeStreamForwardTask,
} from '@/api/device/stream_forward';
import { listScheduleNodes } from '@/api/device/node';
import { resolveRegisteredDeviceId } from '@/views/camera/utils/rtspUrl';
import DeviceCreatePanelLayout from '../DeviceCreatePanelLayout.vue';
import {
  DEVICE_CREATE_COL_LINE,
  DEVICE_CREATE_FORM_GRID,
} from '../deviceCreateForm';

const emit = defineEmits<{ success: [] }>();

const { createMessage } = useMessage();
const submitting = ref(false);
const edgeNodeOptions = ref<Array<{ label: string; value: number }>>([]);
const edgeCommandLoading = ref(false);
const edgeCommandContext = ref<{
  deviceId: string;
  edgeNodeId: number;
  transport: 'tcp' | 'udp';
} | null>(null);
const edgeCommandStatus = ref<{
  commandId?: string | number;
  commandStatus?: string;
  action?: string;
  reasonMessage?: string;
} | null>(null);
const edgeCommandError = ref('');

const [registerForm, { validate, getFieldsValue, updateSchema }] = useForm({
  ...DEVICE_CREATE_FORM_GRID,
  schemas: [
    {
      field: 'name',
      label: '设备名称',
      component: 'Input',
      colProps: DEVICE_CREATE_COL_LINE,
      componentProps: { placeholder: '可选' },
    },
    {
      field: 'source',
      label: 'RTSP 地址',
      component: 'Input',
      required: true,
      colProps: DEVICE_CREATE_COL_LINE,
      componentProps: { placeholder: 'rtsp://username:password@ip:port/path' },
    },
    {
      field: 'access_mode',
      label: '接入方式',
      component: 'Select',
      defaultValue: 'local',
      colProps: DEVICE_CREATE_COL_LINE,
      componentProps: {
        options: [
          { label: '本机转发', value: 'local' },
          { label: 'Edge Agent 出站接入', value: 'edge_agent' },
        ],
      },
    },
    {
      field: 'edge_node_id',
      label: 'Edge 节点',
      component: 'Select',
      colProps: DEVICE_CREATE_COL_LINE,
      componentProps: {
        placeholder: '选择在线 Edge/计算节点',
        options: edgeNodeOptions,
        showSearch: true,
        allowClear: true,
        filterOption: (input: string, option: any) => {
          return option.label.toLowerCase().includes(input.toLowerCase());
        },
      },
      ifShow: ({ values }) => values.access_mode === 'edge_agent',
      required: ({ values }) => values.access_mode === 'edge_agent',
    },
    {
      field: 'edge_transport',
      label: '传输方式',
      component: 'Select',
      defaultValue: 'tcp',
      colProps: DEVICE_CREATE_COL_LINE,
      componentProps: {
        options: [
          { label: 'TCP', value: 'tcp' },
          { label: 'UDP', value: 'udp' },
        ],
      },
      ifShow: ({ values }) => values.access_mode === 'edge_agent',
    },
  ],
});

async function loadEdgeNodes() {
  try {
    const nodes = await listScheduleNodes();
    edgeNodeOptions.value = nodes
      .filter((node) => node.id != null)
      .map((node) => ({
        label: `${node.name || node.host} (${node.host})`,
        value: Number(node.id),
      }));
    updateSchema({
      field: 'edge_node_id',
      componentProps: {
        options: edgeNodeOptions.value,
      },
    });
  } catch (error) {
    console.error('加载 Edge 节点失败', error);
    edgeNodeOptions.value = [];
  }
}

function extractResponseData(response: any) {
  return response?.data?.data ?? response?.data ?? response ?? {};
}

function normalizeEdgeCommandStatus(response: any) {
  const data = extractResponseData(response);
  const command = data.command || {};
  return {
    commandId: command.id ?? command.commandId ?? command.command_id ?? data.commandId ?? data.command_id,
    commandStatus: command.status ?? command.state ?? data.status ?? 'queued',
    action: data.action ?? command.action,
    reasonMessage: command.reason_message ?? command.reasonMessage ?? data.reason_message ?? data.reasonMessage,
  };
}

function getErrorMessage(error: unknown, fallback: string) {
  const err = error as {
    msg?: string;
    message?: string;
    response?: { data?: { msg?: string; message?: string } };
  };
  return err?.response?.data?.msg || err?.response?.data?.message || err?.msg || err?.message || fallback;
}

async function handleSubmit() {
  try {
    await validate();
  } catch {
    return;
  }
  const values = getFieldsValue();
  const source = String(values.source || '').trim();
  if (!source) {
    createMessage.error('RTSP 取流地址不能为空');
    return;
  }
  if (values.access_mode === 'edge_agent' && !values.edge_node_id) {
    createMessage.error('请选择 Edge 节点');
    return;
  }
  edgeCommandStatus.value = null;
  edgeCommandError.value = '';
  edgeCommandContext.value = null;
  submitting.value = true;
  try {
    const response = await registerDevice({
      name: values.name || undefined,
      source,
      stream: 0,
      cameraType: 'custom',
    });
    const deviceId = resolveRegisteredDeviceId(response);
    createMessage.success('设备注册成功');
    if (deviceId) {
      if (values.access_mode === 'edge_agent') {
        try {
          const edgeNodeId = Number(values.edge_node_id);
          const transport = (values.edge_transport || 'tcp') as 'tcp' | 'udp';
          const edgeResponse = await ensureEdgeStreamForwardTask(deviceId, {
            edge_node_id: edgeNodeId,
            transport,
          });
          edgeCommandStatus.value = normalizeEdgeCommandStatus(edgeResponse);
          edgeCommandContext.value = { deviceId, edgeNodeId, transport };
          createMessage.success('Edge Agent 接入命令已下发');
        } catch (error: unknown) {
          edgeCommandError.value = getErrorMessage(error, 'Edge Agent 接入命令下发失败');
          createMessage.error(edgeCommandError.value);
          return;
        }
      } else {
        try {
          await ensureDeviceStreamForwardTask(deviceId);
        } catch {
          /* 保持旧本机转发兜底行为 */
        }
      }
    }
    emit('success');
  } catch (error: unknown) {
    const err = error as { msg?: string; message?: string };
    createMessage.error(err?.msg || err?.message || '设备注册失败');
  } finally {
    submitting.value = false;
  }
}

async function handleCheckEdgeCommand() {
  const context = edgeCommandContext.value;
  if (!context) {
    return;
  }
  edgeCommandLoading.value = true;
  edgeCommandError.value = '';
  try {
    const response = await reconcileEdgeStreamForwardTask(context.deviceId, {
      edge_node_id: context.edgeNodeId,
      transport: context.transport,
      timeout_seconds: 120,
      max_attempts: 3,
    });
    edgeCommandStatus.value = normalizeEdgeCommandStatus(response);
    createMessage.success('Edge Agent 命令状态已更新');
  } catch (error: unknown) {
    edgeCommandError.value = getErrorMessage(error, 'Edge Agent 命令状态检查失败');
    createMessage.error(edgeCommandError.value);
  } finally {
    edgeCommandLoading.value = false;
  }
}

onMounted(() => {
  void loadEdgeNodes();
});
</script>

<style lang="less" scoped>
.edge-command-status {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  max-width: 520px;
  font-size: 13px;
  line-height: 32px;
}

.edge-command-status__ok,
.edge-command-status__error {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.edge-command-status__ok {
  color: #1677ff;
}

.edge-command-status__error {
  color: #cf1322;
}
</style>
