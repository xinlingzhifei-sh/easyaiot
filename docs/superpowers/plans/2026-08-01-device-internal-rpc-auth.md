# DEVICE Internal RPC Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require a deployment-managed service token for every `/rpc-api/**` call while preserving Gateway token validation, Feign background calls, and independently verified user context.

**Architecture:** A pure shared credential object in `iot-common-base` owns length validation and constant-time comparison. Servlet services enforce it through one early Spring Security matcher and inject it into RPC-only Feign requests; Gateway strips caller-supplied internal headers and injects the managed credential into its WebClient token-check call. Compose distributes one required secret to all 11 callers/servers without committing a value.

**Tech Stack:** Java 21, Spring Boot 2.7.18, Spring Security 5, OpenFeign 11.10, Spring Cloud Gateway/WebFlux, JUnit 5, Mockito, Node.js contract verifiers, Docker Compose.

---

## Execution constraints

- Work in the existing `E:\yFeiEye` checkout because the security remediation already has overlapping uncommitted changes there.
- Preserve all unrelated staged, unstaged, and untracked work. Never bulk-stage, reset, clean, or rewrite adjacent files.
- Do not create commits or push/deploy; this task has implementation approval but no Git publication or deployment authorization.
- Use `apply_patch` for edits. Run focused tests after each behavior slice, then a risk-proportional aggregate verification.
- The source result is not live-remediation proof. Runtime secret creation, coordinated container recreation, and negative probes remain separate deployment work.

## File responsibility map

- `DEVICE/iot-common/iot-common-base/.../RpcConstants.java`: shared RPC path/header constants.
- `DEVICE/iot-common/iot-common-base/.../RpcInternalTokenProperties.java`: pure credential holder, minimum-length check, constant-time match.
- `DEVICE/iot-common/iot-common-security/.../RpcInternalAccess.java`: servlet request access expression.
- `DEVICE/iot-common/iot-common-security/.../RpcInternalTokenRequestInterceptor.java`: RPC-only Feign Header injection.
- `DEVICE/iot-common/iot-common-security/.../YudaoSecurityAutoConfiguration.java`: binds `iot.rpc.*` and exposes access evaluator.
- `DEVICE/iot-common/iot-common-security/.../YudaoSecurityRpcAutoConfiguration.java`: registers Feign interceptor.
- `DEVICE/iot-common/iot-common-security/.../YudaoWebSecurityConfigurerAdapter.java`: first-match `/rpc-api/**` rule.
- `DEVICE/iot-common/iot-common-security/.../InnerAuthAspect.java`: removes trust in `from-source: inner`.
- Nine module `SecurityConfiguration.java` files: remove duplicated RPC anonymous rules.
- Gateway `RpcSecurityConfiguration.java`, `SecurityFrameworkUtils.java`, and `TokenAuthenticationFilter.java`: bind credential, sanitize inbound Header, inject WebClient Header, fail 503 on dependency/config failure.
- `DEVICE/docker-compose.yml`, `.scripts/docker/env.example`, and credential verifier files: required secret distribution contract.
- Focused JUnit/Node tests: prove each boundary independently.

### Task 1: Shared credential model

**Files:**
- Modify: `DEVICE/iot-common/iot-common-base/src/main/java/com/basiclab/iot/common/enums/RpcConstants.java`
- Create: `DEVICE/iot-common/iot-common-base/src/main/java/com/basiclab/iot/common/config/RpcInternalTokenProperties.java`
- Create: `DEVICE/iot-common/iot-common-base/src/test/java/com/basiclab/iot/common/config/RpcInternalTokenPropertiesTest.java`

- [x] **Step 1: Write the failing credential tests**

Create tests that prove null/short values are not configured, a 43-character value is configured, matching uses the exact value, and wrong values fail:

```java
class RpcInternalTokenPropertiesTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789a";

    @Test
    void requiresAtLeastFortyThreeCharacters() {
        RpcInternalTokenProperties properties = new RpcInternalTokenProperties();
        assertFalse(properties.isConfigured());
        properties.setInternalToken("x".repeat(42));
        assertFalse(properties.isConfigured());
        properties.setInternalToken(TOKEN);
        assertTrue(properties.isConfigured());
    }

    @Test
    void matchesOnlyTheConfiguredToken() {
        RpcInternalTokenProperties properties = new RpcInternalTokenProperties();
        properties.setInternalToken(TOKEN);
        assertTrue(properties.matches(TOKEN));
        assertFalse(properties.matches(TOKEN + "x"));
        assertFalse(properties.matches(null));
    }
}
```

