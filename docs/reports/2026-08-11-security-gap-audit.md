# 1.0.44 安全差距审计

## 当前结论

初始审计基于 1.0.43 本地工作树，未对生产执行写操作。现有租户/组织/权限边界已有专门策略和测试，但认证凭据生命周期与客户端防护存在结构性风险，因此在 1.0.44 统一迁移而不是继续叠加长期 Token 兼容。

截至 2026-08-12，以下严重和高风险项已在 1.0.44 候选关闭，并完成 WEB、APP、后端、供应链和 Android 模拟器验证。1.0.44 未单独发布，其能力最终随 1.0.45 完成生产迁移、R2 正式包登记和强制更新上线。

## 收口过程中发现并修复的问题

- 空数据库启动：默认管理员缺少必填手机号会导致 Liquibase 后初始化失败；现在首次创建必须显式配置 `DEFAULT_ADMIN_PHONE`，已有数据库不改历史资料，并有独立 H2 集成测试。
- Android 请求摘要：Expo 新架构下原生 digest 无法稳定接收 `ArrayBuffer`；请求 body 统一规范化为自有字节数组并由 `js-sha256` 计算，模拟器会话接口已验证。
- 改密幽灵会话：客户端退出但服务端曾保留当前会话为 `ACTIVE`；现在密码修改撤销本人全部会话，双端清理本地凭据并要求重新登录，模拟器确认旧会话显示“已撤销”。
- 供应链证据：旧扫描报告可能与当前执行混杂；扫描脚本现在每次覆盖 release OSV 和两类 Gitleaks JSON，并用精确工具链基线阻止报告漂移。详见 `docs/reports/2026-08-12-supply-chain-security-audit.md`。

## 严重风险

| 风险 | 仓库证据 | 影响 | 处理 |
| --- | --- | --- | --- |
| WEB 长期 Token 可被脚本读取 | `AuthProvider.jsx`、`apiClient.js` 使用 `localStorage` Bearer Token | XSS 可直接窃取 7 天凭据 | 改为 HttpOnly Cookie，清理旧存储 |
| CSRF 全局关闭且同时使用 Cookie Session | `SecurityConfig` 调用 `csrf(...disable)`，登录创建 `HttpSession` | Cookie 认证写接口可能遭跨站请求 | Cookie CSRF + Origin/Referer 校验 |
| Token 无服务端会话状态 | `AuthTokenService` 只验证 HMAC、到期和授权版本 | 无法单设备撤销、Refresh 轮换或复用检测 | `AuthSession` + Redis 热态 |
| Android 请求可复制重放 | APP 仅发送 Bearer Token 和可预测时间型 request id | Token 泄露后可在其他设备重放 | Keystore 签名、nonce、body digest、设备绑定 |

## 高风险

| 风险 | 仓库证据 | 处理 |
| --- | --- | --- |
| 默认 Token 7 天且无 idle/absolute 分层 | `app.auth-token.ttl-seconds=604800` | APP 15 分钟 Access + 30 天 Refresh；WEB 12 小时 idle/7 天 absolute |
| 固定 remember-me key | `rememberMe.key("remember-me")` | 删除 remember-me 平行机制 |
| 登录只做 IP 固定窗口限流 | `RedisFixedWindowRateLimiter` 对 `remoteAddr` 每分钟 30 次 | 账号/网络/设备三维风险和有上限指数冷却 |
| 每次登录都要求可见数学验证码 | `LoginRequestDTO` 强制 `captchaAnswer` | 低风险无感 PoW，失败后才升级交互挑战 |
| BCrypt 默认成本且新密码仅 6 字符路径仍存在 | `PasswordEncoderConfig`、用户 DTO/导入路径 | Argon2id、15 字符、blocklist、成功登录升级旧哈希 |
| Android Release 默认不混淆、允许备份 | Gradle 属性默认 false；Manifest `allowBackup=true` | R8/资源压缩、禁止备份与明文流量 |

## 中风险与审计扩展项

- Swagger/API docs 当前无条件公开，应改为配置开关且生产默认关闭。
- CORS 允许列表由环境配置但 allowed headers 为 `*`，需收敛到实际协议头并保留上传/Range 所需头。
- 认证错误需继续保持账号存在性不可区分；诊断事件不得记录凭据、挑战答案和签名正文。
- `X-Request-Id` 使用 `Math.random` 只适合链路关联，不能作为安全 nonce；安全 nonce 必须用 CSPRNG。
- 生产默认 `AUTH_TOKEN_SECRET` 有开发回退值，应在非 test/local profile 启动时强制高熵 secret。
- Android Release 在缺少正式签名配置时回退 debug keystore，必须改为 Release 构建直接失败。
- 文件上传已有分片、SHA 和用途/租户校验；安全回归仍需覆盖跨租户 session id、Range 越界、内容类型伪装和压缩包炸弹风险。
- 权限与数据面已有 Delegation Boundary，但需补做匿名、普通用户、区县管理员、市管理员的固定 API 越权矩阵。

## 审计基线

- 密码与认证：NIST SP 800-63B-4、OWASP ASVS 5.0 V6。
- 会话：OWASP ASVS 5.0 V7。
- API 签名：RFC 9421，并吸收 RFC 9449 的 proof-of-possession 绑定思想。
- Android：OWASP MASVS AUTH/RESILIENCE、Android Keystore；Play Integrity 仅做侧载兼容的风险信号。

## 关闭条件

所有严重和高风险项必须有代码、迁移、自动化测试及真实 WEB/Android 验证证据；中风险项必须关闭或记录明确的剩余风险、责任人和后续版本。生产发布继续由用户另行指令触发。
