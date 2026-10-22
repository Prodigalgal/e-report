# 生产数据清理

## 范围

本次清理删除工单、附件、回单物料、分片上传会话、外部指令/绑定、废弃的企业微信归集数据、非管理员用户和 Redis 缓存，并清空 `/data/e-report/uploads`。仅保留默认管理员 `admin`、固定角色与权限、系统配置以及正式 Android 发布记录和 `/data/e-report/app-releases`。

## 执行约束

- 执行前必须确认数据库中存在且仅存在一个 `admin`。
- 执行工具先将 MySQL 逻辑备份和上传目录归档写入 `/data/e-report/backups/<UTC时间>/`。
- 清理时将后端缩容到 0，数据库删除在单个事务中完成，事务提交后才删除上传文件。
- 默认测试用户初始化已从代码和 K8S 配置移除，避免重启后复活。
- 数据库、SMTP、管理员和高德凭据不再写入仓库，统一由 `服务器管理/private/e-report-runtime.env` 注入 K8S Secret。
- 生产环境关闭 Hibernate SQL 与参数 DEBUG 日志。现有 Controller DTO 映射仍依赖 Open EntityManager in View，待全部详情查询完成事务内 DTO 化后再单独关闭。
- 清理完成后恢复后端、清空 Redis，并验证业务表、用户、发布记录和文件目录。

## 命令

```powershell
python .\deploy\local-k8s\prepare-production-data.py inspect
python .\deploy\local-k8s\prepare-production-data.py execute --confirm RESET-E-REPORT-PRODUCTION
python .\deploy\local-k8s\prepare-production-data.py verify
```

## 回滚

数据库可从对应备份目录的 `fault_reporter_db.sql.gz` 恢复；附件可从 `uploads.tar.gz` 恢复。回滚前应先将后端缩容到 0，恢复数据库与附件后再恢复 Deployment。
