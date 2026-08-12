<script lang="ts" setup>
import { computed, onMounted, reactive, ref, unref } from 'vue'

import { Checkbox, Col, Form, Input, Row } from 'ant-design-vue'
import LoginFormTitle from './LoginFormTitle.vue'

import { LoginStateEnum, useFormRules, useFormValid, useLoginState } from './useLogin'
import { useI18n } from '@/hooks/web/useI18n'
import { useMessage } from '@/hooks/web/useMessage'

import { useUserStore } from '@/store/modules/user'
import { usePermissionStore } from '@/store/modules/permission'

import { useGlobSetting } from '@/hooks/setting'
import { useDesign } from '@/hooks/web/useDesign'

import * as authUtil from '@/utils/auth'

import { Verify } from '@/components/Verifition'
import { getTenantByWebsite, getTenantIdByName } from '@/api/base/login'
import { Button } from '@/components/Button'
import { extractLoginErrorMessage, resolveLoginTenantId, runLoginSubmitFlow } from './loginSubmit'
const FormItem = Form.Item
const InputPassword = Input.Password

const { t } = useI18n()
const { notification, createErrorModal } = useMessage()
const { prefixCls } = useDesign('login')
const userStore = useUserStore()
const permissionStore = usePermissionStore()

const { tenantEnable, captchaEnable } = useGlobSetting()

const { setLoginState, getLoginState } = useLoginState()
const { getFormRules } = useFormRules()

const formRef = ref()
const loading = ref(false)
const rememberMe = ref(authUtil.getRememberMe())

onMounted(() => {
  rememberMe.value = authUtil.getRememberMe()
})

const verify = ref()
const captchaType = ref('blockPuzzle') // blockPuzzle 滑块 clickWord 点击文字

const formData = reactive({
  tenantName: 'Admin-IoT',
  username: 'admin',
  password: '',
  captchaVerification: '',
})

const { validForm } = useFormValid(formRef)

// onKeyStroke('Enter', handleLogin);

const getShow = computed(() => unref(getLoginState) === LoginStateEnum.LOGIN)

// 获取验证码
async function getCode() {
  await runLoginSubmitFlow({
    captchaEnable,
    validateForm: validForm,
    login: () => handleLogin({}),
    showCaptcha: () => verify.value.show(),
  })
}

// 根据域名，获得租户信息 && 获取租户ID
async function getTenantId() {
  const tenantId = await resolveLoginTenantId({
    tenantEnable,
    tenantName: formData.tenantName,
    website: location.host,
    getTenantByWebsite,
    getTenantIdByName,
    setTenantId: authUtil.setTenantId,
  })
  if (tenantId)
    formData.tenantName = formData.tenantName || 'Admin-IoT'
}

async function handleLogin(params: { captchaVerification?: string } = {}) {
  await getTenantId()
  const data = await validForm()
  if (!data)
    return
  try {
    loading.value = true
    const userInfo = await userStore.login({
      password: data.password,
      username: data.username,
      captchaVerification: params.captchaVerification || '',
      rememberMe: rememberMe.value,
      mode: 'none', // 不要默认的错误提示
    })
    if (userInfo) {
      console.log(JSON.stringify(userInfo))
      await permissionStore.changePermissionCode(userInfo.permissions)
      notification.success({
        message: t('sys.login.loginSuccessTitle'),
        description: `${t('sys.login.loginSuccessDesc')}: ${userInfo.user.nickname}`,
        duration: 3,
      })
    }
  }
 catch (error) {
    console.error(error)
    createErrorModal({
      title: t('sys.api.errorTip'),
      content: extractLoginErrorMessage(error, t('sys.api.networkExceptionMsg')),
      getContainer: () => document.body.querySelector(`.${prefixCls}`) || document.body,
    })
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <LoginFormTitle v-show="getShow" class="enter-x" />
  <Form
    v-show="getShow" ref="formRef" class="enter-x p-4" :model="formData" :rules="getFormRules"
    @keypress.enter="getCode"
  >
    <FormItem name="tenantName" class="enter-x">
      <Input
        v-if="tenantEnable === 'true'"
        v-model:value="formData.tenantName"
        size="large"
        :placeholder="t('sys.login.tenantName')"
        class="fix-auto-fill"
      />
    </FormItem>
    <FormItem name="username" class="enter-x">
      <Input
        v-model:value="formData.username" size="large" :placeholder="t('sys.login.userName')"
        class="fix-auto-fill"
      />
    </FormItem>
    <FormItem name="password" class="enter-x">
      <InputPassword
        v-model:value="formData.password"
        size="large"
        visibility-toggle
        :placeholder="t('sys.login.password')"
        class="fix-auto-fill"
      />
    </FormItem>

    <Row class="enter-x">
      <Col :span="12">
        <FormItem>
          <!-- No logic, you need to deal with it yourself -->
          <Checkbox v-model:checked="rememberMe" size="small">
            {{ t('sys.login.rememberMe30Days') }}
          </Checkbox>
        </FormItem>
      </Col>
      <Col :span="12">
        <FormItem :style="{ 'text-align': 'right' }">
          <!-- No logic, you need to deal with it yourself -->
          <Button type="link" size="small" @click="setLoginState(LoginStateEnum.RESET_PASSWORD)">
            {{ t('sys.login.forgetPassword') }}
          </Button>
        </FormItem>
      </Col>
    </Row>

    <FormItem class="enter-x">
      <Button type="primary" size="large" block :loading="loading" @click="getCode">
        {{ t('sys.login.loginButton') }}
      </Button>
      <!-- <Button size="large" class="mt-4 enter-x" block @click="handleRegister">
        {{ t('sys.login.registerButton') }}
      </Button> -->
    </FormItem>
  </Form>
  <Verify ref="verify" mode="pop" :captcha-type="captchaType" :img-size="{ width: '360px', height: '180px' }" @success="handleLogin" />
</template>
