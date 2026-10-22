# 个人 MCP 接入需求

- 状态：Backend MCP 协议与受控转发已实现；清理后的 `6.0.3` 候选需要重新构建和部署，真实 MCP 客户端端到端验收待做。Android `versionCode=5000003`。
- 日期：2026-09-29。
- 目标：让每位用户都能使用自己的身份，把 e-report 接入兼容 MCP 的 AI 客户端，获得与本人当前授权一致的工作辅助能力。
- 关联：个人 API Token；旧 `GET /ai/guide` 需求已由 MCP 工具发现替代。

## 目标与边界

- 每个用户使用自己的个人 API Token，不使用共享账号、共享 Token 或管理员代签 Token。
- MCP 工具的可见范围不得超过该 Token 已选择且用户当前仍有效的权限；资源关系、组织范围、授权有效期和租户隔离继续由 Backend 统一校验。
- Token 到期、撤销、用户停用或用户权限收缩后，MCP 后续请求立即按现有 Token 认证结果拒绝或收缩。
- MCP 调用记录至少关联用户、Token ID、工具名、请求 ID、结果和时间；不得记录 Token 明文或 Authorization Header。
- Token 只通过标准 Bearer Header 提交，不能通过 URL、查询参数、工具参数或 MCP 元数据传递。
- MCP 不得提供 API Token/Session 管理、账号凭据、授权委派、系统配置、系统根账号、租户切换等管理控制面能力。
- 任何写工具只能调用现有业务 API，并同时受个人 Token 权限、用户当前授权、组织范围、资源关系、业务状态和幂等规则限制。MCP 不拥有独立授权绕过路径。

## 与现有能力的关系

现有个人 API Token 已按“用户当前授权 ∩ 创建时选择权限 ∩ 当前资源与组织范围”动态判权，Token 明文只显示一次。MCP Server 直接读取 Backend 的内部能力目录和当前认证上下文，按当前 Token 权限生成 `tools/list`，不另维护一套权限清单，也不另建用户、角色、Grant 或数据范围模型。旧 `GET /ai/guide`、Controller、DTO 与权限指南响应已从当前 API 契约移除；MCP `server/discover`、`tools/list` 和 `tools/call` 是新的客户端入口。每次工具调用仍须携带原用户的 Bearer Token 调用 Backend，由现有 API 实时执行授权、组织范围和资源关系校验。

普通用户的 Token 自助能力已有系统基线：`SystemGrantProvisioningService` 为既有用户和新建用户配置本人 Token 的查看、创建与撤销权限；`ApiTokenService` 的对应操作按当前用户所有权访问，不允许管理其他人的 Token。

现有 API Token 管理动作仍由 `api-token:view/create/revoke` 显式权限控制。要达到“每位用户都可接入”的目标，验收必须确认普通用户都能安全地创建、查看、撤销本人 Token；如当前默认岗位未授予这些自助权限，应通过自助动作的所有权约束调整授权基线，不得扩大他人 Token 管理范围。

历史 API Token 需求曾把 MCP Server 列为非目标。本文件按 2026-09-29 的新需求 supersede 这一项非目标；原 Token 的保密、动态权限交集、限流、审计及撤销规则继续有效。

## 验收标准

- 普通用户可使用自己的 Token 配置 MCP 客户端；Token 创建、一次性展示、撤销和使用权限均保持用户所有权边界。
- MCP 返回的工具随 Token 有效权限收缩；对不可用工具的直接调用返回明确拒绝，不泄露其他租户或组织的数据。
- 覆盖只读、写入、过期、撤销、停用、权限收缩、超限、重复请求、跨租户 IDOR、提示注入要求越权工具调用等安全场景。
- 工具目录和说明与 Backend 实际 API 契约保持一致；写操作保留原 API 的版本冲突、幂等和允许操作校验。
- WEB、APP、Backend 和所选 MCP 部署组件的 Token 传递、日志脱敏、限流、监控和版本契约通过验证。

## 已确认的实现边界

- MCP Server 与 Backend 同进程，使用 Streamable HTTP，公开入口为 `/api/mcp`。
- 每次 `tools/list` 按当前 API Token 动态生成工具目录，只暴露 `mcpSupported=true` 的 capability；本版本基于 API Token 当前允许调用的业务 API，不提供被 API Token 拒绝的管理控制面。
- 工具调用必须重新验证请求 Token，并通过既有 HTTP API 执行业务操作，保留 Controller、Service、资源关系、组织范围、幂等及审计规则。
- Streamable HTTP 实现 MCP `2026-07-28`：POST JSON-RPC、`server/discover`、`tools/list`、`tools/call`、`ping`、每请求版本元数据与标准头校验、Origin 校验和 API Token 认证；不启用 GET、SSE、协议会话或旧版 `initialize` 握手。
- MCP 工具仅暴露个人 API Token 可访问的工单/统计业务 API，不暴露附件下载或管理控制面。工具调用再经本地 Backend HTTP API 转发，使用原 Bearer Token；请求体与响应体各限制为 1 MiB，附件上传不在当前能力范围。
- 现有 API Token 限流按 HTTP 请求计数：一次 `tools/call` 的 MCP 入站认证和 Backend API 转发各计一次；默认 `300/min` 下，每个 Token 单独用于 MCP 时最多约 `150` 次工具调用/分钟，和其他 API 请求共享额度。
- MCP `annotations` 和工具说明可标记高风险、建议兼容客户端先确认；协议本身不构成强制人工确认门。必须人工批准的动作仍须由既有 Backend 权限和业务流程强制控制，不能依赖模型遵循描述。

## 尚待确定的实现边界

- 是否支持多个 MCP 客户端配置指南、管理员发布受控服务器 URL，以及 Token 在客户端侧的安全保存说明。
- 客户端配置、真实 API Token 和用户权限下的端到端测试需在可用测试环境完成；本地集成测试只证明协议处理、权限过滤和受控转发。
