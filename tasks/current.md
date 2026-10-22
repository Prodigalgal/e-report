# Current Tasks

## 6.9.0 邮件发送台账

- 目标：后台可查询邮件实际发送尝试、SMTP 接受结果与安全的失败原因，默认仅 `superAdmin` 可见。
- 范围：工单创建/派单调单/超时/日报/配置测试邮件统一按收件人记录；新增 `notification:email-delivery:view`，WEB/Lite 与 RN 的紧凑列表、类型/状态/日期筛选和详情，后端按租户与组织职责范围隔离。
- 非目标：不表示收件箱送达或已读，不回填缺乏证据的历史记录，不增加人工重发或改变现有轮询/通知开关/重试，不自动删除台账；不授予其他管理员或岗位模板默认权限，不提升生产。
- 影响文件：Backend notification/delivery、发送渠道/日报/测试发送器、台账 entity/repository/DTO/controller、权限目录与 Liquibase 077；WEB route/layout/page/service、RN API/admin 入口及页面、共享展示契约与版本元数据。
- 验收标准：每次实际尝试有状态；成功只标记 SMTP 已接受，失败不泄漏 SMTP 密钥或邮件正文；外部发送不处于数据库事务内，SMTP 已接受后的台账写入故障不得触发重复发信；列表/详情/API 均校验独立权限及组织边界。长时间未收尾显示结果未确认，不误判已失败。
- 测试方式：真实数据库台账状态与组织隔离、授权/未授权 API、SMTP 成功/失败和状态落库故障回归；WEB test/lint/build、RN test/typecheck、版本/原生契约、浏览器桌面/窄屏；自动测试部署后继续真实发送和设备验收。
- 本地结果：Backend 532、WEB 95、APP 79 项通过，WEB lint/build、APP typecheck/Android bundle、版本/供应链契约和资产检查通过；Gitleaks 扫描 27.73MB 无发现。真实本地 SMTP 接受/拒收已验证独立落库，数据库收尾故障不重复发送，租户/组织/API 权限与 UTC+8 日期边界通过。原生与 Lite 指纹均不变，继续使用已封存基础包。
- 发布与验收：本次提交交由 main 自动流程构建并发布测试；新台账页面真实测试环境、实际收件与 RN OTA 设备验收尚待执行，不提升生产。结构为向后兼容新增，回滚使用前一不可变 Backend/WEB 镜像及 OTA，保留台账表和记录。

## 6.8.6 后台数据界面密度

- 目标：以后台数据管理为场景，增加首屏有效数据，减少共用布局留白与短信息独占行。
- 范围：WEB 页头、页面边距、表格、筛选、配置/诊断/仪表盘；RN 共用页面/卡片/详情字段和工单查询；Lite 复用 WEB。
- 非目标：不修改权限、查询/导出/分页、API、数据库、颜色、品牌或原生依赖；不提升生产，不处理原工作树的模型改动。
- 影响文件：`shared/ui-layout.json`、WEB theme/layout/工单和管理页面、RN UI/工单卡片/查询页、统一版本元数据。
- 验收标准：主要操作和原有数据均保留，导出仍紧邻更多条件，数量与分页同栏；桌面收紧，移动端保留触控尺寸、字段换行和安全区，空/错/加载态完整。
- 测试方式：WEB test/lint/build、APP test/typecheck、版本/原生指纹契约；自动发布测试后分别记录 WEB/Lite/RN 实测，未执行的验收不得标记完成。
- 迁移收尾：`6.8.5` 五条构建已成功；配置库读取失败定位为 Windows 文本管道把 SSH 私钥 LF 转为 CRLF。已保持原密钥身份，以二进制 stdin 恢复 Secret 并修正私有恢复脚本；下一次正常自动发布验证，不手工修改 GitOps 或 Argo CD。
- 实现：管理顶栏 64→56px，管理页纵向留白 24–32→12px，共用页面/卡片间距 16→12px；小表格单元格上下内边距 12→8px，工单联系人/电话合并一行。配置、诊断、仪表盘收紧，数据列表使用可用宽度；原有导出/筛选/分页保留。RN 查询与筛选同排、详情改为标签和值同行，工单优先级/时限/处理人按空间换行，触控高度仍为 48。
- 本地验证：WEB 92、APP 79 项通过；WEB lint/build、APP typecheck/native/assets、版本/供应链契约均通过。Gitleaks 扫描 27.64MB 无发现，两款原生指纹均与封存基础包一致。WEB 保留现有 500kB chunk 告警。
- 验收更新：浏览器访问已恢复，Pixel_9 已启动并通过 ADB 授权。1440×900 本地源码连接真实测试 API，工单表格顶部 434→264px、视窗 480→624px、样本行高 186→124px；九个管理页面无整页横向溢出，用户/配置/发布列表已等到真实数据加载。320/390px 工单页无横向溢出，主要操作高度 48px，移动筛选保留全部字段；RN/Lite 设备复验另计。6.8.6 云端自动测试发布及两款 APK/OTA 登记均已成功，生产发布任务跳过；实际集群版本需按验收时状态读取。业务/API/数据兼容不变，回滚点为前一已部署测试候选的不可变镜像与 OTA 记录。

## 6.8.5 原生基础包复用门禁

- 目标：修复重建仓库后原生 APK 流水线在复用已封存基础包前，被 Expo 在线补丁建议阻断的问题。
- 范围：仅调整 Android 工作流中 Expo Doctor 的执行分支，业务版本统一为 `6.8.5`；原生基础包仍为 `6.8.2`、Lite 仍为 `6.8.1`。
- 非目标：不升级 Expo/RN/定位依赖，不改变业务/API/数据库契约，不重建或覆盖已发布 APK，不提升生产。
- 影响文件：`.github/workflows/build-android.yml`、版本元数据、本任务记录与源码公开决策。
- 验收：所有候选仍通过锁文件、APP 测试、typecheck、资产检查、版本和原生指纹；复用基础包验证签名/包名/版本/指纹/摘要后生成候选封装；只有需要构建新基础包时执行完整 Expo Doctor。
- 测试：发布/供应链策略测试、actionlint、版本与原生指纹契约，以及从重建后的 GitHub Release 实际下载并验证不可变基础包；云端自动测试发布单独核实。
- 本地结果：Node `24.19.0` 下 APP 79 项与 typecheck、发布工具 26 项、供应链策略 19 项、actionlint 和版本契约通过。原生指纹保持 `4fecd5e6bb0a10bdd2492844deb82f0e9d5b8911b3b9fff0ec7ce3fc5b4c9ebd`；实际从新仓库 Release 下载原生 `6.8.2`，复用校验通过并输出 `reuse=true`，APK 摘要仍为 `33e857de75bc98c7df5b079880355d0bf6d2986072e25fd3b00a721949028787`。云端新候选部署和后续设备验收仍待自动流程完成。

## 源码公开与敏感历史清理

- 目标：将脱敏源码迁入同名 public 仓库，配置仓库保持 private。
- 授权：用户已接受删除并重建源码仓库，放弃原仓库身份和历史记录。
- 计划、验收和回滚边界：`docs/decisions/2026-10-21-public-source-history-cleanup.md`。
- 代码候选 `6.8.4`：真实个人资料、重复使用的私有测试凭据及对应预生成哈希已脱敏，迁移保留精确旧校验和；组织隔离测试改为显式准备当前权限夹具。
- 验证：Backend 518、WEB 92、APP 79、发布工具 26、供应链策略 19 项通过；lint/build/typecheck、版本和原生指纹契约通过。203 份制品和 854 份日志无未处理的凭据发现，45 份 Actions 缓存已清除。
- 状态：源码仓库已重建为 public（ID `1429111686`），初始 main `9141236` 的树与已验证 `6.8.4` 完全一致。11 个 Secret、四份 Release 的八个原始资源和两份 GHCR 镜像的 Actions Write 权限均已恢复。匿名访问新 main 成功，原敏感提交与原 PR 无法访问；配置仓库匿名返回 404。原仓库残留清理工单仅处理已删除仓库，已补充新旧 ID。
- 云端 `6.8.4`：安全审计、Backend/WEB 镜像、Lite、OTA 均通过；原生任务被 Expo Doctor 的在线补丁版本建议阻断，自动测试发布未执行。`6.8.5` 修复复用流程；生产保持 `6.7.2`。

## 6.8.3 列表视窗与信息密度整改

- 目标：让工单和管理列表成为首屏重点，合并简短说明、数量与分页，避免提示和操作重复占行。
- 范围：WEB 共用标题/列表操作栏/自适应表格，工单查询、用户管理、配置、运行诊断等列表；RN 查询、搜索反馈和章节标题；Lite 同步 WEB。
- 非目标：不变更权限、筛选、导出、分页及数据契约，不更换颜色/字体、不提升生产。
- 影响文件：`components/layout/`、工单筛选/列表、管理页面、APP `search.tsx` 与共用 UI 组件。
- 验收：桌面导出紧邻更多条件右侧，数量与分页同排；筛选展开后表格适应剩余视窗；窄屏无横向溢出，触控、权限隐藏、选择/清空/导出正常。
- 测试：WEB test/lint/build、APP test/typecheck、版本与原生指纹契约；浏览器桌面/窄屏与真实 Lite/RN 页面验收，结果按实际记录。
- 结果：真实测试 API 联动的桌面/320px/390px浏览器验收、筛选/空结果/重置/翻页/单条导出与权限隐藏通过；同窗口列表可视高度 480→571px。WEB 92、APP 79 项、lint/build/typecheck 与版本/原生指纹通过。由 main 的 6.8.3 自动测试发布接续，RN OTA 后设备复验另计，未提升生产。

## 6.8.3 Lite 客户端与 Expo OTA 验收修复

- 目标：保留现有 RN 原生 APK，新增 Kotlin WebView Lite APK，接入原生版签名 OTA 与自动测试发布。
- 范围：客户端身份/发布中心/下载隔离、原生基础包版本和兼容指纹、Backend Expo Updates 协议与 R2、Lite 工程、WEB/APP 联动及 Actions。
- 非目标：不迁移 Expo/RN 主版本，不自动提升生产，不改模型/推理源码或手工部署集群。
- 验收与实施计划：`docs/requirements/2026-10-21-lite-and-expo-ota.md`。
- 已实现：两类客户端发布隔离、独立 Lite 构建、签名 OTA 协议/分发/重试、基础包不可变复用和原生兼容指纹。原生版只升级原生版，Lite 只升级 Lite；更换类型先卸载再安装。
- 本地验证：原生基础包验收构建与真实登录、切环境进程重启通过；Lite Release 签名构建/单测/lint 通过，设备实际完成账号创建/编辑/授权/通知偏好、首次改密、会话恢复、三类工单创建、相机/录像/多选上传、定位拒绝后重试与地址解析、视频全屏。WEB 84、APP 79、发布工具 26 项测试通过。
- 云端 `6.8.1` 五条构建及自动测试发布 `39574075718` 全部成功，包含两款 APK 的真实 HEAD/Range/完整摘要和 OTA 签名及全部资源验证；未提升生产。Lite 云端不可变 `6.8.1` / `5008001` 已下载、核对签名/摘要并覆盖安装。
- `6.8.2` 修复原生跨环境 OTA 缓存：SDK 在重用内置 UUID 时不更新地址，启动前按环境清理专属更新缓存。新原生基础包 `6.8.2` / `5008002` 在实际设备断网冷启动、生产/测试切换通过，测试更新数据库的地址与 scope 一致、失败启动数为 0；Lite 继续复用 `6.8.1`。
- 真实 PAT 测试发现 MCP HTTP 默认 Jackson 3 无法读取 Jackson 2 JsonNode，接口返回 500；改为 MCP 边界显式解析/编码 JSON，保留业务服务和协议。补齐真实 HTTP 编解码、空/坏/尾随 JSON、原始字节大小边界回归。Backend 全量 511、发布工具 26 项通过，原生验收包和 WEB 构建成功。
- `6.8.2` main `b95e481` 的安全审计与五条构建均成功，自动发布 `39604657582` 已部署测试 Backend/WEB；测试站 release.json 与源码一致。原生签名基础包已封存并实际安装；APK/OTA 发布收尾由原流水线继续执行，不手工干预或提升生产。
- `6.8.3` 继续修复实测缺陷：旧页面在部署后请求已移除的懒加载文件，只在确认服务器有更新版本时自动刷新一次，保留离线/业务异常的原有错误界面；工单关键词补齐显示编号 `WO-...` 和 `#WO-...`，保留 UUID 搜索、LIKE 转义及权限过滤。纯 JS 后续候选复用原生 `6.8.2` 与 Lite `6.8.1` 基础包，等待前候选自动完成后再推送。
- 追加设备证据：云端 Lite 图片双指放大/拖动/双击/还原、网络工单派单/拒绝/主动接单/挂起/恢复/回单/驳回/调单，以及原处理人权限撤销通过。必填回单照片、物料数量 0、物料合并/删除已真实检验；物料 2 单位及回单附件 API 回读正确。其余闭环仍在执行。
- 登录实测复现密码错误被提示成验证码错误：AuthProvider 和页面重复格式化，通用凭据文案中的“验证码”与“错误编号”被再次误判。保留原始异常，只在页面格式化一次；401 明确为用户名/密码错误。补齐冷却结束自动刷新与静默挑战到期前刷新；数学题保持稳定。普通用户手机菜单的个人信息入口也已移回登录状态，修复偏好/Token 难以访问的问题。
- 三类专属工单已全部通过 Lite 真实验收关闭，API 均为 CLOSED；下载取消/重试/保存及 210,980 字节视频 SHA-256 一致，图片旋转后关闭、回单选中文件旋转保留、Token 原生复制后实际粘贴逐字核对通过。测试站 MCP 已通过 11 项真实 PAT 协议/调用/权限/错误边界检查。
- `6.8.3` 本地 Backend 全量 511、WEB 92、APP 79 项通过；WEB lint/build、APP typecheck、版本契约及原生兼容指纹通过。待完成：原生正式包与后续 OTA 应用、新登录/手机入口线上复验、Lite 其余矩阵及专属资源清理。具体清单见 `docs/reports/2026-10-21-lite-acceptance.md`。生产保持 `6.7.2`。

## 6.7.2 依赖 PR 整合

- 目标：处理全部 17 个未关闭 PR；10 项兼容更新整合验证，7 项迁移类更新记录理由后关闭。
- 范围：GitHub Actions、WEB 运行依赖、APP Query/SHA-256、Backend HttpClient/Bouncy Castle/Jackson；统一 `6.7.2` / Android `5007002`。
- 非目标：不调整业务/API/数据库契约、不迁移 Expo/RN/原生构建器或 WEB 工具链，不提升生产。
- 文件：两端 package/lock、Backend build/lock/verification、工作流、三端版本元数据及决策记录。
- 验收：组合回归和供应链预检通过后进入 main；未关闭 PR 归零，已处置机器人分支清理。
- 验证与逐项理由：`docs/decisions/2026-10-21-dependency-pr-cleanup.md`。
- 实现结果：十个采纳 PR 的 head 均已合入整合历史，七个迁移类 PR 已关闭并删除远端分支；Jackson PR 的 `2.22.2` 经审计修正到 `2.22.3`，避免带回四条已披露漏洞，未修改安全例外。
- 验证结果：Backend 495、WEB 81、APP 75、策略 31 项测试通过；WEB lint/build、APP typecheck/native/version/assets/Android Hermes bundle、48 项 SHA-256 兼容断言通过；OSV 发布依赖 0 未处理发现，现有构建告警基线通过，完整密钥扫描与 SBOM 成功。容器与原生 APK 构建继续由 GitHub Actions 执行，生产待验收。

