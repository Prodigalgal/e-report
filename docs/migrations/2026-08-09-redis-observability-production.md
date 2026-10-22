# Redis 与客户端可观测性生产迁移记录

> 状态：已完成。数据库回退必须使用本次停写备份；不要直接删除 `client_observation_events`，除非观测数据已导出且应用已回滚。

## 发布范围

- 后端：Redis 鉴权快照、组织/SLA/配置/统计缓存，登录挑战与限流，幂等响应加速，地图游标，分布式任务锁，R2 同步状态和有界观测队列。
- Web/Android：轻量 Telemetry、离线保留、网络恢复补传、错误编号、定位/网络/运行环境诊断和管理端查询。
- 数据：Liquibase 016 新增 `client_observation_events`，主键为 UUIDv7 对应的 MySQL `BINARY(16)`，按租户、组织、时间、级别和功能建立索引。
- 基础设施：Redis 7.4 单实例认证、AOF `everysec`、周期 RDB、192MB `maxmemory`、`noeviction` 和宿主机持久卷。

## 迁移与备份

| 项目 | 生产证据 |
| --- | --- |
| 停写备份 | `/data/e-report/backups/20260809-024639-20260809-redis-observability-1` |
| 数据库备份 SHA-256 | `5f3cf5efe305642f634b430190665ca80365113e136ab2557bbfbc25faed5989` |
| 旧 Redis RDB SHA-256 | `ee01722615f60641d86be6be062a97a54c144c808f78822fe751d4d76a44de41` |
| 演练库 | `fault_reporter_rehearsal_redis_0219`，首次 `45 -> 46`，二次执行 `Run: 0` |
| 正式迁移 Job | `e-report-liquibase-production-redis016-1`，`Run: 1`，`Previously run: 45` |
| 数据不变量 | 用户 `117`、工单 `77`、附件 `131`，迁移前后不变 |
| 新表结构 | `client_observation_events.id = BINARY(16)`，8 个独立索引/约束 |

迁移前后后端保持 0 副本；结构与数据不变量通过后才应用候选清单。Liquibase 在克隆库完成首跑和幂等复跑，正式库只执行已演练的 MySQL changeSet。

## 运行态验收

- 生产镜像：backend/frontend `localhost/e-report-*:20260809-redis-observability-2`；后端运行镜像 ID `sha256:6254e1cb181670c96dfe7b5712861b5508987e2f764b5adb15cb7107b5dcf900`。
- Redis：未认证请求返回 `NOAUTH`；认证后 `PING`、AOF/RDB 状态、192MB 上限和 `noeviction` 正常；Pod 重启后测试键恢复，最终无拒绝连接、无淘汰。
- 缓存：鉴权、组织目录、SLA、系统配置和工单统计五个缓存族均在生产生成；浏览器验收时命中率约 52%，诊断 ready/processing/dead-letter 为 `0/0/0`。
- API：管理员挑战登录、`/auth/me`、工单统计、SLA、配置作用域、Redis 状态、观测列表/详情和批量上报均通过；敏感样本未写入 IMEI、Android ID、手机号或邮箱原值。
- Web：主域、relay 和 `IP:60080` 根路径均正确落到 `/e-report/`；区县管理员只看到南靖县诊断，桌面与 `390x844` 无横向溢出。浏览器验收发现并修复了 Jackson `JsonNode` 详情序列化问题。
- Android：`emulator-5554` 冷启动 3.4 秒，Redis 状态、诊断列表/详情和手动上传通过；手动诊断计数 `1 -> 2`，崩溃缓冲为空，未申请或采集硬件唯一标识。

## Android 发布

| 属性 | 值 |
| --- | --- |
| stable 发布 ID | `019fe432-30ea-77aa-8c3b-8f0df3af18be` |
| 版本 | `1.0.34` / code35 / `android-1.0.34-native35` |
| APK 大小 | `76,007,447` 字节 |
| APK SHA-256 | `77d7b92a0eddf5deeee6f2b509f903716291fe90eb2f441f340f8938a84e8304` |
| 固定入口 | `https://e-report.omnnu.xyz:60443/e-report/download/android` |
| R2 对象 | `android/stable/35/app-release.apk` |

固定入口 HEAD 返回 code35 和相同 SHA；R2 Range 返回 `206 bytes 0-1048575/76007447`；完整下载、VPS 源文件和本地 APK 哈希一致。code34 检查返回可升级且需要原生升级，code35 返回已是最新版本，API 暴露的 `apkUrl` 始终为固定主域名入口。

## 回滚

1. 应用优先回滚到 `20260809-redis-observability-1`；新增表、权限和配置可保留，不影响该候选运行。
2. 需要暂停客户端采集时，将 `observability.client.enabled=false` 写入对应租户/组织配置，无需删除历史事件。
3. Redis 缓存可直接清空并重建；恢复基础设施时使用备份中的 Deployment/Secret 和 RDB，保持 Service 名称与密码契约一致。
4. 只有必须完整回退数据库时，停写后校验备份 SHA-256 并整体恢复正式库；不要在应用运行期间手工逆向 UUID 外键或删除观测表。

## 已知非阻断项

- 既有部分用户手机号为空时，邮件收件人解析会记录“缺少可用通知联系方式并跳过”；这是既定兼容策略，不影响 Redis、工单或诊断链路。
- 应用内 Browser 的截图命令超时，因此 Web 以 URL、DOM、控制台、交互和视口尺寸作为验收证据；Android 已保留模拟器截图，生产页面行为已实际操作验证。
