# 1.0.47 集中更新登录性能调查与生产只读压测报告

## 结论

本次“集中更新后登录至少等待 5 秒才成功”已在隔离环境用生产同镜像、同单 Pod、同 1536 MiB 容器上限复现。确定根因不是 APK 回源、VPS 10 MB 出口或 MySQL I/O，而是大量历史 BCrypt 账号在首次成功登录时同步执行密码迁移：

1. `DaoAuthenticationProvider` 先执行一次 BCrypt 密码校验。
2. `UserService.upgradePasswordEncodingIfNeeded` 再执行一次 BCrypt 校验。
3. 随后同步执行 Argon2id 编码，并在请求事务内更新用户密码。

24 个不同历史 BCrypt 账号同时首次登录时，服务端 `POST /auth/login` P50 为 5.234 秒、P95 为 5.515 秒，挑战、PoW 和登录完整链路 P95 为 5.857 秒，24 次均成功。该结果与用户观察到的“等待至少 5 秒后成功”吻合。

截至调查结束，生产仍有 43 个 BCrypt 账号和 35 个 Argon2id 账号。尚未完成首次迁移的历史账号仍会在后续登录时产生同类负载。

## 触发与放大因素

### 1. 历史会话没有跨安全升级迁移

1.0.45 将 APP 存储从旧的 `e_report_auth_token`、`e_report_auth_user` 改为 v3 token、refresh token、session id 和 v3 user 四项凭据。当前启动逻辑只有在四项新凭据完整时才恢复登录态；旧 key 只在新登录成功后删除，没有旧会话换取新会话的迁移路径。

因此，从 1.0.44 或更旧版本直接升级的用户会被要求重新登录。这是代码确认的集中登录触发条件；本次无法仅凭生产库还原每台设备升级前的 APP 版本，不能断言所有用户都由该路径触发。

### 2. 单 Pod 和 JVM 堆放大 Argon2 并发成本

- 生产 Backend：1 个 Pod，容器内存上限 1536 MiB。
- JVM 未显式设置堆上限，`MaxRAMPercentage=25`，实际 `MaxHeapSize=384 MiB`。
- Argon2id 参数：19 MiB、2 iterations、parallelism 1。
- 测试 Backend 在 768 MiB 容器上限、192 MiB Java 堆时，并发 8 已触发 `OutOfMemoryError: Java heap space`，堆栈位于 `Argon2BytesGenerator`；同时出现 Redis command 2 秒超时。
- 对齐生产 1536 MiB 上限后并发 24 未 OOM，但首次迁移登录已进入 5 秒区间。

### 3. 网络限流把所有代理后用户合并

`ClientRequestMetadataService` 只读取 `request.getRemoteAddr()`；生产 Nginx/Envoy 转发链路没有被后端按受信代理解析。生产不同用户的登录失败日志长期显示相同 `networkHash`，说明后端看到的是代理网络，而不是真实客户端网络。

直接影响：

- `/auth/challenge` 的 60 次/分钟限制可能变成全站共享。
- `/auth/login` 的 30 次/分钟限制可能变成全站共享。
- 单个用户的错误密码会增加所有代理后用户共享的 network 风险计数。

固定窗口限流会直接返回 429，不会主动等待 5 秒，因此它不是本次 5 秒成功样本的直接根因，但在更大规模集中登录时会造成额外失败和重试放大。

### 4. APP 本地安全链路增加可见等待

1.0.47 APP 登录还会依次执行设备身份读取、JS 到 native 的多次 SHA-256 PoW、用户名摘要、Android Keystore 签名、登录网络请求和四项 SecureStore 并行写入。

Android 16 模拟器对正式 1.0.47 APK 的一次冷启动有效登录，从点击登录到首页出现约 2.214 秒；同次生产服务端无并发登录通常约 0.1 至 0.2 秒。由于调试版分段埋点构建未在 10 分钟内完成，本次只能给出端到端墙钟时间，不能把剩余客户端耗时进一步精确拆分。

## 压测结果

### 完整登录链路，测试命名空间

环境：生产镜像 `20260813-v1.0.47-fantong-2`，单 Pod，1536 MiB 内存上限，独立 MySQL/Redis，通知、Outbox、日报全部关闭。

| 场景 | 并发 | 成功率 | 登录 P95 | 完整链路 P95 |
| --- | ---: | ---: | ---: | ---: |
| 同一已升级账号 | 8 | 16/16 | 0.921 秒 | 0.979 秒 |
| 同一已升级账号 | 16 | 24/24 | 2.407 秒 | 2.533 秒 |
| 同一已升级账号 | 30 | 30/30 | 2.746 秒 | 3.147 秒 |
| 不同历史账号首次迁移 | 8 | 12/12 | 2.477 秒 | 2.517 秒 |
| 不同历史账号首次迁移 | 12 | 12/12 | 2.922 秒 | 3.049 秒 |
| 不同历史账号首次迁移 | 16 | 16/16 | 3.515 秒 | 3.921 秒 |
| 不同历史账号首次迁移 | 24 | 24/24 | 5.515 秒 | 5.857 秒 |

### 生产公开入口

每项 100 次，并发 20：

