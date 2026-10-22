# 企业微信通知单向集成决策

## 背景

企业微信“会话内容存档”需要付费且依赖企业认证，不适合作为当前工单系统的群聊报单入口。项目不再建设企业微信群消息监听、指令解析、材料归集和指令回单闭环。

## 决策

- 企业微信只作为通知中心的单向通知通道。
- 任意工单上报成功后，由 `NotificationCenterService` 触发 `WeComNotificationChannel`，向配置的企业微信群机器人 webhook 发送一条 Markdown 消息。
- 不再保留 `integration.wecom.command.*` 配置项、自建应用回调接口、会话内容存档归一化入口、企业微信 API relay 和 archive collector。
- 群机器人 webhook 通过 `notification.wecom.webhook-url` 或部署环境变量 `NOTIFICATION_WECOM_WEBHOOK_URL` 配置。

## 验收

- 工单创建成功后，如果 `notification.center.enabled=true` 且 `notification.wecom.enabled=true`，企业微信群收到新工单通知。
- 后端不暴露 `/integrations/wecom/callback`、`/integrations/wecom/archive/messages` 和 `/admin/wecom-groups`。
- 新部署不再要求 `e-report-wecom-command` secret。
