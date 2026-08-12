<script lang="ts" setup>
import LoginForm from './LoginForm.vue'
import ForgetPasswordForm from './ForgetPasswordForm.vue'
import { AppDarkModeToggle, AppLocalePicker, AppLogo } from '@/components/Application'
import { useDesign } from '@/hooks/web/useDesign'
import { useLocaleStore } from '@/store/modules/locale'

defineProps({
  sessionTimeout: {
    type: Boolean,
  },
})

const { prefixCls } = useDesign('login')
const localeStore = useLocaleStore()
const showLocale = localeStore.getShowPicker
</script>

<template>
  <div :class="prefixCls">
    <div :class="`${prefixCls}-toolbar`">
      <AppDarkModeToggle v-if="!sessionTimeout" class="enter-x mr-2" />
      <AppLocalePicker v-if="!sessionTimeout && showLocale" class="enter-x xl:text-gray-600" :show-text="false" />
    </div>

    <div :class="`${prefixCls}-brand`">
      <AppLogo :always-show-title="true" />
    </div>

    <main :class="`${prefixCls}-main`">
      <div :class="`${prefixCls}-content`">
        <div :class="`${prefixCls}-brand-spacer`" />
        <div :class="`${prefixCls}-form`">
          <LoginForm />
          <ForgetPasswordForm />
        </div>
      </div>
    </main>
  </div>
</template>

<style lang="less">
@prefix-cls: ~'@{namespace}-login';
@logo-prefix-cls: ~'@{namespace}-app-logo';
@countdown-prefix-cls: ~'@{namespace}-countdown-input';
@dark-bg: rgba(41, 49, 70, 0.6);

body {
  background-size: cover;
  background-attachment: fixed;
}

html[data-theme='dark'] {
  .@{prefix-cls} {
    background: var(--platform-login-bg-dark, url('@/assets/svg/login-yfeiEye.svg')) center center / cover no-repeat;

    &::before {
      background-image: none;
    }

    .ant-input,
    .ant-input-password {
      background-color: rgba(35, 42, 59, 0.98);
    }

    .ant-btn:not(.ant-btn-link, .ant-btn-primary) {
      border: 1px solid #4a5569;
    }

    &-form {
      background: rgba(23, 23, 23, 0.88) !important;
      border: 1px solid #505050;
    }

    .app-iconify {
      color: #fff;
    }
  }

  input.fix-auto-fill,
  .fix-auto-fill input {
    -webkit-text-fill-color: #c9d1d9 !important;
    box-shadow: inherit !important;
  }
}

