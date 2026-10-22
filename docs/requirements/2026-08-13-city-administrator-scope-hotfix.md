# 市公司管理员授权范围修复

## 目标

- 修复 027 数据迁移将已明确授予吕春珉的市级权限按账号组织归属统一降为南靖县组织树的问题。
- 保持动作权限、组织归属和数据范围相互独立：账号可以归属区县组织，但由有权管理员显式授予的 `TENANT_ALL` 仍然有效。
- 保持示例管理员乙等已确认区县范围账号的现有数据隔离，不整体回滚 027 迁移。

## 范围

- 使用 027 的迁移前快照精确识别吕春珉原有的 34 条 `TENANT_ALL` Grant。
- 新建恢复审计快照，记录恢复前、恢复后的数据范围与原因。
- 仅恢复数据范围，不新增角色、不删除角色、不修改账号组织归属和有效期。
- 递增 `authorization_version`，使旧会话不能继续使用迁移前的授权快照。

## 非目标

- 不把所有非根组织用户自动提升为市级管理员。
- 不把 `ORG_TREE(ROOT)` 与 `TENANT_ALL` 合并为同一种数据范围。
- 不修改工单状态机、SLA、通知、配置或安装包发布行为。
- 不发布新的 Android 安装包；现有 APP 已具备区县筛选能力。

## 影响文件

- `e-report-backen/src/main/resources/db/changelog/changes/2026-08-13-031-city-administrator-scope-restoration.sql`
- `e-report-backen/src/main/resources/db/changelog/db.changelog-master.yaml`
- `e-report-backen/src/test/java/com/pro/ereportbacken/service/WorkOrderLifecycleIntegrationTests.java`

## 验收标准

- 迁移只恢复吕春珉在 027 快照中记录的 34 条原始 `TENANT_ALL` Grant。
- `ROLE_DASHBOARD_VIEWER` 恢复为 `TENANT_ALL`，`GET /work-orders/statistics/scopes` 返回 11 个启用区县。
- 吕春珉重新登录后，APP 管理后台展示“统计范围”，可在全市汇总和各区县之间切换。
- 示例管理员乙、示例管理员甲、示例管理员丙的 Grant 不发生变化；示例管理员乙仍只能查看南靖县组织树。
- `admin` 的 37 条 `TENANT_ALL` 与 1 条根组织 `ORG` 保持不变。
- 测试库迁移首跑、幂等、后端完整测试和生产迁移后只读断言通过。

## 回滚

- 优先使用生产迁移前数据库备份恢复。
- 单变更回滚时，按 `authorization_scope_restore_20260813` 记录恢复原 `ORG_TREE` 和组织锚点，并再次递增用户授权版本。