## 6.7.1 发布依赖安全修复

- 目标：修复 `6.7.0` 在供应链安全阶段发现的五条未审查漏洞，恢复正常自动测试发布。
- 范围：WEB `vite 6.4.3 -> 6.4.4`、间接依赖 `follow-redirects 1.16.0 -> 1.16.1`；OSV 预检检出的图像库 `Pillow 12.3.0 -> 13.0.0` 九条漏洞；三端统一候选 `6.7.1` / Android `5007001`。保留 `6.7.0` 的管理员通知偏好和图片缩放功能。
- 非目标：不更改安全例外、CI 门禁、Android 原生依赖、业务/API/数据库契约或模型/推理源码；不手工上传制品或部署集群；生产保持 `6.6.1`。
- 影响文件：WEB package/lock、media-image-worker 依赖输入和哈希锁、Backend/WEB/APP 版本元数据及当前任务记录。
- 验收：WEB npm audit 零告警；APP 通过现有审计策略；WEB 测试、lint/build、三端版本契约和供应链契约通过；推送后由既有 Actions 自动构建、修改配置仓库和触发测试部署。
- 测试：同 CI 的 npm 审计校验、供应链策略测试、OSV/密钥/SBOM 本地预检；Pillow 13 隔离环境运行图像处理测试及锁文件差异校验；相关构建和版本验证。本地缺少 Docker，容器层扫描由 GitHub Actions 完成。
- 故障证据：源码 `a1e841a` 的 run `39525374509` 云端 WEB/APP/Backend 验证通过，npm audit 阻断后续构建；`6.7.0` 未生成镜像、APK 或部署版本。五条告警于 2026-10-20 UTC 发布，上游补丁已核实。
- 验证结果：WEB npm audit 零告警，81 项测试、lint/build 通过；APP 75 项测试和 native/version contract 通过；31 项供应链/发布策略测试通过，Backend `6.7.1` bootJar 成功。OSV `releaseFindings=0`，构建工具告警通过现有基线，SBOM 已生成。
- Pillow 验证：`uv` 为 Python 3.12 重新生成 SHA-256 锁，依赖版本差异仅 Pillow；Python 3.12.6 隔离环境按全部锁文件和哈希安装成功，14 项现有图像处理测试全通过。模型与推理源码无变化；本轮三端流水线不构建 worker 镜像，不能据此声称现网 worker 已升级。
- 验证边界：本地 Gitleaks 的默认全引用扫描遇到无效 Codex 检查点，`0 commits scanned` 不计为通过；补充的有效 `HEAD` 历史扫描成功、未检出密钥。工作目录扫描位于本地 `.codex` 排除路径，最终以 GitHub 干净检出后的完整密钥扫描为准；容器扫描同样由云端执行。
- 兼容与回滚：三项依赖升级不改变业务/API/数据库契约；Pillow 13 要求 Python 3.11+，既有镜像 Python 3.12 满足。测试异常时 WEB/Backend 回退 `6.6.1` 不可变镜像，APP 使用更高版本修复；生产本轮不提升。

## 6.7.0 APP 图片缩放与管理员邮件通知偏好

- 目标：图片附件支持双指缩放、拖动、双击和按钮缩放；管理员可在用户详情修改他人的四类邮件通知偏好。
- 范围：APP 图片预览、异步加载与几何边界；Backend `GET/PUT /admin/users/{userId}/email-notifications`；WEB、APP 复用个人订阅组件并增加独立管理弹窗；统一候选 `6.7.0` / Android `5007000`。
- 非目标：不改变附件权限、保存、视频、组织邮件总开关或收件权限；不新增数据库字段；不把管理员设置变成个人不可修改的策略；自动发布测试，生产基线保持 `6.6.1`。
- 影响文件：APP 工单详情与图片预览、Backend 邮件偏好管理接口和服务、两端用户详情及订阅组件/API、相关测试、三端版本元数据。
- 验收：图片支持一至五倍缩放、焦点稳定、拖动有边界、切图/视口变化重置且失败可重试；管理入口沿用用户查看和联系方式修改权限，仅允许同租户、本人组织及下级组织，保护系统根账号，禁止修改已删除用户；管理员和本人读取同一设置，保存不覆盖其他用户字段。
- 测试：图片几何回归；管理接口字段校验、读写权限、组织/租户隔离、根账号保护与数据库回读；WEB test/lint/build、APP test/typecheck/native/version/assets/Android bundle、Backend 相关及全量回归；GitHub Actions 生成测试 APK，原生触摸验收单独记录。
- 发布基线：生产与测试 `6.6.1` APK 均已自动上架，生产下载 Range 请求返回 206；`6.7.0` 尚未生成安装包。
- 实现：WEB/APP 用户详情操作区提供“邮件通知偏好”弹窗，复用四项订阅组件，保存时禁止重复提交或关闭；Backend 沿用 `user:manage:view` + `user:manage:contact:update`，按组织范围与账号保护校验后锁行更新，并记录操作人和目标账号。组织邮件总开关、收件权限及个人自助入口保持现有契约。
- 配套修复：空请求体及错误 JSON 字段类型返回 400，不再误报 500；解析错误日志不回显请求内容。运行时 `spring.application.version`、Docker 默认版本纳入统一版本契约。
- 本地验证：Backend 495 项、WEB 81 项、APP 75 项全部通过，无跳过；Backend bootJar、WEB lint/build、APP typecheck/native/version/assets contract 和最新 Android Hermes bundle 成功。未新增依赖或数据库迁移。
- 验收边界：隔离 H2 实例已运行，真实浏览器登录被本地 Redis 缺失阻断；模拟器启动此前被自动审批策略拒绝（仅返回 `blocked by policy`），原生双指手势尚未实测。临时验收进程已停止；正常推送后由 GitHub Actions/Argo CD 自动发布测试，生产升级仍需验收。

## CI 制品提升与 APK 发布恢复

- 目标：源码由 GitHub Actions 编译、打包并提交配置仓库 YAML，由 Argo CD 自动部署；消除已确认的 APK 上传 30 分钟超时。
- 范围：CI 维护范围门禁、测试/生产既有制品提升、APK 发布时限与有限重试、相同制品登记和 R2 同步恢复。
- 非目标：不修改业务源码、API、数据库契约或版本；不重建已生成的 `6.6.1` 镜像和 APK，不手工上传制品或更新集群。
- 影响文件：`.github/workflows/`、`scripts/security/verify-ci-maintenance-scope.mjs`、`scripts/release/`、`e-report-app/scripts/publish-app-release.ps1`。
- 验收：CI 维护提交只能包含明确允许的工具文件；三端不重复构建；复用源码 `5f54af7` 的镜像和签名 APK；失败重试不覆盖版本、不重复登记，人工取消不重启；测试和生产状态分开核验。
- 测试：CI 范围和恢复策略单元测试、真实 PowerShell + 本地 HTTP 服务的分片上传/哈希冲突/同步失败恢复测试、统一版本与供应链契约、工作流静态检查、Actions 与 Argo CD 只读验证。
- 状态：31 项本地测试、统一版本、供应链契约与 actionlint 通过；CI 维护提交 `dc8b9db` 云端校验成功，三端构建均跳过。生产 WEB/Backend `6.6.1` 已由 Actions/Argo CD 发布，迁移 072/074 已执行；生产 run `39469374723`、测试 run `39468186512` 均已完成 APK 自动上架，同一不可变 APK 的 SHA-256 为 `cebf487fae09794f22dc2227d2e0ed2ca1a0180f1e29f6817bca2baae9509028`，生产下载 Range 返回 206。配置提交使用短时共享锁，测试和生产 APK 上传按环境分别排队。

## 6.6.1 WEB / APP 布局、操作层级与邮件测试修复

- WEB / APP 同步按任务分区，个人资料、时限策略、工单详情和配置页保留全部字段及权限；切换分类保留草稿。APP 后台工单查询移至标题旁，统计、区县、类型和管理配置分别进入。
- 两端共用 `shared/ui-action-colors.json`，按用户授权重做白色顶栏、浅灰画布和轻量导航，主操作实蓝、次操作柔和浅底；六组普通/悬停/实色文字对比度回归，不再强制所有控件填色加框。
- WEB 手机用户列表改卡片；发布记录分页并逐条展开；诊断页收起低频筛选和 Redis 明细。APP 的组织、部门、模板和配置作用域使用选择器，长选项可搜索并使用 FlatList。
- 修复 `application-e2e.properties` 的投递开关覆盖问题：默认仍关闭，显式 `NOTIFICATION_DELIVERY_ENABLED=true` 才启用真实投递；测试收件人白名单保持不变。
- 验证：WEB 81 项、APP 69 项、Backend 477 项测试通过；WEB lint/build，APP typecheck/native/version/assets/Android bundle 通过。候选 `6.6.1` / Android `5006001`；测试部署、候选 APK 模拟器复验和真实邮件结果分别记录，生产等待验收。
- 报告：`docs/reports/2026-10-20-layout-and-mail-acceptance.md`。

## 6.5.0 C 版品牌与邮件发件池组织策略

- 用户选定 C 版折叠 `N`；几何源统一为 `shared/brand-mark.json`，更新 WEB、PWA、APP、Android 启动和桌面资源。
- 页面使用透明底蓝色标志，桌面使用蓝底白色标志；保留现有包名、签名、主题色与业务交互。
- WEB 后台开放本级发件池录入与修改，提供仅继承、仅本级、共同使用三种策略，继承账号只读且可单独测试；每个账号保留实际来源，凭据不跨组织复制。
- 统一版本 `6.5.0` / Android `5005000`，不复用已构建的 `6.4.0` 制品。推送后交给 GitHub Actions 自动执行，按用户要求不持续监督构建；生产代码发布仍等待测试验收。

## 6.4.0 APP 环境设置与每日汇总调度修复

- 目标：APP 登录页通过右上角设置管理环境及 API 地址；日报按区县配置的北京时间准点预约，取消固定间隔扫描。
- 运行态修复：2026-10-20 通过生产管理 API 恢复南靖县邮件渠道，继续与即时工单邮件共用开关，日报时间保持 `18:00`；其余区县及收件权限未修改。
- 调度：配置和组织变更提交后通知副本重排，启动/重连/每日零点校准，保留执行台账幂等、180 分钟补发和 60 分钟执行权回收，跨零点使用原定发送日期。
- 规格与边界：`docs/decisions/daily-outstanding-report-scheduling.md`；没有 API 或数据库结构变更；SMTP 部分失败不整批自动重发。
- 发布边界：统一候选 `6.4.0` / Android `5004000`，先由 Actions 构建并自动部署测试环境，生产代码升级等待验收。

## 6.0.7 登录挑战可见性与分阶段风控机会

- 目标：完成登录验证码可见性修复，并确保静默阶段和数学验证阶段各有至少 3 次密码尝试。
- 范围：WEB 登录交互、Backend 登录风控阈值、Expo SDK 57 补丁依赖与 Android Gradle 锁文件、统一版本契约；只部署 `e-report-test`，不发布生产。
- 规则：连续 3 次密码错误后升级数学验证；交互验证阶段再提供 3 次错误密码机会，第 6 次错误后进入现有指数冷却。验证码错误仍不消耗密码尝试；现有请求限流和 proof-of-work 难度保持不变。
- 影响模块：WEB 登录页与挑战 hook、`AuthRiskService`、Backend 风控测试、APP Expo/Gradle 依赖锁、WEB/Backend/APP 版本元数据。
- 验收：登录挑战按当前用户名就绪后才允许提交；挑战升级提示清晰且输入框可见；阈值边界和冷却有自动化测试；Android Gradle 依赖锁可复现；统一版本契约和三组件构建通过；Actions 自动部署测试，生产镜像不变。
- 失败候选：`6.0.5` 的 Android Actions `36585724273` 在 Expo Doctor 失败；`6.0.6` 的 Android Actions `36587046539` 因 Gradle 依赖锁仍引用旧 Expo Router/UI Maven 模块版本而失败；两个候选均未部署。
- 测试候选：`6.0.7`，Android `versionCode=5000007`、`runtimeVersion=android-6.0.7-native5000007`。

## 6.0.3 权限与 MCP 变更收尾及测试发布

- 目标：清除无人引用的权限声明和运行代码，完成组织范围权限、个人 Token MCP 的候选版本，并将自动发布限制在测试环境。
- 范围：Backend 权限目录与 Liquibase 072、WEB/APP 权限常量、会话 API、MCP、管理与授权流程、GitOps 发布流水线。
- 非目标：不改变未审计的业务状态和遥测协议；用户验收前不发布生产或稳定版 APK。
- 验收：相关数据库迁移、Backend/WEB/APP 验证和统一版本契约通过；main 推送后自动构建三组件并部署测试，流水线不得自动修改生产配置或发布稳定版 APK。
- 测试与发布：测试版本 `6.0.3`、Android `versionCode=5000003`；生产只允许手动输入仍在测试环境的已验收 SHA 后执行。

## 图片脱敏模型评估与巡检游标修复（待占用下一正式版本）

- 结论：`cmcc-brand-v1` 在恢复样本上存在严重误判，必须重训；当前精度门控仅作为测试期风险遏制，未替代模型验收。
- 代码：`ImageRedactionReconciliationService`/`Scheduler` 改为按 UUID v7 主键游标分页，修复固定第 0 页导致后续历史附件永远不扫描的问题；后端全量 Gradle 测试通过。
- Worker：对大候选框执行精细窗口推理、品牌颜色证据和窄框收敛；3 组恢复样本复核为 2 组不打码、1 组仅覆盖品牌窄区域；Worker 10 项测试通过。
- 模型选型：生产优先重训现有 MobileNetV3-Small + ONNX；Florence-2、Qwen2.5-VL、Phi-3.5-Vision、NVIDIA VILA 和 SmolVLM 只做离线标注/复核对比；PP-OCRv5 mobile 可作为文字确认二阶段；YOLO11n 需先解决 AGPL/Enterprise 许可。
- 方案与指标：`docs/requirements/2026-09-02-image-redaction-model-evaluation.md`；真实数据至少 300 正样本/600 硬负样本，precision ≥99.5%、FPR <0.5%、recall ≥95%、CPU P95 ≤500ms 后才允许候选切换。
- 运行边界：当前仅测试命名空间 `e-report-test`，生产未变更；新代码尚未构建/部署为新的正式版本，需另占 `x.y.z` 后再做测试候选。

## 5.6.5 密码策略配置与图片脱敏历史巡检

- 状态：已完成代码、自动化验证和测试环境部署，等待用户终测；生产环境未变更。
- 测试候选：`20260902-v5.6.5-password-policy-test-3`，Backend、WEB、image worker 均已滚动成功，Pod Ready、重启 0。
- 密码策略：后端权威校验；默认最小 8 位且要求大小写、数字、特殊字符；支持平台、租户和组织作用域覆盖及恢复继承；新建、重置、修改密码均使用当前作用域策略。
- 图片巡检：按租户、Redis 锁和批量限制扫描漏处理图片，复用已完成上传会话，不伪造任务、不删除原图；检测框合并、5% 扩展且最大 16px，`_sanitized/` 查询使用显式转义。
- 客户端：WEB/APP 密码策略面板与提示已接入；Android `5.6.5` / `versionCode=4006005` release APK 已在 `Pixel_9` 安装启动，日志无 Fatal/React Native 错误。
- 运行边界：测试通知、邮件、日报、Outbox 和数据同步 CronJob 保持关闭或暂停；测试库当前无待补偿图片候选，因此未伪造运行成功记录。
- 风险：`npm audit --omit=dev` 仍报告 5 个构建链高危项（Metro/image-size/Browserslist），生产发布前必须完成可用修复或受控补丁复核。
- 记录：`docs/migrations/2026-09-02-v5.6.5-password-policy-redaction-test.md`。