| 接口 | 成功结果 | P50 | P95 | 备注 |
| --- | ---: | ---: | ---: | --- |
| `/` | 100/100 | 94.9 ms | 148.5 ms | 前端静态入口 |
| APP 更新检查 | 99/100 | 96.8 ms | 115.3 ms | 1 次客户端侧 15 秒 `URLError`，未形成服务端 5xx |
| `/download/android` HEAD | 100/100 | 99.3 ms | 153.4 ms | 100 次均为 302 到 R2 |

APK 由 VPS 返回 302 后直接从 R2 下载，不经过 VPS 回源传输，集中下载不会占满 VPS 的 10 MB 出口。

### 生产已授权只读接口

生产只创建 1 条带唯一标记的测试会话，随后压测均为 GET；结束后该会话已精确删除。每档 100 次，并发 50 的结果：

| 接口 | 成功率 | P50 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: |
| `/auth/me` | 100/100 | 148.2 ms | 299.2 ms | 316.3 ms |
| `/work-orders/statistics` | 100/100 | 141.6 ms | 263.6 ms | 295.9 ms |
| `/work-orders/search` | 100/100 | 700.1 ms | 1295.9 ms | 1437.0 ms |
| `/admin/users` | 100/100 | 460.0 ms | 707.6 ms | 861.5 ms |

只读接口没有出现 4xx/5xx；工单查询是四项中最重的读取，但不是登录等待的来源。

## 被排除项

- APK 下载回源：已排除，下载入口直接 302 到 R2。
- MySQL 连接耗尽：未出现；观测时 `max_connections=151`、`Max_used_connections=12`、`Threads_running=2`。
- MySQL 慢查询或锁等待：未看到 slow query/lock timeout 证据，读接口并发 50 仍全部成功。
- Redis 常态延迟：空闲时 0 至 1 ms，无 eviction；测试小堆 OOM 时出现的 Redis 超时是 JVM 资源争用的伴生结果。
- VPS 整机容量：4 核、约 7.8 GiB 内存，调查空闲时 CPU idle 约 95% 以上；没有历史分钟级 CPU/内存数据，不能据此断言发布峰值时也空闲。

## 可观测性缺口

`ObservationOperationInterceptor` 已计算登录成功耗时，但 `ObservationServerOperationRecorder` 会直接丢弃所有 `SUCCESS`。因此运行诊断无法还原今天每个成功登录请求的耗时和阶段，调查只能通过隔离复现完成。

下一版不应恢复大量 INFO 事件；建议只记录：

- `AUTH_LOGIN_SLOW`：成功但总耗时超过 1500 ms，WARN，采样并包含认证、密码升级、设备登记、会话创建的分段耗时。
- Redis 滚动直方图：每分钟 count、P50、P95、P99、5xx/429 数，不持久化每一条成功事件。
- JVM：heap used/max、GC pause、活跃认证 hash 数和被限流 hash 数。

## 修复优先级

### P0

1. 消除二次 BCrypt 校验。把密码升级接入 Spring Security 的 `UserDetailsPasswordService`，复用已经成功的认证结果，只执行一次旧密码校验和一次 Argon2id 编码。
2. 为密码 hash 增加独立有界并发舱壁，推荐初始上限 4，队列短且有明确 503/Retry-After；不能让 Tomcat 请求线程无限并发分配 Argon2 内存。
3. 在测试环境按生产规格验证 JVM 堆参数。建议从 `-Xmx768m` 或 `MaxRAMPercentage=50` 候选开始，以并发 30 首次迁移登录、GC、RSS 和 OOM 为验收依据，不能只提高容器 limit。

### P1

1. 由 Gateway/Nginx 清洗并重建转发头，Spring 仅信任受信代理链，恢复真实客户端 IP/网段。
2. 将登录限流拆为账号、设备、真实 IP 和全局保护四层；真实 IP 的正常上限不能低于集中发布容量，全局保护不得复用代理地址作为 subject。
3. Backend 至少做双副本候选压测；在当前单节点 4 核上重点验证总 CPU、堆、MySQL/Redis 连接和滚动发布，不机械套用 HPA。

### P2

1. 后续安全会话契约变更必须提供有期限的一次性迁移或明确分批重新登录方案，避免强制更新同时制造全量登录风暴。
2. 强制更新按区县或版本窗口分批开放，更新检查返回可控的 rollout cohort；R2 下载和登录容量分别治理。
3. APP 将 PoW 改为一次 native 批量计算，避免逐 nonce 调用 `Crypto.digestStringAsync` 跨 JS/native 边界；保留相同挑战契约和难度语义。

## 数据与运行态验收

生产压测前后保持一致：

- 工单 87
- 附件 218
- Outbox 572
- 日报分发 66
- Auth Session 82
- Trusted Device 36
- 诊断事件 978

测试临时用户、会话、限流/风险 key 已清理；测试 Backend 已恢复原镜像 `20260813-v1.0.47-county-admin-normalization-test-1` 和 768 MiB 上限。生产和测试 Backend 最终均 `Running / Ready`，重启数为 0。

本轮没有修改认证业务代码、生产配置或部署；只新增调查 task spec 和本报告。
