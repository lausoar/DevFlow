# 第 4 批：企业级门槛

> 对应 [AUDIT_REPORT_2026-11.md](./AUDIT_REPORT_2026-11.md) 的第 4 批"企业级门槛"。
> 这一批的目标不是加功能，而是让平台**进得了中大型客户的采购清单**：
> 没有双因素、没有可验证的审计、没有可观测性，安全评审这一关就过不去。
> SSO/SCIM 未包含在本批（需要外部 IdP 联调，见文末）。

## 一、双因素认证（TOTP）

### 实现

`util/TotpUtil` 自己实现 RFC 6238，只依赖 JDK 的 HmacSHA1 与 commons-codec 的 Base32，
不引入完整 OTP 框架。校验窗口 ±1 个时间步（30s），容忍客户端时钟偏差；
窗口不放宽——窗口越大，6 位码被暴力猜中的概率越高。

**正确性由 RFC 测试向量保证**：`TotpUtilTest` 用 RFC 6238 附录 B 的已知密钥/时间断言
`T=59s → 287082`。这条测试很关键：如果只做"自己生成自己验"的闭环测试，
实现错了也会一起错，而用户拿真实验证器 App 扫码时会发现根本对不上。

### 登录流程

```
密码提交 → 密码正确且已启用 TOTP → 返回 { mfaRequired: true, mfaToken }
         → 同一接口再次提交 { mfaToken, mfaCode } → 返回正式 token
```

关键设计：

- **mfaToken 绑定 userId 且单次使用**：不能拿自己的凭证去完成别人的登录；
- **动态码错误也消耗凭证**：否则可以在 5 分钟窗口里无限次猜 6 位码；
- **动态码错误计入登录失败次数**：与密码失败共用防爆破计数，避免暴力尝试；
- **绑定必须先验证一次动态码**才真正启用——否则用户可能录入了一个自己扫不出来的密钥，
  下次登录就永久进不去；
- **关闭双因素需要当前动态码**：防止会话被劫持后攻击者直接把第二因子关掉。

已知限制：挑战凭证存在进程内 Map（5 分钟 TTL）。多实例部署需迁到 Redis，
与登录防爆破计数是同一类单机假设。

### 前端

设置页新增「双因素认证」卡片：启用 → 显示 Base32 密钥（可复制）与 otpauth 链接 →
输入动态码确认 → 启用。关闭同样需要动态码。登录页识别 `mfaRequired` 后原地切换为动态码输入。

## 二、密码策略

`util/PasswordPolicy` 统一了注册、改密、重置三处的强度校验（此前注册只校验长度 ≥6，
改密完全没校验）。

规则：≥8 位、至少两类字符（字母/数字）、拒绝常见弱口令、拒绝单一重复字符。
返回**全部**问题而不是第一条，前端可以一次性提示。

配套改动：

- **超管重置密码后置 `must_change_password=1`**，登录响应带 `mustChangePassword`，
  前端跳转到设置页并显示醒目提示。临时密码不应长期有效；
- 记录 `password_updated_at`（密码过期策略的锚点，本批不启用强制过期）；
- 改密时拒绝"新密码与旧密码相同"。

规则刻意保守（8 位而非 12 位、不强制特殊字符）：更严格会逼用户写便利贴，
比弱密码更危险。配合防爆破与双因素，这才是有效防线。

## 三、字段级脱敏

`util/Masking` + 企业成员接口按权限决定是否脱敏：

| 调用者权限 | 看到的邮箱 / 手机号 |
|-----------|---------------------|
| 有 `member:manage` / `member:invite` / `enterprise:manage` | 完整 |
| 其他企业成员 | `z***@example.com` / `138****5678` |

组织架构对全员可见是合理的，但联系方式不是。脱敏在服务端完成——
前端隐藏挡不住直接调接口。企业详情里内嵌的成员列表与部门树成员走同一口径，
避免从一个接口绕开脱敏。

## 四、审计防篡改

### 哈希链

每条 `activity_log` 增加 `prev_hash` 与 `record_hash`：

```
record_hash = SHA-256(prev_hash | id | userId | enterpriseId | projectId |
                      action | targetType | targetId | detail | createdAt)
```

- 链式写入加锁串行化——并发写会让 `prev_hash` 指向错误的前驱；
- `GET /enterprises/{id}/audit-logs/verify` 逐条重算并校验链接，
  返回是否连续、校验了多少条、断点在哪；
- 这是"**可发现篡改**"而不是"阻止篡改"：DBA 仍能改数据，但改完能被看出来。
  真正做到不可篡改需要外部存证（WORM 存储 / 第三方时间戳），属后续工作。

同时补了 `ip` 与 `user_agent` 字段（从请求上下文取），这是取证的基本信息。

### 导出

`GET /enterprises/{id}/audit-logs/export` 输出 CSV：带 UTF-8 BOM（Excel 中文不乱码）、
含记录哈希列（可离线核对）、字段做 CSV 转义。导出动作本身也记一条审计。

## 五、可观测性

此前 `SecurityConfig` 放行了 `/actuator/health`，但 pom 里根本没有 actuator —— 
健康探针实际 404，属于"看起来有、实际没有"的假可观测。本批补上：

- `spring-boot-starter-actuator` + `micrometer-registry-prometheus`；
- **管理端点走独立端口 9090**（`management.server.port`），不与业务端口共用：
  生产只把 9090 暴露给内网/监控，业务端口不对外提供 `/actuator`；
- 只暴露 `health,info,prometheus`；
- `management.endpoint.health.probes.enabled=true`，K8s liveness/readiness 可用；
- `management.endpoint.health.show-details` 默认 `never`（不向外部泄露依赖状态）。

## 六、验证

- 后端：`mvn compile` 通过；单测 **81 项**全绿（新增 `TotpUtilTest` 6 项、`PasswordPolicyTest` 7 项）；
- 前端：`tsc --noEmit` 通过；lint 错误数保持 16（均为存量，新文件 0 错误）；
- **未做运行时验证**：本机无 MySQL/Redis，双因素登录链路、审计哈希链、actuator 端口均未实际运行。

## 七、本批未包含（需单独评估）

- **SSO / SCIM**（OIDC / SAML / LDAP）：需要对接真实 IdP 才能验证，属于独立的联调项目；
- **禁用/删除成员后立即失效 token**：当前仍依赖 24h token 过期，需要在
  `JwtAuthFilter` 里回查用户状态，属性能与安全的权衡，应单独评估缓存方案；
- **审计日志外部存证**：当前只能发现篡改，不能阻止。