- [x] **Step 2: Run the test and confirm RED**

Run:

```powershell
cd E:\yFeiEye\DEVICE
mvn '-Drevision=1.0.0' -pl iot-common/iot-common-base '-Dtest=RpcInternalTokenPropertiesTest' test
```

Expected: compilation failure because `RpcInternalTokenProperties` does not exist.

- [x] **Step 3: Add constants and the minimal credential implementation**

Add to `RpcConstants`:

```java
public static final String RPC_INTERNAL_TOKEN_HEADER = "X-Iot-Rpc-Token";
public static final int RPC_INTERNAL_TOKEN_MIN_LENGTH = 43;
```

Create the pure Java properties object without Spring annotations so both servlet and reactive applications can bind it:

```java
package com.basiclab.iot.common.config;

import com.basiclab.iot.common.enums.RpcConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class RpcInternalTokenProperties {

    private String internalToken;

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public boolean isConfigured() {
        return internalToken != null
                && internalToken.length() >= RpcConstants.RPC_INTERNAL_TOKEN_MIN_LENGTH;
    }

    public boolean matches(String candidate) {
        if (!isConfigured() || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [x] **Step 4: Run the focused test and confirm GREEN**

Run the Step 2 command again. Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [x] **Step 5: Record a non-mutating checkpoint**

Run:

```powershell
git diff --check -- DEVICE/iot-common/iot-common-base
git status --short -- DEVICE/iot-common/iot-common-base
```

Expected: only the intended constants, class, and test are reported; do not stage or commit.

### Task 2: Central servlet RPC authorization and `@InnerAuth` hardening

**Files:**
- Create: `DEVICE/iot-common/iot-common-security/src/main/java/com/basiclab/iot/common/service/RpcInternalAccess.java`
- Modify: `DEVICE/iot-common/iot-common-security/src/main/java/com/basiclab/iot/common/config/YudaoSecurityAutoConfiguration.java`
- Modify: `DEVICE/iot-common/iot-common-security/src/main/java/com/basiclab/iot/common/config/YudaoWebSecurityConfigurerAdapter.java`
- Modify: `DEVICE/iot-common/iot-common-security/src/main/java/com/basiclab/iot/common/aspect/InnerAuthAspect.java`
- Create: `DEVICE/iot-common/iot-common-security/src/test/java/com/basiclab/iot/common/service/RpcInternalAccessTest.java`
- Create: `DEVICE/iot-common/iot-common-security/src/test/java/com/basiclab/iot/common/aspect/InnerAuthAspectTest.java`

- [x] **Step 1: Write failing access-evaluator tests**

Cover correct, missing, wrong, and unconfigured Header values with `MockHttpServletRequest`:

```java
class RpcInternalAccessTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789a";

    @Test
    void allowsOnlyConfiguredMatchingHeader() {
        RpcInternalTokenProperties properties = new RpcInternalTokenProperties();
        properties.setInternalToken(TOKEN);
        RpcInternalAccess access = new RpcInternalAccess(properties);

        MockHttpServletRequest valid = new MockHttpServletRequest("GET", "/rpc-api/system/tenant/valid");
        valid.addHeader(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, TOKEN);
        assertTrue(access.isAllowed(valid));

        assertFalse(access.isAllowed(new MockHttpServletRequest("GET", "/rpc-api/system/tenant/valid")));
        MockHttpServletRequest wrong = new MockHttpServletRequest("GET", "/rpc-api/system/tenant/valid");
        wrong.addHeader(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, TOKEN + "x");
        assertFalse(access.isAllowed(wrong));
    }
}
```

For `InnerAuthAspect`, mock a join point and annotation. Prove `from-source: inner` alone throws `InnerAuthException`, the valid RPC Header proceeds, and `isUser=true` still fails without a verified `LoginUser`.

- [x] **Step 2: Run the new tests and confirm RED**

Run:

```powershell
cd E:\yFeiEye\DEVICE
mvn '-Drevision=1.0.0' -pl iot-common/iot-common-security -am '-Dtest=RpcInternalAccessTest,InnerAuthAspectTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: compilation failure because `RpcInternalAccess` and the new aspect constructor do not exist.

- [x] **Step 3: Bind properties and implement the evaluator**

In `YudaoSecurityAutoConfiguration`, expose the shared object and evaluator:

