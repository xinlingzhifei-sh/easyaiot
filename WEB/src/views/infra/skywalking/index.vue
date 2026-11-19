<script lang="ts" setup>
import { ref } from 'vue'
import { IFrame } from '@/components/iFrame'


defineOptions({ name: 'InfraSkywalking' })

const src = ref('http://127.0.0.1:8080')
</script>

<template>
  <div>
    <IFrame :src="src" />
  </div>
</template>
