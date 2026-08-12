# DEVICE 内部 RPC 服务身份鉴权设计

## 状态

- 方案：A——部署级共享高强度服务令牌
- 设计批准：2026-08-01
- 实施状态：实施计划已确认，源码实施中

## 目标

为 DEVICE 内所有 `/rpc-api/**` 端点增加不可由普通外部请求伪造的服务身份，消除模块级 `permitAll` 造成的横向未鉴权访问，同时保持以下现有调用方式可用：

- Gateway 使用 `WebClient` 调用 system-server 的 OAuth2 Token 校验接口；
- DEVICE 服务使用 OpenFeign 调用 system、infra、dataset 等 RPC API；
- 定时任务、消息消费者和其他后台线程在没有用户 `Authorization` 时仍可执行合法 RPC；
- 携带用户 `Authorization` 的 RPC 继续保留经过 system-server 校验的用户上下文。

服务令牌只证明“调用方属于当前 DEVICE 信任域”，不生成用户身份、不授予后台管理角色，也不替代已有用户权限校验。

## 当前证据与边界

当前源码中共有 11 条相关放行规则，需区分为三类：

1. 9 个业务模块声明了 RPC 放行规则，其中 message 错误导入 system 的 `ApiConstants`，实际重复放行 `/rpc-api/system/**`。
2. 当前真正注册实现的 RPC 映射共 46 个：system 44 个、infra 1 个、dataset 1 个。
3. iot-file 的 `/sysFile/upload/**` 与 `/sysFile/uploadByBucket/**` 是公开上传路径，不属于 `/rpc-api/**`，不纳入本设计。

部分模块目前只声明 RPC 前缀而没有对应实现。本设计仍统一保护 `/rpc-api/**`，以免未来新增端点自动继承匿名访问。

现有 `@InnerAuth` 只比较调用方可自行构造的 `from-source: inner`，且仓库中没有实际使用点。该机制不能作为安全边界，必须同步改为验证真实服务令牌，避免未来被误用。

## 非目标

- 本轮不引入每服务独立密钥、调用方矩阵、请求签名、nonce 或 mTLS；这些属于后续缩小共享密钥爆炸半径的演进项。
- 不改变 iot-file 两个公开上传路径的产品契约；它们需要单独的上传鉴权与滥用风险评审。
- 不把内部服务令牌作为用户、租户或管理员身份来源。
- 不在源码、示例配置、测试夹具、日志或报告中写入真实令牌。
- 不把源码测试通过表述为已经部署；容器重建、运行版本和真实负向探测必须单独验证。

## 总体架构

### 1. 共享常量与配置

在公共模块中定义：

- Header：`X-Iot-Rpc-Token`
- 配置项：`iot.rpc.internal-token`（与环境变量 `IOT_RPC_INTERNAL_TOKEN` 直接使用 Spring Boot relaxed binding 对齐）
- 部署环境变量：`IOT_RPC_INTERNAL_TOKEN`
- RPC 路径前缀：继续使用 `RpcConstants.RPC_API_PREFIX`，即 `/rpc-api`

令牌必须来自至少 32 个随机字节，推荐使用 64 位十六进制字符串。Base64URL 编码时不得少于 43 个字符。运行时将少于 43 个字符的配置视为无效配置。

公共校验器只暴露以下职责：

- 判断配置是否有效；
- 使用恒定时间字节比较验证请求令牌；
- 返回已配置、未配置、匹配或不匹配的状态，但不返回、记录或拼接令牌值。

### 2. 服务端统一访问规则

`YudaoWebSecurityConfigurerAdapter` 在模块自定义规则之前注册统一规则：

```text
/rpc-api/** -> @rpcInternalAccess.isAllowed(request)
```

规则直接验证 `X-Iot-Rpc-Token`，不修改 `SecurityContext`，因此不会覆盖 `LoginUser`。执行顺序必须满足：

1. `TokenAuthenticationFilter` 可继续从 `Authorization` 构造已验证用户；
2. RPC 访问规则独立验证服务令牌；
3. 两者同时存在时，用户上下文保留，但 RPC 仍必须具有服务令牌；
4. 后台调用只有服务令牌时可通过 RPC 入口，但不会凭空获得用户权限。

同时执行以下收口：

