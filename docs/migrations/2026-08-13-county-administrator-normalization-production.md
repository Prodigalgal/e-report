# 区县管理员组织与授权生产规整记录

## 业务确认

- 示例管理员甲、示例管理员乙为南靖县管理员。
- 示例管理员丙为漳浦县管理员。
- 三人统一使用内置 `COUNTY_ADMINISTRATOR` 标准岗位，不保留历史市级或平台职责。

## 迁移内容

- Liquibase 032 在修改前分别快照三人的组织、`user_roles` 和 `membership_role_grants`。
- 示例管理员甲、示例管理员乙组织保持南靖县；示例管理员丙从南靖县迁至漳浦县。
- 三人角色和 Grant 均重建为区县管理员标准岗位的 32 项规则：`ROLE_USER` 为所属区县 `ORG`，其余 31 项为所属区县 `ORG_TREE`。
- 移除市公司通知、SLA 编辑、安装包发布/策略/存储、租户创建和租户状态管理职责，补齐区县通知收件人与 SLA 查看职责。
- 三人的 `authorization_version` 各递增 1，并精确清除对应 Redis 授权缓存；旧会话将在下一次请求时失效。

## 测试环境验证

- 测试库备份：`/opt/e-report/candidates/20260813-county-admin-normalization-test/test-before.sql.gz`。
- 备份 SHA-256：`d8643c3e96dcfc868b257ddfa7ca3d56665afb2753b6af3ae684000e87bb85cd`。
- 候选 JAR SHA-256：`b3327c9baccd56b5bb4bc0a525244606aae0a0940d5e2171f6ec24c0d8c53562`。
- 首次 Job：`e-report-liquibase-county-admin-normalization-test-1`，`Run: 2`、`Previously run: 99`、`Rows affected: 634`。
- 幂等 Job：`e-report-liquibase-county-admin-normalization-test-idempotent-1`，`Run: 0`、`Previously run: 101`。
- 逐表断言：每人 32 个角色、32 条 Grant，均为 `1×ORG + 31×ORG_TREE`，无 `TENANT_ALL`、错误组织锚点或有效期。
- 三账号真实登录：均返回 32 个角色、70 项权限和空区县筛选列表；示例管理员甲、示例管理员乙只返回南靖县组织，示例管理员丙只返回漳浦县组织，跨区县统计均返回 403。
- 测试结束后已恢复三人原密码、删除测试会话和认证快照、清理测试 Redis，并恢复数据同步 CronJob。

## 生产执行

- 生产备份：`/opt/e-report/backups/20260813-county-admin-normalization/production-before.sql.gz`。
- 生产备份 SHA-256：`d6b6619bc159117d6b9cec334ad6ee5cc0f1499209a219d52c23e3fa5af96227`。
- 正式 Job：`e-report-liquibase-production-county-admin-normalization-1`，`Run: 2`、`Previously run: 99`、`Rows affected: 634`。
- 幂等 Job：`e-report-liquibase-prod-county-admin-idem-1`，`Run: 0`、`Previously run: 101`。
- 快照数量：3 个用户、109 条原角色关系、109 条原 RoleGrant。
- 终态与测试环境一致：三人各 32 个标准岗位角色和 32 条 RoleGrant；示例管理员甲、示例管理员乙组织为 `350627`，示例管理员丙组织为 `350623`。
- `admin` 保持 37 条 `TENANT_ALL` 和 1 条根组织 `ORG`；吕春珉保持 34 条 `TENANT_ALL`；两人授权版本未变化。
- 三人的授权缓存键剩余数量均为 0；`DATABASECHANGELOGLOCK.LOCKED=0`。
- MySQL、Redis、Backend、Frontend 均 `1/1 Ready`，业务 Pod 未滚动且零重启，后端近 15 分钟无新增 ERROR/Exception。

## 回滚点

1. 优先恢复生产备份 `production-before.sql.gz`。
2. 单独回滚 032 时，按 `county_administrator_user_snapshot_20260813`、`county_administrator_role_snapshot_20260813` 和 `county_administrator_grant_snapshot_20260813` 恢复三人的原组织、角色与 Grant。
3. 回滚会再次递增授权版本；完成后精确清除三人的授权缓存并验证 Liquibase 锁、组织和数据域。
