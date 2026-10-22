# 市公司管理员授权范围生产修复记录

## 根因

- APP 的区县筛选功能没有删除。`GET /work-orders/statistics/scopes` 仅向持有 `dashboard:statistics:view + TENANT_ALL` 的账号返回区县列表。
- 027 迁移将默认租户内所有非根组织账号的 `TENANT_ALL` 无条件改为所属组织 `ORG_TREE`，错误地把账号组织归属当成授权范围上限。
- 吕春珉在 027 前持有 34 条显式市级 Grant，但账号组织归属为南靖县，因此被统一降为 `ORG_TREE(南靖县)`，APP 收到空区县列表后按设计隐藏筛选框。

## 修复内容

- 新增 Liquibase 031，以默认租户、用户名和 027 快照共同约束，只恢复吕春珉迁移前已有的 34 条 `TENANT_ALL` Grant。
- 不新增或删除角色，不修改账号组织、密码和权限有效期；示例管理员甲、示例管理员乙、示例管理员丙保持原范围。
- 写入 `authorization_scope_restore_20260813` 审计快照，并将吕春珉 `authorization_version` 从 10 递增到 11。
- 精确清理吕春珉单账号的 `authorizationSnapshot` Redis 缓存；现有会话因授权版本变化失效，重新登录后读取新范围。

## 测试环境验证

- 测试库备份：`/opt/e-report/candidates/20260813-city-admin-scope-test/test-before.sql.gz`。
- 备份 SHA-256：`25c0b26b157f586e3d1d7435e94eaa8629bab3900ed7e32aaa913dba86d42aa7`。
- 候选 JAR SHA-256：`7cc6faa68c66d2c8d60ad78a96b19c1cbfdcb1c2825e0292ca5e0dce9727e5c9`。
- 首次 Job：`e-report-liquibase-city-admin-scope-test-1`，`Run: 2`、`Previously run: 97`、`Rows affected: 69`。
- 幂等 Job：`e-report-liquibase-city-admin-scope-test-idempotent-1`，`Run: 0`、`Previously run: 99`。
- 隔离测试账号真实登录后返回 34 个角色、76 项权限和 11 个区县；南靖县统计下钻成功。
- 测试结束后已恢复原密码、删除测试会话和临时表、清理测试 Redis，并恢复数据同步 CronJob。

## 生产执行

- 生产备份：`/opt/e-report/backups/20260813-city-admin-scope-hotfix/production-before.sql.gz`。
- 生产备份 SHA-256：`f8eddeee0ff0529968a462bb5bbf082f32cd3dc3fcb90e1565694af6dc613bd3`。
- 正式 Job：`e-report-liquibase-production-city-admin-scope-1`，`Run: 2`、`Previously run: 97`、`Rows affected: 69`。
- 幂等 Job：`e-report-liquibase-production-city-admin-scope-idempotent-1`，`Run: 0`、`Previously run: 99`。
- 迁移后吕春珉 34 条 Grant 全部为 `TENANT_ALL`，`ROLE_DASHBOARD_VIEWER` 为 `TENANT_ALL`，恢复审计快照为 34 条。
- `admin` 保持 37 条 `TENANT_ALL` 和 1 条根组织 `ORG`；示例管理员甲、示例管理员乙、示例管理员丙的 Grant 与授权版本未变化。
- `DATABASECHANGELOGLOCK.LOCKED=0`；MySQL、Redis、Backend、Frontend 均 `1/1 Ready` 且零重启。
- 公网 `admin` 只读 smoke 16 项通过，区县范围接口返回漳州 11 个区县，安装包入口继续重定向到 R2。

## 回滚点

1. 优先恢复生产备份 `production-before.sql.gz`。
2. 单独回滚 031 时，按 `authorization_scope_restore_20260813` 将 34 条 Grant 恢复为迁移前的 `ORG_TREE(南靖县)`，并再次递增授权版本。
3. 回滚后精确删除吕春珉的授权缓存键，验证 Liquibase 锁为 0、示例管理员乙仍为南靖县范围、`admin` 仍为全市范围。