## 5.6.4 脱敏附件预览与下载修复

- 状态：已完成测试环境验证并部署生产（2026-09-02）。
- 根因：`_sanitized/...` 内部存储键进入 URL 后触发 Envoy escaped-slash 重定向；旧 Spring 多段路径变量还会与新入口竞争，最终返回 404。
- 修复：新增按工单 UUID 和附件 UUID 下载的 `/work-orders/attachments/{workOrderId}/file/{attachmentId}`；旧兼容入口收窄为单段键；WEB、APP、AI Guide 和测试脚本全部切换 UUID URL。
- 验证：测试/生产脱敏附件均 `HEAD 200`、`GET Range 206`；生产 9 条嵌套脱敏记录文件缺失 0；生产只读 smoke 14 项通过；R2 APK `5.6.4` / code `4006004` Range/SHA 校验通过，非强制更新。
- 记录：`docs/migrations/2026-09-02-v5.6.4-attachment-download-production.md`。

## 5.6.3 图片原图保留、AI Guide 与性能验证

- 状态：已完成测试与生产发布（2026-09-01）；Backend、WEB、APP、image worker 和 R2 stable 均已切换到 5.6.3。
- 图片：Worker 不再删除 `_quarantine` 原图；`_sanitized/{jobId}.jpg` 作为唯一前端展示/下载结果，原图和派生结果可按数据库相对路径在 VPS 上传根目录追溯。
- AI Guide：`GET /api/ai/guide` schema `2`，补充服务版本、认证边界、权限有效期、能力请求/响应契约、错误状态和幂等要求；权限按有效 Grant 与固定目录交集裁剪，退役权限不会阻断接口。
- 观测：修复合法 W3C trace ID 被手机号样式脱敏导致数据库约束失败的问题。
- 验证：Backend 全量构建/测试、WEB 62 项、APP 60 项、测试/生产 AI Guide Token smoke、生产只读 smoke 14 项和 9 端点 30/100 只读压测通过；生产核心 Pod `1/1`、重启 0、权限目录漂移 0。
- image worker：测试吞吐 4.064 张/秒、生产吞吐 4.152 张/秒（750m CPU、单并发、真实 ONNX，处理器级基准）；两次原图/派生图均 `60/60` 保留，测试文件已清理。
- APP/R2：Android `5.6.3` / code `4006003` / `android-5.6.3-native4006003`，SHA-256 `b8901c7c920070a2f800bfa95cd06e7cdb5e362f7772f85494f2e24c96b3201a`，stable 非强制更新；生产镜像使用不可变别名 `20260901-v5.6.3-image-retention-ai-guide-prod-1`。
- 备份/回滚：`/opt/e-report/backups/pre-v563-prod-20260901T155942Z/`；Backend/WEB/Worker 可回滚到 `20260901-v5.6.2-otel-trace-fix-prod-1`，APP 按版本规则只能发布更高版本修复。
- 记录：`docs/migrations/2026-09-01-v5.6.3-image-retention-ai-guide-production.md`、`docs/reports/2026-09-01-v5.6.3-performance.md`。

## 5.6.1 工单查询、部门协作与报单体验增强

- 状态：开发与测试中，仅允许部署 `e-report-test`，用户验收前禁止切换生产。
- 根因：Hibernate 7 对 `WorkOrder` 的 `LONG32VARCHAR` 字段做 `LIKE` 语义校验时，将 `COALESCE` 表达式判定为非 string-like，关键词查询直接返回 500。
- 处理：所有工单长文本筛选统一经过显式 `String` 表达式；问题描述、回单详情、验收意见和更换结果均覆盖回归测试；安装包说明和用户查询的同类 `COALESCE` 也改为显式字符串类型，避免同类漂移。
- 版本：Backend、WEB、APP 和数据库候选统一占用 `5.6.1`；Android `versionCode=4006001`、`runtimeVersion=android-5.6.1-native4006001`。
- 登录设备改为服务端分页，返回统一分页 DTO 和有效的其他会话数量；WEB/APP 将撤销全部会话、刷新和退出操作上移到紧凑操作区。
- 小屏工单查询改为关键词 + 单个“筛选条件”入口，筛选项在底部面板内集中编辑，避免顶部连续堆叠输入框；桌面端保留多列筛选布局。
- 同组织同级部门可协作接单，跨部门处理追加 `PROCESSING` 参与部门记录；原提单部门、责任部门保持不变，箱门未关工单继续不开放普通驻地网人员主动接单。
- 报单表单默认带出当前账号姓名和手机号，用户可在提交前修改，空值仍按原有必填校验提示。
- 当前测试候选镜像标签：`20260901-v5.6.1-contact-default-test-1`（5.6.0-test-1/2 未用于验收，5.6.1 修正表单初始化时序）。
- 规格与审计：`docs/reports/2026-09-01-work-order-search-audit.md`。

## 5.4.1 工单图片品牌脱敏

- 状态：实施中，只允许部署 `e-report-test`，用户验收前禁止切换生产。
- 目标：对新上传工单图片中的中国移动图形标识和品牌文字执行小范围自动模糊；本机训练轻量模型，VPS 独立单并发 CPU Worker 推理。
- 边界：本期不处理视频、GIF、PDF/Office，不批量迁移历史附件，不在客户端或生产环境训练；失败不回退原图。
- 版本：Backend、WEB、APP 和数据库候选统一占用 `5.4.1`；Android `versionCode=4004001`、`runtimeVersion=android-5.4.1-native4004001`。`5.4.0` 测试候选因 Worker PostgreSQL 字典行访问缺陷作废，不复用制品。
- 规格：`docs/requirements/2026-08-30-v5.4.0-image-redaction.md`；决策：`docs/decisions/image-redaction-runtime.md`。

## 5.3.10 侧栏与工单状态 UI 优化

- 状态：5.3.10 已完成本地、真实 WEB、模拟器 APP 和 K8S 运行态闭环，并已部署 `e-report-test` 等待用户终验；5.3.5 至 5.3.9 均因各自验收缺陷拒绝提升且不得覆盖，用户明确验收前禁止切换生产。
- 目标：统一桌面端侧栏外壳、菜单内容和主内容区的收缩展开节奏；把工单列表从多个同等级状态胶囊重构为“主流程状态 + 次级时限提示”，消除状态文字、阶段颜色和 SLA 文案冲突。
- 范围：WEB 侧栏动画、WEB/APP 工单列表与详情状态语义、WEB 列表密度/层级/键盘焦点/加载态/空态；不改变工单状态机、权限、查询条件、颜色契约和移动端抽屉行为。
- 版本：Backend、WEB、APP 统一占用 `5.3.10`；Android `versionCode=4003010`、`runtimeVersion=android-5.3.10-native4003010`；本版本不新增数据库迁移。
- 验收：侧栏展开/收缩终态居中且中间帧无突跳；派单阶段文字与颜色一致；SLA 不再出现“已超时 · 超时”或“已完成 · 已结束”；已闭环/已取消列表不展示无意义的已结束时限；WEB 桌面/窄屏与 APP 类型检查回归通过；生产保持原版本。

## 5.3.4 组织与部门树形工作台

- 状态：5.3.3 因归档误用最后一次权限态修正前的旧 WEB `dist` 被拒绝提升；5.3.4 不可变候选已部署 `e-report-test` 并完成真实浏览器和运行态验收，等待用户终验，终验前禁止切换生产。
- 目标：用统一组织树定位数据边界，在右侧围绕当前组织展示部门树、节点详情与生命周期操作，替代组织/部门平铺页面。
- 权限态：只有部门查看权限时，根据后端已授权返回的部门构造只读组织入口，不请求不可见的组织目录、不显示无权操作按钮，也不扩大数据范围。
- 版本：Backend、WEB、APP 统一占用 `5.3.4`；Android `versionCode=4003004`、`runtimeVersion=android-5.3.4-native4003004`；本版本不新增数据库迁移。
- 规格：`docs/requirements/2026-08-24-v5.3.3-org-department-tree-ui.md`。
- 测试记录：`docs/migrations/2026-08-24-v5.3.3-org-department-tree-test.md` 记录被拒候选；最终验收记录为 `docs/migrations/2026-08-24-v5.3.4-org-department-tree-test.md`。

## 5.3.2 南靖县部门与人员归属测试迁移

- 状态：测试环境已完成；创建网络部、移动铁通、驻地网维护三个部门，55 名业务人员迁移成功，3 个系统/测试账号保持组织直属。
- 数据结果：网络部 3 人、移动铁通 42 人、驻地网维护 10 人、组织直属 3 人；跨组织部门引用 0，工单仍为 87，通知 Outbox 增量 0。
- 授权修复：示例报单员通过服务端 Diff 预检撤销 18 项测试残留 Grant，最终只保留三类提单相关 21 项权限。
- 遗留：驻地网人员的历史关箱处理权限和林志博的主管权限需要另行确认后收敛；本轮未自动扩权或撤权。
- 记录：`docs/migrations/2026-08-24-v5.3.2-nanjing-department-test.md`。

## 5.3.2 内部观测全量对齐 OpenTelemetry

- 状态：代码、迁移、三端构建、供应链、测试命名空间、浏览器与 Android 模拟器真实失败链路均已验收通过；仅部署 `e-report-test`，等待用户终验，终验前禁止切换生产。
- 版本：Backend、WEB、APP 和数据库候选统一占用 `5.3.2`；Android `versionCode=4003002`、`runtimeVersion=android-5.3.2-native4003002`。`5.3.0` 因运行诊断页 MUI 9 插槽兼容问题作废；`5.3.1` 因 APP User-Agent 遗留固定版本作废，候选制品均不复用。
- 目标：复用现有客户端队列、Redis、PostgreSQL 和管理端，统一 Resource、InstrumentationScope、Log、Span、Span Event、Severity 与 W3C Trace Context。
- 技术边界：Backend 可使用 Spring Boot OpenTelemetry/Micrometer Observation；所有 OTLP exporter 关闭，不部署任何外部观测服务。
- 数据边界：不采集硬件唯一标识、坐标、地址或凭据；认证主体、租户和组织只接受服务端解析结果。
- 决策：`docs/decisions/internal-opentelemetry-observability.md`；规格：`docs/requirements/2026-08-24-v5.3.0-internal-opentelemetry-alignment.md`。
- 运行证据：Android 5.3.2 权限拒绝定位 Span 已在 PostgreSQL 与运行诊断页闭环，设备/系统/网络/权限/Provider/耗时/操作阶段与 W3C Trace 信息完整；测试工单仍为 87，生产继续运行 2.5.3。详见 `docs/migrations/2026-08-24-v5.3.2-internal-opentelemetry-test.md`。

## 5.2.0 委派基线修复与装维关箱岗位收敛

- 状态：测试候选已部署并完成自动化、浏览器、API、数据库和 Android 模拟器回归；仅运行于 `e-report-test`，等待用户终验，终验前禁止切换生产。
- 版本：Backend、WEB、APP 和数据库候选统一占用 `5.2.0`；Android `versionCode=4002000`、`runtimeVersion=android-5.2.0-native4002000`；测试镜像使用不可变 tag `20260824-v5.2.0-delegation-baseline-test-1`。
- 根因：V5 PostgreSQL 迁移保留了权限 Grant，但历史 `grant_option` 默认全部为 `false`，同时测试运行态关闭了旧 `DataInitializer`，使固定岗位与系统管理员基线没有重新校准；岗位模板和可选权限共用委派边界，因此同时返回空集合。
- 目标：保留 `grantOption` 安全约束，校准 `superAdmin`、市公司和区县系统基线管理员，补齐明确空态，并将箱门未关处理模板收敛为装维专用。
- 边界：不硬编码默认账号或装维人员，不自动撤销既有箱门处理 Grant，不切换生产；`superAdmin` 新密码必须符合现行 15–100 字符策略。
- 测试结果：PostgreSQL Liquibase 首次执行 1 条、幂等复跑 0 条；18 个市县系统基线管理员全部与标准岗位一致，`superAdmin` 拥有 164 项原子权限和系统根身份，岗位模板与授权选项均非空；测试期间工单和通知 Outbox 增量均为 0。
- 客户端结果：WEB 浏览器验证岗位模板和用户授权弹窗；Android `5.2.0-acceptance`（`versionCode=4002000`）在 `emulator-5554` 安装、登录、管理后台、岗位模板、当前 Grant 清单和追加岗位模板均通过，未提交授权变更，最终 APK 仅包含测试域名。
- 运行结果：测试 Backend/WEB Ready；受控密码重置同时撤销历史会话并提升授权版本。维护任务首次误入业务 Pod 导致测试 Backend 出现过一次 OOM 重启，随后改为独立 1GiB Job 并成功完成；生产 Backend/WEB 始终保持 `2.5.3` 未变。
- 规格：`docs/requirements/2026-08-24-v5.2.0-delegation-baseline-box-door-maintenance.md`。
- 迁移记录：`docs/migrations/2026-08-24-v5.2.0-delegation-baseline-test.md`。

## 5.1.2 用户操作与定位轨迹埋点

- 状态：已完成代码、构建、安全扫描、PostgreSQL/Liquibase、测试命名空间、只读并发压测和 Android 模拟器定位成功/失败闭环；当前仅部署 `e-report-test`，等待用户终验，终验前禁止切换生产。
- 版本：当前 Backend、WEB、APP 候选统一占用 `5.1.2`；Android `versionCode=4001002`、`runtimeVersion=android-5.1.2-native4001002`。`5.1.0` 因逆地理编码结果误脱敏、`5.1.1` 因定位前置检查失败未携带结构化设备诊断而拒绝提升，两个不可变测试候选均保留；数据库迁移 002 随 5.1.0 已幂等执行，5.1.2 不新增迁移。
- 目标：在不阻塞业务交互的前提下，补齐 WEB/APP 用户操作、服务端关键写操作和 GPS 分阶段诊断轨迹。
- 边界：不采集坐标、地址、账号联系方式和硬件唯一标识；正常操作采样、失败与定位全量；故障诊断与操作轨迹分开展示。
- 数据：观测事件按租户分组异步落库，PostgreSQL 强制 RLS；成功操作默认保留 14 天，其他诊断默认 30 天。
- 验证：测试 Backend/WEB 均为不可变镜像 `20260824-v5.1.2-user-telemetry-test-1` 且 Ready `1/1`、重启 `0`；只读压测并发 `1/8/16` 全部返回 `200`，完整登录 `3/3` 成功；模拟器实测定位成功和关闭系统定位后的失败诊断均可由后台按用户追溯，事件不含坐标和地址；测试期间新增工单和通知 Outbox 事件均为 `0`，生产仍保持原版本。
- 规格：`docs/requirements/2026-08-23-v5.1.0-user-operation-telemetry.md`。

## 5.0.9 权限体系 V4 原子授权与资源关系

