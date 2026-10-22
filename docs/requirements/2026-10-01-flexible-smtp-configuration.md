# 多邮箱服务商 SMTP 配置补齐

## 目标

在现有通知中心发件池上补齐常见邮箱服务商的快捷配置、可编辑传输参数、发信前测试能力，并移除 10 个发件方的固定上限。

## 范围

- 提供 Gmail、Microsoft 365、139 邮箱和自定义 SMTP 配置预设；预设只填入可编辑初始值，不锁定实际配置。
- 每个发件方独立配置认证方式、SMTP AUTH 机制、SSL/STARTTLS、STARTTLS 强制、TLS 协议、EHLO 名称和连接/读取/写入超时。
- 支持密码/授权码认证，以及 Google 和 Microsoft 365 的 OAuth2 refresh-token 流程；OAuth Token Endpoint 固定为服务商官方 HTTPS 地址，不接受任意 URL。
- 提供受 `notification:sender:update` 与现有组织作用域授权保护的单发件方测试邮件接口；只使用草稿，不保存配置。
- 移除发件池 10 条数量限制；允许相同 From 地址通过不同 SMTP 账号或服务商发送。
- 将 MySQL 配置列扩为 `MEDIUMTEXT`；PostgreSQL/H2 的 `TEXT` 本身不设 64 KiB 上限。应用回滚时保留列扩容，避免误截断较大的发件池。
- Kubernetes Secret `e-report-notification` 的 `MAIL_SENDER_POOL_JSON` 通过只读 Volume 提供部署默认池；后台可另行维护本级账号，并选择仅继承、仅本级或共同使用。Secret 投影内容变化后按请求重新读取；后台账号保存在租户/组织配置表。策略与升级行为见[组织发件池策略](2026-10-20-mail-sender-pool-policy.md)。
- 保持旧单发件方 JSON、租户默认继承、区县覆盖、密钥不回显和轮询语义。
- 将 Backend、WEB、APP 的正式版本契约提升到 `6.2.0`。

## 非目标

- 不在此变更中自动开启测试/生产通知开关。
- 不承诺 SMTP 接受后一定进入收件箱；测试邮件只验证 SMTP 认证与服务器接受发送。
- 不支持任意 SMTP 私有 JavaMail 属性或任意 OAuth Token Endpoint，避免不受控协议行为和 SSRF。

## 影响文件

- Backend 发件配置 DTO/Service、SMTP sender factory、OAuth token provider、测试邮件 Service/Controller、Redis 轮询测试。
- WEB 通知设置表单、服务商预设、发件测试交互和相应单元测试。
- 版本契约文件及本需求记录。

## 验收标准

- 11 个及以上发件方可以保存、重新读取和轮询；API/WEB 不再将数量限定为 10。
- 每个账户能独立使用密码认证或受支持的 OAuth2，并能选择受支持的 SMTP AUTH 机制和传输参数。
- 选择 STARTTLS 时默认强制 STARTTLS；TLS 身份校验开启，测试覆盖 TLS 配置映射。
- 草稿测试不持久化配置、不回传或记录任何凭据；权限不足和组织越权会被拒绝。
- OAuth2 只访问固定官方 HTTPS Token Endpoint，缓存受限且不记录 access/refresh token。
- 旧配置仍可读取；密码留空保存时保留已有密钥。
- `managedExternally` 只描述有效池是否来自部署默认，不限制后台维护本级配置。继承账号可按 ID 测试，但不能由客户端改写连接目标；OAuth 刷新凭据回写账号实际来源，部署默认仍由 Secret 维护。
- Backend 测试、WEB lint/test/build、版本契约校验通过；数据库迁移在 MySQL/H2 运行路径通过验证。

## 验证

- 单元测试覆盖 SMTP 参数映射、11 个以上发件方、兼容读取、密钥掩码、OAuth refresh、失败脱敏、Redis 轮询及回退。
- Controller/Service 测试覆盖测试邮件不落库、权限边界和错误处理。

## 服务商默认配置依据

- Gmail SMTP 使用 465 SSL 或 587 STARTTLS；OAuth2 与授权码认证按照 Google 官方支持范围配置。
- Microsoft 365 SMTP AUTH 常用 587，现代认证使用 OAuth；刷新时返回的新 refresh token 需要替换存储值。
- Microsoft 365 OAuth 默认 delegated scope 使用 `https://outlook.office.com/SMTP.Send offline_access`；管理员可按邮箱租户策略自定义 scope。
- 139 邮箱预设采用官方配置页的 `smtp.139.com:465` SSL。
- 自定义 SMTP 保留全部手动编辑入口；QQ、163 等具体账号设置以各自控制台当前显示为准，不猜测预设参数。

参考：[Gmail SMTP 配置](https://support.google.com/a/answer/176600?authuser=2&hl=en)、[Google OAuth SMTP](https://developers.google.com/workspace/gmail/imap/imap-smtp)、[Microsoft SMTP AUTH](https://learn.microsoft.com/en-us/exchange/clients-and-mobile-in-exchange-online/authenticated-client-smtp-submission)、[Microsoft Refresh Token](https://learn.microsoft.com/en-us/entra/identity-platform/refresh-tokens)、[139 邮箱服务器设置](https://help.mail.10086.cn/statichtml/6/Content/1454.html)、[Angus Mail SMTP 属性](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html)。
- 本地 Backend 全量测试与 WEB test/lint/build、自动测试候选发布分别记录；生产升级等待测试验收。
