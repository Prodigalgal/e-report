# UTC+8 展示契约（5.8.4）

## 目标

数据库继续统一保存 UTC `LocalDateTime`，所有面向用户的时间展示固定使用 `Asia/Shanghai`（UTC+8），不受浏览器、Android 设备或 JVM 默认时区影响。

## 范围

- WEB、APP 的工单列表、详情、处理时间线、会话、授权有效期、用户/组织、配置、观测、发布中心和导入导出界面。
- 授权有效期的日期选择器：显示 UTC+8 墙上时间，提交时转换回 UTC `LocalDateTime`。
- 邮件/Markdown 通知、工单与用户电子表格、导出文件名。
- 工单编号日期前缀按 UTC+8 的业务日期生成。

## 非目标

- 不修改历史数据库数据，不修改 SLA、会话、Token、通知调度等基于 UTC 的业务计算。
- 不把观测埋点、接口签名、内部重试时间等机器字段改成展示时区；这些字段继续使用绝对 UTC 时间。
- 不改变 API 的字段类型和 JSON 兼容形状；前端按 UTC 解析无时区 `LocalDateTime`。

## 实现约定

- WEB `src/utils/formatDate.js` 和 APP `src/utils/format.ts` 是唯一展示入口，显式固定 `Asia/Shanghai`。
- 后端 `com.pro.ereportbacken.time.DisplayTime` 负责将 UTC 存储值转换为邮件/电子表格展示值。
- 所有 `datetime-local` 和原生日期选择器输入都按 UTC+8 墙上时间解释，再写回 UTC。

## 验收

- `2026-09-16T09:21:50` 在 WEB、APP、邮件和电子表格均显示为 `2026-09-16 17:21:50`（按各界面精度显示）。
- 在 UTC、UTC+8、America/New_York 等设备时区运行，展示结果和授权时间回写结果一致。
- 数据库字段仍为 UTC，历史工单无需迁移；回滚代码可继续读取原有 UTC 数据。

## 验证

- WEB：`npm test`、`npm run lint`、`npm run build`。
- APP：`npm test`、`npm run typecheck`、`npm run version:contract`，并在 UTC 与 America/New_York 时区执行 UTC+8 单元测试。
- Backend：`gradlew.bat test --no-daemon --console=plain`。