- 从公共安全配置中移除 `/rpc-api/system/oauth2/token/check` 的显式 `permitAll`；保留 `TokenAuthenticationFilter.shouldNotFilter(...)`，只用于避免 Token 校验递归。
- 删除 visualize、dataset、infra、device、node、tdengine、system、message、sink 的 RPC `permitAll` 规则。
- 修正 message 对 system `ApiConstants` 的错误依赖。
- 继续保留明确的健康检查、验证码、Swagger 及其他经评审的非 RPC 例外。

请求未携带令牌、令牌不匹配或服务端配置无效时均返回 HTTP 403。外部响应不区分“未配置”和“错误令牌”，防止泄露配置状态；服务端仅记录不含令牌值的限频诊断日志。

### 3. OpenFeign 调用方

增加公共 Feign 请求拦截器，仅当请求模板路径以 `/rpc-api/` 开头时：

1. 清除调用上下文中可能存在的同名 Header；
2. 从受管配置读取令牌；
3. 配置有效时写入唯一的 `X-Iot-Rpc-Token`。

非 RPC Feign 调用不携带该令牌，避免把共享凭据扩散到普通内部业务接口。现有 `Authorization`、tenant-id 与用户上下文透传保持不变。

配置缺失或过短时，拦截器不发送 Header，由服务端统一拒绝；禁止回退到 `from-source: inner`、固定默认值或匿名调用。

### 4. Gateway WebClient 特例

Gateway 的 OAuth2 Token 校验使用 `WebClient`，不会经过 Feign 拦截器，因此必须在 `checkAccessToken(...)` 请求中显式添加 `X-Iot-Rpc-Token`。

Gateway 还必须在处理外部请求的最前段移除客户端传入的 `X-Iot-Rpc-Token`，与现有伪造 `login-user` Header 清理逻辑同级。Gateway 只在自身创建的内部 Token 校验请求中重新加入受管令牌。

若 Gateway 未配置有效服务令牌或 system-server 的内部校验调用不可用，应返回 HTTP 503，并将用户 Token 校验视为内部认证依赖不可用；不能继续把请求当作匿名请求放行。用户 Token 本身无效时仍返回 HTTP 401。

### 5. `@InnerAuth` 兼容加固

保留注解的源码兼容性，但修改切面语义：

- 不再信任 `from-source`；
- 使用同一个公共服务令牌校验器；
- `isUser=false` 只要求服务身份；
- `isUser=true` 还要求 `SecurityFrameworkUtils.getLoginUser()` 返回由有效 `Authorization` 构造的用户，不能检查原始 `user_id`、`username` 或 `login-user` Header。

仓库当前没有 `@InnerAuth` 使用点，因此该修改只封闭潜在误用，不改变已注册接口。

## 请求数据流

### 用户请求触发内部 RPC

```text
用户 Authorization
  -> Gateway 清理伪造内部 Header
  -> Gateway WebClient 携带受管 RPC Token 校验 Authorization
  -> system-server 验证 RPC Token
  -> Gateway 转发原用户 Authorization
  -> 业务服务重新校验用户 Authorization
  -> Feign 调用仅对 /rpc-api/** 附加受管 RPC Token
  -> 目标服务同时验证服务身份，并保留已验证用户上下文
```

### 后台线程触发内部 RPC

```text
定时任务 / 消费者 / 异步线程（无 Authorization）
  -> Feign 对 /rpc-api/** 附加受管 RPC Token
  -> 目标服务验证服务身份
  -> RPC 方法在无 LoginUser 上下文中执行
```

需要用户语义的 RPC 不得依赖后台调用；此类端点应使用 `@InnerAuth(isUser = true)` 或等价的显式用户约束。

## 配置与部署契约

`IOT_RPC_INTERNAL_TOKEN` 以空值形式加入 `.scripts/docker/env.example`，真实值只保存在未跟踪的运行环境文件或秘密管理系统中。

DEVICE Compose 必须把同一变量注入以下 11 个服务：

- iot-gateway
- iot-system
- iot-infra
- iot-device
- iot-dataset
- iot-node
- iot-visualize
- iot-tdengine
- iot-file
- iot-message
- iot-sink

Compose 使用 required 形式引用环境变量，缺失时拒绝生成配置。直接运行 Jar 时即使应用能启动，RPC 规则也必须失败关闭，不能因缺少配置退回匿名模式。

本轮只支持单个活动令牌。轮换时先在受控环境生成新令牌，再协调重建上述全部服务；不设计双令牌兼容窗口，避免永久扩大密钥接受面。

## 错误处理与可观测性

