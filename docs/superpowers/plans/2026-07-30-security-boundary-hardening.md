# yFeiEye 安全边界加固实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除本轮代码评审确认的匿名代码执行、危险反序列化、任意命令下发和凭据入库链路，并修复 APP、WEB、EDGE 三个已确认逻辑缺陷。

**Architecture:** 以“入口默认拒绝、身份不得由调用方伪造、危险执行面在授权后仍受沙箱约束”为主线。Java 微服务恢复 Spring Security 的 `anyRequest().authenticated()` 兜底；GraalJS 仅暴露明确允许的编解码 API并限制语句数；Web 上传面不再接受会触发 pickle 反序列化的 `.pt`；Agent、EDGE、TaskManager 对控制指令实行显式令牌、固定入口和路径约束。

**Tech Stack:** Java 21 / Spring Security / JUnit 5 / GraalJS 24.1.2、Python 3 / unittest、C++17 / cpp-httplib、Vue 3 / TypeScript、PowerShell / Git。

---

## 验收矩阵

| 风险 | 完成证据 |
|---|---|
| sink 匿名脚本执行 | 匿名请求被 Spring Security 拒绝；Graal 脚本无法访问 `Java.type`、文件、进程或任意宿主类；无限循环触发资源限制 |
| device 动态 Java 编译 | `/protocolCompileXcode/dynamicallyXcode` 不再调用编译器或执行用户代码 |
| 五模块 `/** permitAll` | node/device/dataset/sink/visualize 安全配置均无全局放行，RPC、健康检查和显式 bootstrap 例外除外 |
| `login-user` 伪造 | 仅携带调用方构造的 `login-user` 头不能建立 Spring Security 身份；有效 OAuth Token 仍可认证 |
| AI `.pt` RCE | 上传接口只接受 ONNX；验证器和检查点工具中不存在 `weights_only=False` 或默认不安全 `torch.load` |
| TaskManager 未鉴权控制 | 除 `/health` 外缺失或错误 Token 均返回 401；配置路径只能位于生成目录；INI 字段拒绝 CR/LF |
| NODE/EDGE 命令链 | bootstrap 必须提供独立 Token；Agent Token 常量时间比较；EDGE 不再回退执行 MQTT 自带命令；开放注册默认关闭 |
| APP/WEB/EDGE bug | APP 解密异常 reject；Axios 两个错误拦截器返回 rejected Promise；`edgeNodeId` 写入 state 后再落盘 |
| 凭据入库 | 本地 `.env` 停止被 Git 跟踪并有无敏感值模板；deploy-packages 停止跟踪；危险永久 Token 脚本失效 |
| 报告准确性 | P0 表补 sink/device，AI 修复建议改为拒绝不可信 `.pt`，TaskManager/APP/AES/v-html 等分级与证据边界修正 |

### Task 1: 恢复 Java 服务默认认证并拒绝伪造身份头

**Files:**
- Modify: `DEVICE/iot-common/iot-common-security/src/main/java/com/basiclab/iot/common/filter/TokenAuthenticationFilter.java`
- Modify: `DEVICE/iot-common/iot-common-security/src/test/java/com/basiclab/iot/common/filter/TokenAuthenticationFilterTest.java`
- Modify: `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/framework/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/framework/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-dataset/iot-dataset-biz/src/main/java/com/basiclab/iot/dataset/framework/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/framework/security/config/SecurityConfiguration.java`
- Modify: `DEVICE/iot-visualize/iot-visualize-biz/src/main/java/com/basiclab/iot/visualize/framework/security/config/SecurityConfiguration.java`
- Test: `DEVICE/iot-common/iot-common-security/src/test/java/com/basiclab/iot/common/filter/TokenAuthenticationFilterTest.java`

- [ ] **Step 1: 写伪造身份头回归测试**

```java
@Test
void forgedLoginUserHeaderWithoutTokenDoesNotAuthenticate() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/product-script/simulate");
    request.addHeader(SecurityFrameworkUtils.LOGIN_USER_HEADER,
            URLEncoder.encode("{\"id\":1}", StandardCharsets.UTF_8));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) ->
            assertNull(SecurityFrameworkUtils.getLoginUser()));
}
```

- [ ] **Step 2: 运行测试确认旧实现会信任伪造头**

Run:

```powershell
cd DEVICE
mvn '-Drevision=1.0.0' -pl iot-common/iot-common-security -Dtest=TokenAuthenticationFilterTest test
```

