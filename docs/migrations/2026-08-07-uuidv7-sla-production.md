# UUIDv7、组织权限与 SLA 生产迁移记录

## 结果

2026-08-07 已完成生产停写迁移、三端发布和运行态验收。生产数据库的全部技术主键和真实外键已统一为 UUID；新实体由 Hibernate 生成 UUIDv7，MySQL 使用 `BINARY(16)`，REST/JSON 使用标准 UUID 字符串。

本次同时投产全局唯一账号登录、细粒度岗位权限、组织归属、配置与通知隔离、故障 SLA、历时仪表盘、Web/App 统一详情动线和报单定位合并。

## 迁移策略

- Liquibase 011 使用影子 UUID 列完成历史主键映射和外键回填，映射校验为 0 后再重建主键、唯一索引和外键。
- 生产早期 Hibernate 哈希外键先规范化为稳定约束名。
- 已退出运行时代码但仍存在于生产库的 API/企业微信表保留数据并同步转换，不静默删表。
- 历史记录使用迁移 UUID，不承诺版本为 7；迁移后新建实体必须满足 `UUID.version() == 7`。
- 数值 ID、数字路由和旧 JWT 不提供双轨兼容；发布后账号重新登录。

## 演练

- MySQL：8.4.11。
- 数据源：生产库在线一致性快照，30 张表，69 用户、30 工单、16 附件、13 角色、47 权限。
- rehearsal 库：`fault_reporter_uuid_rehearsal_20260806_2332`，按生产 `utf8mb4_0900_ai_ci` 创建。
- Liquibase：25 个新增 MySQL changeset 全部通过，011 主键交换约 15.5 秒，整次迁移约 33 秒。
- 候选后端：Hibernate schema validation、认证挑战、全局账号登录、`/auth/me`、UUID 工单搜索/详情、12 条 SLA 策略均通过。
- 新建 rehearsal 工单 ID 为 `019fd7c3-81ca-7a70-bdc0-7c585142ff0f`，版本位为 7；优先级、组织归属和 SLA 快照同时正确写入。

## 生产执行

- 停写方式：`e-report-backend` 缩容到 0 后备份，再运行独立 Liquibase Job。
- 备份目录：`/data/e-report/backups/20260807T000411+0800-20260806-uuidv7-sla-2/`。
- 数据库备份：`fault_reporter_db-before.sql.gz`，33,172 字节。
- 备份 SHA-256：`a4332087e897feaea3189ed8782e883963f11190c04b86f2f0c2883e912570b0`。
- 正式 Job：`e-report-liquibase-production-uuidv7-0004`。
- Liquibase tag：`20260806-uuidv7-sla-2-production`。
- 正式迁移：011 主键交换约 13.7 秒，Job 约 33 秒完成。

## 数据验收

- 技术主键：31/31 为 `BINARY(16)`。
- 真实外键：54/54 为 `BINARY(16)`。
- 核心数据：69 用户、30 工单、16 附件和 26 条历史 App 发布记录完整保留。
- 权限模型：角色 13 -> 18，权限 47 -> 58；11 个漳州区县管理员保留。
- SLA：12 条类型/优先级策略存在；活动工单 30，24 小时内 28，24-48 小时 1，48-72 小时 0，超过 72 小时 1，当前超时 27。
- 破坏性清理：`fault_reports.township`、`fault_reports.village_or_street` 均不存在。

## 运行态

- Backend：`localhost/e-report-backend:20260807-uuidv7-sla-outbox-4`。
- Frontend：`localhost/e-report-frontend:20260806-uuidv7-sla-2`。
- Backend/Frontend/MySQL/Redis 全部 Ready，应用 Pod 重启数为 0。
- 主域、relay 和 `IP:60080` Web 均返回 200；认证挑战、登录、UUID 搜索、详情、统计和 SLA API 均返回 200。
- 历史 SLA 首次评估生成 104 条分通道 Outbox 事件；处理器收敛为单条 claim、20 秒轮询后全部 `COMPLETED`，当前后端日志无 ERROR/FATAL/Exception。

## Android 发布

- 版本：`1.0.28` / code29 / `android-1.0.28-native29`。
- stable 发布 ID：`019fd7e8-b10f-7eca-bb7a-ef511ab49a06`。
- `minSupportedVersionCode=29`，`forceUpdate=true`。
- APK：72,949,161 字节。
- SHA-256：`b1d342f0d08575cb7efd24b16d48e76a56284e83c20ef325e3d33cc1b8fdefd6`。
- R2 与源站备用入口均为 HEAD 200、Range 206；R2 整包回读哈希与本地签名包一致。
- 小米 M2102J2SC / Android 13 真机完成覆盖安装、旧 token 失效、新登录、首页、查询、详情、报单定位与地址解析、管理 SLA 仪表盘验证；Crash buffer 为空。

## 回滚

UUID contract 迁移不可通过反向 changeset恢复数值主键。若发生结构或核心行为故障，必须同时停止写入、恢复上述全库备份，并把 backend/frontend 回滚到迁移前 YAML 中记录的镜像；不能只回滚应用镜像。
