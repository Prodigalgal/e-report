# 区县未处理工单日报与个人信息生产迁移记录

## 结果

2026-08-07 至 2026-08-08 已完成用户联系方式、区县通知路由、市公司区县对比、每日 18:00 未处理工单日报和 Web/Android 个人信息维护的生产迁移与应用发布。

生产日报默认开启，发送时间为 `18:00`、时区为 `Asia/Shanghai`。发布发生在 21:00 后，超过 180 分钟补发窗口，因此没有补发 2026-08-07 日报；首次真实定时执行需要在 2026-08-08 18:00 后继续观察派发台账和 SMTP 结果。

## 迁移内容

- Liquibase 013：新增 `users.phone`、`users.email`，仅回填手机号格式用户名，退役静态邮件收件人配置，关闭并清空未启用的机器人渠道配置。
- Liquibase 014：新增 `daily_work_order_report_dispatches`，以 `(tenant_id, org_unit_id, report_date)` 唯一约束保证日报幂等。
- 为日报读取新增 `fault_reports(tenant_id, reporting_org_id, status_code, submitted_at)` 索引。
- 日报状态覆盖 `PROCESSING`、`SENT`、`PARTIAL_FAILED`、`FAILED`、`SKIPPED_EMPTY`、`SKIPPED_NO_RECIPIENT`。
- `PROCESSING` 执行权默认 60 分钟超时后可原子回收；SMTP 连接 10 秒、读取/写入 30 秒超时，避免异常进程永久占住当天唯一台账。

## 生产备份

- 目录：`/data/e-report/backups/20260807-232018-20260807-daily-report-profile-1`。
- 数据库：`fault_reporter_db.sql`，1,102,559 字节。
- 数据库 SHA-256：`4d459807f3339d098c9ae27e90400239d596b8fad49d7c871ebf73353cc2cf94`。
- 同目录保存迁移前 Deployment、ConfigMap、Secret 快照和 `SHA256SUMS`；目录权限为 `0700`，文件为 `0600`。

## Liquibase 演练

- 演练库：`fault_reporter_rehearsal_daily_2321`，来源为迁移前生产一致性转储。
- 首跑：`DATABASECHANGELOG 43 -> 44`，执行 014 MySQL changeset 1 个。
- 二次运行：执行 0 个 changeset，changelog 保持 44。
- 表、唯一约束和四列工单索引均存在，派发表初始行数为 0。
- 候选 backend 连接演练库完整启动，Hibernate schema validation、23 个 JPA Repository 和 Spring Bean 装配通过；演练配置强制日报关闭且未产生派发记录。

## 生产执行

- 正式 Job：`e-report-liquibase-production-daily014-2325`。
- 正式迁移：`DATABASECHANGELOG 43 -> 44`，表和索引检查通过，派发表为 0。
- Backend：`localhost/e-report-backend:20260808-daily-report-profile-3`。
- Frontend：`localhost/e-report-frontend:20260807-daily-report-profile-2`。
- 两个 Deployment 均为 `1/1 Ready`、Pod 重启数 0；主站 Web 返回 200。
- 首个 frontend 候选因构建上下文中的 `dist` 目录为空而 readiness 返回 403，未达到可用状态；保留失败镜像证据后以 `profile-2` 重建，修正版 15 秒内就绪。
- 最终后端 35 个测试套件、97 项测试全部通过并生成 Boot JAR；执行权超时回收、SMTP 有限超时以及上海调度时区与持久化时钟分离随 backend `profile-3` 投产。

## 运行态验收

- 生产配置：`notification.daily-outstanding-report.enabled=true`、`notification.daily-outstanding-report.send-time=18:00`。
- 夜间发布后派发表仍为 0，证明补发窗口外不会误发迟到日报。
- Web 通知中心真实显示日报开关、18:00 和 `Asia/Shanghai`；个人信息页的非法手机号会阻止保存。
- Android `1.0.32` / code33 使用既有正式证书覆盖安装成功，登录态保留、冷启动 crash buffer 为空；个人信息和通知中心页面完成 UI 树及截图核对。
- 生产 Secret 已删除 `ADMIN_NOTIFICATION_PHONES`、`NOTIFICATION_WECOM_ENABLED`、`NOTIFICATION_WECOM_WEBHOOK_URL`，当前 Deployment 无引用且 Pod 保持就绪。

## Android 正式发布

- stable 发布 ID：`019fdd32-b6d9-7251-b9a6-6685b9a11674`；版本 `1.0.32`、code33、runtime `android-1.0.32-native33`，最低支持 code31、非强制升级。
- APK 大小：`75,866,267` 字节；SHA-256：`f041d53b5f2dde164f90de101cae8362ccfe1fa408224a5d2ab20903b5ef7e71`；Android v2 签名和正式证书指纹已校验。
- 发布顺序为后端分片上传完成、R2 不可变对象上传成功、最后登记版本元数据；发布失败时脚本会终止未完成会话或清理本次 R2 对象。
- 对外只返回统一入口 `https://e-report.omnnu.xyz:60443/e-report/download/android`；HEAD 返回 code33 和正确哈希，Range 回源为 `206 bytes 0-1023/75866267`，整包回读大小和 SHA-256 与本地一致。
- code31 检查结果为 `updateAvailable=true`、`forceUpdate=false`、`nativeUpgradeRequired=true`；code33 返回 `updateAvailable=false` 和“当前已是最新版本”。后台发布中心显示 code33 已同步。

## 已知运行风险

- 当前 11 个区县均有 `ROLE_COUNTY_ADMIN`；仅 1 个区县存在至少一个可投递邮箱或手机号，其他区县在补齐管理员联系方式前会记录 `SKIPPED_NO_RECIPIENT`，不会跨区县兜底发送。
- 139 SMTP 已对部分既有工单通知返回 `550 Mail rejected score`。系统会记录脱敏地址和失败原因并按 Outbox 策略处理；日报逐收件人记录成功与失败，不会把失败伪装为成功。
- 首次真实 18:00 调度尚未发生，不能把单元测试、演练启动或夜间 smoke 表述为真实定时邮件已送达。

## 回滚

- 关闭 `notification.daily-outstanding-report.enabled` 可立即停用日报，无需回滚数据库。
- 应用可分别回滚到 backend `20260807-user-contact-dashboard-1`、frontend `20260807-user-contact-dashboard-2`；014 为增量表和索引，旧应用会忽略。
- 若需要物理恢复数据，先停止写入，再使用本记录中的全库转储和 Deployment/Secret 快照整体恢复；不要只删除派发表后继续运行新后端。
