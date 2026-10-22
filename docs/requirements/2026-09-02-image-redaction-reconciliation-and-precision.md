# 图片脱敏巡检与精细化打码（5.6.5）

## 目标

补齐历史图片未进入脱敏队列的问题，并降低打码区域过大的误伤，保持上传链路异步、原图可审计、租户数据隔离。

## 范围

- 后端按租户、固定批次、Redis 分布式锁定期扫描工单图片附件。
- 通过已有 `file_transfer_upload_sessions` 关联历史附件，复用 `ImageRedactionService.enqueue` 的唯一约束实现幂等入队；不创建虚构上传会话，不修改工单数据。
- 已存在的脱敏任务仅补齐附件关联；终态失败任务不自动重试，避免损坏文件或模型问题形成无限重试，并在巡检日志中暴露待处理数量。
- Worker 支持历史根目录 UUID 文件，同时拒绝目录穿越、`_quarantine`、`_sanitized`、`_zip-cache` 等保留目录输入。
- 打码先合并原始检测框，再按比例和最大像素扩展；相邻但不重叠的目标不因 padding 链式合并。
- 保留原始文件，结果继续写入 `_sanitized/{jobId}.jpg`；视频不纳入本期处理。

## 非目标

- 不做生产环境自动巡检开关变更或批量生产处理；先部署测试环境验收。
- 不新增数据库表或迁移。当前历史图片均可由附件存储名匹配既有上传会话；若未来发现无法匹配的记录，只告警并进入人工治理清单。
- 不引入新的检测模型，不通过扩大框体弥补模型漏检。

## 版本与配置

本轮作为后端/Worker 现有图片脱敏契约的补齐和精度修复，候选版本占用 `5.6.5`。新增配置：

- `IMAGE_REDACTION_RECONCILER_ENABLED`
- `IMAGE_REDACTION_RECONCILER_INITIAL_DELAY_MS`
- `IMAGE_REDACTION_RECONCILER_INTERVAL_MS`
- `IMAGE_REDACTION_RECONCILER_BATCH_SIZE`
- `IMAGE_REDACTION_RECONCILER_MAX_BATCHES_PER_RUN`
- `IMAGE_REDACTION_PADDING_RATIO`（默认从 `0.12` 收紧为 `0.05`）
- `IMAGE_REDACTION_MAX_PADDING_PIXELS`（默认 `16`）

## 验收标准

1. 插入或准备一条没有 `image_redaction_job_id`、但能匹配已完成上传会话的历史图片，巡检后只生成一条任务并回填附件关联；重复运行不生成重复任务。
2. 无法匹配上传会话的图片不被静默删除、不被错误入队，并在租户维度产生结构化告警摘要。
3. 已有 `_quarantine` 新文件仍能处理；历史根目录文件能处理；保留目录和目录穿越路径被拒绝。
4. 两个相邻但不重叠的检测框不会因 padding 被合并；padding 不超过配置的最大像素，图片主体之外区域不被无界扩展。
5. Worker 单元测试、后端测试和构建通过；测试环境只验证脱敏任务和读取结果，不创建工单、通知或生产数据。
6. 本轮未获生产发布授权前，不切换生产 Deployment、不批量处理生产图片。

## 回滚

测试候选使用不可变镜像标签。回滚只恢复测试 Backend/Worker 到上一镜像；不删除已生成的脱敏结果和任务记录。生产发布前保留数据库、对象存储和 Deployment 备份。
