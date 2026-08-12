import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const loginForm = readFileSync(resolve('src/views/base/login/LoginForm.vue'), 'utf8')
const useLogin = readFileSync(resolve('src/views/base/login/useLogin.ts'), 'utf8')

assert.match(
  loginForm,
  /@keypress\.enter="getCode"/,
  'Pressing Enter in the login form must use the same validation-before-captcha flow as the login button.',
)

assert.doesNotMatch(
  loginForm,
  /@keypress\.enter="handleLogin"/,
  'Pressing Enter must not call handleLogin directly because that bypasses captcha pre-validation and can show a login error before required-field validation.',
)

assert.match(
  useLogin,
  /const getTenantFormRule = computed\(\(\) => createRule\(t\('sys\.login\.tenantPlaceholder'\)\)\)/,
  'Login rules should define a tenant required-field message so an empty tenant is reported before captcha.',
)

assert.match(
  useLogin,
  /default:\s*return\s*{[\s\S]*tenantName:\s*tenantFormRule,[\s\S]*username:\s*accountFormRule,[\s\S]*password:\s*passwordFormRule,[\s\S]*}/,
  'Login form rules must match LoginForm.vue field names: tenantName, username, and password.',
)
