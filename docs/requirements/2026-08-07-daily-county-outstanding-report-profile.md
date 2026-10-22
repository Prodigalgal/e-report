# 区县未处理工单日报与个人信息维护

> 状态：已实现并投产（首次正式定时执行待 2026-08-08 18:00 观测）
> 日期：2026-08-07 至 2026-08-08
> 等级：S3（调度、邮件、报表、个人资料、Liquibase、Web、Android 与生产发布）

## 目标

- 每天晚上 18:00 按区县汇总未闭环工单，生成中文 XLSX 附件并发送给该区县的 `ROLE_COUNTY_ADMIN` 人员。
- 邮件正文直接展示工单总量、状态、类型、当前超时数量和最长历时，附件承载逐单明细。
- 日报总开关和发送时间进入“系统配置 -> 通知中心”，支持市公司默认值与区县覆盖。
- Web 与 Android 用户均可维护本人姓名、必填手机号和可选邮箱；用户名、组织和权限保持只读。

## 范围

- 后端调度、区县隔离查询、XLSX 生成、邮件投递、执行台账、通知配置 API。
- Web/Android 通知中心配置展示和修改。
- Web 个人信息页面、导航入口和认证状态刷新；复用 Android 现有个人信息页面并补回归。
- Liquibase 新增日报执行台账与工单报表查询索引。

## 非目标

- 不发送已闭环或已取消工单，不跨区县合并附件。
- 不新增第三方调度框架、消息队列或独立报表微服务。
- 不开放用户自助修改用户名、组织、角色、权限或密码。
- 本期不提供历史日报下载、手工补发或执行台账管理页面。

## 关键设计

### 调度与幂等

- 调度器按各区县配置的北京时间预约下一次执行；启动、配置或组织变更、Redis 订阅恢复及每日零点重新加载预约，邮件发送使用独立线程池。
- 默认发送时间为 `18:00`，时区固定 `Asia/Shanghai`；默认允许 180 分钟补发，服务短暂停机不会丢失当天日报，超过窗口不发送迟到邮件。
- `daily_work_order_report_dispatches` 以 `(tenant_id, org_unit_id, report_date)` 唯一约束抢占当天执行权，多副本只能有一个实例发送。
- `PROCESSING` 超过 60 分钟时允许通过带旧启动时间条件的原子更新回收执行权，进程异常退出不会永久阻断当天日报；SMTP 连接、读取和写入均设置有限超时。
- 台账记录 `PROCESSING`、`SENT`、`PARTIAL_FAILED`、`FAILED`、`SKIPPED_EMPTY`、`SKIPPED_NO_RECIPIENT`，以及工单数、收件人数、成功/失败数和脱敏错误摘要。
- SMTP/文件生成不进入数据库事务；短事务仅用于抢占和回写执行结果。

### 配置契约

- `notification.daily-outstanding-report.enabled`：日报开关，默认 `false`，支持市公司默认和区县覆盖。
- `notification.daily-outstanding-report.send-time`：`HH:mm`，默认 `18:00`，支持市公司默认和区县覆盖。
- 日报同时受 `notification.center.enabled` 与 `notification.email.enabled` 约束；任一关闭均不发送。
- 区县无未处理工单时写入 `SKIPPED_EMPTY`，不发送空附件。

### 数据与隔离

- 每个活动租户独立遍历其活动区县；查询必须同时包含 `tenant_id`、区县组织树 ID 集合和非终态状态条件。
- 工单归属使用 `reporting_org_id`，不会因当前处理人或调单目标变化而跨区县。
- 报表读取使用扁平 DTO Projection，只选择导出和汇总所需字段，不加载附件、事件等聚合关系。
- 收件人复用现有区县管理员解析：个人邮箱优先，否则合法手机号拼接 `@139.com`；无有效地址或 SMTP 失败只记录脱敏结果。

### XLSX 与邮件

- 工作表包含工单编号、类型、状态、优先级、地点、问题、联系人、报单人、当前处理人、报单时间、历时、SLA 截止和超时标记。
- 表头冻结、启用筛选、使用中文标签和固定列宽；文本以字符串单元格写入，避免公式注入。
- 邮件按收件人单独投递，避免泄露其他管理员地址；部分失败不重复发送给已经成功的收件人，当天结果记为 `PARTIAL_FAILED`。

### 个人信息

- `PUT /auth/me` 继续作为唯一自助更新契约，字段为 `name`、`phone`、`email`。
- 手机号按中国大陆 11 位格式校验且必填，邮箱为空时归一为 `null`；成功后返回新 token 和最新用户数据。
- Web/Android 保存成功后同时更新持久化会话，刷新页面或冷启动仍显示最新信息。

## 主要影响文件

- `e-report-backen/src/main/java/.../reporting/`：调度、查询、汇总、XLSX、邮件与执行编排。
- `e-report-backen/src/main/java/.../notification/`：可复用邮件发送配置、收件人解析和通知设置。
- `e-report-backen/src/main/resources/db/changelog/changes/2026-08-07-014-daily-outstanding-report.sql`。
- `e-report/src/pages/UserProfilePage/`、认证上下文、路由与导航。
- `e-report/src/components/Admin/NotificationCenterSettings.jsx`。
- `e-report-app/app/admin/configs.tsx`、`e-report-app/src/api/admin-api.ts`、类型定义。

## 验收标准

- 18:00 前不发送；18:00 后每个区县每天最多执行一次，服务恢复后可补执行。
- 市公司默认配置和区县覆盖均生效；通知中心或邮件渠道关闭时日报不发送。
- 南靖县报表不包含其他区县工单，市公司/区县/工单三层隔离测试通过。
- XLSX 可被 Apache POI 重新读取，表头、行数、中文内容、日期和超时标记正确。
- 无工单、无收件人、单个收件人失败和全部失败均有明确台账状态且不阻断其他区县。
- Web 与 Android 均能修改个人信息，非法手机号/邮箱被前后端拒绝，用户名和权限不可修改。

## 测试与发布

- 后端：日报服务单元测试、XLSX 读取测试、收件人/配置测试、Liquibase H2 启动、全量 `test bootJar`。
- Web：个人信息表单测试、通知设置测试、`npm test`、lint、build、桌面与 390px 浏览器 smoke。
- Android：unit、typecheck、Expo Doctor、release APK、模拟器配置页和个人信息保存 smoke、crash log。
- 生产：迁移前备份、Liquibase rehearsal、候选启动校验、正式迁移/滚动发布、只向受控测试地址执行日报 smoke，最后登记 APK。

## 回滚点

- 应用回滚到上一 backend/frontend 镜像时保留新增表和配置键，不影响旧版本读取。
- 如需撤销定时发送，先关闭 `notification.daily-outstanding-report.enabled`，无需回滚数据库。
- 表结构物理回滚仅在确认没有审计保留需求后执行 Liquibase rollback。
