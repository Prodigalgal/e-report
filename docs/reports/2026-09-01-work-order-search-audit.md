# 工单万能搜索 500 审计与修复记录

## 现象

2026-09-01 11:26 左右，APP 工单查询输入“冯达明”后返回 500。服务端 trace `529226fa-1f2e-4200-93bc-b5b6ff3cf722` 对应 Hibernate 报错：`Operand of 'like' is of type 'java.lang.String' which is not a string (its JDBC type code is not string-like)`。

## 根因

`WorkOrder.reason`、`resolutionDetails`、`closureReason`、`replacementResult` 使用 `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)` 映射 PostgreSQL `TEXT`。旧实现把这些表达式直接放入 `COALESCE(..., '')` 后执行 `LIKE`。Hibernate 7 的语义校验不会把 `LONG32VARCHAR` 当作 string-like 类型，因此在 SQL 生成前就拒绝请求。

## 修复

- `WorkOrderSpecification` 的长文本筛选统一通过 `Expression<String>.as(String.class)` 建立显式字符串表达式，再执行 `COALESCE` 与 `LIKE`。
- `reason` 独立筛选也复用同一 helper，避免关键词筛选修复后，按问题描述筛选仍触发相同异常。
- 普通字符串字段的 `COALESCE` 改为显式 typed path + `CriteriaBuilder.literal("")`，减少 Hibernate 泛型推断漂移。
- `AppUpdateService` 的安装包说明搜索同样使用显式字符串表达式；用户查询的可空联系方式和拼音字段统一使用 typed coalesce。

## 审计结论

全仓检查了 `criteriaBuilder.like`、`COALESCE` 和 `LONG32VARCHAR` 的组合：只有工单长文本路径会触发本次异常；安装包说明、用户、运行诊断搜索均为普通字符串列或已改为显式 typed expression。未发现其它直接对工单长文本执行 `LIKE` 的 JPQL/native 查询。

## 验证

- Backend：`EReportBackenApplicationTests.fuzzySearchSupportsTextColumns` 覆盖问题描述、回单详情、验收意见、更换结果，测试通过。
- Backend：`AppUpdateServiceTests` 与工单关键词回归一起通过。
- PostgreSQL 运行态：发布测试候选后使用真实测试库执行关键词查询，并检查 Backend 日志无 `not string-like`。

## 兼容与发布

本修复保留工单查询参数、权限和数据内容；由于同时加入登录设备分页这一新增能力，候选按版本契约提升为 `5.5.0`。会话接口返回统一分页 DTO，并新增有效其他会话数量字段；新增会话分页索引迁移。生产保持原制品，待测试环境验收后再决定发布。
