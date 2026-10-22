# CGNAT 认证保护与已接工单可见性修复

## 目标

- 消除代理链、运营商 NAT/CGNAT 让多名用户共用登录限流桶和失败冷却的问题。
- 在仍然保护登录容量与防暴力破解的前提下，以账号、设备为主要安全主体，以精确 IP 和全局桶为高阈值防洪主体。
- 让具有对应工单类型查看权限且数据授权覆盖工单责任组织的处理员，在他人接单后仍能查看列表和详情。
- 移除历史 BCrypt 账号在同一次成功登录中的重复密码校验，保留修改密码流程的独立安全校验。

## 范围

- Backend：认证挑战、登录限流、失败风险、历史密码编码升级、工单范围策略。
- 数据：使用 Liquibase 将内置处理员角色的 `view` 范围从“可接或参与”迁移为授权组织范围。
- 边缘入口：形成可信源地址传递候选；公网 TCP 转发层使用 PROXY protocol，Envoy 解析后再向 Nginx/Spring 传递。
- 测试：账号/设备/IP/全局分层限流、CGNAT 共享网络失败隔离、移动网络切换、已接工单列表与详情、密码并发升级。

## 非目标

- 不按 IP、`/24` 网段或运营商出口识别用户身份。
- 不放宽接单、接受派单、回单、挂起、调单等动作权限。
- 不改变提单员只能查看本人所提工单的规则。
- 本轮不直接切换生产边缘入口；PROXY protocol 必须先完成测试环境整链验证和回滚演练。

## 关键规则

1. 未登录认证接口依次使用全局、精确 IP、账号、可信设备/安装实例四层独立桶；IP 阈值只承担防洪，不能承担用户级冷却。
2. 登录失败的阶梯冷却只作用于账号和设备；`/24` 网段只保留聚合观测计数，不参与验证码模式或冷却计算。
3. 登录挑战绑定账号、客户端类型、设备密钥/安装实例、WEB 指纹和一次性 nonce，不绑定可能在 Wi-Fi/蜂窝网络切换时变化的公网 IP。
4. 工单 `view` 决定可见数据面；处理员的 `view` 使用 `GRANT_SCOPE`。处理动作继续使用 `PENDING_OR_CURRENT_HANDLER`、`CURRENT_HANDLER` 等关系策略。
5. `AuthenticationManager` 返回成功后即视为旧密码已经匹配；密码升级使用认证时读取的编码值做数据库 compare-and-set，避免再次 BCrypt，也避免并发改密时覆盖新密码。

## 影响文件

- `e-report-backen/src/main/java/.../controller/AuthController.java`
- `e-report-backen/src/main/java/.../service/AuthenticationRateLimitService.java`
- `e-report-backen/src/main/java/.../service/AuthRiskService.java`
- `e-report-backen/src/main/java/.../service/AuthChallengeService.java`
- `e-report-backen/src/main/java/.../service/UserService.java`
- `e-report-backen/src/main/java/.../security/BuiltInRoleCatalog.java`
- `e-report-backen/src/main/resources/db/changelog/`
- `e-report/nginx.conf`
- `deploy/dedicated-cloud/`
- 相关后端单元/集成测试与前端 Nginx 契约测试。

## 验收标准

- 同一真实 IP/同一 `/24` 下不同账号、不同设备的正常登录不会因他人的密码错误进入共享冷却。
- 账号或设备连续失败仍会进入交互验证码和阶梯冷却；高频单 IP/全局攻击仍会被高阈值桶拒绝。
- challenge 与 login 之间公网 IP 变化时，只要其他绑定上下文不变，挑战仍可使用。
- 同类型、同授权组织的未参与处理员可以在工单被他人接收后看到搜索结果和详情，但 `allowedActions` 为空。
- 不同类型或授权组织不覆盖的处理员仍返回 403/不可见。
- 历史 BCrypt 首次登录只执行一次 BCrypt 校验和一次 Argon2id 编码；并发改密不会被登录升级覆盖。
- Liquibase MySQL/H2 迁移、后端测试和前端 Nginx 契约测试全部通过。

## 测试方式

- `./gradlew test`（Backend）。
- `npm test` 与 `npm run build`（WEB）。
- 测试 namespace 中验证 HAProxy PROXY v2 -> Envoy -> Nginx -> Spring 的真实 IP 链，并从同一出口模拟多账号登录。
- 生产只在候选验证通过后滚动发布；发布前后核对 429、登录 P95、Backend Ready/重启数和认证失败风险键。