- 缺失、错误和过短请求令牌统一返回 HTTP 403，不回显原因或任何敏感信息。
- 服务端配置缺失或过短时，启动日志记录一次明确的配置错误，但绝不记录配置值。
- Gateway 内部 Token 校验依赖不可用时返回 HTTP 503，不把请求继续转发为匿名流量；用户 Token 无效仍返回 HTTP 401。
- Feign 调用失败沿用现有 fallback/error contract，不新增“匿名重试”。

## 测试设计

### 公共安全层

- 有效配置与正确 Header 允许访问。
- 缺失 Header、错误 Header、过短配置、空配置均拒绝。
- 比较逻辑使用恒定时间 API，源码契约测试禁止普通字符串相等比较。
- 带 context path 的请求仍正确识别 `/rpc-api/**`。
- 有效 RPC Token 不创建 `LoginUser`。
- 有效 RPC Token 与有效用户 Authorization 同时存在时保留用户上下文。
- `/rpc-api/system/oauth2/token/check` 不再匿名可达，但 Token 过滤器仍避免递归。

### Feign 与 Gateway

- Feign 对 RPC 路径写入且覆盖内部 Header。
- Feign 对非 RPC 路径不写入内部 Header。
- 后台无 Servlet/User 上下文时仍写入 RPC Header。
- Gateway 删除外部伪造的 RPC Header。
- Gateway WebClient 调用 Token 校验时使用受管令牌。
- Gateway 缺少有效配置时失败关闭。

### 模块和部署门禁

- 9 个模块不再存在 RPC `permitAll`。
- message 不再导入 system `ApiConstants` 作为自身安全规则。
- 46 个当前实现端点均受统一路径规则覆盖。
- credential-config verifier 检查 11 个服务的 Compose 透传和 env.example 声明。
- DEVICE 基础 Compose 与唯一的 `tdengine` Compose profile 均能使用进程级验证值展开；`deploy_profile.sh` 对 mini、standard、full 的服务选择与 profile 参数契约分别通过只读断言，且没有把真实值写入输出证据。
- Maven 聚焦测试覆盖 common-security、gateway、system、infra、dataset，并编译全部受影响 DEVICE 服务。

## 验收标准

以下条件必须全部成立：

1. 无内部令牌直接访问任意 `/rpc-api/**` 均被拒绝，包括宿主机暴露的 system-server 端口。
2. 错误内部令牌与伪造 `from-source: inner` 均不能绕过。
3. 正确内部令牌可完成 Gateway Token 校验、普通 Feign RPC 和无用户上下文后台 RPC。
4. 用户 Authorization 仍由 system-server 校验，且不因服务身份验证而丢失。
5. 源码搜索中 RPC `permitAll` 为零；iot-file 两个公开上传例外被单独报告，不伪装成已整改 RPC。
6. Compose 对 11 个服务强制传入 `IOT_RPC_INTERNAL_TOKEN`，env.example 只有空声明。
7. 聚焦测试、受影响 Reactor 编译、基础/`tdengine` Compose 静态展开、三种部署形态选择契约和凭据门禁全部通过。
8. 报告明确区分源码完成、未提交工作树、远端分支、部署版本与真实运行验证；没有部署证据时不得声称线上已修复。

## 上线与回滚原则

上线必须是协调发布：先在目标环境安全生成并保存令牌，再使用包含完整服务端与调用端改动的同一版本重建 11 个服务。不得只升级 system-server 或只升级 Gateway，否则会造成内部认证中断。

上线后至少执行：

- 无令牌和错误令牌的直接 RPC 负向探测；
- Gateway 正常登录/Token 校验；
- 一个带用户上下文的 Feign RPC；
- 一个后台无用户上下文的 RPC；
- 服务健康、错误日志和拒绝计数检查；
- 当前容器镜像/提交与源码版本对应关系核验。

回滚时回滚完整 DEVICE 版本集合，不回滚或公开真实令牌。旧版本若不识别该变量，可保留环境变量而不影响启动；回滚完成后仍需执行安全与健康验证。

## 已知风险与后续演进

共享令牌意味着任一持有令牌的服务被攻陷后，可访问同一信任域内的 RPC。这是本方案为快速封闭匿名横向访问接受的明确权衡。后续演进顺序为：

1. 每服务身份与调用方白名单；
2. 带时间戳和 nonce 的 HMAC 请求签名；
3. mTLS 或 Service Mesh 工作负载身份；
4. 网络策略限制业务端口仅对明确调用方开放。

这些演进不属于本次实现计划，不得为其预留未经需求验证的复杂抽象。
