# 邮箱发件池与工单调单通知

## 目标

管理员可在现有通知配置作用域下维护多个 SMTP 发件方，邮件发送按 Redis 原子游标轮询。工单派单或调单成功后，通过邮件 Outbox 通知当前接收人。

## 范围

- 复用现有市级默认、区县覆盖、通知权限和通知开关。
- 管理界面支持查看、添加、编辑、移除发件方；接口不返回 SMTP 密码，已有单发件方配置作为一个发件方读取。
- 所有现有邮件用途使用同一作用域发件池；重试时重新轮询。
- `ASSIGNMENT_RECEIVED` 邮件事件和工单事务原子写入 Outbox，按 assignment ID 定位接收人；待接受派单提示其接单，已生效调单提示其已成为当前处理人；投递时该 assignment 若已失效则跳过。
- 沿用既有邮件渠道开关及 Outbox 重试策略。
- 本功能按业务版本 `6.1.1` 对齐 Backend、WEB、APP 与 Android 版本契约。

## 非目标

- 不更换通知传输、SMTP 库、工单调单权限或移动端流程。
- 不在工单事务内直接发送邮件。
- 不新增账号邮箱验证、短信渠道或组织配置模型。

## 影响模块

- Backend：发件配置解析/保存、SMTP sender factory、Redis 游标、Outbox、工单生命周期、邮件通知与相应测试。
- WEB：通知中心发件方列表交互与请求 DTO。
- 发布版本契约：Backend/WEB/APP/Expo/Android 统一到 `6.1.1`。

## 验收标准

- 同一作用域下两个及以上可用发件方按并发安全的轮询游标发送；旧配置可正常读取；密码不出现在读取 DTO 或配置日志。
- 可管理发件方列表且保留作用域继承、授权码留空保留、撤销本级配置等既有行为。
- 派单和调单事务提交时仅增加 email Outbox 事件；当前接收人收到主题明确的通知，其他组织管理员不收到此事件。
- 邮件失败进入既有重试/失败状态；已过时 assignment 不会收到调单邮件；渠道关闭时不发送。
- Backend 相关单元/集成测试、WEB lint/build/test、版本契约校验通过。

## 验证方式

- Backend：发件配置服务、轮询、收件人解析、Outbox 处理及 `WorkOrderLifecycleIntegrationTests`。
- WEB：notification settings 单元测试、`npm run lint`、`npm test`、`npm run build`。
- 全仓：`node scripts/verify-version-contract.mjs`。
