import * as assert from 'node:assert/strict'
import { extractLoginErrorMessage, resolveLoginTenantId, runLoginSubmitFlow } from '../src/views/base/login/loginSubmit'

async function testInvalidFormDoesNotShowCaptcha() {
  const events: string[] = []

  const result = await runLoginSubmitFlow({
    captchaEnable: 'true',
    validateForm: async () => {
      events.push('validate')
      throw new Error('invalid form')
    },
    login: async () => {
      events.push('login')
    },
    showCaptcha: () => {
      events.push('captcha')
    },
  })

  assert.equal(result, 'invalid')
  assert.deepEqual(events, ['validate'])
}

async function testValidFormShowsCaptchaAfterValidation() {
  const events: string[] = []

  const result = await runLoginSubmitFlow({
    captchaEnable: 'true',
    validateForm: async () => {
      events.push('validate')
      return { username: 'admin', password: 'test-password' }
    },
    login: async () => {
      events.push('login')
    },
    showCaptcha: () => {
      events.push('captcha')
    },
  })

  assert.equal(result, 'captcha')
  assert.deepEqual(events, ['validate', 'captcha'])
}

async function testValidFormLogsInWhenCaptchaDisabled() {
  const events: string[] = []

  const result = await runLoginSubmitFlow({
    captchaEnable: 'false',
    validateForm: async () => {
      events.push('validate')
      return { username: 'admin', password: 'test-password' }
    },
    login: async () => {
      events.push('login')
    },
    showCaptcha: () => {
      events.push('captcha')
    },
  })

  assert.equal(result, 'login')
  assert.deepEqual(events, ['validate', 'login'])
}

function testExtractsBackendLoginErrorMessage() {
  const message = extractLoginErrorMessage(
    {
      response: {
        data: {
          message: '账号或密码错误',
        },
      },
      message: 'Request failed with status code 401',
    },
    '网络异常',
  )

  assert.equal(message, '账号或密码错误')
}

function testExtractsNestedLoginErrorMessage() {
  const message = extractLoginErrorMessage(
    {
      data: {
        message: '验证码错误',
      },
      message: 'Request failed',
    },
    '网络异常',
  )

  assert.equal(message, '验证码错误')
}

async function testTenantLookupFallsBackToDefaultWhenProbeFails() {
  const tenantIds: number[] = []
  const result = await resolveLoginTenantId({
    tenantEnable: 'true',
    tenantName: 'Admin-IoT',
    website: 'eye.yfeiai.com',
    getTenantByWebsite: async () => {
      throw new Error('system exception')
    },
    getTenantIdByName: async () => {
      throw new Error('system exception')
    },
    setTenantId: (id) => tenantIds.push(id),
  })

  assert.equal(result, 1)
  assert.deepEqual(tenantIds, [1])
}

async function testTenantLookupUsesWebsiteTenantWhenAvailable() {
  const tenantIds: number[] = []
  const result = await resolveLoginTenantId({
    tenantEnable: 'true',
    tenantName: 'Admin-IoT',
    website: 'eye.yfeiai.com',
    getTenantByWebsite: async () => ({ id: 7, name: 'yFeiEye' }),
    getTenantIdByName: async () => {
      throw new Error('should not be called')
    },
    setTenantId: (id) => tenantIds.push(id),
  })

  assert.equal(result, 7)
  assert.deepEqual(tenantIds, [7])
}

async function main() {
  await testInvalidFormDoesNotShowCaptcha()
  await testValidFormShowsCaptchaAfterValidation()
  await testValidFormLogsInWhenCaptchaDisabled()
  testExtractsBackendLoginErrorMessage()
  testExtractsNestedLoginErrorMessage()
  await testTenantLookupFallsBackToDefaultWhenProbeFails()
  await testTenantLookupUsesWebsiteTenantWhenAvailable()
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
