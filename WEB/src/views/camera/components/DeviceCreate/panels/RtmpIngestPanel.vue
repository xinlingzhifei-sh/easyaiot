<template>
  <DeviceCreatePanelLayout result-title="公网 RTMP 推流配置" :fill-result="false">
    <template #form>
      <BasicForm @register="registerForm" />
    </template>
    <template #actions>
      <Button type="primary" :loading="submitting" @click="handleSubmit">生成推流地址</Button>
      <Button v-if="ingestUrlInfo" :loading="rotating" @click="handleRotateToken">轮换 Token</Button>
    </template>
    <template #result>
      <div v-if="ingestUrlInfo" class="rtmp-ingest-result">
        <div class="rtmp-ingest-result__meta">
          <span>设备：{{ ingestUrlInfo.device_id }}</span>
          <span>租户：{{ ingestUrlInfo.tenant_id }}</span>
          <span>Token v{{ ingestUrlInfo.token_version }}</span>
          <span>过期时间：{{ formatExpiresAt(ingestUrlInfo.expires_at) }}</span>
        </div>
        <div v-if="tokenRotatedNotice" class="rtmp-ingest-result__notice">
          旧推流地址已作废，请使用下方新地址。
        </div>
        <pre class="rtmp-ingest-result__url">{{ ingestUrlInfo.push_url }}</pre>
        <Button size="small" @click="copyPushUrl">复制推流地址</Button>
      </div>
      <div v-else class="rtmp-ingest-result__empty">
        生成后可复制设备专属 RTMP 推流地址；地址带租户、过期时间、token version 和签名。
      </div>
    </template>
  </DeviceCreatePanelLayout>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { BasicForm, useForm } from '@/components/Form';
import { Button } from '@/components/Button';
import { useMessage } from '@/hooks/web/useMessage';
import {
  issueRtmpIngestUrl,
  registerDevice,
  rotateRtmpIngestToken,
  type RtmpIngestUrlInfo,
} from '@/api/device/camera';
import { getTenantId } from '@/utils/auth';
import DeviceCreatePanelLayout from '../DeviceCreatePanelLayout.vue';
import {
  DEVICE_CREATE_COL_LINE,
  DEVICE_CREATE_FORM_GRID,
} from '../deviceCreateForm';

const emit = defineEmits<{ success: [] }>();

const { createMessage } = useMessage();
const submitting = ref(false);
const rotating = ref(false);
const currentDeviceId = ref('');
const currentTenantId = ref('');
const ingestUrlInfo = ref<RtmpIngestUrlInfo | null>(null);
const tokenRotatedNotice = ref(false);

const [registerForm, { validate, getFieldsValue }] = useForm({
  ...DEVICE_CREATE_FORM_GRID,
  schemas: [
    {
      field: 'name',
      label: '设备名称',
      component: 'Input',
      required: true,
      colProps: DEVICE_CREATE_COL_LINE,
      componentProps: { placeholder: '例如：园区北门 RTMP 推流' },
    },
    {
      field: 'tenant_id',
      label: '租户 ID',
      component: 'Input',
      required: true,
      defaultValue: getTenantId(),
      colProps: DEVICE_CREATE_COL_LINE,
      componentProps: { placeholder: '用于绑定推流 URL 的租户' },
    },
    {
      field: 'ttl',
      label: '有效期(秒)',
      component: 'InputNumber',
      defaultValue: 3600,
      colProps: DEVICE_CREATE_COL_LINE,
      componentProps: { min: 60, max: 86400, step: 60 },
    },
    {
      field: 'base_url',
      label: 'RTMP 入口',
      component: 'Input',
      colProps: DEVICE_CREATE_COL_LINE,
      componentProps: { placeholder: '可选，例如 rtmp://stream.example.com/live' },
    },
  ],
});

function responseData(response: any) {
  return response?.data?.data ?? response?.data ?? response ?? {};
}

function formatExpiresAt(value?: number) {
  if (!value) return '-';
  return new Date(value * 1000).toLocaleString();
}

function errorMessage(error: unknown, fallback: string) {
  const err = error as {
    msg?: string;
    message?: string;
    response?: { data?: { msg?: string; message?: string } };
  };
  return err?.response?.data?.msg || err?.response?.data?.message || err?.msg || err?.message || fallback;
}

async function issuePushUrl(deviceId: string, values: Record<string, any>) {
  const response = await issueRtmpIngestUrl(deviceId, {
    tenant_id: String(values.tenant_id || '').trim(),
    ttl: Number(values.ttl) || 3600,
    base_url: values.base_url || undefined,
  });
  ingestUrlInfo.value = responseData(response) as RtmpIngestUrlInfo;
}

async function handleSubmit() {
  try {
    await validate();
  } catch {
    return;
  }

  const values = getFieldsValue();
  const tenantId = String(values.tenant_id || '').trim();
  if (!tenantId) {
    createMessage.error('租户 ID 不能为空');
    return;
  }

  submitting.value = true;
  try {
    tokenRotatedNotice.value = false;
    const registerResponse = await registerDevice({
      name: values.name,
      source: 'rtmp://public-ingest/pending',
      stream: 0,
      cameraType: 'custom',
      enable_forward: false,
    });
    const device = responseData(registerResponse);
    const deviceId = String(device.id || device.device_id || '').trim();
    if (!deviceId) {
      throw new Error('注册设备成功但未返回设备 ID');
    }

    currentDeviceId.value = deviceId;
    currentTenantId.value = tenantId;
    await issuePushUrl(deviceId, values);
    createMessage.success('RTMP 推流地址已生成');
    emit('success');
  } catch (error: unknown) {
    createMessage.error(errorMessage(error, '生成 RTMP 推流地址失败'));
  } finally {
    submitting.value = false;
  }
}

async function handleRotateToken() {
  if (!currentDeviceId.value || !currentTenantId.value) return;
  rotating.value = true;
  try {
    await rotateRtmpIngestToken(currentDeviceId.value, { tenant_id: currentTenantId.value });
    await issuePushUrl(currentDeviceId.value, getFieldsValue());
    tokenRotatedNotice.value = true;
    createMessage.success('RTMP 推流 Token 已轮换');
  } catch (error: unknown) {
    createMessage.error(errorMessage(error, '轮换 RTMP 推流 Token 失败'));
  } finally {
    rotating.value = false;
  }
}

async function copyPushUrl() {
  if (!ingestUrlInfo.value?.push_url) return;
  await navigator.clipboard?.writeText(ingestUrlInfo.value.push_url);
  createMessage.success('推流地址已复制');
}
</script>

<style lang="less" scoped>
.rtmp-ingest-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rtmp-ingest-result__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  color: rgb(0 0 0 / 65%);
  font-size: 13px;
}

.rtmp-ingest-result__url {
  max-width: 100%;
  margin: 0;
  padding: 10px 12px;
  overflow: auto;
  color: #111827;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-all;
}

.rtmp-ingest-result__notice {
  color: #ad6800;
  font-size: 13px;
}

.rtmp-ingest-result__empty {
  color: rgb(0 0 0 / 45%);
  font-size: 13px;
}
</style>
