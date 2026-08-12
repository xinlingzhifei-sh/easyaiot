# Login Page Camera Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the confirmed camera-and-city login branding while preserving current authentication behavior and genuine user branding customizations.

**Architecture:** Keep `Login.vue` and `LoginForm.vue` unchanged because their current layout and authentication flow already match the accepted page. Add one pure resolver for login-branding defaults, wire it into the existing branding storage loader, and change the runtime background override from stretched sizing to centered `cover`. Test the pure migration behavior plus the source-level wiring before building and visually comparing the page with the historical screenshot.

**Tech Stack:** Vue 3, TypeScript, Vite, Less, Node test runner, pnpm

---

## File map

- Create `WEB/src/utils/loginBrandingDefaults.ts`: pure, environment-independent resolver for current, legacy, invalid, and custom login-branding values.
- Create `WEB/tests/loginPageRestoration.test.ts`: focused regression test for migration behavior and production wiring.
- Modify `WEB/src/utils/platformBrandingStorage.ts`: restore the camera asset and historical title as defaults, then use the resolver when loading saved configuration.
- Modify `WEB/src/hooks/web/usePlatformBranding.ts`: apply customized/default backgrounds with centered `cover` instead of `100% 100%` stretching.
- Do not modify `WEB/src/views/base/login/Login.vue`, `WEB/src/views/base/login/LoginForm.vue`, authentication stores, tenant APIs, CAPTCHA APIs, dashboard files, or AI/video files.

### Task 1: Add the failing login-branding regression test

**Files:**

- Create: `WEB/tests/loginPageRestoration.test.ts`
- Test: `WEB/tests/loginPageRestoration.test.ts`

- [ ] **Step 1: Create the regression test**

Create `WEB/tests/loginPageRestoration.test.ts` with this complete content:

```ts
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

  assert.match(
    storageSource,
    /import defaultLoginBg from '@\/assets\/svg\/login-yfeiEye\.svg'/,
  )
  assert.match(storageSource, /DEFAULT_LOGIN_NAME\s*=\s*'逸飞 AI 智眼管控平台'/)
  assert.match(storageSource, /loginName:\s*DEFAULT_LOGIN_NAME/)
  assert.match(storageSource, /loginBgLight:\s*defaultLoginBg/)
  assert.match(storageSource, /loginBgDark:\s*defaultLoginBg/)
  assert.match(storageSource, /resolveLoginBrandingValues\(data,/)

  const coverDeclarations = hookSource.match(
    /background:\s*url\("\$\{(?:lightBg|darkBg)\}"\) center center \/ cover no-repeat !important;/g,
  )
  assert.equal(coverDeclarations?.length, 2)
  assert.doesNotMatch(hookSource, /background-size:\s*100% 100% !important;/)
}

testMissingInvalidAndLegacyValuesUseRestoredDefaults()
testGenuineCustomValuesArePreserved()
testProductionWiringUsesCameraAssetAndCoverSizing()
```

- [ ] **Step 2: Run the focused test and verify the red state**

Run from `E:\yFeiEye\WEB`:

```powershell
pnpm exec esno tests/loginPageRestoration.test.ts
```

Expected: FAIL with `ERR_MODULE_NOT_FOUND` for `WEB/src/utils/loginBrandingDefaults`, proving the new behavior does not exist yet.

### Task 2: Implement the restored defaults and legacy-value resolver

**Files:**

- Create: `WEB/src/utils/loginBrandingDefaults.ts`
- Modify: `WEB/src/utils/platformBrandingStorage.ts:1-61`
- Modify: `WEB/src/hooks/web/usePlatformBranding.ts:35-43`
- Test: `WEB/tests/loginPageRestoration.test.ts`

- [ ] **Step 1: Add the pure login-branding resolver**

Create `WEB/src/utils/loginBrandingDefaults.ts` with this complete content:

