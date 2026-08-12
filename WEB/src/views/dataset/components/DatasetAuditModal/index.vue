<template>
  <BasicModal
    @register="register"
    title="数据集审核"
    :width="520"
    :canFullscreen="false"
    @ok="handleOk"
  >
    <Spin :spinning="submitting">
      <div class="audit-summary">
        <div class="audit-summary__name">{{ currentRecord?.name || '--' }}</div>
        <div class="audit-summary__meta">
          当前状态：{{ getAuditText(currentRecord?.audit) }}
        </div>
      </div>

      <Form :labelCol="{ span: 5 }" :wrapperCol="{ span: 19 }">
        <FormItem label="审核结果" required>
          <RadioGroup v-model:value="form.audit">
            <Radio :value="1">审核通过</Radio>
            <Radio :value="2">审核驳回</Radio>
          </RadioGroup>
        </FormItem>
        <FormItem
          v-if="form.audit === 2"
          label="驳回原因"
          required
          :validate-status="reasonError ? 'error' : undefined"
          :help="reasonError"
        >
          <Textarea
            v-model:value="form.reason"
            :rows="4"
            placeholder="请输入驳回原因"
          />
        </FormItem>
      </Form>
    </Spin>
  </BasicModal>
</template>

<script lang="ts" setup>
import {computed, reactive, ref} from 'vue';
import {Form, FormItem, Radio, RadioGroup, Spin, Textarea} from 'ant-design-vue';
import {BasicModal, useModalInner} from '@/components/Modal';
import {useMessage} from '@/hooks/web/useMessage';
import {updateDataset} from '@/api/device/dataset';
import {
  buildDatasetAuditPayload,
  validateDatasetAuditForm,
  type DatasetAuditForm,
  type DatasetAuditRecord,
} from './auditPayload';

defineOptions({name: 'DatasetAuditModal'});

const emit = defineEmits(['success']);
const {createMessage} = useMessage();
const currentRecord = ref<(DatasetAuditRecord & { audit?: number; reason?: string }) | null>(null);
const submitting = ref(false);
const form = reactive<DatasetAuditForm>({
  audit: 1,
  reason: '',
});

const reasonError = computed(() => validateDatasetAuditForm(form));

const [register, {closeModal}] = useModalInner((data) => {
  const record = data?.record || {};
  currentRecord.value = record;
  form.audit = Number(record.audit) === 2 ? 2 : 1;
  form.reason = Number(record.audit) === 2 ? record.reason || '' : '';
});

function getAuditText(audit?: number): string {
  if (Number(audit) === 1) return '审核通过';
  if (Number(audit) === 2) return '审核驳回';
  return '待审核';
}

async function handleOk() {
  if (!currentRecord.value) return;
  const error = validateDatasetAuditForm(form);
  if (error) {
    createMessage.error(error);
    return;
  }

  try {
    submitting.value = true;
    await updateDataset(buildDatasetAuditPayload(currentRecord.value, form));
    createMessage.success('审核完成');
    closeModal();
    emit('success');
  } catch (error) {
    console.error(error);
    createMessage.error('审核失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="less" scoped>
.audit-summary {
  padding: 12px 16px;
  margin-bottom: 16px;
  background: #f7f9fc;
  border: 1px solid #edf0f5;
  border-radius: 6px;

  &__name {
    font-size: 15px;
    font-weight: 600;
    color: #1f2937;
  }

  &__meta {
    margin-top: 4px;
    font-size: 13px;
    color: #64748b;
  }
}
</style>