- 状态：实施中，仅允许部署 `e-report-test`，用户终验前禁止切换生产。
- 版本：Backend、WEB、APP 和数据库契约统一占用 `5.0.9`；Android `versionCode=4000009`、`runtimeVersion=android-5.0.9-native4000009`。`5.0.0` 至 `5.0.8` 分别因数据库管理驱动缺失/未注册、无关迁移参数、Node 构建链漏洞、WEB Docker context 路径缺陷、历史岗位规则未安全收敛、元数据断言未限定当前 Schema 及新增审计表未纳入 PostgreSQL 归档计划被拒绝提升，既有候选均不可覆盖。
- 目标：以 every-action 原子权限、用户直接 Grant、组织/部门/工单资源关系替换旧 V3 管理域、账号保护、管理主体、独立委托和审批体系。
- 非目标：不实现工单多标签/多任务，不切换生产，不保留旧控制面运行时兼容层。
- 决策：`docs/decisions/authorization-policy-v4.md`。
- 执行计划：`docs/requirements/2026-08-23-v5-authorization-v4-execplan.md`。

## 4.2.0 用户组织迁移预检与差异确认

- 状态：实施中，仅部署 `e-report-test`，用户终验前禁止切换生产。
- 版本：Backend、WEB、APP 和测试候选统一为 `4.2.0`，Android `versionCode=3002000`、`runtimeVersion=android-4.2.0-native3002000`；`4.1.5` 作为已部署测试候选保留且不覆盖。
- 目标：把单用户所属组织调整升级为“选择目标与策略 -> 服务端预检 -> 查看组织、授权、管理身份和工单责任差异 -> 确认执行”，避免盲目迁移。
- 范围：新增只读预检 API/显式 DTO；复用组织范围、账号管理面和授权计划校验；WEB/APP 展示结构化 diff、阻断原因与推荐策略；执行时校验预检版本。
- 非目标：不迁移历史工单归属，不自动改派当前工单，不变更管理主体或默认管理员，不修改生产数据。
- 影响文件：`AdminUserController`、用户组织迁移领域服务与 DTO、授权计划预览、工单/管理事实统计、WEB/APP 用户详情组织调整弹窗、版本契约及测试。
- 验收标准：预检零写入；普通调拨默认推荐岗位重定位；明确展示保留/调整/撤销授权、未结提单和处理中责任；数据漂移后执行失败并要求重新预检；权限、管理域和租户边界不可绕过。
- 测试方式：Backend 单元/集成测试、WEB/APP workflow 测试及构建、测试命名空间 API/浏览器/模拟器 smoke；生产保持不变。

## 4.1.5 权限系统 V3 与 PostgreSQL 联合迁移

- 状态：实施中，仅允许部署 `e-report-test`，用户终验前禁止切换生产。
- 目标：在同一破坏性版本中完成权限 V3 四平面、PostgreSQL 原生 UUID/租户隔离、MySQL 数据深迁移和拼音搜索，不保留 V2 判定、数据库双写或长期兼容层。
- 版本：功能架构名称为权限系统 V3；`4.1.5` 已完成 PostgreSQL/RLS 测试部署，后续组织迁移预检能力占用 `4.2.0`；`4.0.0`、`4.0.1`、`4.1.0`、`4.1.1`、`4.1.2`、`4.1.3`、`4.1.4`、`4.1.5` 均作为已生成的迁移测试候选保留。
- 规格：`docs/requirements/2026-08-20-v3-authorization-security-upgrade.md`；决策：`docs/decisions/authorization-policy-v3.md`；迁移计划：`docs/migrations/2026-08-21-v4-postgresql-authorization-v3-plan.md`。

## 2.5.3 权限目录漂移与默认管理员修复

- 状态：2.5.3 已于 2026-08-20 完成生产发布；Backend、WEB、Liquibase、R2、Android 模拟器和真实管理员 smoke 均通过，APP 为非强制更新。
- 目标：历史权限行不再阻断岗位模板，权限升级可审计可预警，并统一修复平台、市公司根组织和区县默认管理员岗位。
- 范围：权限目录查询、启动校验、轻量漂移诊断、Liquibase 050、组织默认管理员归属、标准岗位收敛、授权版本失效，以及退役 `app.bootstrap-admin.*` 和 `DEFAULT_ADMINISTRATOR` 全局标记。
- 非目标：不提供页面删权限，不自动扩展一对多映射，不授予区县管理员全市 SLA 修改权限。
- 验收：未知无引用权限不展示且接口 200；缺失有效权限阻断启动；迁移幂等且引用安全；默认管理员分层岗位、数据域和会话失效正确。
- 规格：`docs/requirements/2026-08-20-permission-catalog-drift-default-admin-repair.md`。

## 2.5.0 工单附件容量提升

- 状态：已完成并发布生产（2026-08-19）；Backend、WEB、R2、统一下载入口和模拟器验收通过，APP 为非强制更新。
- 目标：报单和回单均支持最多 10 个附件、单次合计不超过 100 MiB，并在 WEB、APP 与 Backend 提供一致、可操作的数量和容量错误提示。
- 范围：附件选择 UI、客户端提交前校验、服务端 DTO 与领域边界校验、分片上传单文件上限、413/业务错误翻译、版本与部署候选清单。
- 非目标：不改变附件类型、存储目录、分片大小、下载方式和历史附件；不新增数据库迁移。
- 影响文件：`WorkOrderAttachmentLimits`、工单请求 DTO、`WorkOrderAttachmentService`、`FileTransferService`、WEB/APP 附件组件与错误模型、相关测试及版本文件。
- 验收标准：1 至 10 个且总量不超过 100 MiB 可提交；第 11 个、单文件或总量超限均被前后端阻断并明确说明实际值与上限；绕过客户端仍由 Backend 拒绝；既有分片重试和必传规则不退化。
- 测试方式：Backend 边界/校验/异常测试与全量测试；WEB/APP 附件策略测试、全量测试、lint/typecheck/build；测试命名空间接口、页面、日志及数据不变性 smoke。

## 2.4.6 显式分页响应契约

- 状态：已合并进入 2.5.0 候选，测试环境分页契约与日志验收通过；随 2.5.0 发布。
- 目标：所有分页 HTTP 接口统一返回显式 `PageResponseDTO`，消除直接序列化 Spring Data `PageImpl` 的不稳定契约与运行警告。
- 范围：工单、用户、权限、运行诊断、安装包、系统配置、用户导入导出任务及明细分页接口；Backend/WEB/APP/镜像统一占用 `2.4.6`。
- 非目标：不改变 Service/Repository 内部分页实现，不改变筛选、排序、数据域、权限与业务字段，不新增数据库迁移。
- 影响文件：通用分页 DTO、上述 Controller、分页契约测试、版本与部署清单。
- 验收标准：现有 `content/totalElements/totalPages/size/number` 等字段保持兼容；Spring 分页内部对象不再进入响应序列化；所有分页端点使用同一 DTO；WEB/APP 构建与后端测试通过。
- 测试方式：DTO 元数据单测、Controller/服务全量测试、WEB/APP 单测与构建、测试环境接口契约和日志 smoke；通过后提升同一不可变制品至生产。

## 2.1.3 API Token、AI Guide 与入口性能收口

- 状态：已完成并投产（2026-08-17）；测试、生产迁移、R2、WEB、模拟器与性能验收全部通过。
- 目标：个人用户创建可过期或长期 API Token，并把 Token 权限限制为用户当前 Grant 与创建时选择权限的动态交集；提供按实际权限裁剪的 LLM Guide API。
- 范围：Liquibase 043、Backend Token 生命周期与认证过滤器、`GET /ai/guide`、WEB/APP 个人中心、限流/审计/测试、HAProxy/PROXY v2 入口并发治理；最终候选占用 `2.1.3` / Android code `1001003`。`2.1.0` 因 acceptance Gradle lock 缺失、`2.1.1` 因入口迁移脚本缺陷、`2.1.2` 因 release Gradle lock 缺失被拒绝提升。
- 边界：Token 明文只显示一次；API Token 不能自我复制或管理会话；数据域、资源关系和 Grant 有效期继续由统一授权引擎判定；先测后发布。
- 规格：`docs/requirements/2026-08-16-v2.1.0-api-token-ai-guide.md`。
- 生产：Liquibase `101 -> 126`，首次 25 个 changeSet、幂等 `Run: 0`；核心 Pod Ready/重启 0；备份位于 `/opt/e-report/backups/pre-v213-20260817-031230`。
- Android：stable 发布 ID `01a00c08-c5e0-742b-9b97-a2e17199aedb`，非强制；APK 62,904,739 bytes，SHA-256 `c86b155a37b674bb036e7b4ded46869c5d8ade9b21ea975b853ee75076f01701`，公网 R2 Range/整包哈希与模拟器覆盖安装通过。
- 报告：`docs/reports/2026-08-17-v2.1.3-performance-api-token-acceptance.md`；生产记录：`docs/migrations/2026-08-17-v2.1.3-api-token-production.md`。

## 2.0.1 性能优化专案

- 状态：已随 `2.1.3` 投产；HAProxy 正式入口切换、叠加压力和独立基线均为 0 错误。
- 目标：降低登录、首页、工单查询、仪表盘、用户与授权管理、批量治理的用户可感知等待，并控制数据库、Redis、线程池和客户端资源开销。
- 边界：压力测试只在 `e-report-test` 执行；生产仅运行可精确清理的 Token smoke 和只读验证，未创建工单或通知，也未降低安全强度。
- 版本：上一个测试部署为 `2.0.0`；本轮包含新代码，按候选也必须递增版本的规则占用 `2.0.1` / Android code `1000001`。
- 规格：`docs/requirements/2026-08-16-v2.0.0-performance-optimization.md`。

## 用户导入导出生产级收口

- 状态：已随 `2.1.3` 完成桌面/移动视口终验并投产（2026-08-17）。
- 目标：补齐大批量流式导出、预览一致性、导出任务恢复与取消、导入失败结果表、原子权限边界和多租户公平调度，使用户批量治理在刷新、重启、并发和部分失败场景下仍可恢复、可解释。
- 范围：用户传输 API/DTO、任务状态机、导出查询与 XLSX 生成、临时文件登记、租户级任务调度、WEB 导入导出任务中心及相关测试。
- 非目标：不改变现有 V3.1 模板业务字段和手机号唯一键规则；不新增角色/岗位概念；不提供 APP 导入导出入口；不部署生产环境。
- 影响文件：`UserTransfer*`、`UserExport*`、`FileTransferService`、`AdminUserTransferController`、用户管理 WEB 页面与服务、相关测试。
- 验收标准：导出生成不持有完整工作簿或全部用户授权；预览过期或数据变化时阻断导出；刷新后可恢复导出并下载；停止操作能明确取消后台排队任务；部分失败可筛选并下载错误表；任务读取/取消按导入导出原子权限控制；同一租户并发受限且不会长期占满全局队列。
- 测试方式：后端状态机、权限、失败结果、恢复、并发和大数据流式测试；WEB workflow/unit、lint/build；生成真实 XLSX 后做结构、内容及可打开性验证；浏览器桌面/移动端 smoke。
- 测试环境：Backend/WEB `20260816-v2.0.0-user-transfer-hardening-test-2`，Pod Ready 且零重启；`042` 首次执行 1 个 changeset、幂等复跑 0 个；真实导出覆盖 76 个用户、792 条授权来源和 4948 条 Grant，任务恢复、单字节 Range、482162 字节整包及 SHA-256 一致。
- 现场修复：真实任务中心读取发现历史结果文件失效会使外层只读事务 rollback-only；已移除不必要的列表/详情事务、收窄预期异常并增加失效文件回归测试，重新部署 `test-2` 后任务恢复接口通过。

## 1.0.45 授权范围、组织根契约与历史账号修复

- 状态：已完成并投产（2026-08-12）；1.0.43 用户导入与角色收敛、1.0.44 安全治理能力随本版本统一上线。
- 四平面：`Tenant.rootOrgUnitId -> OrgUnit -> User.orgUnitId -> MembershipRoleGrant(dataScope) -> Role -> Permission(scopeProfile)` 单向组合；动作权限、数据范围和再授权上限在后端取交集。
- 数据：Liquibase 027 显式回填租户根组织、将漳州市公司本部编码原位迁移为 `3506`，快照并收敛历史非根用户遗留的 `TENANT_ALL`。
- 账号：默认管理员恢复全市授权上限并允许编辑其范围内 RoleGrant；区县历史异常账号收敛到所属组织树；用户详情重新展示权限化的角色编辑入口。
- 组织：根关系使用同租户复合外键，运行时不按编码、名称、类型或 `parent_id` 推断；根组织不可停用、改类型或直接换父节点。
- 新租户：内置角色直接由固定权限和角色目录创建，不再复制默认租户数据库状态。
- Android：正式版本 `1.0.45` / code45 / `android-1.0.45-native45`，与 Web/Backend 1.0.43、1.0.44 未发布能力一并交付。
- 自动验收：Backend 193 项、Web 29 项、App 40 项测试通过；隔离 E2E `287/287`，Android 16 模拟器覆盖附件预览/相册保存、账号切换、权限菜单、用户详情与会话治理；生产只读 smoke 16 项通过。
- 生产发布：Liquibase `81 -> 94`，首轮 13 个 changeSet、第二轮 `Run: 0`；四个核心 Pod Ready/重启 0，Redis `PONG`，备份与回滚点位于 `/data/e-report/backups/pre-v1045-20260812-174209/`。
- Android 发布：stable ID `019ff585-9802-7553-a5a2-1778697315fd`，强制更新且最低 code45；APK 62,723,871 bytes，SHA-256 `7d2a0c1150adf32d512b2463d14ac97c7f0e61d92acd67c7785c10cd8e61e88c`，统一下载入口的 R2 Range/整包哈希通过。
- 规格：`docs/requirements/2026-08-12-v1.0.45-authorization-scope-repair.md`；决策：`docs/decisions/permission-data-delegation-ceiling.md`。

## 1.0.44 深度安全审计与认证治理

- 状态：已随 1.0.45 完成生产部署、R2 与强制更新登记（2026-08-12）。
- 目标：统一 WEB/APP 服务端会话，补齐隐私友好的 Android 设备证明、智能挑战、强密码、指数冷却、请求签名/防重放、Token 轮换/撤销、Release 加固和软件供应链门禁。
- 迁移：Liquibase 新增安全事实表；1.0.44 切换时旧长期 Token 整体失效，已知区县默认管理员首次登录强制改密，不采集 IMEI、MAC、序列号或 Android ID。
- 供应链：npm/Gradle 锁定与 checksum、Action SHA、镜像 digest、OSV/npm/Trivy/Gitleaks、CycloneDX SBOM、受控例外到期和工具链精确指纹门禁均已落地。
- 验证：WEB 26/26、APP 33/33、Backend 182/182、供应链策略 13/13；R8 APK v1.0.44/code44 已在 Android 16 模拟器完成登录、改密、重登和会话撤销链路。
- 候选：1.0.44 APK 为 62,720,139 bytes，SHA-256 `8708C283E0C8BCBECCA9E5671E3F99F7DA4DA8FBF4CE507E7B54C7FB29D8FB45`；该候选未单独发布，正式生产制品为 1.0.45。
- 规格：`docs/requirements/2026-08-11-v1.0.44-security-hardening.md`；决策：`docs/decisions/auth-session-device-proof.md`；审计：`docs/reports/2026-08-11-security-gap-audit.md`、`docs/reports/2026-08-12-supply-chain-security-audit.md`。
- 发布门禁：生产备份、Liquibase 首轮/幂等迁移、最终制品扫描、模拟器升级 smoke、生产只读 smoke 和回滚点均已完成。

