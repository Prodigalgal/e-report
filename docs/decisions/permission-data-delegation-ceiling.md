# ADR：权限动作、数据视野与再授权上限统一

- 日期：2026-08-11
- 状态：已接受，1.0.43 起实施，1.0.45 补齐显式租户根契约

## 决策

系统将授权能力拆成彼此不可替代的两个维度：

- Permission 决定用户能执行什么动作。
- PermissionScopeProfile 与 MembershipRoleGrant 共同决定该动作能作用于什么关系、组织范围和有效期。

所有运行时访问必须同时满足动作权限和数据视野。所有再授权入口必须满足“受授能力不高于授予者当前有效能力”，并统一经过后端授权上限策略，前端隐藏仅作为交互优化。

组织、角色、权限与数据范围采用单向契约，不允许相互推断：

1. `Tenant.rootOrgUnitId` 显式指向租户根组织，`OrgUnit` 只维护组织归属、父子关系和路径。
2. `User.orgUnitId` 表达用户唯一的主归属组织，不因此自动获得任何业务能力。
3. `Role` 只组合 `Permission + PermissionScopeProfile`，不保存组织或租户范围。
4. `MembershipRoleGrant` 将 Role 授予用户，并以 `dataScope + scopeOrgId + expiresAt` 表达数据范围和有效期。
5. 运行时访问范围是 Permission 资源选择器与 MembershipRoleGrant 数据范围的交集；组织树本身不会提升权限，角色本身也不会扩大数据面。
6. 根组织不通过编码、名称、`parentId` 或组织类型推断；客户端只消费服务端下发的 `tenantRoot` 标志。

授权上限按以下规则计算：

1. 权限名称必须由授予者持有，目标 `PermissionScopeProfile` 必须被授予者的有效策略覆盖。
2. `PermissionScopeProfile` 是按权限解释的资源选择器，不是简单的线性等级。同类选择器可以覆盖自身；`PENDING_OR_CURRENT_HANDLER` 可以覆盖 `CURRENT_HANDLER`；`CLAIMABLE_OR_PARTICIPANT` 可以覆盖 `PARTICIPANT`。`GRANT_SCOPE`、`REPORTER`、`PARTICIPANT` 等其余选择器默认互不可比，除非领域策略能够证明目标资源集合包含于来源集合。
3. 目标 `TENANT_ALL` 必须由同权限的显式 `TENANT_ALL` Grant 覆盖；多个组织 Grant 的并集不能推导出可覆盖未来组织的 `TENANT_ALL`。
4. 目标 `ORG`、`ORG_TREE` 或 `CUSTOM_ORGS` 的实际组织集合，必须完整包含于授予者同权限、兼容策略的有效 Grant 并集。
5. 永久 Grant 只能由永久 Grant 授予；限时 Grant 的到期时间不得晚于实际覆盖对应权限和组织集合的授予者 Grant。
6. 用户角色分配还必须同时受 `user:manage:assign_roles` 的数据范围和有效期约束。

角色定义属于租户内可复用的权限策略目录，本身不携带数据视野；真正的数据授权仍由用户 MembershipRoleGrant 承载。修改已被授予的自定义角色时，服务端必须用新权限策略重新校验该角色的全部现存 Grant。只要存在一条 Grant 会因角色修改而超过操作者上限，整次修改即拒绝并回滚。

## 入口覆盖

统一策略必须覆盖：

- 自定义角色创建、权限策略修改和角色目录可见性。
- 用户直接角色授权、旧版角色集合接口和用户创建时的显式授权。
- 岗位模板创建、修改、复制和展开后的角色授权。
- Excel 用户导入的预校验与事务内二次校验。
- 用户详情和列表中的角色、Grant 可见性。

权限目录响应返回当前操作者可授予的 `PermissionScopeProfile`；角色响应返回当前操作者可实际提交的 Grant 选项。Web 与 Android 只展示服务端返回的选项，不在客户端复制权限强弱规则。角色目录可见性与用户授权可操作性分别计算：可以查看某个角色定义，不代表当前操作者一定能在任意组织或有效期下授予该角色。

## 运行时资源选择器

运行时授权按具体 Permission 解释资源选择器：

- `GRANT_SCOPE`：资源所属组织必须由 Grant 覆盖。
- `MEMBERSHIP_ORG`：用户当前所属组织必须由 Grant 覆盖。
- `REPORTER`、`PARTICIPANT`、`CURRENT_HANDLER`：分别匹配提单人、历史参与人和当前处理人关系。
- `PENDING_OR_CURRENT_HANDLER`、`CLAIMABLE_OR_PARTICIPANT`：按名称表达的关系并集计算，其中需要组织范围的分支继续使用 Grant。

租户边界始终先于任何关系选择器校验。列表查询、详情读取、下载导出和写操作必须使用相同选择器语义，不允许一处按组织过滤、另一处仅按角色名判断。

## 非目标与边界

- 本次上限在每次创建或修改授权时强制执行，不引入授权依赖图或授予者被降权后的自动级联撤权。
- 历史或系统初始化 Grant 没有授予链元数据，不在本次迁移中伪造来源。
- 角色、岗位模板是租户内策略目录，不承载工单、用户、配置或诊断数据；业务数据仍按各领域查询策略隔离。

## 验证

- 同权限但更强 `PermissionScopeProfile` 不可见、不可创建、不可提交。
- 区县 Grant 不能派生其他区县或 `TENANT_ALL` Grant。
- 临时 Grant 不能派生更晚到期或永久 Grant。
- 修改已在全市范围使用的角色时，区县操作者不能加入仅在本区县持有的权限。
- 市公司管理员和区县管理员原有合法角色、岗位模板、导入及用户管理流程保持可用。
