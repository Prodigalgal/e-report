# 工单生命周期与多租户生产迁移

## 目标

将生产系统从三状态、全局账号语义迁移到六状态工单生命周期、漳州市默认租户、组织数据范围和分层配置模型，并保持全部存量账号、工单、附件、物料和 App 发布数据。

## 发布版本

- Backend：`localhost/e-report-backend:20260806-zhangzhou-lifecycle-2`
- Frontend：`localhost/e-report-frontend:20260806-zhangzhou-lifecycle-1`
- Android：`1.0.27` / code `28` / runtime `android-1.0.27-native28`
- Android stable release：ID `29`
- Liquibase tag：`20260806-zhangzhou-lifecycle-1`

`-2` 后端只修正通知 Outbox 的按通道独立重试，不改变数据库 Schema 或 API 契约。

## 初始化数据依据

- 漳州市 4 个市辖区、7 个县的清单以福建省财政厅发布的[漳州市行政区划概况](https://czt.fj.gov.cn/zwgk/tzgg/202402/P020240202749874620233.pdf)为核对依据。
- 区县管理员初始密码使用项目确认的“区县首字母 + `@` + 区县邮编”规则；邮政资料结合[中国邮政公开信息](https://www.chinapost.com.cn/xhtml1/report/2411/9240-1.htm)和[漳州市公共资源交易中心公开联系信息](https://gcjyzx.zhangzhou.gov.cn/gcxx/001001/001001001/20251111/aae4717d-f1b8-49a1-9142-a8957c4a398a.html)核对。

## 停写与备份

- 停写开始：2026-08-06 16:40:21 CST。
- 新后端恢复：2026-08-06 16:43:36 CST。
- 备份目录：`/data/e-report/backups/pre-lifecycle-production-20260806-1640`。
- 备份内容：MySQL 单事务逻辑备份、上传目录、backend/frontend Deployment YAML、Pod 状态、业务计数和全部既有 App 发布文件 SHA-256 清单。
- `SHA256SUMS` 全量校验通过。

停写后的迁移前计数：

| 对象 | 数量 |
| --- | ---: |
| 用户 | 59 |
| 工单 | 17 |
| 附件 | 3 |
| 工单物料 | 3 |
| 上传会话 | 16 |
| App 发布记录 | 25 |

## Liquibase 执行

生产 backend 先缩容为 0，再运行独立 Job `e-report-liquibase-production-20260806-1640`。应用 Deployment 明确设置：

```text
SPRING_LIQUIBASE_ENABLED=false
DEFAULT_TENANT_GROUP_CODE=FUJIAN_PROVINCE
DEFAULT_TENANT_CODE=DEFAULT_CITY
```

执行结果：

- 13 个 MySQL changeset 成功，3 个 H2 changeset 因 DBMS 不匹配被过滤。
- Liquibase 报告 `Rows affected: 579`。
- `DATABASECHANGELOG` 为 13 条，`DATABASECHANGELOGLOCK.LOCKED=0`。
- 正式迁移前，最新生产快照还独立完成两次执行：首次 `Run: 13`，再次 `Run: 0 / Previously run: 13`。

## 数据校验

迁移后、应用启动前：

- 用户 59、工单 17、附件 3、物料 3，均与停写备份一致。
- 默认租户 1 个、组织 12 个，其中区县组织 11 个。
- 用户缺失租户/组织、工单缺失租户/责任组织、跨租户组织引用、重复租户用户名、非法状态均为 0。
- 历史状态映射为 `SUBMITTED=16`、`PENDING_VERIFICATION=1`。
- 历史 `longwen` 和 `dongshan` 账号 ID 不变，组织分别更新为龙文区和东山县。

应用初始化后：

- 建立或收编 11 个区县管理员，总用户数 68。
- 区县管理员没有 `TENANT_ALL`，`ROLE_COUNTY_ADMIN` 均为本区县 `ORG_TREE`，`ROLE_USER` 为 `SELF`。
- `xiangcheng`、`longwen` 使用规定初始密码完成真实 API 登录，仅可见本区县配置；访问兄弟区县返回 403。
- 市级管理员可见租户默认、根组织和 11 个区县共 12 个组织作用域。

## 运行态验收

- backend、frontend、MySQL、Redis 均为 `1/1 Running`，新 Pod 重启数为 0。
- 主域、relay、`IP:60080` 的 Web 和认证 API 均返回 200。
- 生产浏览器以区县管理员完成配置中心桌面与 `390x844` 移动 smoke：无横向溢出，console/page error 为 0。
- 新工单 `#26` 产生 `WORK_ORDER_CREATED:dingtalk/email/wecom` 三条独立 Outbox，均一次完成；单一通道失败不再触发其他成功通道重发。
- Android code24 被最低版本策略要求强制升级；code26 可选升级；code28 不再提示升级。
- R2 和源站备用 APK 均支持 `HEAD 200`、`Range 206`，整包回读 SHA-256 与本地、发布记录一致。

## 回滚边界

后端 `-2` 无 Schema 变化，可直接回滚到 `20260806-zhangzhou-lifecycle-1`，但会恢复聚合通知重试并重新引入跨通道重复风险。

如需整体回到迁移前版本，必须：

1. 先停止当前 backend 写入。
2. 导出并单独保全迁移后新增的用户、工单和附件。
3. 使用备份目录中的 `fault_reporter_db.sql`、`uploads.tar.gz` 和 `runtime-before.yaml` 恢复。
4. 验证计数和文件哈希后再启动旧镜像。

迁移恢复服务后生产已继续新增工单，因此禁止直接覆盖恢复迁移前数据库；优先采用前向修复。