## 1.0.43 用户导入、授权收敛与用户筛选

- 状态：已随 1.0.45 完成生产交付（2026-08-12）。
- 账号：11 个区县账号保持区县全拼小写；默认密码通过 Liquibase 重置为区县首字母小写、`@`、区县邮编。
- 导入：Web 提供中文 XLSX 模板、预校验和按手机号事务性新增/更新；岗位模板展开角色，Android 不提供导入入口。
- 授权：后端统一裁剪不可授予的角色、权限和岗位模板；退役三个无权限管理员身份角色，以真实职责组合和通知收件窄角色替代。
- 列表：Web/Android 增加组织、状态、角色筛选；角色只展示前三个，其余显示数量。
- 规格：`docs/requirements/2026-08-11-v1.0.43-user-import-role-security.md`；决策：`docs/decisions/role-visibility-and-identity-role-retirement.md`。

## 1.0.42 工单查询体验与独立测试命名空间

- 状态：已完成并投产（2026-08-10）。
- Web/App：工单查询统一 Chip/Badge 高度、文字基线、状态区宽度和操作图标命中区；SLA 辅助文案改为纯时长，彻底消除“已超时 · 已超时 …”。
- 测试隔离：新增 `e-report-test` namespace，独立 MySQL、Redis、上传目录、安装包目录和密钥；每天北京时间 03:30 由 CronJob 停止测试后端、只读同步生产数据、清理出站/日报/观测/幂等/上传会话和 Redis 后恢复服务，不配置公网路由。
- 验证：Web 20 项测试、lint/build；App 23 项测试、typecheck；Backend clean test bootJar；测试 Job `e-report-test-data-sync-initial-1786373612` Complete；测试/生产工单、用户、附件计数 `45/71/89` 一致，隔离表和 Redis 均为 0。
- 发布：生产 backend/frontend 均为 `localhost/e-report-*:20260810-v1.0.42-ui-test-1`，Pod `1/1` Ready；APK `1.0.42` / code42 / `android-1.0.42-native42` 已发布 R2 并强制更新，固定入口 Range/SHA 校验通过。
- 规格：`docs/requirements/2026-08-10-v1.0.42-ui-test-namespace.md`；决策：`docs/decisions/test-namespace-scheduled-production-sync.md`；发布记录：`docs/migrations/2026-08-10-v1.0.42-ui-test-namespace-production.md`。

## 1.0.41 工单领域标识与语义颜色深度迁移

- 状态：已完成并投产（2026-08-10），1.0.42 在其契约基础上修复 SLA 展示重复文案。
- 领域：`NORMAL_FAULT -> NETWORK_FAULT`、`UNCLOSED_BOX -> BOX_DOOR_OPEN`、`NORMAL -> ROUTINE`，角色与权限编码原位同步迁移，不保留兼容别名。
- 展示：建立共享颜色契约；红色只表示紧急，紫色只表示 SLA 超时，工单类型、优先级、SLA 和流程状态分层显示。
- SLA：后端统一输出 `NORMAL / DUE_SOON / BREACHED / PAUSED / COMPLETED` 风险等级，临期阈值为当前环节预算剩余 20%。
- Android：目标 `1.0.41` / code41 / `android-1.0.41-native41`，作为强制更新版本发布。
- 规格：`docs/requirements/2026-08-10-v1.0.41-work-order-domain-color-migration.md`；决策：`docs/decisions/work-order-domain-identifiers-and-semantic-colors.md`。

## 1.0.40 只读用户详情与安装包发布策略

- 状态：已完成并投产（2026-08-10）。
- 用户详情：Web/APP 主页面只读，个人资料、账号状态、角色权限和密码重置改为独立权限化弹窗。
- 发布中心：新增强制更新、最低支持构建号和更新说明的正式策略 API 与双端编辑弹窗。
- 权限：新增 `app-update:policy-update` 和窄角色 `ROLE_RELEASE_POLICY_MANAGER`，现有发布员授权通过 Liquibase 021 平移。
- Android：统一升级为 `1.0.40` / code40 / `android-1.0.40-native40`。
- 生产：Liquibase `68 -> 69`，backend/frontend 均为 `localhost/e-report-*:20260810-v1.0.40-1`；1.0.40 已作为强制更新版本发布到 R2，最低支持 code37。
- 规格：`docs/requirements/2026-08-10-v1.0.40-read-only-user-detail-release-policy.md`；决策：`docs/decisions/release-policy-and-read-only-user-detail.md`；迁移记录：`docs/migrations/2026-08-10-v1.0.40-read-only-user-detail-release-policy-production.md`。

## 1.0.39 测试隔离、用户详情与版本号对齐

- 状态：已完成并投产（2026-08-10）。
- 测试隔离：全流程写入型 E2E 只允许在生产克隆库或独立测试环境执行，通知通道使用禁用配置或测试替身；生产部署后只运行登录、查询、权限投影、健康状态和下载链路等只读 smoke。
- 脚本防误用：写入型 E2E 必须拒绝官方生产域名，不能依赖客户端请求头或普通用户可控字段跳过邮件；生产环境不得产生测试工单、测试附件或测试 Outbox。
- 历史清理：下一版发布前先备份并盘点当前 `E2E-*` 终态工单、软删除账号、附件、上传会话和关联事件，只清理可证明属于测试的数据，清理后复核业务计数和外键完整性。
- 版本规则：`versionName` 的末段与 `versionCode` 保持一致。由于 code38 已由 `1.0.37` 正式占用，下一版从 `1.0.39 / code39 / android-1.0.39-native39` 开始，不复用 code38。
- 自动校验：发布前同时校验 `package.json`、`package-lock.json`、`app.json`、Gradle、APK manifest、runtimeVersion 和发布中心登记值，任一不一致即阻断发布。
- 用户管理：修复角色取消后未生效；列表编辑迁入详情页，资料/状态/角色范围原子保存，新增个人工单效率分析及 Web/APP 角色搜索。
- Grant 数据：Liquibase 020 将历史重复授权收敛为每用户每角色一条，并增加唯一约束。
- 生产迁移：Liquibase `65 -> 68`；历史测试账号、工单、附件、上传会话和 92 个已登记上传文件均清理为 0，重复 Grant 组为 0；清理前数据库与上传目录备份位于 `/data/e-report/backups/v1.0.39-test-cleanup/20260810-134247/`。
- 生产版本：backend `localhost/e-report-backend:20260810-v1.0.39-2`、frontend `localhost/e-report-frontend:20260810-v1.0.39-3`，Pod Ready、重启 0；生产只读 smoke 10/10 通过。
- Android：stable 发布 ID `019fea62-661f-71e1-a1d8-77620372254d`，`1.0.39` / code39 / `android-1.0.39-native39`，已启用强制更新；APK 76,042,427 字节，SHA-256 `a1c2db692088c6d75eb0255e435700e259b4d179cae5be233fdf7a519c242b36`，R2 37 段下载、签名、模拟器覆盖安装和登录态保留均通过。
- 规格：`docs/requirements/2026-08-10-v1.0.39-test-isolation-version-contract.md`；决策：`docs/decisions/production-test-isolation-and-version-contract.md`；迁移记录：`docs/migrations/2026-08-10-v1.0.39-user-administration-test-isolation-production.md`。

## 全域细粒度角色与工单关系授权

- 状态：已完成并投产（2026-08-10）。
- 目标：将三类工单的提单、处理、调度、验收、管控、审计全部拆为窄角色，并拆分用户、配置、发布和租户管理中的高风险宽职责。
- 数据：`role_permissions` 原位升级为带固定 `scope_profile` 的策略规则；Liquibase 迁移现有角色和 Grant，不保留长期角色别名。
- 岗位模板：内置模板只读可复制，租户自定义模板可维护；应用后展开为明确角色授权，不对既有用户产生隐式联动。
- 修复：提单人闭环后历史可见、历史处理人可追溯、无 Grant 或过期 Grant 必须拒绝、列表与详情授权同源。
- 验证：后端 47 个套件 131 项测试、Web 14 项测试及 lint/build、App 11 项测试及 typecheck/Expo Doctor 19/19 全部通过；生产全角色 E2E 193 项断言通过，Android 模拟器完成管理员和最小提单角色回归。
- 生产：Liquibase `50 -> 65`，backend/frontend 均为 `localhost/e-report-*:20260810-fine-rbac-2`，Pod Ready 且重启 0；9 个内置岗位模板、45 个角色、76 个权限、161 条范围规则已生效。
- Android：`1.0.37` / code38 / `android-1.0.37-native38` 已登记 stable；APK 76,030,319 字节，SHA-256 `35b78395d74cd31db86082068be57d3ba187c969d6e9f30624692b7862e788a3`，官方入口到 R2 的 37 段 Range 下载、签名和覆盖安装通过。
- 规格：`docs/requirements/2026-08-09-fine-grained-role-policy.md`；决策：`docs/decisions/fine-grained-role-policy-rules.md`。
- 迁移记录：`docs/migrations/2026-08-10-fine-grained-role-policy-production.md`。

## APK 仅 R2 直连分发

- 状态：已完成并投产（2026-08-09）。用户固定下载入口只返回到 R2 不可变对象的 `302`，APK 字节不经过 VPS/relay。
- 后端：版本 API 删除 `apkFallbackUrl`，公开按 ID 源站下载路由已移除；R2 未配置时返回 `503` 而不回源。生产镜像为 `localhost/e-report-backend:20260809-r2-direct-1`。
- APP：只对同一 R2 URL 进行 2 MiB、4 路并发、单片 5 次自动重试，并精确校验 `Content-Range`。`1.0.36` / code37 已在模拟器从 code36 通过端内 R2 下载、校验和系统安装器完成原地升级。
- 发布：stable ID `019fe5ae-9414-7849-819a-fe0566cd9007`，APK 76,007,975 字节，SHA-256 `092b486e420aa305bbd37cc869d31152b282ebdcbddf2d8afcbbc7a1fcc38461`，R2 37 段直连下载 6.7 秒完成且无重试。
- 运行态：Backend、Frontend、MySQL、Redis 均 Ready、重启 0；本次无数据库变更，不执行 Liquibase。决策见 `docs/decisions/apk-r2-only-distribution.md`，发布记录见 `docs/migrations/2026-08-09-apk-r2-only-production.md`。

## 管理权限门、职责角色与运行诊断降噪

- 状态：已完成并投产（2026-08-09）。Liquibase 017、Web、后端和 Android `1.0.36` 已发布，生产回归闭环。
- 权限结果：`management:access` 是管理平面唯一入口，`dashboard:statistics:view` 独立控制看板；身份角色只保留入口标记，实际能力由 R/RW 窄职责角色组合并继续受组织数据范围约束。
- 账号结果：示例报单员仍只有一线提单者和普通用户，管理与统计权限均为 0；Web/App 同账号切换不再继承上一管理员菜单或“系统管理员”文案。
- 诊断结果：成功操作 INFO 不再采集和展示，失败操作保存为 WARN，主动诊断继续保留；Android 已实际入库厂商、型号和系统版本，未采集 IMEI、MAC、序列号或 Android ID。
- 生产迁移：正式库 changelog `46 -> 50`，迁移前后用户/工单/附件为 `117/77/131`；备份位于 `/data/e-report/backups/20260809-140221-rbac-observability-1`，SHA-256 `1d16f83e277b3155579fcf6352528e1aa28180cb3a4d76ee3f07fa81e4c7692a`。
- 生产镜像：backend 为 `localhost/e-report-backend:20260809-r2-direct-1`，frontend 为 `localhost/e-report-frontend:20260809-rbac-observability-1`；MySQL、Redis、backend、frontend 均 Ready、重启 0。
- Android 发布：stable ID `019fe5ae-9414-7849-819a-fe0566cd9007`，`1.0.36` / code37 / `android-1.0.36-native37`；APK 76,007,975 字节，SHA-256 `092b486e420aa305bbd37cc869d31152b282ebdcbddf2d8afcbbc7a1fcc38461`，R2 直连 37 段并行下载与整包哈希通过。
- 规格：`docs/requirements/2026-08-09-diagnostic-noise-role-access-hardening.md`；决策：`docs/decisions/rbac-capability-role-bundles.md`；迁移记录：`docs/migrations/2026-08-09-rbac-observability-production.md`。

## 工单查询三维多选与全环节 SLA

- 状态：已完成并投产（2026-08-08）。Backend、Web 和 Liquibase 015 已上线；Android `1.0.33` / code34 已登记 stable、同步 R2，并通过统一下载入口回读安装和模拟器生产回归。
- 目标：工作队列、工单类型、处理状态均支持多选；SLA 新增派单待接收和验收驳回整改阶段，使全部可停留环节都有独立时限。
- 范围：后端查询集合契约、SLA 策略/阶段/恢复上下文与 Liquibase，Web/Android 多选和 SLA 策略页面。
- 非目标：不调整工单可见范围、主状态、队列定义、排序分页和超时后的主状态。
- 影响文件：查询 DTO/Controller/Specification，SLA domain/service/lifecycle/migration，Web 与 Android 查询、策略及 API 类型。
- 验收标准：单选旧链接继续生效；多选刷新不丢失；派单、接收、处理、验收、整改、挂起和闭环时限正确；调单不重置原处理截止时间。
- 测试方式：后端集合过滤和全生命周期集成测试、Liquibase、Web unit/lint/build、App unit/typecheck/Expo Doctor、Android release 和模拟器回归。
- 规格：`docs/requirements/2026-08-08-work-order-search-multiselect-detailed-sla.md`。
- 迁移记录：`docs/migrations/2026-08-08-work-order-multiselect-detailed-sla-production.md`。
- Android 发布：stable ID `019fe18e-1728-7223-8ab3-a52dc3cc748d`，最低支持 code31、非强制升级；APK 为 75,870,411 字节，SHA-256 `72c7a75963ac1ccb41655c3dd66fb5cb84b9c09ff2c6b6eb778e746dc7f74573`。

## 区县未处理工单日报与个人信息维护

- 状态：已实现并投产（2026-08-08）；首次真实定时发送待当日 18:00 运行态观测。
- 结果：每天 18:00 按租户和区县生成未闭环工单中文 XLSX，逐一发送给区县管理员；通知总开关、邮件开关、日报开关和发送时间支持市公司默认及区县覆盖，执行台账按区县/日期唯一幂等。
- 个人资料：Web 与 Android 均可维护本人姓名、必填手机号和可选邮箱；用户名、组织、角色和权限只读。
- 数据迁移：Liquibase 014 已在生产克隆库首跑和二次幂等验证，正式库 changelog `43 -> 44`；备份目录 `/data/e-report/backups/20260807-232018-20260807-daily-report-profile-1`。
- 生产版本：backend `localhost/e-report-backend:20260808-daily-report-profile-3`、frontend `localhost/e-report-frontend:20260807-daily-report-profile-2`，均 `1/1` Ready、重启 0；日报默认开启，时间 `18:00`，夜间发布未产生迟到派发记录。
- Android 发布：`1.0.32` / code33 / `android-1.0.32-native33`，stable 发布 ID `019fdd32-b6d9-7251-b9a6-6685b9a11674`，最低支持 code31、非强制升级；正式签名、模拟器覆盖安装、冷启动、个人资料和通知中心均通过。APK 为 `75,866,267` 字节，SHA-256 `f041d53b5f2dde164f90de101cae8362ccfe1fa408224a5d2ab20903b5ef7e71`，R2、固定入口 HEAD/Range/整包哈希和 code31/code33 升级检查全部通过。
- 规格：`docs/requirements/2026-08-07-daily-county-outstanding-report-profile.md`；迁移记录：`docs/migrations/2026-08-08-daily-report-profile-production.md`。

