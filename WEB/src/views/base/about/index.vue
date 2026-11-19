<script lang="ts" setup>
import { h } from 'vue'
import { Tag } from 'ant-design-vue'
import { PageWrapper } from '@/components/Page'
import type { DescItem } from '@/components/Description'
import { Description, useDescription } from '@/components/Description'

const { pkg, lastBuildTime } = __APP_INFO__

const { dependencies, devDependencies, name, version } = pkg

const schema: DescItem[] = []
const devSchema: DescItem[] = []

const commonTagRender = (color: string) => curVal => h(Tag, { color }, () => curVal)

const infoSchema: DescItem[] = [
  {
    label: '版本',
    field: 'version',
    render: commonTagRender('blue'),
  },
  {
    label: '最后编译时间',
    field: 'lastBuildTime',
    render: commonTagRender('blue'),
  },
]

const infoData = {
  version,
  lastBuildTime,
}

Object.keys(dependencies).forEach((key) => {
  schema.push({ field: key, label: key })
})

Object.keys(devDependencies).forEach((key) => {
  devSchema.push({ field: key, label: key })
})

const [register] = useDescription({
  title: '生产环境依赖',
  data: dependencies,
  schema,
  column: 3,
})

const [registerDev] = useDescription({
  title: '开发环境依赖',
  data: devDependencies,
  schema: devSchema,
  column: 3,
})

const [infoRegister] = useDescription({
  title: '项目信息',
  data: infoData,
  schema: infoSchema,
  column: 2,
})
</script>

<template>
  <PageWrapper title="关于">
    <template #headerContent>
      <div class="flex items-center justify-between">
        <span class="flex-1">
          {{ name }} 基于 Vue3.0、Vite、Ant-Design-Vue、TypeScript 构建。
        </span>
      </div>
    </template>
    <Description class="enter-y" @register="infoRegister" />
    <Description class="enter-y my-4" @register="register" />
    <Description class="enter-y" @register="registerDev" />
  </PageWrapper>
</template>
