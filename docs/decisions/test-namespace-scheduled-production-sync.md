# 决策：独立测试命名空间与定时生产数据同步

## 决策

1. 测试运行态固定在 `e-report-test` namespace，使用独立的 MySQL、Redis、上传目录、安装包目录和密钥；不复用 `e-report` 的 Service、PVC、Secret 或 Gateway。
2. 同步由 namespace 内 CronJob 编排三个容器：控制器负责停/启测试后端，MySQL 容器负责生产只读导出和测试导入，Redis 容器负责清空测试缓存。
3. 同步时间为每天北京时间 03:30，`concurrencyPolicy=Forbid`；任务超时或失败不会启动测试后端，保留失败 Job 供排查，避免半同步数据被使用。
4. 测试后端启用 `e2e` profile 并关闭所有通知和出站开关；同步后清理出站、日报、观测、幂等和上传会话数据。

## 取舍

- 采用完整逻辑 dump，而不是跨 namespace 直接共享 MySQL 数据目录，保留 MySQL 一致性并避免底层文件耦合。
- 使用独立 hostPath 目录而不是生产目录的子目录，误挂载时更容易通过路径审计发现。
- 测试空间不挂公网入口，访问需要集群权限；这比复制生产密码后开放公网更符合测试数据隔离要求。

## 回滚

- 删除 `e-report-test` namespace 前，先停止并删除 CronJob；生产 namespace 不受影响。
- 修改同步逻辑时只暂停 CronJob、检查测试 Pod 和数据库，再恢复，不需要回滚生产应用或 Liquibase。
