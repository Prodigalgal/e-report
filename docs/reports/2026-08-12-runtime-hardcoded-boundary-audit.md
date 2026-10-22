# 1.0.45 运行时硬编码边界审计

## 结论

运行时业务判断不再依赖漳州区县名称、行政区划编码、默认账号名或 `parent_id IS NULL`。区域种子和历史账号只保留在 Liquibase 一次性数据迁移中；默认租户和默认管理员通过配置指定，租户根组织通过数据库关系显式指定。

## 已修复

- 根组织识别：删除 `ROOT` 编码和根节点推断，统一读取 `Tenant.rootOrgUnitId`。
- 数据完整性：`tenants(id, root_org_unit_id)` 复合外键指向 `org_units(tenant_id, id)`，禁止跨租户根引用。
- 客户端根标记：Web/Android 只消费后端 `OrgUnitDTO.tenantRoot`，不根据 `parentId`、名称或类型判断。
- 默认管理员：后端以配置的全局唯一用户名标记系统保护账号；前端消费 `systemProtected`，不比较字面值 `admin`。
- 新租户角色：直接从 `PermissionCatalog + BuiltInRoleCatalog` 创建，不再复制默认租户数据库角色。
- 租户展示：前端使用登录上下文的租户名称和后端 `defaultTenant` 标记，不比较 `DEFAULT_CITY` 或硬编码“漳州市公司”。
- 缓存 key：移除把 `ROOT` 当作租户级配置哨兵的命名，改用无地域语义的 `TENANT`。

## 合法保留

- `application.properties` 中 `DEFAULT_CITY`、默认管理员用户名和默认租户名称是可覆盖的部署默认值，不参与区域授权推断。
- Liquibase 中漳州 11 个区县、初始账号、密码迁移和 `3506` 是经业务确认的一次性数据事实。
- `Locale.ROOT`、Android root 检测、JPA Criteria `Root` 和脚本文件系统 `ROOT` 不是组织根业务判断。
- R2 域名只用于对象存储内部发布链路；对用户公开的 APK 下载地址保持统一入口。

## 后续门禁

- 新增组织、角色或租户逻辑必须从显式 ID/配置/目录读取，不以名称、编码或层级位置推导权限。
- 任一数据查询必须先限定 `tenantId`，再套用 Permission 选择器与 RoleGrant 数据范围。
- 任一再授权操作必须同时校验动作权限、权限策略覆盖、组织覆盖和有效期，前端隐藏不能替代后端拒绝。
