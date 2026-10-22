# 中文化、报单校验与派调单增强生产迁移

> 日期：2026-08-07  
> 当前状态：后端、Web 和 Liquibase 已投产；Android `1.0.29` / code30 已完成签名候选与模拟器验收，待 USB 真机验收后登记 stable 版本。

## 发布内容

- 用户界面、业务错误、角色、权限、生命周期事件、安装包和系统配置说明统一使用简体中文；移除用户可见的移动品牌或终端标识。
- Web、Android 和后端统一要求报单字段完整、定位坐标有效，并至少上传一份现场附件。
- Android 工单查询的工作队列、工单类型和处理状态改为紧凑选择控件；查询列表与最近工单统一进入详情页。
- 派单、调单候选人改为输入姓名或账号后查询，空关键词返回空列表，单次最多 20 人。
- 管理员调单改为事务内锁定工单并立即原子切换当前处理人；演练库覆盖连续两次调单、版本刷新和唯一当前槽位。
- 12 条故障时限策略全部放宽为原值两倍，28 张未闭环工单按迁移前当前阶段时长延后截止时间。
- Nginx 根路径与无尾斜杠路径使用相对重定向，保留公网 HTTPS、主机和显式端口。

## 数据迁移与回滚点

- 正式 Liquibase Job：`e-report-liquibase-production-cn-validation2-035101`。
- 变更标签：`20260807-cn-validation-reassign-2`。
- 执行结果：2 个 changeSet，`Rows affected: 59`，Job `Complete`。
- 停写备份：`/data/e-report/backups/20260807-034910-cn-validation-reassign-2/`。
- 数据库备份：`fault_reporter_db.sql.gz`，55,509 字节。
- 备份 SHA-256：`1558f1fa4a1e86014ff91dd64a79ca9e7f751c2e4e449fa635251a7f4109dc16`。
- 备份目录同时保存迁移前后 Deployment、HTTPRoute、集群状态、迁移日志和跨库核对结果。

回滚时先将后端缩容为 0，恢复备份目录中的数据库和旧 Deployment：

- backend：`localhost/e-report-backend:20260807-uuidv7-sla-outbox-4`
- frontend：`localhost/e-report-frontend:20260806-uuidv7-sla-2`

SLA 迁移修改了存量截止时间和策略值，应用与数据库必须作为同一回滚单元，不允许只回滚镜像。

## 迁移核对

- 用户：迁移前后均为 69。
- 工单：迁移前后均为 30。
- 时限策略：迁移前后均为 12，逐行核对五个时限字段均精确等于原值两倍，差异 0。
- 活动时限：28 行，逐单核对新截止时间等于原截止时间加原阶段时长，差异 0。
- 当前超时点未清空：0；历史曾超时标记差异：0。
- 中文配置说明差异：0；`ROLE_SLA_MANAGER`、`sla-policy:view`、`sla-policy:update` 中文名已生效。
- 用备份恢复临时库并按 `BINARY(16)` UUID 跨库 JOIN 核验；核验完成后临时库已删除。

## 生产发布与验证

- backend：`localhost/e-report-backend:20260807-cn-validation-reassign-2`，`1/1` Ready，重启 0。
- frontend：`localhost/e-report-frontend:20260807-cn-validation-reassign-2`，`1/1` Ready，重启 0。
- 生产 API：全局唯一账号登录成功，自动返回漳州市公司租户和中文角色；空候选查询为 0，关键词“简”返回 3 个匹配人员；生命周期事件均有中文标签。
- 报单 API：完整业务字段但附件列表为空时返回中文 `400`，工单总数保持 30。
- 公网：主域、备用域 `:60443` 与直连 `:60080` 的 `/`、`/e-report` 均相对跳转到 `/e-report/`；页面和认证挑战接口返回 `200`。
- 浏览器：桌面和 `390x844` 报单必填提示、查询到详情、搜索式调单均通过，控制台错误和警告均为 0；未确认调单、未创建生产工单。

## Android 候选

- 版本：`1.0.29` / versionCode `30` / runtime `android-1.0.29-native30`。
- APK：`e-report-app/android/app/build/outputs/apk/release/app-release.apk`。
- 大小：72,953,109 字节。
- SHA-256：`86a90e3d41a446f05a556584a5f4149c033369ad8c9e23382353a8f18fdafb6c`。
- 签名：APK Signature Scheme v2，证书 SHA-256 `c63061845c0ca95444e9e9507338cc25284f2b5b022f6662bf85045371cb2d27`。
- 模拟器：覆盖安装、登录、冷启动会话恢复、紧凑筛选、报单必填、查询到详情、搜索式调单和新 SLA 展示通过，`crash` 缓冲区为空。
- USB 真机：Windows 当前未枚举到 MTP、ADB 或 `VID_2717`，无线 ADB/mDNS 也未发现设备；在真机安装和流程通过前不登记 code30 stable 版本。