```ts
export interface LoginBrandingValues {
  loginName: string
  loginBgLight: string
  loginBgDark: string
}

export interface LoginBrandingResolutionOptions {
  current: LoginBrandingValues
  legacy: LoginBrandingValues
}

export function resolveLoginBrandingValues(
  stored: Partial<Record<keyof LoginBrandingValues, unknown>>,
  options: LoginBrandingResolutionOptions,
): LoginBrandingValues {
  return {
    loginName: resolveValue(
      stored.loginName,
      options.current.loginName,
      options.legacy.loginName,
    ),
    loginBgLight: resolveValue(
      stored.loginBgLight,
      options.current.loginBgLight,
      options.legacy.loginBgLight,
    ),
    loginBgDark: resolveValue(
      stored.loginBgDark,
      options.current.loginBgDark,
      options.legacy.loginBgDark,
    ),
  }
}

function resolveValue(value: unknown, currentDefault: string, legacyDefault: string): string {
  if (typeof value !== 'string' || !value.trim() || value === legacyDefault) {
    return currentDefault
  }
  return value
}
```

- [ ] **Step 2: Wire the restored defaults into platform-branding storage**

Apply these exact changes to `WEB/src/utils/platformBrandingStorage.ts`:

```diff
 import defaultLogo from '@/assets/images/logo.png'
-import defaultLightBg from '@/assets/images/light-bg.png'
-import defaultDarkBg from '@/assets/images/dark-bg.png'
+import legacyDefaultDarkBg from '@/assets/images/dark-bg.png'
+import legacyDefaultLightBg from '@/assets/images/light-bg.png'
+import defaultLoginBg from '@/assets/svg/login-yfeiEye.svg'
+import { resolveLoginBrandingValues } from '@/utils/loginBrandingDefaults'
 import { clearLocalStorage, getLocalStorage, setLocalStorage } from '@/utils/storage'

 export const PLATFORM_BRANDING_STORAGE_KEY = 'PLATFORM_BRANDING_CONFIG'
 export const PLATFORM_BRANDING_FAB_HIDDEN_KEY = 'PLATFORM_BRANDING_FAB_HIDDEN'
+export const DEFAULT_LOGIN_NAME = '逸飞 AI 智眼管控平台'
+const LEGACY_DEFAULT_LOGIN_NAME = '逸飞AI智眼系统'
```

In `getDefaultPlatformBranding`, replace only the three login defaults:

```diff
-    loginName: envTitle,
+    loginName: DEFAULT_LOGIN_NAME,
     loginLogo: defaultLogo,
     loginFormTitle: '',
-    loginBgLight: defaultLightBg,
-    loginBgDark: defaultDarkBg,
+    loginBgLight: defaultLoginBg,
+    loginBgDark: defaultLoginBg,
```

Immediately after `const data = raw as Partial<PlatformBrandingConfig>` in `loadPlatformBrandingConfig`, add:

```ts
  const loginBranding = resolveLoginBrandingValues(data, {
    current: {
      loginName: defaults.loginName,
      loginBgLight: defaults.loginBgLight,
      loginBgDark: defaults.loginBgDark,
    },
    legacy: {
      loginName: LEGACY_DEFAULT_LOGIN_NAME,
      loginBgLight: legacyDefaultLightBg,
      loginBgDark: legacyDefaultDarkBg,
    },
  })
```

Then replace the three login fields in the returned object:

```diff
-    loginName: pickString(data.loginName, defaults.loginName),
+    loginName: loginBranding.loginName,
     loginLogo: pickString(data.loginLogo, defaults.loginLogo),
     loginFormTitle: pickString(data.loginFormTitle, defaults.loginFormTitle),
-    loginBgLight: pickString(data.loginBgLight, defaults.loginBgLight),
-    loginBgDark: pickString(data.loginBgDark, defaults.loginBgDark),
+    loginBgLight: loginBranding.loginBgLight,
+    loginBgDark: loginBranding.loginBgDark,
```

- [ ] **Step 3: Restore the original background fitting behavior**

In `WEB/src/hooks/web/usePlatformBranding.ts`, replace the generated CSS block with:

```ts
  el.textContent = `
    html:not([data-theme='dark']) .xingyuv-login {
      background: url("${lightBg}") center center / cover no-repeat !important;
    }
    html[data-theme='dark'] .xingyuv-login {
      background: url("${darkBg}") center center / cover no-repeat !important;
    }
  `
```

- [ ] **Step 4: Run the focused test and verify the green state**

Run from `E:\yFeiEye\WEB`:

```powershell
pnpm exec esno tests/loginPageRestoration.test.ts
```

Expected: PASS with one passing test file and exit code `0`.