```java
@Bean
@ConfigurationProperties(prefix = "iot.rpc")
public RpcInternalTokenProperties rpcInternalTokenProperties() {
    return new RpcInternalTokenProperties();
}

@Bean("rpcInternalAccess")
public RpcInternalAccess rpcInternalAccess(RpcInternalTokenProperties properties) {
    return new RpcInternalAccess(properties);
}
```

Implement `RpcInternalAccess` as a focused request adapter:

```java
public class RpcInternalAccess {
    private final RpcInternalTokenProperties properties;

    public RpcInternalAccess(RpcInternalTokenProperties properties) {
        this.properties = properties;
    }

    public boolean isAllowed(HttpServletRequest request) {
        return properties.matches(request.getHeader(RpcConstants.RPC_INTERNAL_TOKEN_HEADER));
    }
}
```

- [x] **Step 4: Make `/rpc-api/**` the first authorization matcher**

In `YudaoWebSecurityConfigurerAdapter`, place this matcher before annotation-derived and configured permit lists:

```java
.antMatchers(RpcConstants.RPC_API_PREFIX + "/**")
.access("@rpcInternalAccess.isAllowed(request)")
```

Remove `/rpc-api/system/oauth2/token/check` from the captcha `permitAll` matcher, but retain `TokenAuthenticationFilter.shouldNotFilter(...)` so the token-check endpoint does not recursively validate the user token it is checking.

- [x] **Step 5: Replace forgeable `@InnerAuth` semantics**

Inject `RpcInternalAccess` into `InnerAuthAspect`. Reject the request unless the service Header is valid. For `isUser=true`, require `SecurityFrameworkUtils.getLoginUser() != null`; do not read `from-source`, `user_id`, `username`, or `login-user` as proof.

- [x] **Step 6: Run tests and compile the common security module**

Run:

```powershell
cd E:\yFeiEye\DEVICE
mvn '-Drevision=1.0.0' -pl iot-common/iot-common-security -am '-Dtest=RpcInternalTokenPropertiesTest,RpcInternalAccessTest,InnerAuthAspectTest,TokenAuthenticationFilterTest,SecurityFrameworkServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: all selected tests pass and `/rpc-api/system/oauth2/token/check` remains covered by the recursion test.

### Task 3: RPC-only Feign Header injection

**Files:**
- Create: `DEVICE/iot-common/iot-common-security/src/main/java/com/basiclab/iot/common/feign/RpcInternalTokenRequestInterceptor.java`
- Modify: `DEVICE/iot-common/iot-common-security/src/main/java/com/basiclab/iot/common/config/YudaoSecurityRpcAutoConfiguration.java`
- Create: `DEVICE/iot-common/iot-common-security/src/test/java/com/basiclab/iot/common/feign/RpcInternalTokenRequestInterceptorTest.java`

- [x] **Step 1: Write failing Feign tests**

Use `RequestTemplate` to prove an existing forged Header is overwritten for `/rpc-api/system/tenant/valid`, non-RPC `/message/template/get` has no Header, and the interceptor works without any servlet or user context.

```java
@Test
void injectsOnlyForRpcPathsAndOverwritesCallerValue() {
    RpcInternalTokenProperties properties = configuredProperties();
    RpcInternalTokenRequestInterceptor interceptor = new RpcInternalTokenRequestInterceptor(properties);
    RequestTemplate rpc = new RequestTemplate().uri("/rpc-api/system/tenant/valid");
    rpc.header(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, "forged");
    interceptor.apply(rpc);
    assertEquals(List.of(TOKEN), new ArrayList<>(rpc.headers()
            .get(RpcConstants.RPC_INTERNAL_TOKEN_HEADER)));

    RequestTemplate ordinary = new RequestTemplate().uri("/message/template/get");
    interceptor.apply(ordinary);
    assertFalse(ordinary.headers().containsKey(RpcConstants.RPC_INTERNAL_TOKEN_HEADER));
}
```

- [x] **Step 2: Run the test and confirm RED**

Run:

```powershell
cd E:\yFeiEye\DEVICE
mvn '-Drevision=1.0.0' -pl iot-common/iot-common-security -am '-Dtest=RpcInternalTokenRequestInterceptorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: compilation failure because the interceptor does not exist.

- [x] **Step 3: Implement and register the interceptor**

```java
public class RpcInternalTokenRequestInterceptor implements RequestInterceptor {
    private final RpcInternalTokenProperties properties;

    public RpcInternalTokenRequestInterceptor(RpcInternalTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public void apply(RequestTemplate template) {
        String path = template.path();
        if (path == null || !(path.equals(RpcConstants.RPC_API_PREFIX)
                || path.startsWith(RpcConstants.RPC_API_PREFIX + "/"))) {
            return;
        }
        template.removeHeader(RpcConstants.RPC_INTERNAL_TOKEN_HEADER);
        if (properties.isConfigured()) {
            template.header(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, properties.getInternalToken());
        }
    }
}
```

