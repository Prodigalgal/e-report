# 工单查询多选与全环节 SLA 生产迁移记录

## 结果

2026-08-08 已完成工单查询三维多选、派单待接收 SLA、验收驳回整改 SLA 的生产数据库迁移及 Web/Backend 发布。Android `1.0.33` / code34 已使用正式证书构建，在模拟器完成覆盖安装和生产链路验证，并登记为 stable 当前版本、同步到 R2。

## 变更内容

- `/work-orders/search` 的 `queue`、`reportType`、`status` 支持同名重复参数；维度内取并集，维度间与关键词、组织范围取交集。
- Web 和 Android 的工作队列、工单类型、处理状态统一为多选控件；Web URL 保留重复参数，单值旧链接仍可自然读取。
- SLA 新增 `ASSIGNMENT_ACCEPTANCE` 和 `REWORK` 阶段，覆盖派单待接收和验收驳回整改两个此前没有独立时限的环节。
- 调单期间保存原阶段、截止时间和原阶段超时事实；接受、拒绝或撤销调单后恢复原时限，不通过反复派调单刷新处理时限。
- Android 启动会话查询增加 12 秒有限超时，避免异常网络导致启动界面无限等待。

## 数据迁移

Liquibase 015 对生产库执行以下增量变更：

- `work_order_sla_policies` 新增 `assignment_acceptance_minutes`、`rework_minutes`，分别从现有接单、处理时限回填。
- `fault_reports` 新增 `sla_resume_stage`、`sla_resume_due_at`、`sla_resume_breached_at`。
- 存量待接收派单按派单时间和派单接收策略初始化 SLA；存量验收驳回工单细化为 `REWORK`，保留原截止时间。
- 本次变更未新增工单主状态、角色、权限或可见范围规则。

## 生产备份

- 目录：`/data/e-report/backups/20260808-1601-20260808-multiselect-detailed-sla-1`。
- 数据库：`fault_reporter_db.sql`，1,237,387 字节。
- 数据库 SHA-256：`b408adce76a7e495346c0712e7d6ff74b680a6c49e524b7acf414c735ba787ad`。
- 迁移前基线：Liquibase 44、用户 117、工单 76、附件 121、SLA 策略 12、待接收派单 1、活动工单 40。
- 同目录保存 Deployment、ConfigMap、Secret、迁移 Job 和验证输出，附 `SHA256SUMS`；目录权限为 `0700`，文件为 `0600`。

## Liquibase 演练

- 使用迁移前生产一致性转储创建独立 rehearsal 数据库。
- 首次运行：`DATABASECHANGELOG 44 -> 45`；第二次运行执行 0 个 changeset，changelog 保持 45。
- 两个策略字段空值数为 0，回填值差异为 0；待接收派单和整改阶段迁移差异均为 0。
- 候选 backend 使用与生产一致的租户环境变量连接 rehearsal 数据库完整启动，Hibernate schema validation 和 Spring Bean 装配通过。
- 首次候选启动因演练环境缺少默认租户变量在 Liquibase 前失败，没有写入数据库；补齐与生产一致的环境变量后重新执行并通过。

## 生产执行

- 正式 Job：`e-report-liquibase-production-sla015-1602`。
- 迁移期间先将 backend 缩容到 0，完成一致性备份后执行 Liquibase，再发布应用，避免旧版本写入新阶段值。
- 正式迁移：`DATABASECHANGELOG 44 -> 45`；5 个新增字段、策略回填、待接收派单和整改阶段检查全部通过。
- Backend：`localhost/e-report-backend:20260808-multiselect-detailed-sla-1`，镜像 digest `sha256:ffb681409d17effde6dde62be32a37859b7c0c5443743e9bae9ff6470015fe45`。
- Frontend：`localhost/e-report-frontend:20260808-multiselect-detailed-sla-1`，镜像 digest `sha256:75d31af99b11d7d9a1f3cf0f7c17234a49d8e92941c73a324ce17f3cfd062418`。
- 最终 Pod：backend `e-report-backend-5855dbc5fc-nbscw`、frontend `e-report-frontend-6cfcf54796-p2962`，均 `1/1 Ready`、重启数 0。
- 发布后数据库：Liquibase 45、用户 117、工单 77、附件 127、SLA 策略 12；策略空值、待接收派单迁移差异均为 0。

