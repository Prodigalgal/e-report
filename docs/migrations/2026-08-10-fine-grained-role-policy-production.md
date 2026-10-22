# 细粒度角色与可自定义岗位模板生产发布

## 发布范围

- 三类工单分别拆为提单、处理、调度、验收、管控、审计六类职责，共十八个窄角色。
- `role_permissions` 增加固定 `scope_profile`，工单列表、详情、历史、附件和动作使用同一关系授权策略。
- 用户授权必须具备明确 `MembershipRoleGrant`；缺失、过期、跨组织或跨租户 Grant 不再回退为租户全域。
- 新增租户隔离的 `role_bundles`、`role_bundle_roles`。内置模板只读可复制，自定义模板支持创建、编辑、停用、复制和删除。
- Web 和 Android 同步角色分组、Grant 编辑、岗位模板及 `allowedViews`；移除管理员自验收特殊输入框。

## 迁移与回滚

- 生产备份：`/data/e-report/backups/20260810-092223-20260810-fine-rbac-2`。
- 数据库备份 SHA-256：`3f08769fab2916eaba98382b693c526f372e0e749a8079640cebb20915f33e4e`。
- 发布目录：`/opt/e-report/releases/20260810-fine-rbac-2`。
- 首次克隆演练发现生产默认 collation 与新表不一致，未触碰生产库；Liquibase 增加 staging 表 collation 归一化后，克隆库首跑和二次幂等均通过。
- 正式 Liquibase 从 50 个变更集执行到 65 个，新增 15 个变更集。迁移前后用户、工单、附件保持 `118 / 78 / 134`。
- 迁移后角色 `27 -> 45`、权限 `61 -> 76`、显式 Grant `486 -> 928`；无旧宽角色、无重复 Grant、无空提单人，`reported_by_user_id` 保持非空约束。
- 回滚必须同时恢复上述数据库备份和上一版镜像：backend `localhost/e-report-backend:20260809-r2-direct-1`、frontend `localhost/e-report-frontend:20260809-rbac-observability-1`。禁止只回滚应用而保留新权限契约。

## 构建与自动测试

- Backend：`clean test` 通过，47 个测试套件、131 项测试、0 失败；`bootJar` 通过。
- Web：14 项测试、lint、production build 通过；保留既有 Vite 约 541 KiB chunk 告警。
- Android：11 项测试、TypeScript typecheck、Expo Doctor 19/19、release 构建和 v2 签名校验通过。
- 候选 JAR SHA-256：`232d1b23b7143c2ccba55eddb1cbef3b6ca9893bc0e43ea6a93088ebd9e756ae`。
- backend 镜像 digest：`sha256:88b9f04821694c73b6ad76decb89e192f2d2e449692aa55669977d258a1495b3`。
- frontend 镜像 digest：`sha256:cb39d48e56ea098324faeb116285511759b000172a2984fe50a36c1d2f8cb0e0`。

## 生产验收

- 全角色工单生命周期 E2E 通过 193 项断言，覆盖三类工单、十八个职责、显式 Grant、跨区县隔离、接单、派单、调单、拒绝、挂起、恢复、整改、验收、撤回、取消、并发接单及附件 HEAD/Range。
- E2E 创建的 12 个临时账号均软删除，2 个自定义测试模板均删除，12 张测试工单均进入 `CLOSED` 或 `CANCELLED` 终态。
- Android 模拟器从 `1.0.36/code37` 原地升级到 `1.0.37/code38`，登录态保留；管理员完成自定义模板创建、停用、复制回显和删除，模板数恢复为 9。
- 最小权限账号仅有 `ROLE_NORMAL_FAULT_REPORTER`：主页显示“普通故障提单员”，保留报单和工单查询，不显示“系统管理员”或“管理后台”，工单查询按本人范围返回空态。
- App crash buffer 为 0 行；无 React Native ERROR/WARN，仅有模拟器 HWUI 格式和 Chromium seed 环境告警。
- 生产最终为 45 个角色、76 个权限、161 条角色权限规则、9 个内置模板、86 条模板角色关系；自定义测试模板、旧角色和重复 Grant 均为 0。

## Android 发行

- stable 发布 ID：`019fe994-ee15-75d6-ba50-487a9258266e`。
- 版本：`1.0.37`，versionCode `38`，runtime `android-1.0.37-native38`；最低支持 code37，非强制升级。
- APK 大小：`76,030,319` 字节；SHA-256：`35b78395d74cd31db86082068be57d3ba187c969d6e9f30624692b7862e788a3`。
- 签名证书 SHA-256：`c63061845c0ca95444e9e9507338cc25284f2b5b022f6662bf85045371cb2d27`，APK Signature Scheme v2 校验通过。
- 用户统一入口：`https://e-report.omnnu.xyz:60443/e-report/download/android`；版本 API 也只返回该地址。
- 官方入口返回 code38 和 SHA 响应头并 302 到 R2 不可变对象；37 段、2 MiB、4 路并行 Range 下载零重试，9.7 秒重组完成且 SHA 一致。公网 APK 已在模拟器覆盖安装成功。

## 运行态与遗留风险

- backend 和 frontend 镜像均为 `20260810-fine-rbac-2`，Deployment `1/1 Ready`；MySQL、Redis、backend、frontend Pod 均为 Running、重启 0，Redis 返回 `PONG`。
- E2E 邮件通知中，11 条成功或按渠道配置完成，1 条因测试组织无有效收件地址持续重试；该测试事件已精确置为 `FAILED` 终态并保留审计，测试工单 Outbox 可投递数为 0。
- 139 邮箱通道仍观察到 SMTP 连接超时和 `450 Mail rejected`。这不影响工单事务和本次权限发布，但需要在通知中心继续监测最终失败记录，并由运维核查 SMTP 出口与收件地址质量。
