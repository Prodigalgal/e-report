# ADR：统一会话、设备证明与双端认证边界

## 状态

已批准，目标版本 1.0.44；生产部署暂停。

## 背景

现状同时创建 `HttpSession` 和 7 天自包含 Bearer Token，WEB 将 Token 放入 `localStorage`，后端全局关闭 CSRF，Android 请求没有设备密钥绑定或重放保护。继续叠加兼容层会让撤销、轮换和审计语义更混乱。

## 决策

### 统一服务端会话

引入 `AuthSession` 聚合，所有 WEB/APP 登录都创建会话记录。权限快照仍由现有 `authorization_version` 控制，会话负责凭据生命周期、设备绑定、撤销、空闲和绝对超时。MySQL 是会话事实来源；Redis 只承载挑战、风险计数、冷却和 nonce 等短期安全状态，当前版本不缓存会话事实。

### 双端凭据

- WEB：随机 256-bit opaque session secret，仅以 HttpOnly Cookie 传输，服务端仅保存摘要；采用 Cookie CSRF token 和 Origin/Referer 双校验。
- APP：15 分钟 HMAC Access Token 只承载 session id、membership id、tenant id、authorization version、device key id 和过期时间；Refresh Token 为随机 256-bit opaque 值并每次轮换。
- APP 对上一枚 Refresh Token 保留 30 秒网络重试恢复窗口；窗口外复用立即把整条会话标记为 `COMPROMISED`，不能用无限兼容窗口掩盖重放。
- 旧 v1/v2 Bearer Token 在 1.0.44 切换时整体失效，不保留长期双协议认证。公开升级接口继续可用。

### Android 设备证明

Android Keystore 生成不可导出的 P-256 密钥。`keyId=base64url(SHA-256(SPKI public key))`。设备记录同时包含随机安装实例摘要，以及经白名单和长度约束的厂商、品牌、型号、产品/硬件代号、CPU ABI、OS/安全补丁、屏幕、内存/存储档位、语言/时区、安装来源、应用签名和 root/模拟器/调试/Hook 风险信号。所有字段都不是永久硬件标识。

APP 认证请求采用 RFC 9421 思路的项目内明确子集：

```text
@method:POST
@target-uri:/api/work-orders?...
content-digest:sha-256=:...:
created:...
expires:...
nonce:...
session-id:...
```

签名使用 `SHA256withECDSA`。请求 body 摘要由锁定版本的 `js-sha256` 对规范化 `BodyInit` 字节计算，设备签名仍由 Android Keystore 原生模块执行，避免新架构下原生 digest 对 `ArrayBuffer` 兼容不一致。服务端执行规范化 URI、摘要、时间窗、session/key 绑定、公钥签名和 Redis nonce 原子占用校验。未签名或签名错误的 Android 已认证请求不进入 Controller。

### WEB 不做客户端长期密钥验签

浏览器脚本无法安全保管共享 secret 或私钥，所谓 WEB 请求验签无法抵御同一 XSS 上下文。WEB 的等价控制是 HttpOnly Cookie、CSRF、Origin/Referer、安全响应头、短会话和敏感操作再认证。

### 智能挑战

现有 Redis 一次性数学验证码和 PoW 重构为 `AuthRiskService + AuthChallengeProvider`。低风险只做后台 PoW；失败、陌生设备或网络异常时升级为数学验证码；处于冷却期时直接返回 429。外部 Turnstile 作为可插拔高风险 Provider，未配置 site/secret 时不启用，不在仓库或 APP 内写入 secret。

## 取舍

- 服务端会话校验增加一次数据库读取，但换取即时撤销、Refresh reuse 检测和设备治理。当前流量规模先保持单一事实来源，后续只有在指标证明必要时才引入可失效的会话缓存。
- Android 每请求 ECDSA 验签有 CPU 成本，但请求量与 10MB 出口规模可控；nonce TTL 和限长先于验签，避免无界 Redis/CPU 消耗。
- 不使用证书 Pinning：当前入口经 Cloudflare，证书轮换会造成不可控停服；TLS、设备签名和发布证书校验提供更可运维的边界。
- Play Integrity 先接入能力和观测接口，不作为 R2 侧载 APK 的硬门禁；否则侧载分发可能被误判为未由 Play 识别。

## 迁移

1. Liquibase 新增安全表、用户改密标记和索引，不修改工单事实表。
2. 部署 1.0.44 后不再接受旧 v1/v2 Token，所有用户重新登录。
3. 已知区县默认管理员登录后进入强制改密流程。
4. 空数据库首次创建默认管理员时要求 Secret 提供 `DEFAULT_ADMIN_PHONE`；已有用户数据不在启动时补写或覆盖。
5. WEB 清理旧认证 `localStorage`；APP 升级时清理旧 Token，生成 Keystore key 后重新登录；本人改密后双端都撤销全部会话并返回登录页。
6. 观察登录失败、挑战升级、签名拒绝和会话撤销数据，再决定是否启用外部 Turnstile/Play Integrity 强制策略。

## 参考

- NIST SP 800-63B-4：密码长度、blocklist、哈希和限流要求。
- OWASP ASVS 5.0 V6/V7：自适应反自动化、会话轮换、超时和撤销要求。
- RFC 9421：HTTP Message Signatures 的覆盖字段和时间/nonce 约束。
- RFC 9449：Proof-of-Possession Token 的设备密钥绑定思路。
- OWASP MASVS AUTH/RESILIENCE：移动认证和客户端加固控制。
