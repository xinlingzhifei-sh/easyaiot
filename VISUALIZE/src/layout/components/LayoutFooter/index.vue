<template>
  <div class="go-footer">
    <slot>
      <n-space :size="50">
        <n-text depth="2">
          <n-a>yFeiEye</n-a>
          <n-text depth="3"> — 低代码数据可视化平台</n-text>
        </n-text>
      </n-space>
    </slot>
  </div>
</template>

<script setup lang="ts">
</script>

<style lang="scss" scoped>
@include go(footer) {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  padding: 0 40px;
  height: $--footer-height;
}
</style>