Register it as a bean in `YudaoSecurityRpcAutoConfiguration`; do not add it to the legacy `FeignRequestInterceptor`, which copies user/request fields and has a different responsibility.

- [x] **Step 4: Run Feign and common security tests**

Run the Step 2 command, then the Task 2 aggregate command. Expected: all pass.

### Task 4: Gateway WebClient credential and inbound Header sanitization

**Files:**
- Create: `DEVICE/iot-gateway/src/main/java/com/basiclab/iot/gateway/filter/security/RpcSecurityConfiguration.java`
- Modify: `DEVICE/iot-gateway/src/main/java/com/basiclab/iot/gateway/util/SecurityFrameworkUtils.java`
- Modify: `DEVICE/iot-gateway/src/main/java/com/basiclab/iot/gateway/filter/security/TokenAuthenticationFilter.java`
- Modify: `DEVICE/iot-gateway/src/test/java/com/basiclab/iot/gateway/filter/security/TokenAuthenticationFilterTest.java`

- [x] **Step 1: Add failing Gateway tests**

Extend `TokenAuthenticationFilterTest` with:

- an external request containing both `login-user` and `X-Iot-Rpc-Token`, asserting both are absent from the forwarded exchange;
- a request with `Authorization` and unconfigured RPC properties, asserting the chain is not called and the response is HTTP 503;
- a mocked load-balancer filter that captures the system-server `ClientRequest`, asserting the configured RPC Header is present;
- an invalid user-token response from system-server, asserting HTTP 401 rather than 503.

- [x] **Step 2: Run Gateway tests and confirm RED**

Run:

```powershell
cd E:\yFeiEye\DEVICE
mvn '-Drevision=1.0.0' -pl iot-gateway -am '-Dtest=TokenAuthenticationFilterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: constructor/signature assertions fail because Gateway does not yet bind or send the RPC credential.

- [x] **Step 3: Bind Gateway properties and sanitize external headers**

Create `RpcSecurityConfiguration` with a `@ConfigurationProperties(prefix = "iot.rpc")` bean returning `RpcInternalTokenProperties`.

Replace the single-header sanitization helper with a method that always removes both untrusted headers:

```java
public static ServerWebExchange removeUntrustedIdentityHeaders(ServerWebExchange exchange) {
    ServerHttpRequest request = exchange.getRequest().mutate()
            .headers(headers -> {
                headers.remove(LOGIN_USER_HEADER);
                headers.remove(RpcConstants.RPC_INTERNAL_TOKEN_HEADER);
            }).build();
    return exchange.mutate().request(request).build();
}
```

- [x] **Step 4: Inject WebClient Header and distinguish 401/503**

Inject `RpcInternalTokenProperties` into `TokenAuthenticationFilter`. Before the WebClient call, fail with an internal dependency error when the credential is unconfigured. For configured calls:

```java
return webClient.get()
        .uri(OAuth2TokenApi.URL_CHECK,
                uriBuilder -> uriBuilder.queryParam("accessToken", token).build())
        .header(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, rpcProperties.getInternalToken())
        .headers(headers -> WebFrameworkUtils.setTenantIdHeader(tenantId, headers))
        .retrieve()
        .bodyToMono(String.class);