Expected: 新测试 FAIL，证明旧代码将 `login-user` 设为已认证身份。

- [ ] **Step 3: 以 OAuth Token 结果作为唯一外部身份**

`TokenAuthenticationFilter` 不再从请求头直接构造 `LoginUser`。先读取 `Authorization`，存在时调用 `OAuth2TokenApi`；无 Token 时保持匿名。Feign 已透传 `Authorization`，RPC 路径继续由各模块 `ApiConstants.PREFIX` 显式放行。

- [ ] **Step 4: 删除五个模块的 `antMatchers("/**").permitAll()`**

保留 Swagger、Actuator、RPC 和明确的 Agent/EDGE 协议端点规则，让公共安全配置最终的：

```java
.anyRequest().authenticated();
```

成为默认规则。

- [ ] **Step 5: 运行公共安全测试与五模块编译**

Run:

```powershell
cd DEVICE
mvn '-Drevision=1.0.0' -pl iot-common/iot-common-security,iot-node/iot-node-biz,iot-device/iot-device-biz,iot-dataset/iot-dataset-biz,iot-sink/iot-sink-biz,iot-visualize/iot-visualize-biz -am -DskipTests compile
mvn '-Drevision=1.0.0' -pl iot-common/iot-common-security -Dtest=TokenAuthenticationFilterTest test
```

Expected: 编译成功，过滤器测试全部 PASS。

### Task 2: 对 sink 产品脚本实行显式沙箱和执行预算

**Files:**
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/javascript/JsEngine.java`
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/javascript/JsScriptManager.java`
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/controller/ProductScriptController.java`
- Modify: `DEVICE/iot-sink/iot-sink-biz/pom.xml`
- Create: `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/javascript/JsEngineSecurityTest.java`

- [ ] **Step 1: 写宿主访问和无限循环失败测试**

```java
@Test
void rejectsHostClassLookup() {
    JsScriptManager.CheckResult result = manager.checkScript(
            "Java.type('java.lang.Runtime').getRuntime();"
            + REQUIRED_FUNCTIONS);
    assertFalse(result.isSuccess());
}

@Test
void stopsUnboundedLoop() {
    JsScriptManager.CheckResult result = manager.checkScript(
            "while (true) {}" + REQUIRED_FUNCTIONS);
    assertFalse(result.isSuccess());
}
```

- [ ] **Step 2: 用 `Context.Builder` 创建最小权限 Graal 引擎**

```java
HostAccess hostAccess = HostAccess.newBuilder(HostAccess.NONE)
        .allowArrayAccess(true)
        .allowMapAccess(true)
        .build();
Context.Builder context = Context.newBuilder("js")
        .allowHostAccess(hostAccess)
        .allowHostClassLookup(name -> false)
        .allowHostClassLoading(false)
        .allowIO(IOAccess.NONE)
        .allowNativeAccess(false)
        .allowCreateProcess(false)
        .allowCreateThread(false)
        .allowEnvironmentAccess(EnvironmentAccess.NONE)
        .allowPolyglotAccess(PolyglotAccess.NONE)
        .resourceLimits(ResourceLimits.newBuilder()
                .statementLimit(100_000L, source -> true)
                .build());
