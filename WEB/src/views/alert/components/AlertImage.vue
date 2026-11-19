<script lang="ts" setup>
import { ref, watch } from 'vue';
import { isProtectedAlertImageUrl, resolveAlertImageDisplayUrl } from '@/utils/alertMinioImage';
import { getAccessToken, getTenantId } from '@/utils/auth';

const props = defineProps<{ src?: string | null }>();
const emit = defineEmits<{ error: [] }>();
const displayUrl = ref<string>();

watch(() => props.src, async (source, _, onCleanup) => {
  displayUrl.value = undefined;
  const url = resolveAlertImageDisplayUrl(source);
  if (!url) return;

  const controller = new AbortController();
  let objectUrl: string | undefined;
  onCleanup(() => {
    controller.abort();
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  });

  try {
    const endpoint = resolveAlertImageDisplayUrl('/video/alert/image');
    if (!isProtectedAlertImageUrl(url, endpoint, window.location.href)) {
      displayUrl.value = url;
      return;
    }
    const headers = new Headers();
    const token = getAccessToken();
    const tenant = getTenantId();
    if (token) headers.set('Authorization', `Bearer ${token}`);
    if (tenant != null) headers.set('tenant-id', String(tenant));
    const response = await fetch(url, {
      headers, signal: controller.signal, mode: 'same-origin', redirect: 'error', cache: 'no-store',
    });
    if (!response.ok || !response.headers.get('Content-Type')?.startsWith('image/')) {
      throw new Error('告警图片加载失败');
    }
    const blob = await response.blob();
    if (controller.signal.aborted) return;
    objectUrl = URL.createObjectURL(blob);
    displayUrl.value = objectUrl;
  } catch {
    if (!controller.signal.aborted) emit('error');
  }
}, { immediate: true });
</script>

<template>
  <img :src="displayUrl" @error="emit('error')">
</template>
