# 中文化、报单校验与派调单生产增强

> 状态：已完成本地、模拟器、USB 真机、生产全流程验证并统一发布
> 日期：2026-08-07
> 等级：S3（跨后端、Web、Android、Liquibase、生产部署与源码发布）

## 目标

- 用户可见界面和可返回给用户的业务错误统一使用简体中文，移除“移动通信”“移动端”等品牌或终端标识文字。
- 报单页面展示的业务项全部必填，定位坐标必须有效，现场附件至少一份；Web、Android 和后端使用同一约束。
- Android 工单查询将工作队列、工单类型和处理状态收敛为紧凑选择控件。
- 全租户 SLA 接单、处理、验收和挂起时限统一放宽为当前值的两倍，并延长未闭环工单当前阶段期限。
- 派单、调单候选人改为按姓名或账号搜索后返回，空关键词不返回人员，单次最多 20 人。
- 管理员调单原子切换当前处理人，不再出现“提交成功但处理人未变化”；初次派单继续保留目标人员接受或拒绝。
- 修复公网根路径和无尾斜杠路径重定向丢失 HTTPS 与端口的问题。
- 生产验证完成后，将当前完整源码建立为 GitHub 私有仓库的可追溯主分支版本。

## 范围

- 后端：报单 DTO 与领域校验、候选人搜索 API、调单事务、中文业务错误、SLA 默认值与 Liquibase 数据迁移。
- Web：报单必填提示、中文文案、候选人搜索式派调单、公网 Nginx 重定向。
- Android：报单必填提示、查询筛选收敛、候选人搜索式派调单、中文化及版本升级。
- 运维：数据库迁移演练、生产备份、镜像发布、线上浏览器/API/真机验证、GitHub 私有仓库推送。

## 非目标

- 不改变六状态工单主状态机，不取消初次派单的接受/拒绝流程。
- 不重命名内部代码标识、API 字段、数据库表列、权限代码及第三方协议名。
- 不清除历史 `sla_ever_breached` 记录；放宽时限只修正当前阶段期限和当前超时标记。
- 不新增兼容旧报单客户端的旁路；新版本发布后以服务端校验为准。

## 影响文件

- `e-report-backen/src/main/java/.../work order DTO/service/repository`
- `e-report-backen/src/main/resources/db/changelog/`
- `e-report/src/components/WorkOrderForm/`、`e-report/src/components/work-orders/`、用户可见文案与 `nginx.conf`
- `e-report-app/app/report/`、`e-report-app/app/search.tsx`、`e-report-app/src/features/work-orders/`、`e-report-app/src/ui/`
- `deploy/`、`tasks/current.md`、`docs/migrations/`

## 验收标准

- 缺少任一必填字段、无有效定位坐标或无附件时，客户端阻止提交；绕过客户端直调 API 同样返回明确中文校验错误。
- Android 查询页三组筛选不再平铺，选择结果可清除、刷新与翻页不丢失。
- 打开派调单时不加载人员；输入姓名或账号后最多展示 20 个合格人员，当前处理人不出现在调单候选中。
- 调单成功响应中的 `currentAssignment` 立即变为目标人员、`pendingAssignment` 为空；旧处理人不能再回单，新处理人可以回单。
- 所有 SLA 策略数值翻倍，未闭环工单按原阶段时长再延长一次；历史曾超时事实保留。
- `https://e-report.omnnu.xyz:60443/`、`/e-report` 和直连入口均跳转到保留协议、主机和端口的 `/e-report/`。
- 后端测试、Web test/lint/build、Android test/typecheck/doctor/release 构建、浏览器 smoke 和 Android 真机流程通过。
- 生产迁移有停写备份和回滚点；Pod、日志、API、Web 与 APK 更新检查正常；GitHub 仓库为 private 且 `main` 可克隆。

## 测试方式

- `e-report-backen/gradlew test` 与 `bootJar`。
- `e-report/npm test`、`npm run lint`、`npm run build`。
- `e-report-app/npm test`、`npm run typecheck`、`npm run doctor`、Android release 构建和签名校验。
- API 覆盖必填失败、候选空查询、候选搜索、调单即时生效、并发/版本冲突与 SLA 迁移结果。
- Playwright 验证 Web 桌面和窄屏；ADB 真机验证报单、查询筛选、人员搜索与调单结果。

## 附件读取与验收交互补充规格

### 目标

- 修复 UUID 改造后 Web 工单附件预览无法携带登录凭证、统一显示加载失败的问题。
- 保留管理员自验覆盖的审计语义，同时把“验收意见”和“管理员自验覆盖原因”收敛为一个输入框。

### 范围

- 后端详情 DTO 返回当前登录人是否需要管理员自验说明，并复用同一领域判断执行验收校验。
- Web 通过认证 API 获取附件 Blob 后预览图片和视频；Android 保留已有的认证附件请求并纳入真机回归。
- Web 与 Android 始终只展示普通验收说明输入；管理员自验差异不在界面显式呈现，由系统内部同时写入验收意见和覆盖原因。

### 非目标

- 不公开附件下载接口，不把 Token 放入 URL，不修改历史附件记录、UUID 或物理文件路径。
- 不取消管理员自验审计字段，不改变普通处理人员禁止自验、驳回必须填写整改意见的规则。

### 影响文件

- `e-report-backen` 的 `WorkOrderResponseDTO`、生命周期服务、详情映射、仓储 EntityGraph 与集成测试。
- `e-report` 的工单附件预览、回单附件入口与验收操作组件。
- `e-report-app` 的工单类型定义与验收操作组件。

### 验收标准

- 有权查看工单的登录用户可预览和下载历史图片/视频附件；匿名访问仍返回 401，跨租户访问规则不变。
- 管理员验收自己的回单时仍显示普通验收说明框，提交内容在内部同时形成验收意见和自验覆盖审计原因。
- 非自验场景不显示覆盖原因字段；验收通过意见可选，驳回整改意见必填。