.@{prefix-cls} {
  position: relative;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  padding: 32px;
  overflow: auto;
  background: var(--platform-login-bg-light, url('@/assets/svg/login-yfeiEye.svg')) center center / cover no-repeat;

  &-toolbar {
    position: fixed;
    top: 18px;
    right: 18px;
    z-index: 4;
    display: flex;
    align-items: center;
  }

  &-brand {
    position: absolute;
    top: 6px;
    left: 34px;
    z-index: 2;
    max-width: 660px;
  }

  &-main {
    display: flex;
    align-items: flex-start;
    justify-content: center;
    width: 100%;
    min-height: calc(100vh - 64px);
    min-height: calc(100dvh - 64px);
    padding-top: 92px;
  }

  &-content {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    margin-top: 0;
  }

  &-brand-spacer {
    display: none;
  }

  &-form {
    box-sizing: border-box;
    width: min(350px, calc(100vw - 64px));
    padding: 24px 32px 24px;
    background-color: #fff;
    border-radius: 8px;
    box-shadow: 0 18px 48px rgb(0 17 56 / 18%);
  }

  @media (max-width: @screen-xl) {
    background-color: #293146;

    &-brand {
      top: 10px;
      left: 24px;
      max-width: min(660px, calc(100vw - 48px));
    }

    &-content {
      margin-top: 0;
    }

    &-main {
      padding-top: 104px;
    }
  }

  .@{logo-prefix-cls} {
    position: static;
    height: 116px;
    gap: 18px;

    .logo-icon {
      flex: 0 0 160px;
      width: 160px;
      height: 110px;
    }

    .logo-title,
    &__title {
      align-self: flex-start;
      max-width: 460px;
      margin-top: 18px;
      font-size: 40px !important;
      font-weight: 800;
      line-height: 1.12;
      color: #fff;
      white-space: normal;
      text-shadow: 0 0 12px rgb(87 146 255 / 65%), 0 0 26px rgb(30 106 255 / 45%);
    }

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
  }

  &-sign-in-way {
    .anticon {
      font-size: 22px;
      color: #888;
      cursor: pointer;
    }
  }

  input:not([type='checkbox']) {
    width: 100%;
    min-width: 0;
    max-width: 100%;
  }

  .@{countdown-prefix-cls} input {
    min-width: unset;
  }

  .ant-divider-inner-text {
    font-size: 12px;
  }

  .ant-form {
    padding: 4px 0 !important;
  }

  .ant-form-item {
    margin-bottom: 16px;
  }

  .ant-btn-link {
    padding-right: 0;
    padding-left: 0;
  }

  .form-title {
    padding: 0 0 8px !important;
    font-size: 28px;
    line-height: 1.2;
  }

  @media (max-width: @screen-lg) {
    display: block;
    padding: 24px;

    &-brand {
      position: relative;
      top: auto;
      left: auto;
      width: 100%;
      max-width: 620px;
      margin: 8px auto 24px;
    }

    &-main {
      min-height: calc(100vh - 156px);
      min-height: calc(100dvh - 156px);
      padding-top: 0;
    }

    &-content {
      display: block;
      width: 100%;
      margin-top: 0;
    }

    &-brand-spacer {
      display: none;
    }

    &-form {
      width: min(350px, calc(100vw - 48px));
      max-width: 100%;
      padding: 28px 30px 26px;
      margin: 0 auto;
    }

    .@{logo-prefix-cls} {
      justify-content: center;
      height: 104px;
      gap: 16px;

      .logo-icon {
        flex-basis: 146px;
        width: 146px;
        height: 100px;
      }

      .logo-title,
      &__title {
        max-width: 390px;
        font-size: 32px !important;
      }
    }
  }

  @media (max-width: @screen-md) {
    padding: 18px;

    &-toolbar {
      top: 12px;
      right: 12px;
    }

    &-brand {
      max-width: 100%;
      margin: 10px auto 18px;
    }

    &-main {
      min-height: auto;
    }

    &-form {
      width: min(350px, calc(100vw - 36px));
      max-width: 100%;
      padding: 26px 18px 24px;
    }

    .@{logo-prefix-cls} {
      justify-content: center;
      height: auto;
      gap: 12px;

      .logo-icon {
        flex-basis: 112px;
        width: 112px;
        height: 78px;
      }

      .logo-title,
      &__title {
        max-width: 230px;
        font-size: 24px !important;
      }
    }

    .form-title {
      font-size: 26px;
    }

    &-form {
      .ant-row {
        gap: 14px;
        align-items: center;
      }

      .ant-col-12 {
        flex: 0 0 auto;
        max-width: none;
      }

      .ant-form-item[style] {
        text-align: left !important;
      }
    }
  }

  @media (max-width: @screen-sm) {
    &-brand {
      margin-top: 42px;
    }

    &-form {
      width: min(360px, calc(100vw - 36px));
      padding: 26px 14px 22px;
    }

    .@{logo-prefix-cls} {
      .logo-icon {
        flex-basis: 92px;
        width: 92px;
        height: 64px;
      }

      .logo-title,
      &__title {
        max-width: min(220px, calc(100vw - 148px));
        font-size: 20px !important;
      }
    }
  }
}
</style>