```

Map configuration/upstream exceptions to an HTTP 503 `CommonResult`; keep an invalid token response mapped to the existing HTTP 401 result.

- [x] **Step 5: Run Gateway tests**

Run the Step 2 command. Expected: all existing three tests and all new Gateway RPC tests pass.

### Task 5: Remove nine module RPC anonymous rules

**Files:**
- Modify: `DEVICE/iot-visualize/iot-visualize-biz/src/main/java/com/basiclab/iot/visualize/framework/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-dataset/iot-dataset-biz/src/main/java/com/basiclab/iot/dataset/framework/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-infra/iot-infra-biz/src/main/java/com/basiclab/iot/infra/framework/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/framework/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/framework/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-tdengine/iot-tdengine-biz/src/main/java/com/basiclab/iot/tdengine/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/framework/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-message/iot-message-biz/src/main/java/com/basiclab/iot/message/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/framework/security/config/SecurityConfiguration.java`

- [x] **Step 1: Capture the failing source contract**

Run:

```powershell
rg -n 'ApiConstants\.PREFIX \+ "/\*\*"\)\.permitAll' DEVICE --glob 'SecurityConfiguration.java'
```

Expected before the change: nine matching rules, including message's erroneous system prefix.

- [x] **Step 2: Delete only the RPC matcher statements and now-unused imports**

Retain Swagger, health, Druid, bootstrap, and all explicitly reviewed non-RPC rules. In message, remove the incorrect `com.basiclab.iot.system.enums.ApiConstants` import. Do not modify iot-file's two upload rules.

- [x] **Step 3: Re-run the source contract**

Expected: no matches. Also run:

```powershell
rg -n 'rpc-api.*permitAll|permitAll.*rpc-api' DEVICE --glob '*.java'
```

Expected: no RPC anonymous rule, including the OAuth2 token-check endpoint.

- [x] **Step 4: Compile all affected servlet services**

Run:

```powershell
cd E:\yFeiEye\DEVICE
mvn '-Drevision=1.0.0' -pl iot-visualize/iot-visualize-biz,iot-dataset/iot-dataset-biz,iot-infra/iot-infra-biz,iot-device/iot-device-biz,iot-node/iot-node-biz,iot-tdengine/iot-tdengine-biz,iot-system/iot-system-biz,iot-message/iot-message-biz,iot-sink/iot-sink-biz -am -DskipTests compile
```

Expected: Reactor `BUILD SUCCESS`.

### Task 6: Compose secret distribution and repository gate

**Files:**
- Modify: `DEVICE/docker-compose.yml`
- Modify: `.scripts/docker/env.example`
- Modify: `.scripts/verify-device-credential-config.mjs`
- Modify: `.scripts/verify-device-credential-config.test.mjs`

- [x] **Step 1: Add a failing verifier test for required service coverage**

Export a helper that accepts the Compose service-environment map, required services, and required variable, and returns missing service names. Test that a fixture missing the token on one service reports exactly that service.

```javascript
test('RPC token must reach every DEVICE caller and server', () => {
  const services = collectComposeServiceEnvironmentNames(`
services:
  iot-gateway:
    environment:
      - IOT_RPC_INTERNAL_TOKEN=\${IOT_RPC_INTERNAL_TOKEN:?required}
  iot-system:
    environment:
      - POSTGRES_PASSWORD=\${POSTGRES_PASSWORD:?required}
`);
  assert.deepEqual(
    findServicesMissingEnvironment(
      services,
      ['iot-gateway', 'iot-system'],
      'IOT_RPC_INTERNAL_TOKEN',
    ),
    ['iot-system'],
  );
});
```

- [x] **Step 2: Run the Node test and confirm RED**

Run:

```powershell
node --test E:\yFeiEye\.scripts\verify-device-credential-config.test.mjs
```

Expected: import/definition failure for `findServicesMissingEnvironment`.

- [x] **Step 3: Implement the verifier contract**

Add the helper and make the main verifier require `IOT_RPC_INTERNAL_TOKEN` on exactly these services:

```javascript
const RPC_TOKEN_SERVICES = [
  'iot-gateway', 'iot-system', 'iot-infra', 'iot-device', 'iot-dataset',
  'iot-node', 'iot-visualize', 'iot-tdengine', 'iot-file', 'iot-message', 'iot-sink',
];
```

Also require `IOT_RPC_INTERNAL_TOKEN` to exist in `.scripts/docker/env.example`. Findings name the missing service/variable but never print a value.

- [x] **Step 4: Add the empty declaration and required Compose pass-through**

Add exactly one empty example declaration:

```dotenv
IOT_RPC_INTERNAL_TOKEN=
```

For each of the 11 service environment blocks add:

```yaml
- IOT_RPC_INTERNAL_TOKEN=${IOT_RPC_INTERNAL_TOKEN:?IOT_RPC_INTERNAL_TOKEN is required}
```

Do not add a default or real token.

- [x] **Step 5: Run verifier tests and the real verifier**

Run:

```powershell
node --test E:\yFeiEye\.scripts\verify-device-credential-config.test.mjs
node E:\yFeiEye\.scripts\verify-device-credential-config.mjs
```

Expected: tests pass and the real command prints `DEVICE_CREDENTIAL_CONFIG_OK`.

- [x] **Step 6: Validate Compose expansion and all deployment-profile selectors**

DEVICE Compose has one actual optional profile, `tdengine`; mini/standard/full service selection is implemented by `.scripts/docker/deploy_profile.sh`, not by three Compose profiles. First expand the base and `tdengine` variants with temporary process-scoped values only:

```powershell
$composePath = 'E:\yFeiEye\DEVICE\docker-compose.yml'
$composeText = Get-Content -LiteralPath $composePath -Raw
$requiredNames = [regex]::Matches(
  $composeText,
  '\$\{([A-Z_][A-Z0-9_]*):\?[^}]*\}',
) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
foreach ($name in $requiredNames) {
  [Environment]::SetEnvironmentVariable($name, 'validation-only', 'Process')
}
$tokenBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($tokenBytes)
$rng.Dispose()
$env:IOT_RPC_INTERNAL_TOKEN = -join ($tokenBytes | ForEach-Object { $_.ToString('x2') })
docker compose -f $composePath config --quiet
docker compose -f $composePath --profile tdengine config --quiet
```

Then assert the three deployment selectors without calling any function that writes `.deploy_profile`:

```powershell
bash -lc 'set -euo pipefail; source .scripts/docker/deploy_profile.sh; EASYAIOT_DEPLOY_PROFILE=mini; test "$(device_enabled_services)" = "iot-system"; test "$(device_skipped_services)" = "iot-gateway iot-infra iot-device iot-dataset iot-node iot-visualize iot-file iot-message iot-gb28181 iot-tdengine iot-sink"; test -z "$(device_compose_profile_flags)"; EASYAIOT_DEPLOY_PROFILE=standard; test -z "$(device_enabled_services)"; test "$(device_skipped_services)" = "iot-device iot-tdengine iot-visualize"; test -z "$(device_compose_profile_flags)"; EASYAIOT_DEPLOY_PROFILE=full; test -z "$(device_enabled_services)"; test -z "$(device_skipped_services)"; test "$(device_compose_profile_flags)" = "--profile tdengine"'
```

Expected: both Compose expansions and all selector assertions exit zero. Record only success/failure; never print expanded configuration or any token value.

### Task 7: Requirement-level verification and report update

**Files:**
- Modify: `docs/code-review/2026-07-30-全项目代码评审报告.md`
- Modify: `docs/superpowers/specs/2026-08-01-device-internal-rpc-auth-design.md` only if implementation reveals a factual contract correction.

- [x] **Step 1: Run all focused Java and Node tests**

Run the test commands from Tasks 1–6, plus the existing security configuration tests for system, sink, device, node, message, GB28181, and gateway. Expected: all selected test classes pass.

- [x] **Step 2: Run static security invariants**

Verify:

```powershell
rg -n 'rpc-api.*permitAll|permitAll.*rpc-api' DEVICE --glob '*.java'
rg -n 'from-source|FROM_SOURCE' DEVICE/iot-common/iot-common-security/src/main/java --glob '*.java'
rg -n 'IOT_RPC_INTERNAL_TOKEN' DEVICE/docker-compose.yml .scripts/docker/env.example
```

Expected:

- zero RPC `permitAll` matches;
- `InnerAuthAspect` no longer reads `from-source` (legacy constants may remain unused);
- 11 Compose pass-throughs plus one empty example declaration.

- [x] **Step 3: Run affected Reactor compile and formatting checks**

Run the Task 5 compile, `git diff --check` on all touched paths, and check Java/YAML/Markdown files for required LF and UTF-8 without BOM. Expected: all pass; unrelated baseline failures are reported separately and not “fixed” by editing unrelated files.

- [x] **Step 4: Update the code-review report precisely**

Change the residual `/rpc-api/** permitAll` finding to source-fixed only after tests and invariants pass. Record:

- 46 implemented RPC mappings protected centrally;
- Gateway WebClient and Feign background-call compatibility evidence;
- `from-source: inner` no longer authenticates;
- source/config verification completed;
- runtime token generation, coordinated container recreation, direct-port negative probes, and live behavior remain unverified until deployment.

- [x] **Step 5: Final completion audit against the active objective**

Re-check every original item: sink, device, global auth, AI `.pt`, TaskManager classification/hardening, APP/WEB, NODE/EDGE, credential tracking/history, report corrections, and 4,875 deploy files. Keep the goal incomplete if external rotation/history rewrite/commit/push/deployment evidence is still absent; report source completion separately.

- [x] **Step 6: Record the final worktree evidence without staging**

Run:

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
git diff --check
```

Report branch, HEAD, relevant changed files, tests, and remaining external actions. Do not commit, push, deploy, rotate production secrets, or rewrite Git history without separate authority.