## 用户联系方式、区县通知路由与仪表盘域分增强

- 状态：已完成并投产（2026-08-08）。
- 目标：新增用户手机号/邮箱；区县工单邮件按提单组织路由到本区县管理员；系统配置与通知中心拆分 Tab；市公司支持区县下钻及区县横向比较。
- 范围：Liquibase、后端用户/通知/统计、Web、Android、生产迁移与发布。
- 验收：三端自动化构建、通知和统计隔离测试、浏览器、Android 模拟器及生产运行态闭环；旧静态收件人和企业微信 Secret 键已从生产清理。
- 规格：`docs/requirements/2026-08-07-user-contact-notification-county-dashboard.md`。

## 中文化、必填报单、搜索式派调单与时限放宽

- 状态：已完成并投产（2026-08-07）。中文化、必填报单、搜索式派调单、SLA 放宽、UUID 附件修复、单输入框验收、APP 媒体预览/相册保存和通知中心分层开关已统一发布。
- 业务范围：系统可见文案中文化并移除“移动”标识；Web/APP/后端统一要求报单字段完整且至少一份附件；APP 查询筛选改为紧凑选择控件；派调单按姓名或账号搜索，管理员调单即时原子切换当前处理人；通知总开关及邮件、企业微信、钉钉按市/区县作用域独立启停；公网根路径使用相对重定向保留协议、主机和端口。
- 生产版本：backend/frontend 分别为 `localhost/e-report-backend:20260807-notification-media-5`、`localhost/e-report-frontend:20260807-notification-media-5`，Deployment 均为 `1/1` Ready、重启 0；APP `1.0.30` / code31 / runtime `android-1.0.30-native31` 已登记 stable，发布 ID `019fdb16-2292-7535-bd26-f68fe6bed69d`，最低支持 code31、强制升级。
- 自动验证：后端 30 个测试套件共 82 项测试、`bootJar`、Web 5 项 unit/lint/build、APP 3 项 unit/typecheck/Expo Doctor 19/19、Android release 与 v2 签名全部通过；生产全角色全状态 E2E 共 163 项断言通过，12 个临时账号和 12 张临时工单均完成软删除或取消清理。
- 生产证据：本次部署回滚目录为 `/opt/e-report/releases/20260807-notification-media-5/`，数据库快照 SHA-256 为 `57693a60486408e60601c0c92f417281295d0a8da39cca1bb589896f3a01c0b1`；通知设置 API、Playwright 桌面/390px、主域/relay/IP 入口和最近 45 分钟日志均通过。本次没有数据库结构或配置键变更，无需新增 Liquibase changeSet。
- Android 与下载：小米 `M2102J2SC` 从 code30 原地升级并保留登录态，图片和 H.264 视频均可全屏预览并新增 `DCIM/` MediaStore 记录；APK 为 `75,855,427` 字节，SHA-256 `f9bb0c2b8b764cbc32c7f47cbf8aff5c71ce837360a48c9305ea365a78b0a12b`，R2 CDN 与后端回源各按 37 个 Range 分片完成整包哈希验证。

## UUIDv7、账号权限、组织归属与故障 SLA 完整增强

- 状态：已完成并投产（2026-08-07）。
- 业务结果：登录只填写全局唯一账号；旧用户归属南靖县；新工单继承提单人组织；配置与通知支持市租户和组织树隔离；角色按报单验收、处理、派调、验收、控制、审计和管理职责拆分；Web/App 工单列表统一先进入详情；报单删除乡镇/村字段并合并定位与地址解析。
- SLA：按三类工单和 `URGENT / HIGH / NORMAL / LOW` 建立 12 组策略，覆盖接单、处理、验收、挂起、驳回和一次性超时事件；Web/App 仪表盘展示 24/48/72 小时、当前/曾超时和最长未闭环历时。
- UUID：全部技术主键及真实外键使用 `java.util.UUID`；新实体由 Hibernate `@UuidGenerator(style = VERSION_7)` 生成，MySQL 使用 `BINARY(16)`，H2 使用原生 UUID，JSON 使用标准 36 字符字符串。数值 ID、数字路由和旧 JWT 不保留双轨兼容。
- 数据迁移：Liquibase 在生产快照 rehearsal 库完整通过后正式执行；生产 31/31 主键和 54/54 外键均为 `BINARY(16)`，69 用户、30 工单、16 附件全部保留，角色由 13 增至 18、权限由 47 增至 58，乡镇/村字段为 0。
- 备份与回滚：停写备份位于 `/data/e-report/backups/20260807T000411+0800-20260806-uuidv7-sla-2/`，数据库备份 SHA-256 为 `a4332087e897feaea3189ed8782e883963f11190c04b86f2f0c2883e912570b0`；UUID contract 迁移只能通过该备份整体回滚数据库和应用。
- 自动验证：后端 27 个测试套件、65 项测试全部通过并生成 Boot JAR；Web unit/lint/build 通过；App unit/typecheck、Expo Doctor `20/20`、签名 release 构建通过；MySQL 8.4.11 rehearsal、生产 API、Playwright 桌面/390px 窄屏和小米 Android 13 真机均通过。
- 通知稳态：首次历史 SLA 超时产生的 104 条分通道 Outbox 事件已全部完成；处理器调整为单条 claim、20 秒轮询，email 平均约 60 秒一封，当前 Pod 日志无 ERROR/FATAL/Exception。
- 生产镜像：backend `localhost/e-report-backend:20260807-uuidv7-sla-outbox-4`，frontend `localhost/e-report-frontend:20260806-uuidv7-sla-2`；MySQL、Redis、backend、frontend 全部 Ready，应用 Pod 重启数 0。
- Android 发布：`1.0.28` / code29 / runtime `android-1.0.28-native29`，stable 发布 ID `019fd7e8-b10f-7eca-bb7a-ef511ab49a06`，最低支持 code29、强制升级；APK `72,949,161` 字节，SHA-256 `b1d342f0d08575cb7efd24b16d48e76a56284e83c20ef325e3d33cc1b8fdefd6`，R2 与源站 HEAD/Range/整包哈希通过。

## 工单生命周期、多租户与权限增强

- 状态：已完成并投产（2026-08-06）。
- 目标：按 `docs/requirements/work-order-lifecycle-multitenancy-detailed-design.md` 将现有三状态、全局账号工单系统演进为市公司租户隔离、组织数据范围、主动接单、派调单、回单轮次与权限化验收闭环。
- 范围：Liquibase baseline 与迁移、租户/组织/成员授权、token v2 兼容层、工单状态机和派单双槽位、回单/验收/事件、Web 与 Android 工作台和角色管理。
- 非目标：不跨市直接调单、不重命名 `fault_reports` 物理表、不引入 BPMN、不删除旧客户端兼容字段；SLA 自动化、省级跨市汇总和 Contract 清理由后续版本实施。
- 影响文件：`e-report-backen` 的 build/resources/domain/security/repository/service/controller/tests，`shared/work-order-catalog.json`，Web 工单与管理页面，App API/types/工单页面，以及本任务记录。
- 验收标准：旧账号和现有工单可兼容迁移；跨租户读取返回 404；接单并发只有一个成功；派调单仅允许合格具体人员；处理人不可普通自验；新旧三端状态和动作一致。
- 测试方式：后端完整 Gradle test/bootJar 与 Liquibase 空库/存量结构测试，Web unit/lint/build 和浏览器桌面/移动 smoke，App unit/typecheck/Expo Doctor，并通过生产克隆库三轮迁移、正式 migration Job 和运行态 smoke 验收。
- 实现结果：六状态生命周期、接派调单、派单接受/拒绝、回单轮次、权限化验收、组织数据范围、token v2、租户化附件/上传/缓存/幂等/Outbox，以及 Web/Android `allowedActions` 工作台均已投产。配置与通知中心按“平台默认 -> 市租户覆盖 -> 组织祖先 -> 当前组织”解析。
- 漳州初始化：默认租户为漳州市公司，建立芗城区、龙文区、龙海区、长泰区、云霄县、漳浦县、诏安县、东山县、南靖县、平和县、华安县 11 个区县组织及默认管理员。历史 `longwen`、`dongshan` 账号保持 ID 并收编为对应区县管理员，其余 9 个账号新建；所有区县管理员仅有 `ROLE_COUNTY_ADMIN + ROLE_USER`，数据范围为 `ORG_TREE + SELF`。
- 数据迁移：独立 Liquibase Job 执行 13 个 MySQL changeset，影响 579 行；迁移前停写备份为 `/data/e-report/backups/pre-lifecycle-production-20260806-1640`。迁移前 59 个用户、17 张工单、3 个附件、3 条物料记录全部保留，空租户/组织、跨租户引用、重复用户名和非法状态均为 0。
- 自动验证：最终后端 25 个测试套件共 63 项测试通过并生成 `bootJar`；Web unit/lint/build 和生产浏览器桌面/移动 smoke 通过；App unit/typecheck、Expo Doctor `20/20`、Android release 构建和签名校验通过。Web 主 chunk 保留约 `526.44 kB` 的非阻断告警。
- 生产发布：backend `localhost/e-report-backend:20260806-zhangzhou-lifecycle-2`、frontend `localhost/e-report-frontend:20260806-zhangzhou-lifecycle-1` 已部署，MySQL、Redis、backend、frontend 均 `1/1 Running`、重启 0。主域、relay 和 `IP:60080` 的 Web/API 均返回 200。
- 通知可靠性：生产发现一次 SMTP 瞬时失败导致聚合事件重发企微，已改为 email/wecom/dingtalk 每通道独立 Outbox。真实工单 `#26` 生成三条通道事件并一次完成，单通道失败不会再重发其他成功通道。
- Android 发布：`1.0.27` / `versionCode 28` / runtime `android-1.0.27-native28` 已登记 stable release ID `29`，最低支持 code25、非强制升级。APK 为 `72,939,585` 字节，SHA-256 `5c654a75b87a96e628c2394fcd36f90e94923c9efb91373815228010b8a43676`；R2、源站备用和固定入口的 HEAD/Range/整包哈希均通过。

## 回单闭环、关闭权限与中文权限目录

- 目标：回单成功确认后返回统一工单查询；将回单与关闭彻底拆分；清理不可达的“处理中”状态；为角色和固化权限增加独立中文显示名。
- 范围：后端工单关闭授权、角色/权限模型与初始化迁移，Web/App 工单详情、回单页、状态筛选和角色管理界面。
- 非目标：不新增接单/抢单流程，不改变三类工单模型，不修改英文角色编码和权限编码。
- 影响文件：`PermissionCatalog`、`DataInitializer`、角色/权限实体与 DTO、`WorkOrderController/Service/AccessPolicy`、Web/App 回单与详情页、角色管理页。
- 验收标准：普通处理人员只能回单且调用关闭接口返回 403；管理员可填写关闭原因关闭工单；App 回单成功确认后回到 `/search`；三端不再出现“处理中”；角色和权限 API/UI 同时展示中文名与英文编码。
- 测试方式：后端完整测试与权限 smoke、Web unit/lint/build、App unit/typecheck/Android release build，并做管理员与普通处理人员真机或模拟器回归。
- 实现结果：回单与关闭已拆为两个授权动作；App 回单成功后显示不可取消确认框，确认后返回统一 `/search`；回单页不再包含关闭操作；关闭端点新增 `work-order:close` 并同时要求 `ROLE_ADMIN`，Web/App 仅向具备管理员角色和该权限的用户展示关闭原因入口。角色、权限实体和 DTO 新增 `displayName`，固化权限目录、内置角色和自定义角色均完成中文名初始化/回填。
- 状态清理：后端状态机继续只保留 `PENDING / RESOLVED / CLOSED`；Web/App 源码中的不可达“处理中”筛选、颜色和操作判断已全部删除，不新增接单或抢单流程。
- 自动验证：后端 18 个测试套件共 42 项测试通过并成功生成 `bootJar`；Web unit test、lint、生产构建通过；App unit test、TypeScript 和 Android release 构建通过。线上权限 smoke 验证管理员关闭一个不存在工单时通过授权后返回 `404`，普通 `test` 用户返回 `403`，匿名返回 `401`，未修改真实工单。
- 生产发布：backend `localhost/e-report-backend:20260805-work-order-rbac-1`、frontend `localhost/e-report-frontend:20260805-work-order-rbac-1` 已滚动部署；backend、frontend、MySQL、Redis 均 `1/1 Running` 且重启 `0`。数据库 7 个角色、27 个权限均具有非空中文名；主域、relay 和 `IP:60080` 的 Web、认证挑战及权限 API smoke 通过。
- Android 发布：`1.0.25` / `versionCode 26` / runtime `android-1.0.25-native26` 已登记 stable 发布 ID `28`，`minSupportedVersionCode=25`、`forceUpdate=false`。APK 为 `72,921,845` 字节，SHA-256 `2a25892a1e46fc242e7b3bfb79c2aae8a655b4f51fefbeb7c1c6e218bd976250`；固定入口 `https://e-report.omnnu.xyz:60443/e-report/download/android` 返回 `302` 到 R2，CDN Range 返回 `206`，整包回读大小和哈希完全一致。
- 发布设施修复：`publish-app-release.ps1` 将 R2 对象的 `Cache-Control` 从非法的 `HttpContent.Headers` 移到 `HttpRequestMessage.Headers`，修复新版 PowerShell/.NET 下发布脚本在 R2 PUT 前失败的问题；脚本语法检查和真实 code26 发布均通过。
- 设备验证：code26 已在 `emulator-5554` 覆盖安装并启动，版本元数据、登录页、验证码加载和中文错误包装正常。Android 16 模拟器的系统键盘在 ADB 自动输入特殊字符时存在重复/切换 Activity 干扰，因此未把登录后只读点击计为通过；登录后行为由 API 权限矩阵、源码条件检查和自动测试覆盖，未创建、回单或关闭任何生产工单。

## Android 安装包 CDN 与并发分片下载

