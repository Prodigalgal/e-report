# 发布制品携带数据库迁移

个人邮件订阅属于 `6.4.0`，需要迁移 `074`。当前 PostgreSQL 使用独立 changelog；测试和生产 Backend 均不依赖应用启动时的 Spring Liquibase 自动迁移，测试数据同步工具的镜像版本也固定在较早版本。

## 决定

- 发布脚本为目标环境生成独立 Kustomize patch，在 Backend Pod 中加入数据库迁移 init container。
- init container 与应用使用完全相同的不可变 Backend 镜像，通过镜像内既有 `LiquibaseMigrationMain` 执行 PostgreSQL changelog。
- 数据库迁移密码仅注入 init container，引用当前环境既有 `e-report-postgresql` Secret；应用数据库账号不增加 DDL 权限。
- 迁移结束后才启动新应用。多副本由 Liquibase 数据库锁串行执行；再次启动、回滚后重启或测试数据恢复后重启均按台账幂等执行。
- 继续保留测试自动部署、生产验收后批准的发布顺序。更新测试配置不会更新生产资源。

## 取舍与回滚

- 不新增独立镜像或额外常驻组件，迁移与应用版本不会脱节；代价是每次 Pod 启动会进行一次短暂的迁移检查。
- `074` 仅添加默认开启的四列，与上一版应用兼容。应用回滚可以保留新增列及迁移 patch；不自动执行删除列回滚，避免丢失个人选择。
- 破坏性迁移仍需另外定义分阶段升级和回滚方案，不能依赖 init container 自动解决协议兼容。
- 验证包含迁移入口启动、严格依赖构建、配置 patch 合成、测试 Pod init container 日志、Liquibase 台账和实际列默认值。
