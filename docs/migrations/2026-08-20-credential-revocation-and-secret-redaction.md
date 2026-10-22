# 凭据统一撤销与敏感配置 write-only 迁移

候选版本：`2.5.5`  
Liquibase：`2026-08-20-052-credential-revocation.sql`  
状态：代码、迁移与自动化验证已完成，未部署测试或生产环境。

## 行为变化

- `GET /api/admin/configs` 与单项查询对敏感配置返回 `configValue=null`，同时返回 `configured` 和固定 `displayValue`；搜索不再匹配敏感配置原值。
- 更新敏感配置支持 `retainSensitiveValue=true` 且 `configValue=null` 表示保留当前有效值；该标志不能用于非敏感项，也不能与新值同时提交。
- 本人修改密码与管理员重置密码均通过 `CredentialRevocationService` 统一撤销全部活动会话、全部尚未撤销 API Token，并撤销当前受信设备状态。
- 设备信任撤销使用 `REAUTHENTICATION_REQUIRED`，防止把合法 APP 安装永久封死；完成新密码认证且安装实例、公钥均一致时恢复信任。人工 `REVOKED` 状态不可自动恢复。

## 数据迁移

- 将 `trusted_devices.status` 从 `VARCHAR(20)` 扩展为 `VARCHAR(32)`，以容纳 `REAUTHENTICATION_REQUIRED`。
- 新增可空列 `trusted_devices.revoke_reason VARCHAR(255)`，用于记录撤销来源。
- 迁移不主动修改历史设备、会话或 Token；只有后续真实密码事件才触发对应账号的统一撤销。

## 兼容性与回滚

- 旧 WEB/APP 不会再获得敏感原值；若旧客户端把空值作为新值提交，后端会明确拒绝而不会清空秘密。Backend 与 WEB/APP 仍应使用同一 `2.5.5` 制品切换，以提供“留空保留”的完整交互。
- 应用回滚前，将 `REAUTHENTICATION_REQUIRED` 设备转为 `REVOKED`，再回滚状态列长度和 `revoke_reason` 列；已撤销的 Session 和 API Token 不恢复。
- 建议回滚点为迁移前的 `trusted_devices`、`auth_sessions`、`api_tokens` 精确备份；不恢复任何已确认发生密码变更账号的历史凭据。

## 验收

1. 在配置值中放入唯一测试秘密，列表、单项查询、搜索结果和日志均不得出现原文。
2. 编辑敏感配置留空后原值保持不变；输入新值后只可通过功能行为验证已生效，查询仍不回显。
3. 本人修改密码和管理员重置密码后，旧 WEB Cookie、APP Access/Refresh Token、API Token 均立即失效。
4. 原受信 APP 使用新密码重新登录后恢复设备信任；人工撤销设备仍拒绝登录。
5. Liquibase MySQL/H2 迁移、后端全量测试、WEB/APP 构建和版本契约全部通过。

## `2.5.5` 验证记录

- 后端：89 个测试类、277 项测试全部通过；覆盖 H2 全量 Liquibase、三类凭据真实批量更新、密码两条入口和敏感配置响应/搜索/保留操作。
- WEB：52 项测试、ESLint、Vite 生产构建通过；存在既有主 chunk 大于 500 kB 的构建提示，不影响本次功能正确性。
- APP：52 项测试、TypeScript、Expo Doctor 19/19、版本契约与构建资产安全检查通过；Expo SDK 57 的七项 patch 依赖已对齐当前兼容版本。
- 版本：Backend、WEB、APP 统一为 `2.5.5`，Android `versionCode=1005005`、`runtimeVersion=android-2.5.5-native1005005`。
- 依赖审计仍报告 Expo/Metro 构建链 `image-size` 的 8 项 high 传递告警；当前审计给出的自动修复会不合理地降级到 Expo 53。项目继续使用魔数门禁拒绝 ICNS/JXL/HEIF/AVIF 构建资产，本轮未执行破坏性 `npm audit fix --force`。
- 未构建 APK、未登记发布中心、未修改测试或生产运行态。