- [ ] **Step 5: Commit the focused implementation**

Run from `E:\yFeiEye`:

```powershell
git add -- WEB/tests/loginPageRestoration.test.ts WEB/src/utils/loginBrandingDefaults.ts WEB/src/utils/platformBrandingStorage.ts WEB/src/hooks/web/usePlatformBranding.ts
git commit -m "fix(login): restore camera branding defaults"
```

Expected: one commit containing only the focused login-branding test and implementation files.

### Task 3: Prove authentication behavior and production build remain intact

**Files:**

- Test: `WEB/tests/loginPageRestoration.test.ts`
- Test: `WEB/tests/loginSubmit.test.ts`
- Test: `WEB/tests/loginFormTemplate.test.ts`

- [ ] **Step 1: Run the focused login regression suite**

Run from `E:\yFeiEye\WEB`:

```powershell
pnpm exec esno tests/loginPageRestoration.test.ts
pnpm exec esno tests/loginSubmit.test.ts
node --test tests/loginFormTemplate.test.ts
```

Expected: all three commands PASS with exit code `0`.

- [ ] **Step 2: Run TypeScript checking**

Run from `E:\yFeiEye\WEB`:

```powershell
pnpm exec vue-tsc --noEmit --skipLibCheck
```

Expected: exit code `0` and no TypeScript diagnostics.

- [ ] **Step 3: Build the production bundle**

Run from `E:\yFeiEye\WEB`:

```powershell
pnpm build
```

Expected: Vite and `postBuild.ts` finish with exit code `0`; the generated bundle contains the hashed `login-yfeiEye` asset.

- [ ] **Step 4: Check the final patch for accidental edits**

Run from `E:\yFeiEye`:

```powershell
git diff --check HEAD^ HEAD
git show --stat --oneline HEAD
```

Expected: no whitespace errors; the commit lists only the four files from Task 2.

### Task 4: Validate the accepted page visually

**Files:**

- Reference: `codex-smoke-screenshots-2026-06-12/01-login-root-redirect.png`
- Inspect: `WEB/src/views/base/login/Login.vue`

- [ ] **Step 1: Start a fresh local development origin**

Run from `E:\yFeiEye\WEB` and keep the process running:

```powershell
pnpm dev --host 127.0.0.1 --port 8891
```

Expected: Vite reports `http://127.0.0.1:8891/`. The new port avoids stale branding storage from earlier browser sessions.

- [ ] **Step 2: Capture and compare the desktop login page**

Open `http://127.0.0.1:8891/login?redirect=/dashboard` in the in-app browser at 1280×720. Verify all of the following against the historical reference:

- The camera-and-city image fills the viewport without stretching.
- The left brand shows the existing bird Logo and exact title “逸飞 AI 智眼管控平台”.
- The login card is centered, with one copy of every element and no duplicated screenshot content.
- The right-side monitoring panels and physical cameras remain visible at the expected crop.
- The top-right theme and locale controls remain usable.

Expected: the layout and visible copy match the accepted 2026-06-12 screenshot.

- [ ] **Step 3: Verify both themes keep the camera background**

Use the top-right theme toggle once, then inspect the page again.

Expected: form controls switch theme, while the same camera-and-city background and historical title remain visible.

- [ ] **Step 4: Verify narrow-screen usability**

Resize the browser to 390×844 and reload the login route.

Expected: title and card stay within the viewport, inputs and login button remain operable, and the background is cropped rather than stretched.

- [ ] **Step 5: Stop the local development server**

Send `Ctrl+C` to the Vite process.

Expected: the development server exits cleanly.

### Task 5: Prepare the login-only handoff

**Files:**

- Inspect: all files in the Task 2 commit

- [ ] **Step 1: Verify repository state and commit identity**

Run from `E:\yFeiEye`:

```powershell
git status --short --branch
git log -2 --oneline
```

Expected: no tracked login-page changes remain uncommitted; pre-existing untracked artifacts remain untouched; the latest code commit is `fix(login): restore camera branding defaults` after the design-document commit.

- [ ] **Step 2: Report the verified local result before deployment**

Report the focused test result, type-check result, production-build result, desktop and mobile visual result, and the exact local commit hash. State explicitly that no remote push or server deployment has occurred, then request separate authorization before changing the live server.
