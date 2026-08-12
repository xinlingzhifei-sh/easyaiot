import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolveLoginBrandingValues } from '../src/utils/loginBrandingDefaults'

const currentDefaults = {
  loginName: '逸飞 AI 智眼管控平台',
  loginBgLight: '/assets/login-yfeiEye.svg',
  loginBgDark: '/assets/login-yfeiEye.svg',
}

const legacyDefaults = {
  loginName: '逸飞AI智眼系统',
  loginBgLight: '/assets/light-bg.png',
  loginBgDark: '/assets/dark-bg.png',
}

const options = {
  current: currentDefaults,
  legacy: legacyDefaults,
}

function testMissingInvalidAndLegacyValuesUseRestoredDefaults() {
  assert.deepEqual(resolveLoginBrandingValues({}, options), currentDefaults)
  assert.deepEqual(resolveLoginBrandingValues(legacyDefaults, options), currentDefaults)
  assert.deepEqual(
    resolveLoginBrandingValues(
      {
        loginName: '   ',
        loginBgLight: 42,
        loginBgDark: null,
      },
      options,
    ),
    currentDefaults,
  )
}

function testGenuineCustomValuesArePreserved() {
  const custom = {
    loginName: '客户自定义管控平台',
    loginBgLight: 'https://cdn.example.com/login-light.webp',
    loginBgDark: 'data:image/png;base64,custom-image',
  }

  assert.deepEqual(resolveLoginBrandingValues(custom, options), custom)
}

function testProductionWiringUsesCameraAssetAndCoverSizing() {
  const storageSource = readFileSync(
    new URL('../src/utils/platformBrandingStorage.ts', import.meta.url),
    'utf8',
  )
  const hookSource = readFileSync(
    new URL('../src/hooks/web/usePlatformBranding.ts', import.meta.url),
    'utf8',
  )
  const loginSource = readFileSync(
    new URL('../src/views/base/login/Login.vue', import.meta.url),
    'utf8',
  )

  assert.match(
    storageSource,
    /import defaultLoginBg from '@\/assets\/svg\/login-yfeiEye\.svg'/,
  )
  assert.match(storageSource, /DEFAULT_LOGIN_NAME\s*=\s*'逸飞 AI 智眼管控平台'/)
  assert.match(storageSource, /loginName:\s*DEFAULT_LOGIN_NAME/)
  assert.match(storageSource, /loginBgLight:\s*defaultLoginBg/)
  assert.match(storageSource, /loginBgDark:\s*defaultLoginBg/)
  assert.match(storageSource, /resolveLoginBrandingValues\(data,/)

  assert.match(
    hookSource,
    /setProperty\('--platform-login-bg-light', `url\("\$\{lightBg\}"\)`\)/,
  )
  assert.match(
    hookSource,
    /setProperty\('--platform-login-bg-dark', `url\("\$\{darkBg\}"\)`\)/,
  )

  const coverDeclarations = loginSource.match(
    /background:\s*var\(--platform-login-bg-(?:light|dark), url\('@\/assets\/svg\/login-yfeiEye\.svg'\)\) center center \/ cover no-repeat;/g,
  )
  assert.equal(coverDeclarations?.length, 2)
  assert.doesNotMatch(loginSource, /background-size:\s*100% 100% !important;/)
}

testMissingInvalidAndLegacyValuesUseRestoredDefaults()
testGenuineCustomValuesArePreserved()
testProductionWiringUsesCameraAssetAndCoverSizing()
