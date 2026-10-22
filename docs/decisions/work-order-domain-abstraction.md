# 统一工单领域抽象

## 决策

系统只保留一个 `WorkOrder` 聚合。普通故障、未关箱和箱体破损换箱由 `WorkOrderType` 区分，共享报单、回单、关闭、附件、物料、定位、查询、统计和通知流程。

## 边界

- `WorkOrder` 负责公共状态流转与聚合内一致性。
- `WorkOrderType` 负责类型专有字段初始化和回单规则，公共应用服务不判断具体类型。
- `WorkOrderAccessPolicy` 负责当前用户、类型权限、本人报单可见性、附件权限和查询范围。
- `WorkOrderService` 负责编排事务、文件归集、持久化和通知，不复制三套业务流程。
- `WorkOrderAttachmentService` 内聚分片上传归集、50MB 总量校验、附件读取权限和 ZIP 归档；`WorkOrderService` 不直接处理文件系统。
- Web 端使用唯一 `WORK_ORDER_CATALOG` 派生表单、菜单、筛选和权限；Android 使用唯一 `WORK_ORDER_TYPES` 目录。
- `/work-orders` API 和 `reportType` 字段是统一工单契约，不为三种类型新增平行端点。

## 类型扩展

新增工单类型时必须完成：

1. 增加 `WorkOrderType` 及其类型规则。
2. 在 `PermissionCatalog` 固化该类型的报单、回单、查看和下载权限。
3. 在 Web/App 工单目录增加展示元数据。
4. 增加类型规则、权限范围和完整报单回单测试。

## 依赖方向

Controller 只依赖统一工单应用服务和附件服务；应用服务依赖 `WorkOrder`、`WorkOrderType`、`WorkOrderAccessPolicy` 与 Repository。类型规则不进入 Controller、Repository 或前端路由，文件系统和传输会话也不进入工单聚合。这样公共流程保持稳定，新增类型只扩展类型规则和目录元数据。

## 持久化说明

本次只重构领域与应用层。现有 MySQL 物理表 `fault_reports` 和外键列 `fault_report_id` 暂作为持久化映射细节保留，避免将无业务收益的在线 DDL 与应用重构同时发布。后续若统一物理命名，应作为独立停机迁移执行，包含备份、外键检查、逐表行数校验和回滚脚本。