- 目标：解决 Tunnel 下载 APK 速度慢、长连接易中断的问题，同时保留主站和 relay 灾备链路。
- 范围：R2 自定义域名、升级 API 主备地址、App 并发 Range 下载、发布脚本原子登记和真机升级验证。
- 非目标：不把动态 API 迁移到 CDN，不取消现有业务存储和 relay，不改变 APK SHA-256 完整性校验。
- 影响文件：`AppReleaseUrlBuilder`、`AppUpdateController`、K8S 环境变量、App 更新下载器、Android 发布脚本和本决策文档。
- 验收标准：生产升级 API 返回 CDN 主地址和源站备用地址；CDN 支持 Range 206；每片自动重试；CDN 失败可回退；旧版真机可完成下载和覆盖安装。
- 测试方式：后端完整测试、App typecheck/doctor/release build、CDN HEAD/Range/整包测速、主站与 relay API smoke、真机破坏性升级回归。
- 历史实现：`code19` 起曾使用独立 CDN 域名；当前已统一为主域 `60443` 源站下载。App `code20` 起使用 2MB 分片、4 路并发、单片最多自动重试 5 次，并在下载后校验长度和 SHA-256。发布脚本锁定项目内 Wrangler，业务分片默认通过 relay 上传，R2 仅保留异地镜像，失败会主动终止上传会话。
- 性能结果：桌面整包 72.9MB 下载用时 `10.29s`；真机同网络 4 个 2MB 分片串行耗时 `23.25s`，4 路并发耗时 `4.03s`，约提升 `5.8` 倍。主站与 relay 备用下载均支持 Range 206。
- 发布结果：Android `1.0.19` / `versionCode 20` 已登记 stable 发布 ID `22`，非强制升级；主站和 relay 均让 code19 检测到 code20，code20 不再提示升级。APK 为 `72921837` 字节，SHA-256 为 `a84fcda6d5b69f00578abfdc0266162e708148ef91c972fe618f86b14cb5c5dc`。
- 运行态清理：终止两条历史 `INITIATED` 发布会话并删除对应临时分片；一份未被发布记录引用的 `COMPLETED` APK 经引用数为 0 校验后删除，正式 code19/code20 文件保持不变。
- 最终验收：主站与 relay 的 `/e-report/download/android` 均动态跳转到当前 CDN 对象；指定版本源站接口继续支持 Range 206 作为灾备。K8S 中 MySQL、Redis、backend、frontend 全部 Ready、重启数 0，发布后日志无 `ERROR/Exception`。

## Android 外部地图导航完善

- 目标：工单详情和处理页点击导航后，可在已安装的地图应用中选择并直接导航到工单现场。
- 范围：Android 地图应用探测、系统选择器、高德/百度/Google Maps URI、WGS84 调用时坐标转换、无地图应用时网页回退和错误提示。
- 非目标：不在本应用内实现路线规划，不修改数据库 WGS84 存储规范，不接入微信 OpenSDK 或小程序。
- 影响文件：`map-navigation.ts`、Android 原生导航模块、`MainApplication.kt`、`AndroidManifest.xml`。
- 验收标准：有效 GPS 可拉起地图选择器或唯一已安装地图并进入驾车导航；高德/百度终点无坐标偏移；未安装地图时打开网页导航；无效 GPS 返回中文提示。
- 测试方式：App typecheck、Expo doctor、Android release build、模拟器安装与导航入口 smoke。
- 实现结果：新增 `EReportNavigation` Android 原生模块，按包名探测高德、百度和 Google Maps；唯一地图直接启动，多地图交给系统选择器，无支持地图时回退高德网页。业务坐标继续保存 WGS84，仅在调用高德/百度时转换为 GCJ-02。
- 验证结果：TypeScript typecheck、Expo Doctor `20/20`、Kotlin 编译和 release APK 构建均通过；候选 `1.0.19/code20` 已在小米 Android 13 真机覆盖安装并保留登录状态，工单 `#1` 的 `117.364950,24.519304` 经 MIUI 首次跨应用授权后成功拉起高德并进入目标导航，应用无崩溃。
- 发布状态：导航与高德位置链接分享已随 `1.0.19/code20` 登记发布中心，发布 ID `22`。

## OSIV、行政区划解析、外部导航与位置分享

- 目标：关闭 OSIV 后保持所有接口正常，准确区分道路与行政村/社区，并支持从工单 GPS 导航或分享现场位置。
- 范围：后端工单事务内 DTO、附件/物料两阶段加载、高德逆地址与同区县行政地名候选、Web/App 导航和系统分享入口。
- 非目标：不修改数据库坐标存储规范，不内建地图或路线规划，不改变工单状态机和权限。
- 影响文件：`FaultReportService`、`FaultReportController`、`FaultReportRepository`、`LocationService`、三端测试、Web/App 工单详情与 App 处理页。
- 验收标准：OSIV=false 时工单 create/detail/respond/close 正常；道路名称不再写入村镇/街道；不确定的社区/行政村返回候选供现场确认；WGS84 坐标可拉起地图并生成高德位置链接；线上 ID 1 主站/relay 详情返回 200。
- 测试方式：后端完整测试、逆地址样例测试、Web build、App typecheck/doctor/release build、K8S 和真实 API smoke。
- 实现结果：逆地址采用 `neighborhood` 强证据与同 `adcode` 行政地名 POI 候选组合，返回 `CONFIRMED / NEEDS_CONFIRMATION / UNRESOLVED`；道路、门牌、建筑和普通小区不再写入行政村/社区字段。Web/App 均可点选候选；Android 导航通过原生模块调用已安装地图应用，并按地图坐标系转换；位置分享统一生成高德地图链接，不依赖微信 SDK。
- 发布结果：后端/Web 已在 `100.64.0.5` 完成滚动部署，Pod 均 `1/1 Running`、零重启；Android `1.0.18` / `versionCode 19` 已登记 stable 发布 ID `21`，非强制升级。
- 生产验收：主站和 relay 的工单 ID 1 详情均返回 200；坐标 `117.364960,24.519241` 返回乡镇“山城镇”、行政村候选“三卞村（334米）”，不再误填“杉行街”；code18 检测到 code19，code19 不再提示升级；主备固定下载均支持 Range 206，APK 为 `72920249` 字节，SHA-256 为 `46d8ee19919d556100f6a4a74513d8b705fd29adbada45c6c6f1fd742051e4be`。
- 发布补充：`1.0.19/code20` 已通过 typecheck、Expo Doctor 20/20、release 构建、APK 签名校验和小米真机覆盖安装，并完成正式登记。

## 生产数据清理与生产就绪

- 目标：清除线上全部测试业务数据和测试账号，仅保留默认管理员，并防止测试账号在服务重启后重新生成。
- 范围：用户与关联角色、工单、附件、物料消耗、上传会话、Redis 缓存、上传卷；保留权限目录、角色模板、系统配置、通知配置和正式 APK 发布记录/文件。
- 非目标：不修改工单状态机、权限端点、管理员凭据、应用发布记录和 MySQL 基础结构。
- 影响文件：`DataInitializer`、生产配置、K8S manifest、部署脚本、生产清理工具和迁移记录。
- 验收标准：数据库仅有 `admin`；工单/附件/物料/上传会话为 0；上传卷为空；code18 发布记录和 APK 可下载；管理员可登录；Pod 全部就绪。
- 测试方式：清理前备份与表计数、后端测试、生产部署、清理后数据库/卷/K8S/API/下载链接 smoke。
- 实现结果：默认测试账号初始化已从后端、K8S manifest 和部署脚本移除；生产清理工具支持只读盘点、强确认执行、自动备份、失败恢复和独立复验。
- 生产结果：仅保留 `admin`；`1012` 条工单、`669` 条附件、`23` 个上传会话、`4` 条外部指令及其他测试运行数据已清零；上传卷由 8 个文件清为 0；权限/角色、18 项系统配置、17 条正式发布记录和 17 个 APK 文件保持不变。
- 备份位置：目标机 `/data/e-report/backups/20260728T044236Z`，包含 MySQL 逻辑备份、上传卷归档、清理前状态和 SHA-256 清单。
- 安全加固：仓库内数据库、SMTP、管理员和高德明文默认值已移除，改由 `e-report-mysql`、`e-report-default-admin`、`e-report-external-integrations`、`e-report-auth` 等 K8S Secret 注入；生产 SQL DEBUG 已关闭。Open EntityManager in View 因现有 DTO 映射兼容性保持默认开启。
- 最终验收：主站与 relay 均以 `admin` 登录成功，用户数 1、工单数 0；统计接口三类工单全为 0；最新 APK code18 主备下载均为 200；Pod 全部 `1/1 Running`、零重启，最近日志无 ERROR/Exception/SQL DEBUG。

## 附件读取、稳定 APK 下载与全 API Relay

- 目标：修复压缩图片元数据导致的文件读取中断，提供永久不变的浏览器 APK 下载链接，并让 relay 覆盖完整 API 契约。
- 范围：App 附件真实大小与分片读取；后端 latest APK 下载别名；Web/relay 固定下载入口；relay 全路径代理与生产验证。
- 非目标：不改变工单状态机和权限模型；非幂等写请求不在响应未知时自动双发。
- 影响文件：`e-report-app/src/api/file-transfer-api.ts`、附件大小工具、`AppUpdateController`、`AppUpdateService`、`SecurityConfig`、Web/relay Nginx、发布与部署文档。
- 验收标准：相机和相册压缩图片按实际文件长度上传；主/备固定链接支持浏览器下载与 Range；relay 可访问公开、认证、工单、管理、上传和下载 API；三端构建与生产 smoke 通过。
- 测试方式：App typecheck/release build、后端单元测试、Web build、Nginx 配置检查、K8S/relay rollout、主备 API 与 APK 分片校验。
- 实现结果：App 上传改为使用打开后文件句柄的真实长度并显式管理读取偏移，修复 ImagePicker 压缩文件 URI 与原图 `fileSize` 不一致导致的读取中断；后端新增 `GET|HEAD /app-updates/latest/apk`；主站和 relay 新增永久固定入口 `/e-report/download/android`；relay 通用 API location 覆盖全部 HTTP 方法并增加可观测响应头。
- 发布结果：backend/frontend 已在 `100.64.0.5` 单节点 K8S 完成两次滚动发布，最终 Pod 均 `1/1 Running` 且零重启；VPS relay 已通过 `nginx -t`、热重载和 Traefik HTTPS smoke；Android `1.0.17` / `versionCode 18` 通过 relay 完成认证、分片上传、合并和 stable 发布，发布 ID `20`，非强制升级。
- 生产验收：主/备升级接口均返回 code18；code17 检测到升级、code18 不再提示升级；主/备固定链接均返回 code18 文件名、大小、SHA-256 和 Range 能力；relay 固定链接完成 `72914321` 字节整包下载，SHA-256 为 `89ea0f5c8f27d84f4dbb7d8afb6f9b0ec52f7bdb692c541dadfa284056508119`。模拟器安装并启动 code18，连接 `AndroidWifi`，验证码、test 登录和一线工作台正常。
- 依赖校验：Expo SDK 保持 57，不跨主版本；补丁依赖对齐至 Expo `57.0.8`、ImagePicker `57.0.6`、FileSystem `57.0.1` 等兼容版本，`expo-doctor` 为 `20/20`。

## 回单材料、上传错误和定位链路优化

- 目标：回单支持现场照片与物料消耗，修复移动端报单上传 413，限制材料总大小 50MB，高精度定位，并让错误提示可读且可排查。
- 范围：后端回单 API/数据模型、Web 与 Android 回单表单、Nginx 上传限制、前端错误格式化。
- 非目标：本轮不调整工单状态机、不新增库存管理、不改通知中心与权限模型。
- 验收标准：报单大图上传不再暴露 Nginx HTML；回单必须可上传照片并填写光缆/接头盒/分光器消耗；错误提示包含可排查编号；Android 与 Web 构建通过。
- 测试方式：后端 `gradlew test`、Web `npm run build`、Android `npm run typecheck` 和 release APK 构建。

## Web 与 App UI/UX 人机工效优化

- 目标：缩短报单、查单、处理回单和后台浏览的操作动线，提高触控命中率、信息扫描效率与窄屏可用性。
- 范围：Web 全局交互基线、工单筛选和列表；App 共用 Screen/Card/Button/TextField、首页、报单入口、工单列表与管理入口。
- 非目标：不修改工单状态机、API、权限、品牌颜色、业务字段和部署配置。
- 影响文件：`e-report/src/components/`、`e-report/src/theme.js`、`e-report-app/src/ui/`、`e-report-app/src/features/`、相关列表/入口页面。
- 验收标准：高频操作在首屏可达；筛选采用主条件优先、次条件渐进展开；列表关键信息无需横向反复扫描；App 触控目标不小于 44px；桌面和窄屏无重叠或截断。
- 测试方式：Web `npm run build` 和浏览器桌面/移动截图；App `npm run typecheck`、模拟器截图与关键页面 smoke。

## 三端统一工单搜索

- 目标：让用户从明确入口快速检索权限范围内的工单，并统一 Web、App 的关键词语义和搜索反馈。
- 范围：后端工单模糊查询；Web 跨类型工单搜索、分类工单和回单搜索；App 共用搜索组件、统一工单搜索页及现有列表搜索入口。
- 非目标：不引入 Elasticsearch、不修改权限模型、不搜索附件正文、不改变工单状态机。
- 影响文件：`FaultReportSpecification` 及查询测试、Web 路由/导航/搜索组件、App 路由/搜索页面/列表页面。
- 验收标准：支持工单号、地点、乡镇、村街、联系人、电话、原因、状态、报单人和处理人模糊搜索；搜索可清除、有加载及结果反馈；所有结果继续受既有权限约束。
- 测试方式：后端 `gradlew test`、Web `npm run build`、App `npm run typecheck`，并做 Web 与 Android 搜索 smoke。

## 1.0.15 生产发布

- 目标：将统一工单搜索和近期 UI/UX 优化发布到单节点 K8S，并登记 Android `1.0.15` 正式安装包。
- 范围：后端、Web 镜像本地构建与 containerd 导入；Android `versionCode 16` 构建、分片上传和发布中心登记。
- 非目标：不修改数据库结构、不调整权限或工单状态机、不强制旧版本升级。
- 影响文件：App 版本元数据、K8S 中 `e-report-backend` / `e-report-frontend` 运行态、发布中心记录。
- 验收标准：Pod 滚动发布成功；公网 Web/API 正常；线上关键词搜索生效；升级接口向 code 15 返回 code 16；APK Range 下载和 SHA-256 校验通过。
- 测试方式：三端构建、K8S rollout、公开 HTTP smoke、鉴权搜索 smoke、升级检查和 APK 分段下载验证。
- 发布结果：`e-report-backend`、`e-report-frontend` 已在单节点 K8S 完成滚动发布，应用、MySQL、Redis 均为 `1/1 Running` 且新 Pod 零重启；Android `1.0.15` / `versionCode 16` 已登记为稳定版发布 ID `18`。
- 生产验收：管理员鉴权搜索 `#1011` 在 1011 条工单中唯一命中；code 16 查询不再提示升级；APK 以 2MB 分片完成 35 段下载并校验为 `72453253` 字节，SHA-256 为 `ada99ba47e56a766ecae242404d907bab790067715e2225543fd5068b92b2a41`。

## 未登录入口与登录体验重构

- 目标：未认证用户访问 Web 或 App 业务页面时直接进入登录页，登录成功后返回原目标页面，并统一提升登录体验。
- 范围：Web 根路由与公共布局、普通/管理端共用登录组件；App 根 Stack 认证守卫、登录页和认证返回地址。
- 非目标：不修改认证协议、PoW/数学验证码算法、账号权限模型、后端接口和品牌 Logo。
- 影响文件：`e-report/src/router/`、`e-report/src/components/Auth/`、普通/管理登录页、`MainLayout`；`e-report-app/src/navigation/`、`app/_layout.tsx`、`app/login.tsx` 及相关 UI primitive。
- 验收标准：未登录访问 `/e-report/` 或任一受保护页面不显示首页并进入登录页；认证恢复期间不闪现业务内容；登录成功按 `returnTo` 返回；桌面和窄屏无重叠、截断或键盘遮挡。
- 测试方式：Web `npm run lint`、`npm run build` 和 Browser 桌面/移动 smoke；App `npm run typecheck`、Android 模拟器登录页与路由 smoke。
- 实现结果：Web 根路由、未知路由和既有业务路由统一进入认证守卫，普通/管理端共用登录组件；App 将认证判断提升到根 Stack，仅保留 `/login` 为公开页面，并携带安全的 `returnTo`。
- 验收结果：Web 定向 ESLint、生产构建、App typecheck 和 Android release 构建通过；Browser 在桌面与 `390x844` 下无重叠，未认证业务 URL 仅渲染登录页且最终 console 无错误；模拟器冷启动直接显示登录页，验证码正常加载，应用进程无崩溃。

