# 管理平面权限边界

## 决策

- `ROLE_ADMIN` 是进入管理平面的必要身份。Web 与 App 的管理入口、管理路由以及后端 `/admin/**` 端点都必须先满足该角色。
- 固化权限继续用于管理端点内的细粒度授权。后端管理接口采用 `ROLE_ADMIN AND endpoint permission`，角色身份不能替代端点权限，端点权限也不能把普通角色提升为管理员。
- 工单报单、统一查询、详情和回单属于业务工作台，不属于管理平面；一线提单者和各工单处理角色继续按固化的工单权限工作。
- Web 未登录用户进入登录页；已登录非管理员直接访问管理 URL 时显示 `403`。App 不渲染管理入口，并在根导航层拦截所有 `admin` 路由，避免页面内容短暂暴露。
- 前端权限隐藏只用于操作体验，后端授权是最终安全边界。任何新增管理接口必须位于 `/admin/**`，并显式声明对应的固定权限。

## 实现边界

- Web 统一使用 `isAdminUser` 判断 `ROLE_ADMIN`，不再通过任意管理权限推断管理员身份。
- App 统一使用 `userIsAdmin`，首页和认证后的根 Stack 共用同一角色判定。
- Spring Security 对 `/admin/**` 统一要求 `ROLE_ADMIN`；Controller 的 `@PreAuthorize` 再校验固定端点权限。
- 不引入动态权限端点，不改变现有工单可见范围和三类工单处理角色。

## 验收

- 具有管理权限字符串但没有 `ROLE_ADMIN` 的用户访问管理接口返回 `403`。
- 具有 `ROLE_ADMIN` 但缺少目标端点权限的用户访问该接口返回 `403`。
- 同时具有 `ROLE_ADMIN` 和目标端点权限的用户访问该接口成功。
- Web 与 Android 均验证管理员可见、非管理员不可见；Android 冷启动后正确恢复管理员会话。

## 回滚

- 后端可回滚到 `localhost/e-report-backend:20260805-unified-work-orders-2`，前端可回滚到 `localhost/e-report-frontend:20260805-unified-work-orders-5`。
- Android 已发布版本不可原地降版；如需回滚客户端行为，应发布更高 `versionCode` 的修复版本，并保留后端角色边界。
