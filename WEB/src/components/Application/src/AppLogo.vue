<script lang="ts" setup>
import {computed, unref} from 'vue'
import {useRoute} from 'vue-router'
import {useGlobSetting} from '@/hooks/setting'
import {useGo} from '@/hooks/web/usePage'
import {useMenuSetting} from '@/hooks/setting/useMenuSetting'
import {useDesign} from '@/hooks/web/useDesign'
import {usePlatformBranding} from '@/hooks/web/usePlatformBranding'
import {PageEnum} from '@/enums/pageEnum'

const props = defineProps({
  // 当前父组件的主题
  theme: {type: String, validator: (v: string) => ['light', 'dark'].includes(v)},
  // 是否显示标题
  showTitle: {type: Boolean, default: true},
  // 折叠菜单时也会显示标题
  alwaysShowTitle: {type: Boolean},
})

const {prefixCls} = useDesign('app-logo')
const {getCollapsedShowTitle} = useMenuSetting()
const {title} = useGlobSetting()
const {config} = usePlatformBranding()
const route = useRoute()
const go = useGo()

const isLoginPage = computed(() => route.path.includes('/login'))

const displayTitle = computed(() => {
  if (unref(isLoginPage)) {
    return config.value.loginName
  }
  return config.value.platformName || title
})

const displayLogo = computed(() => {
  if (unref(isLoginPage)) {
    return config.value.loginLogo
  }
  return config.value.platformLogo
})

const getAppLogoClass = computed(() => [prefixCls, props.theme, {'collapsed-show-title': unref(getCollapsedShowTitle)}])

const getTitleClass = computed(() => [
  `${prefixCls}__title`,
  {
    'xs:opacity-0': !props.alwaysShowTitle,
  },
])

function goHome() {
  go(PageEnum.BASE_HOME)
}
</script>

<template>
  <div class="ant-icon" :class="getAppLogoClass" @click="goHome">
    <div class="logo-icon">
      <img class="uc-logo" :src="displayLogo" :alt="displayTitle" />
    </div>
    <div v-show="showTitle" class="truncate md:opacity-100 logo-title" :class="getTitleClass">
      {{ displayTitle }}
    </div>
  </div>
</template>

<style lang="less" scoped>
.ant-icon{
  display: flex;
  align-items: center;
  gap: 0.58rem;
  min-width: 0;
  .logo-icon{
    display: flex;
    flex: 0 0 44px;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 36px;
    overflow: hidden;
    border-radius: 6px;
    .uc-logo {
      width: 100% !important;
      height: 100%;
      display: block;
      object-fit: contain;
    }
  }
  .logo-title{
    margin-top: 0;
    font-family: moon,sans-serif;
    font-size: 1.45rem !important;
    line-height: 1.15;
  }
}

@prefix-cls: ~'@{namespace}-app-logo';

.@{prefix-cls} {
  display: flex;
  align-items: center;
  padding-left: 7px;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-top: 0;

  &.light {
    border-bottom: 1px solid var(--border-color);
  }

  &.collapsed-show-title {
    padding-left: 20px;
  }

  &.dark &__title {
    color: @white;
  }

  &__title {
    font-size: 16px;
    font-weight: 700;
    line-height: normal;
    transition: all 0.5s;
  }
}
</style>