## 1.0.16 生产发布

- 目标：将未登录入口与登录体验重构发布到单节点 K8S，并登记 Android `1.0.16` 正式安装包。
- 范围：后端、Web 镜像在目标机本地构建并导入 containerd；Android `versionCode 17` 构建、分片上传和稳定版登记。
- 非目标：不修改数据库结构、认证协议、权限模型、PoW/数学验证码算法，不设为强制升级。
- 影响文件：App 版本元数据、K8S 中 `e-report-backend` / `e-report-frontend` 运行态、发布中心稳定版记录。
- 验收标准：Pod 滚动发布成功；公网未登录首页直接进入登录页；code 16 检测到 code 17；code 17 不提示升级；APK Range 下载和 SHA-256 校验通过。
- 测试方式：三端构建、K8S rollout、Browser/HTTP smoke、升级检查和 APK 分片下载验证。
- 发布结果：`e-report-backend`、`e-report-frontend` 已在单节点 K8S 完成目标机本地构建、containerd 导入和滚动发布；应用、MySQL、Redis 均为 `1/1 Running`，新 backend/frontend Pod 零重启。Android `1.0.16` / `versionCode 17` 已登记为 stable 发布 ID `19`，`forceUpdate=false`，最低支持 `versionCode 3`。
- 生产验收：未登录访问 `/e-report/` 自动进入 `/e-report/login`，认证挑战接口返回 `200`，浏览器控制台零错误，`390x844` 移动视口无溢出；code 16 检测到 code 17 且 code 17 不提示升级；APK Range 返回 `206`，通过 2MB 分片完成 35 段回读，文件为 `72456453` 字节，SHA-256 为 `d86791dfff50c09a54c5f457ae3b4380043fef5fd1778238b572959364397e1e`。
- 已知非阻断项：Web 主入口 chunk 仍有约 `523.37 kB` 的 Vite 体积告警；Expo doctor 因三个 Expo 包仅存在 patch 版本偏差为 `19/20`，不影响本次 release 构建、签名和安装包发布。

## 1.0.21 UI/UX 统一导航与生产发布

- 目标：将 Web/App 主入口按工作任务组织，收敛三类工单的重复一级导航，并发布 Web UI/UX 改造和 Android `versionCode 22`。
- 实现结果：Web/App 均统一为“我要报单、待我处理、我的工单、工单管理、管理设置”任务入口；三类工单继续由共享 catalog 派生类型选择、Tab 和筛选；入口按权限隐藏，保留搜索、筛选、附件、地图导航、位置分享、角色识别和敏感配置掩码。
- Web 验证：`npm run lint`、`npm run build` 通过；线上 Playwright 桌面 `1440x900`、移动 `390x844` 验证未登录跳转、工作台、工单管理、我的工单、配置掩码和移动导航抽屉，页面无横向溢出，console error 为 `0`。截图：`test-results/ui-web-code22-desktop.png`、`test-results/ui-web-code22-mobile.png`、`test-results/ui-web-code22-mobile-drawer.png`。
- Web 发布：目标机单节点 K8S 已滚动到 `localhost/e-report-frontend:20260805-uiux-1`，镜像配置 digest `sha256:672fda051b465c031bbc83a08835d8a9b6e8ee5d2497503d5448706409dcdc46`，Pod `e-report-frontend-9889d5bcd-zmxhb` 为 `Running`、重启 `0`；旧镜像和部署前 YAML 保留在 `/opt/e-report/releases/20260805-uiux-1/`。
- App 验证：`npm run typecheck`、`npm run doctor`（20/20）和 Android release 构建通过；候选 APK 已在 `emulator-5554` 覆盖安装并启动，包版本为 `versionCode 22`、`versionName 1.0.21`、runtime `android-1.0.21-native22`。本轮停止了重复的模拟器登录自动化，未创建生产工单；已有登录页/安装截图证据保留在 `test-results/`。
- 发布结果：Android stable release ID `24`，`minSupportedVersionCode=21`、`forceUpdate=false`；APK `72,929,349` 字节，SHA-256 `83ca22138bc1dd9088b7f45339e2a4fafd517f7b90094400d2893bfafbbd5321`；当前统一入口为 `https://e-report.omnnu.xyz:60443/e-report/download/android`，源站备用入口由 `apkFallbackUrl` 返回。
- 发布验收：主站和 relay 的 update API 对 code21 均返回 `updateAvailable=true`，对 code22 返回 `false`；CDN 与备用入口 HEAD 均为 `200`、长度一致并声明 `Accept-Ranges: bytes`；两者 Range 均返回 `206`、`bytes 0-1023/72929349`；CDN 全量下载 SHA-256 与发布中心和本地 APK 一致。
- 运行态清理：Playwright MCP 相关进程剩余 `0`，Gradle daemon 已停止；未删除旧镜像、旧 APK、生产数据或回滚记录。

## 1.0.23 统一工单工作台与直连发布

- 目标：收敛 Web、App 和后端重复工单列表，提高单页信息密度，并支持域名与 `IP:PORT` 访问。
- 实现结果：Web/App 只保留“工单查询”业务入口；统一关键词、类型、状态和高级筛选，详情与回单从结果行进入；后端收敛为 `/work-orders/search` 与 `/work-orders/statistics`，可见范围统一为“本人上报或职责权限范围”。
- 下载链路：固定下载入口改为同源自动分片下载页；Nginx 直接分发不可变 APK，支持 `HEAD`/`Range 206`；发布脚本默认不再强制上传 R2。
- 网络入口：主域和 relay 使用 HTTPS `60443`；新增 `http://36.212.129.231:60080/e-report/` 及同路径 API、下载入口；不使用 `80/443`。
- 发布结果：backend `localhost/e-report-backend:20260805-unified-work-orders-2`，frontend `localhost/e-report-frontend:20260805-unified-work-orders-5`；Android `1.0.23` / `versionCode 24`，stable 发布 ID `26`，最低支持版本 24，强制升级。
- 生产验收：Web/App/后端构建测试通过；主域、relay 和 IP 入口返回 200；旧后端列表端点返回 404；浏览器通过主域完成 `72,920,721` 字节 APK 分片下载，SHA-256 为 `33d187f116de81b2e35722d19fefc99cc35eb8210f64bdb897db1b8119f9efd1`；模拟器覆盖安装并启动 code24，统一工作台读取线上工单正常。

## 1.0.24 管理平面角色边界与生产发布

- 目标：管理后台只对 `ROLE_ADMIN` 展示和开放，同时保留固化端点权限的细粒度控制，并完成 Android 管理员/非管理员真实回归。
- 实现结果：Web 管理入口和路由统一按 `ROLE_ADMIN` 判定；App 首页与根导航统一拦截非管理员的全部 `admin` 路由；后端 `/admin/**` 先要求管理员角色，各 Controller 再以 `ROLE_ADMIN AND endpoint permission` 校验固定权限。
- 自动验证：后端 13 个测试套件共 35 项测试全部通过；Web unit test、lint、生产构建通过；App unit test、TypeScript、Expo Doctor `20/20` 和 Android release 构建通过。
- Web 生产验收：非管理员首页不展示管理入口，直接访问 `/admin/dashboard` 显示 `403`；管理员可进入仪表盘并加载数据；最终浏览器 console error/warning 均为 `0`。
- App 设备验收：`emulator-5554` 上真实完成 `test` 登录、权限隐藏、冷启动会话恢复，以及管理员登录、管理入口、后台配置列表和用户管理页访问；最终设备停留在管理员首页，冷启动后会话恢复，进程无 Fatal、ANR 或 React Native error。
- 权限旁路验收：线上 `test` 调用 `/admin/users` 返回 `403`，管理员调用返回 `200`，两者调用 `/auth/me` 均返回 `200`。
- 发布结果：backend `localhost/e-report-backend:20260805-admin-role-gate-1`，frontend `localhost/e-report-frontend:20260805-admin-role-gate-2`；Android `1.0.24` / `versionCode 25`，stable 发布 ID `27`，最低支持版本 25，强制升级。
- APK 证据：安装包 `72,920,909` 字节，SHA-256 为 `3cb136ef43489d7883a7a4b863c130c1eac7ebb41ca4bd4289870d2850db6bca`；固定入口为 `https://e-report.omnnu.xyz:60443/e-report/download/android`，不可变文件支持 `Range 206`，远端分片哈希与本地一致。
- 运行态验收：单节点 K8S 的 backend、frontend、MySQL、Redis 均为 `1/1 Running` 且重启 `0`；HTTPRoute 主域、relay 和 IP 直连均 Accepted；最近一小时应用日志无 ERROR/FATAL；主域、relay、`IP:PORT` 的 Web 和 auth API 均返回 `200`。

## APK R2 主分发与管理后台

- 目标：解决 VPS 约 10MB 出口带宽导致的 APK 下载慢和中断问题，同时保留固定下载入口、源文件灾备和可运维的 R2 管理能力。
- 架构：`apkUrl` 和固定下载入口统一指向 `download.omnnu.xyz/android/{channel}/{versionCode}/app-release.apk`；VPS 只承载版本元数据、源文件和 `apkFallbackUrl`，不再输出日常 APK 主体流量。
- 后端：新增 R2 对象列表、补传/重传、删除和状态聚合；只允许 `ROLE_ADMIN` 且具备 `app-update:manage`。当前版本禁止删除。补传采用后台任务，接口在 `478ms` 返回 `202`，实测 code24 立即进入 `SYNCING` 并最终到 `SYNCED`。
- Web：新增“安装包发布”管理页，提供搜索、状态汇总、公开链接、异步同步、历史对象删除和桌面/移动端布局；当前 code25 删除按钮禁用，后端仍做第二道保护。
- 发布脚本：恢复 R2 直传，但不引入 Wrangler；Cloudflare token 只从 `D:\WorkSpace\Project\服务器管理\private\cloudflare\tokens.env` 读取。K8S 仅保存 `e-report-r2` Secret，仓库无 token 明文。
- 自动验证：后端 17 个测试套件共 39 项测试通过，`bootJar` 成功；Web unit test、lint 和生产构建通过；App unit test 与 TypeScript 检查通过；PowerShell 发布脚本语法检查通过。Vite 保留约 `523.43 kB` 的既有主 chunk 告警。
- 生产发布：backend `localhost/e-report-backend:20260805-r2-apk-center-5`，frontend `localhost/e-report-frontend:20260805-r2-apk-center-4`；Pod 均 Ready、重启 `0`，最近日志无 `ERROR/Exception`。
- 下载验收：主域、relay、`IP:60080` 固定入口均返回 `302` 到 code25 R2 对象；浏览器真实下载得到 `72,920,909` 字节，SHA-256 `3cb136ef43489d7883a7a4b863c130c1eac7ebb41ca4bd4289870d2850db6bca`，与发布记录完全一致；Range 请求返回 `206`。
# 轻量化客户端可观测性一期

- 状态：已完成并投产（2026-08-09）。
- 目标：在现有 Spring Boot、MySQL、Web 和 Android 内完成生产级错误、警告、关键操作与诊断采集，不新增重量级观测组件。
- 范围：Liquibase、批量采集/查询/清理、组织隔离、双重脱敏、Web SDK、App SQLite 队列与定位诊断、管理端查询。
- 非目标：硬件唯一标识、告警规则、市县观测对比、问题状态流转和外部观测套件。
- 验收标准：三端请求编号关联、离线幂等补传、权限隔离和敏感信息清理均有自动化及真实运行证据。
- 测试方式：后端集成测试和 Liquibase，Web unit/lint/build 与浏览器 smoke，App unit/typecheck/Doctor/release 与 Android 模拟器回归。
- 生产结果：Liquibase 016 已将正式库 changelog `45 -> 46`，新增 UUIDv7 `BINARY(16)` 观测表且用户/工单/附件保持 `117/77/131`；区县管理员仅能查看本组织树，市公司管理员可查看租户全域。
- 客户端结果：Web 和 Android 均已完成错误、警告、关键操作、离线重试和主动诊断；双重脱敏验证未落库 IMEI、MAC、序列号、Android ID、手机号或邮箱原值。
- Android 发布：stable ID `019fe432-30ea-77aa-8c3b-8f0df3af18be`，`1.0.34` / code35 / `android-1.0.34-native35`，APK `76,007,447` 字节，SHA-256 `77d7b92a0eddf5deeee6f2b509f903716291fe90eb2f441f340f8938a84e8304`。

# Redis 系统级并发基础设施

- 状态：已完成并投产（2026-08-09）。
- 目标：将 Redis 从零散配置缓存提升为共享鉴权、热读缓存、原子并发控制、分布式协调和可靠缓冲基础设施，实质降低数据库 I/O。
- 范围：鉴权快照、组织目录、SLA 策略、工单统计、配置缓存、命令幂等快速回放、登录挑战/限流、定时任务锁、R2 同步状态、地图游标、客户端观测队列及 Redis 有状态部署加固。
- 非目标：不把工单、权限、通知 Outbox、幂等记录或审计事实迁出 MySQL；不引入 Redis Cluster/Sentinel 和重量级监控套件。
- 影响文件：后端缓存与安全服务、用户/角色/组织/SLA/工单写后失效、调度器、Kubernetes manifests、Redis/Liquibase/三端验证测试。
- 验收标准：缓存命中减少重复 SQL；写后立即读到新值；Redis 故障语义明确；并发原子性、AOF 恢复、认证、容量边界和三端业务链路全部验证。
- 测试方式：后端全量测试与 SQL 查询计数、真实 Redis Lua/序列化/重启恢复、Kubernetes dry-run、Web/App 构建、浏览器与 Android 模拟器回归。
- 生产结果：Redis 7.4 使用认证、AOF `everysec`、周期 RDB、`noeviction`、192MB 上限和 `/data/e-report/redis` 持久卷；鉴权、组织、SLA、配置和工单统计五类缓存已真实生成，命中率约 52%，无拒绝连接或淘汰。
- 可靠性结果：登录挑战一次性消费、限流、Lua 原子队列、64MB 缓冲边界、32 批死信上限和分布式任务锁均已通过自动化、隔离 canary 与生产重启恢复验证；生产诊断队列最终为 `0/0/0`。
- 生产版本：backend/frontend 为 `localhost/e-report-*:20260809-redis-observability-2`，Pod 均 Ready、重启 0；迁移与回滚证据见 `docs/migrations/2026-08-09-redis-observability-production.md`。

# 5.6.8 媒体脱敏依赖链与生产发布

- 状态：已完成（2026-09-03）。
- 依赖链：`opencv-python` 运行时补齐 `libgl1`、`libglib2.0-0`；Tomcat 固定 `11.0.25` 并更新 Gradle lock/verification metadata。
- 运行态：测试、生产 worker 使用同一不可变镜像 `20260903-v5.6.8-image-redaction-ocr-1`；资源为 `256Mi/1536Mi`，单并发串行处理，原图和 `_sanitized` 结果均保留。
- 生产验证：worker 处理历史队列稳定，重启 `0`，峰值 `VmHWM=1112236 kB`；备份、HTTP、APK 更新接口和 Range 下载均通过。详见 `docs/migrations/2026-09-03-v5.6.8-production-release.md`。