## 自动化与运行态验收

- Backend：35 个测试套件、100 项测试，failure/error/skipped 均为 0；`bootJar` 和 H2 Liquibase 015 启动通过。
- Web：10 项 unit test、lint、production build 全部通过。
- Android：5 项 unit test、TypeScript、Expo Doctor `19/19`、正式签名 release build 全部通过。
- 生产 Web 查询使用 2 个工作队列、2 个工单类型和 2 个处理状态，URL 输出 6 个重复参数并返回 12 条工单；浏览器日志为空。
- 生产 SLA 页面显示待接单、派单接收、处理、验收、驳回整改和完成六阶段，新增策略值可读取。
- 模拟器使用相同三组多选条件返回 11 条工单；点击查询结果先进入工单详情，未直接进入回单页；冷启动和验证后 crash buffer 为空。
- 主域和 relay 的 Web/challenge 路径均返回 200。

## 通知配置校正

候选运行日志发现南靖县组织单独开启了钉钉渠道，但 webhook 为空。已通过生产通知中心正常管理界面关闭该组织覆盖；数据库确认全局和区县机器人渠道均关闭，企业微信/钉钉非空密钥数量为 0。等待一个 Outbox 轮询周期后没有新增钉钉错误。

## Android 候选

- 版本：`1.0.33`、code34、runtime `android-1.0.33-native34`，最低 Android API 24、target API 36。
- APK：75,870,411 字节；SHA-256：`72c7a75963ac1ccb41655c3dd66fb5cb84b9c09ff2c6b6eb778e746dc7f74573`。
- Android v2 签名有效，正式证书 SHA-256：`c63061845c0ca95444e9e9507338cc25284f2b5b022f6662bf85045371cb2d27`。
- 模拟器重启后重新覆盖安装 code34，包版本、前台进程、生产登录态恢复、多选查询、详情跳转和 SLA 页面均已验证。

## Android 正式发布

- stable 发布 ID：`019fe18e-1728-7223-8ab3-a52dc3cc748d`；最低支持 code31，非强制升级。
- 发布顺序为 backend 分片上传完成、R2 不可变对象 `android/stable/34/app-release.apk` 上传成功、最后登记 stable 元数据；发布中心确认 code34 状态为 `SYNCED`、当前版本、期望大小与对象大小均为 75,870,411 字节。
- 固定入口 `https://e-report.omnnu.xyz:60443/e-report/download/android` 返回 code34 和正确 SHA-256；R2 与源站灾备的 Range 均返回 `206 bytes 0-1023/75870411`。
- R2 整包回读大小和 SHA-256 与本地正式 APK 完全一致；回读包在模拟器覆盖安装成功，冷启动恢复登录态，多选查询返回结果且 crash buffer 为空。
- 更新检查：code33 返回可选原生升级，code34 返回已是最新版本；低于最低支持版本的 code30 返回强制原生升级。三种响应的 `apkUrl` 均为统一主站下载入口。

## 回滚

- 旧版本镜像为 backend `localhost/e-report-backend:20260808-daily-report-profile-3`、frontend `localhost/e-report-frontend:20260807-daily-report-profile-2`。
- 新后端可能写入 `ASSIGNMENT_ACCEPTANCE` 或 `REWORK`；旧后端枚举无法解析这两个值，因此不能只回滚 Deployment 并保留迁移后数据库。
- 如需版本回滚，先停止写入，再使用本记录的全库备份恢复数据库，同时回滚 backend 和 frontend；恢复后核对 changelog 44、行数、附件和 Pod readiness。
- 如果只需临时降低影响，可继续运行新 backend 并由前端隐藏新增入口；不要手工删除字段或把新阶段批量改写成旧值。