### 测试方式

- 后端集成测试覆盖管理员自验标识、普通验收标识及自验原因校验。
- Web test/lint/build 与 Playwright 验证认证附件预览和验收表单分支。
- Android test/typecheck/release 构建，并在 USB 真机验证附件加载、查询到详情和版本升级。

## 全角色全流程回归矩阵

- 提单者：三类工单提交、本人查看、未接单撤回、他人回单后的验收与本人回单禁止自验。
- 专业处理人员：主动接单、接受/拒绝派单、回单、挂起/恢复、被调单后的旧权限失效及类型越权。
- 调度员：首次派单、待接收改派、处理中即时调单、挂起中调单、同人调单与跨范围调单失败。
- 验收员：验收通过、驳回返工、空整改意见失败、非待验收状态失败及无派调权限。
- 管控员与审计员：管控员可取消非终态工单但不可派调/验收；审计员只读且所有状态变更均被拒绝。
- 管理员：全租户动作、本人回单的内部自验审计、重复验收和旧版本请求冲突。
- 状态与并发：`SUBMITTED -> IN_PROGRESS -> PENDING_VERIFICATION -> CLOSED`、返工环、挂起环、取消终态、派单拒绝重派，以及抢单并发唯一成功。
- 每个动作同时断言 `allowedActions` 与服务端实际授权一致，避免界面展示可点但提交后被拒绝的假动作。

## APP 附件与通知中心补充规格

### 目标与范围

- Android 工单详情中的图片和视频附件均可点击进入鉴权全屏预览；视频使用系统原生控制播放。
- “保存到相册”将鉴权分片下载完成的图片或视频写入 Android `MediaStore`，成功后删除 APP 缓存副本。
- 通知中心以渠道注册表、作用域开关策略、统一编排服务和低层渠道 adapter 分层；总开关、邮件、企业微信、钉钉可分别启停。
- Web 配置页按漳州市默认或指定区县读取专用通知设置 API，不直接维护底层配置键。

### 非目标与兼容性

- 不支持把 PDF、压缩包等非媒体附件写入系统相册；当前报单和回单附件契约仍为图片或视频。
- 不改变现有通知配置键、配置继承顺序或分渠道 Outbox 事件类型，不新增数据库字段和 Liquibase changeSet。
- 不覆盖已发布的不可变 APK；本次 Android 版本为 `1.0.30` / `versionCode 31`。

### 验收标准与测试

- UUID 工单附件继续通过 Bearer Token 加载，匿名请求为 401；预览关闭后返回原详情滚动位置。
- 图片和 H.264 MP4 均能显示真实内容；视频可播放、暂停和拖动；图片与视频保存后在 `content://media/external/*/media` 出现 `DCIM/` 记录。
- 连续操作期间保存按钮和通知开关防止并发重复提交；下载分片有超时、重试、长度与 `Content-Range` 校验。
- 后端测试覆盖注册表唯一性、组织作用域开关、目标渠道投递、禁用跳过、设置读取/更新与未知渠道拒绝；Web test/lint/build 和 APP test/typecheck/doctor/release 全部通过。

## 最终交付记录

- 后端与 Web 已部署为 `localhost/e-report-backend:20260807-notification-media-5` 和 `localhost/e-report-frontend:20260807-notification-media-5`；两个 Deployment 均 `1/1` Ready，应用 Pod 重启数为 0，最近 45 分钟无 `ERROR/FATAL`。
- Android stable 为 `1.0.30` / code31 / runtime `android-1.0.30-native31`，发布 ID `019fdb16-2292-7535-bd26-f68fe6bed69d`，`minSupportedVersionCode=31`、`forceUpdate=true`。code30 检查结果为强制原生升级，code31 检查结果为当前最新版本。
- 签名 APK 为 `75,855,427` 字节，SHA-256 为 `f9bb0c2b8b764cbc32c7f47cbf8aff5c71ce837360a48c9305ea365a78b0a12b`。R2 CDN 和后端回源均返回 HEAD 200、Range 206，并分别以 37 个 2 MiB 分片读完整包后得到相同哈希。
- 小米 `M2102J2SC` 从 code30 覆盖升级到 code31 后保留登录态；H.264 MP4 原生播放器完整播放到 `00:02`，视频写入 `DCIM/1786087643323-E2E-video-preview-code31.mp4`；PNG 全屏预览后写入 `DCIM/1786087976339-E2E-260807065903-cbcc-组织隔离-report.png`。Android 16 模拟器同步通过图片、视频和相册验证。
- 生产全角色全生命周期脚本运行 ID `260807065903-cbcc`，163 项断言全部通过，覆盖必填与幂等、权限矩阵、派单拒绝重派、处理中/挂起中调单、挂起恢复、返工验收、三类工单、自验审计、取消、并发接单、跨区县隔离及附件鉴权；12 个临时账号和 12 张工单全部完成清理。
- 后端 30 个测试套件共 82 项测试零失败，Web 5 项测试及 lint/build、APP 3 项测试及 typecheck、Expo Doctor 19/19、release 构建和 v2 签名验证全部通过。Playwright 已验证市公司与南靖县通知开关在桌面和 390px 窄屏下无重叠、无控制台错误。
- 本次通知中心重构沿用现有配置键、配置继承和分渠道 Outbox 契约，没有数据库结构或数据修正，无需新增 Liquibase changeSet。部署回滚目录为 `/opt/e-report/releases/20260807-notification-media-5/`，部署前数据库快照 SHA-256 为 `57693a60486408e60601c0c92f417281295d0a8da39cca1bb589896f3a01c0b1`。