```

仅通过 `HostAccess.Builder.allowAccess(...)` 暴露 `JsUtilFunction`、`ReadBuffer`、`WriteBuffer` 的业务方法和必要的 Map 方法；删除 `Java.type` 全局导入。

- [ ] **Step 3: 限制脚本文本与输出规模并关闭废弃引擎**

脚本最大 256 KiB，输出最大 4 MiB；替换、删除和清空缓存时关闭 `GraalJSScriptEngine`，资源限制触发后的运行时从缓存移除。

- [ ] **Step 4: 为产品脚本管理接口增加认证约束**

服务级默认认证已覆盖 `/product-script/**`；控制器显式使用：

```java
@PreAuthorize("isAuthenticated()")
```

防止后续 URL 规则回归时再次匿名暴露。

- [ ] **Step 5: 运行 sink 安全测试**

Run:

```powershell
cd DEVICE
mvn '-Drevision=1.0.0' -pl iot-sink/iot-sink-biz -am -Dtest=JsEngineSecurityTest test
```

Expected: 模板脚本可运行，Host class lookup 和无限循环用例 PASS（即被拒绝）。

### Task 3: 永久关闭 device 动态 Java 编译接口

**Files:**
- Modify: `DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/controller/protocol/CompileXcodeController.java`
- Modify: `DEVICE/iot-device/iot-device-biz/pom.xml`
- Create: `DEVICE/iot-device/iot-device-biz/src/test/java/com/basiclab/iot/device/controller/protocol/CompileXcodeControllerTest.java`

- [ ] **Step 1: 写“代码不会执行”测试**

调用接口传入会设置系统属性的 Java 源码，断言响应为失败且系统属性未出现。

- [ ] **Step 2: 将端点改为固定拒绝**

```java
@PostMapping("/dynamicallyXcode")
public AjaxResult dynamicallyXcode() {
    return AjaxResult.error("动态 Java 编译接口已因安全原因永久禁用");
}
```

删除动态编译、字节码注入和 `executeMain` 相关导入。

- [ ] **Step 3: 运行 controller 测试**

Run:

```powershell
cd DEVICE
mvn '-Drevision=1.0.0' -pl iot-device/iot-device-biz -am -Dtest=CompileXcodeControllerTest test
```

Expected: PASS，源码扫描中该控制器不再引用 `DynamicLoaderEngine`、`ClassInjector` 或 `executeMain`。

### Task 4: 禁止 AI Web 进程加载不可信 `.pt`

**Files:**
- Modify: `AI/app/blueprints/model.py`
- Modify: `AI/app/utils/yolo_validator.py`
- Modify: `AI/services/ai_service/app/utils/yolo_validator.py`
- Modify: `AI/app/utils/train_checkpoint.py`
- Modify: `AI/test_yolo_validator.py`
- Modify: `AI/test_train_checkpoint_resume.py`
- Create: `AI/tests/test_model_upload_format_security.py`

- [ ] **Step 1: 写上传 `.pt` 被拒绝且不调用 torch/YOLO 的测试**

```python
def test_pt_upload_is_rejected_before_deserialization(client, monkeypatch):
    load = Mock(side_effect=AssertionError('torch.load must not run'))
    monkeypatch.setattr(yolo_validator.torch, 'load', load)
    response = client.post('/model/upload', data={'file': (BytesIO(b'pickle'), 'yolo8.pt')})
    assert response.status_code == 400
    load.assert_not_called()
```

- [ ] **Step 2: 上传接口只接受 ONNX**

扩展名校验改为 `.onnx`；错误消息明确说明 `.pt` 使用 pickle，不允许经 Web 上传，需在隔离环境转换为 ONNX 后再上传。

- [ ] **Step 3: 删除文件名捷径与不安全加载回退**

`validate_yolo_model` 不再凭 `original_filename` 返回成功；`.pt` 路径直接抛出安全错误。两个 yolo_validator 副本同步修改。

- [ ] **Step 4: 检查点元数据读取使用安全模式**

```python
return torch.load(
    checkpoint_path,
    map_location='cpu',
    weights_only=True,
)
```

安全模式无法读取的旧检查点按不可恢复处理，不再回退 `weights_only=False`。

- [ ] **Step 5: 运行 AI 定向测试和危险调用扫描**

Run:

```powershell
cd AI
python -m unittest test_yolo_validator.py test_train_checkpoint_resume.py
python -m pytest tests/test_model_upload_format_security.py
rg -n "weights_only\\s*=\\s*False|torch\\.load\\([^\\n]*\\)$" app services/ai_service/app
```

Expected: 测试 PASS；危险调用扫描无结果。

### Task 5: 加固 NODE bootstrap、Agent Token 和 EDGE 命令边界

**Files:**
- Modify: `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/controller/ComputeNodeController.java`
- Create: `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/security/PlatformAgentBootstrapAuthenticator.java`
- Modify: `DEVICE/iot-node/iot-node-biz/src/main/resources/application.yaml`
- Modify: `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/service/impl/EdgeNodeServiceImpl.java`
- Modify: `DEVICE/docker-compose.yml`
- Modify: `.scripts/docker/env.example`
- Modify: `.scripts/node/ensure_platform_agent.sh`
- Modify: `.scripts/node/ensure_platform_agent_centos7.sh`
- Modify: `NODE/run_agent.py`
- Modify: `NODE/agent_server.py`
- Modify: `EDGE/edge/workload_runner.py`
- Modify: `EDGE/edge/config.py`
- Create: `EDGE/tests/test_security_boundaries.py`

- [ ] **Step 1: bootstrap 缺 Token 时失败**

新增 `YFEIEYE_PLATFORM_AGENT_BOOTSTRAP_TOKEN`，控制器要求 `X-Bootstrap-Token`。服务端配置为空、请求为空或不匹配时返回 401；比较使用 `MessageDigest.isEqual`。

- [ ] **Step 2: Agent 和同步脚本发送 bootstrap Token**

`NODE/run_agent.py` 及两份平台 Agent 安装脚本只在 Token 非空时调用 bootstrap，并发送：

```python
headers={'X-Bootstrap-Token': PLATFORM_AGENT_BOOTSTRAP_TOKEN}
```

- [ ] **Step 3: Agent API Token 常量时间比较**

```python
if not hmac.compare_digest(
    request.headers.get('X-Agent-Token', ''),
    AGENT_TOKEN,
):
    abort(401)
```

- [ ] **Step 4: EDGE 关闭开放注册并删除任意命令回退**

Java 属性和 YAML 环境默认值均改为 `false`；`workload_runner.py` 在 `EDGE/runtime` 缺失时直接抛 `FileNotFoundError`，不读取 `deploy.command`、`deploy.workDir` 或 `deploy.env`。

- [ ] **Step 5: 修正 EDGE state 落盘顺序**

先把 `runtime.edgeNodeId` 写入 `state`，最后仅调用一次 `save_state(state)`。

- [ ] **Step 6: 运行 NODE/EDGE 测试**

Run:

```powershell
python -m unittest discover -s EDGE/tests -p 'test_*.py'
python -m py_compile NODE/run_agent.py NODE/agent_server.py EDGE/edge/config.py EDGE/edge/workload_runner.py
cd DEVICE
mvn '-Drevision=1.0.0' -pl iot-node/iot-node-biz -am test
```

Expected: 未带 bootstrap Token、开放注册默认关闭、MQTT 任意命令不执行、`edgeNodeId` 持久化用例全部 PASS。

### Task 6: TaskManager 默认拒绝未认证控制并约束配置

**Files:**
- Modify: `TASK/src/TaskManager.h`
- Modify: `TASK/src/TaskManager.cpp`
- Modify: `TASK/src/TaskManagerMain.cpp`
- Modify: `TASK/docker-compose.yml`
- Modify: `TASK/tests/test_taskmanager_api.py`

- [ ] **Step 1: 写 401、路径逃逸和换行注入测试**

测试 `/health` 可匿名；其余端点无 Token/错误 Token 返回 401；正确 Token 才能访问。`config_path` 指向生成目录外返回 400，任意 INI 字符串含 `\r` 或 `\n` 返回 400。

- [ ] **Step 2: 从环境加载必填 Token**

`TaskManagerOptions` 增加 `authToken`；`TaskManagerMain` 从 `TASK_MANAGER_TOKEN` 读取，为空时拒绝启动。Docker Compose 使用：

```yaml
environment:
  TASK_MANAGER_TOKEN: ${TASK_MANAGER_TOKEN:?TASK_MANAGER_TOKEN is required}
```

- [ ] **Step 3: 为控制端点统一认证**

除 `/health` 外，要求 `Authorization: Bearer ...` 或 `X-Task-Token`，常量时间比较，失败返回 401 JSON。

- [ ] **Step 4: 限制 INI 与启动路径**

拒绝所有字符串中的 CR/LF；`task/start` 只接受规范化后位于 `configDir` 下且文件名等于 `task<taskId>.ini` 的文件。

- [ ] **Step 5: 构建并运行 API 测试**

Run:

```powershell
cd TASK
cmake -S . -B build -DBUILD_TASK_RUNTIME=OFF -DBUILD_TASK_MANAGER=ON -DCMAKE_BUILD_TYPE=Release
cmake --build build --target TaskManager --config Release
$env:TASK_MANAGER_BIN=(Resolve-Path 'build/Release/TaskManager.exe')
python -m unittest tests.test_taskmanager_api -v
```

Expected: 全部 PASS。

### Task 7: 修复 APP、WEB、EDGE 已确认逻辑缺陷

**Files:**
- Modify: `APP/src/http/http.ts`
- Modify: `APP/env/.env`
- Modify: `WEB/src/api/axios.ts`
- Create: `WEB/tests/axiosInterceptorErrorPropagation.test.ts`
- Modify: `EDGE/edge/config.py`

- [ ] **Step 1: APP 解密异常显式 reject**

```typescript
catch (error) {
  console.error('响应数据解密失败:', error)
  return reject(new Error(`响应数据解密失败: ${(error as Error).message}`))
}
```

- [ ] **Step 2: 禁用客户端共享密钥方案**

`APP/env/.env` 将 `VITE_APP_API_ENCRYPT_ENABLE` 设为 `false`，请求/响应 Key 置空，同时清除默认登录密码。传输机密性由 TLS 提供。

- [ ] **Step 3: Axios 错误拦截器返回 rejected Promise**

两个错误处理函数均写成：

```typescript
(error: AxiosError) => Promise.reject(error)
```

- [ ] **Step 4: 运行前端验证**

Run:

```powershell
cd WEB
pnpm exec tsx tests/axiosInterceptorErrorPropagation.test.ts
pnpm type:check
cd ../APP
pnpm type-check
```

Expected: 定向测试与两端类型检查 PASS。

### Task 8: 停止跟踪凭据和部署产物，禁用永久 Token 工具

**Files:**
- Modify: `.gitignore`
- Create: `AI/env.example`
- Create: `VIDEO/env.example`
- Modify: `.scripts/docker/env.example`
- Modify: `.scripts/docker/set_permanent_token.sh`
- Stop tracking, preserve local file: `NODE/agent.env`
- Stop tracking, preserve local file: `AI/.env`
- Stop tracking, preserve local file: `VIDEO/.env`
- Stop tracking, preserve local file: `.scripts/docker/.env.docker`
- Stop tracking: `deploy-packages/**`

- [ ] **Step 1: 增加根忽略规则**

```gitignore
**/.env
**/.env.*
!**/*.env.example
!**/env.example
/deploy-packages/
/output/
/.artifacts/
```

保留明确需要随源码分发且不含敏感值的 APP 公共构建配置例外。

- [ ] **Step 2: 创建无敏感值模板**

模板只保留变量名、安全本机默认值和 `<required>`/空值，不复制现有凭据。

- [ ] **Step 3: 禁用永久 Token 脚本**

脚本改为 fail-closed，说明 OAuth Token 必须遵循服务端 TTL 和正常续期，不再连接 Redis、扫描 Token、输出 Token 或执行 `PERSIST`。

- [ ] **Step 4: 从索引移除本地密钥文件和部署产物**

先逐个用 `Resolve-Path` 核验目标位于 `E:\yFeiEye`，再使用仅影响 Git 索引、保留工作树文件的方式停止跟踪。不得删除本地 `.env`。

- [ ] **Step 5: 验证当前版本不再跟踪**

Run:

```powershell
git ls-files -- NODE/agent.env AI/.env VIDEO/.env .scripts/docker/.env.docker 'deploy-packages/**'
git check-ignore -v NODE/agent.env AI/.env VIDEO/.env .scripts/docker/.env.docker deploy-packages
```

Expected: 第一条无输出，第二条逐项显示命中的忽略规则。

外部密钥轮换和 Git 历史清理不通过本地源码改动冒充完成；需要在对应服务轮换后，再协调 `git filter-repo`/强制推送。

### Task 9: 修订评审报告并执行总验收

**Files:**
- Modify: `docs/code-review/2026-07-30-全项目代码评审报告.md`

- [ ] **Step 1: 修正报告事实**

补充 sink/server-side JS 与 device 动态编译；AI 建议改为拒绝 Web `.pt`；TaskManager 改为 P1/条件式 P0；APP Promise 改 P1；AES 改设计缺陷；移除已转义 `v-html` 的确认 XSS；修正 `WEB/.env.development.bak` 和未跟踪 JVM 日志。

- [ ] **Step 2: 标记源代码已修复与外部动作边界**

分开记录：源码修复、当前 Git 跟踪状态、密钥是否已轮换、历史是否已清理、生产端口和路由是否已验证。

- [ ] **Step 3: 执行残留扫描**

Run:

```powershell
rg -n 'antMatchers\\(\"/\\*\\*\"\\)\\.permitAll|weights_only\\s*=\\s*False|deploy\\.get\\(\"command\"\\)|DynamicLoaderEngine|ClassInjector|Promise\\.reject\\(' DEVICE AI EDGE WEB
git diff --check
git status --short --untracked-files=no
```

Expected: 不再出现本计划覆盖的危险模式；`Promise.reject` 仅以返回形式存在；无空白错误。

- [ ] **Step 4: 汇总验证**

分别报告 Java、AI、EDGE/NODE、TASK、WEB、APP 的实际命令与结果；未运行或因既有基线失败的检查必须单列，不能用某个局部测试替代全目标完成证明。
