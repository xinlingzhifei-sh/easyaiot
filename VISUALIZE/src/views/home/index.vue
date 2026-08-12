<template>
  <div class="go-editor-home">
    <n-result
      status="info"
      title="可视化编辑器"
      description="请从 yFeiEye 管理后台「可视化 → 可视化项目管理」打开编辑器"
    />
  </div>
</template>

<script lang="ts" setup></script>

<style lang="scss" scoped>
.go-editor-home {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100vw;
  height: 100vh;
  @include background-image('background-image');
}
</style>
