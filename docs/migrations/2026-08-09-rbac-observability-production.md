# 管理权限门、职责角色与运行诊断降噪生产迁移记录

> 状态：已完成。权限契约回滚必须同时恢复数据库和 Web/后端镜像，不允许只回滚其中一层。

## 发布范围

- 权限：以 `management:access` 作为管理平面唯一入口，以 `dashboard:statistics:view` 单独控制统计看板；具体端点继续校验原子权限和 `DataScope`。
- 角色：管理员身份角色收窄为身份标记，实际能力由用户、角色、组织、配置、SLA、发布、观测、调度、验收、管控和审计等窄职责角色组合。
- 访问模式：权限目录将动作派生为 R 或 W，角色按有效权限派生 `NONE / R / W / RW`，不在数据库重复保存模式字段。
- 运行诊断：停止生成和接收成功操作 `INFO`；失败写操作统一保存为 `WARN`；历史成功操作保留但查询隐藏；主动诊断继续以 `INFO` 保存并显示为“主动诊断”。
- 客户端：Web/App 管理入口只认 `management:access`；角色文案按实际身份和中文角色名解析；Android 继续采集厂商和型号，不采集硬件唯一标识。

## 生产备份与迁移

| 项目 | 生产证据 |
| --- | --- |
| 停写备份目录 | `/data/e-report/backups/20260809-140221-rbac-observability-1` |
| 数据库备份 | `fault_reporter_db.sql.gz`，261,579 字节，权限 `600` |
| 数据库备份 SHA-256 | `1d16f83e277b3155579fcf6352528e1aa28180cb3a4d76ee3f07fa81e4c7692a` |
| 迁移前 Deployment | `deployments-before.yaml` |
| 正式迁移 Job | `e-report-liquibase-production-rbac017-1` |
| Liquibase 结果 | `Run: 4`、`Previously run: 46`、影响 684 行，changelog `46 -> 50` |
| 生产镜像 | `localhost/e-report-backend:20260809-rbac-observability-1`、`localhost/e-report-frontend:20260809-rbac-observability-1` |

迁移期间后端保持 0 副本。备份通过 `gzip -t` 和 SHA-256 校验后才运行 Liquibase；用户、工单、附件、角色、权限和授权不变量全部通过后才启动候选后端，候选后端完成 `DataInitializer` 后才发布前端。

## 克隆库演练

- 生产克隆基线为 46 个 changeset、117 个用户、77 张工单、131 个附件、19 个角色、59 个权限和 237 条范围授权。
- 首轮演练发现同一用户同时具有多个管理员身份时，管理员组合映射会产生重复候选行；迁移在克隆库失败，正式库未执行。
- 修复为先按用户、租户和来源身份去重，再生成 UUIDv7 授权记录；H2 全量测试和 MySQL 新克隆重新执行通过。
- 干净克隆库首次执行为 `Run: 4`，第二次幂等执行为 `Run: 0`；候选后端启动完成固定目录、角色和默认管理员同步。
- 演练后仍为 117 个用户、77 张工单和 131 个附件；角色增至 27、权限增至 61、范围授权增至 484，重复授权为 0。

## 数据与授权不变量

| 指标 | 迁移前 | 正式迁移后 | 最终运行态 |
| --- | ---: | ---: | ---: |
| Liquibase changeset | 46 | 50 | 50 |
| 用户 | 117 | 117 | 118 |
| 工单 | 77 | 77 | 77 |
| 附件 | 131 | 131 | 131 |
| 角色 | 19 | 27 | 27 |
| 权限 | 59 | 61 | 61 |
| 范围授权 | 237 | 484 | 486 |

最终多出的 1 个用户和 2 条范围授权来自生产权限回归账号 `qa-rbac36-1409`。账号已按系统审计模型软删除为 `DELETED`，无法认证或访问；保留其角色、授权和诊断记录用于生产验收审计，没有创建工单或附件。

- 示例报单员账号 `13000000093` 最终仍只有 `ROLE_FRONTLINE_REPORTER + ROLE_USER`，状态为 `ACTIVE`，有效 `management:access` 和 `dashboard:statistics:view` 均为 0。
- `ROLE_ADMIN`、`ROLE_COUNTY_ADMIN`、`ROLE_PLATFORM_ADMIN` 各自只保留 1 个权限 `management:access`。
- 区县管理员由 16 个窄职责角色组合，数据范围继续保持组织树；默认市公司管理员由市公司和平台职责角色组合。
- 管理员接口对一线提单者返回 403，工单查询返回 200；管理员访问用户管理和统计接口返回 200。

## 运行诊断验收

- 成功登录、用户创建、删除和发布操作没有新增 `OPERATION / INFO`，生产历史计数保持 51。
- 故意提交缺少必填字段的工单返回 400，并新增一条 `OPERATION / WARN / FAILURE`，计数 `4 -> 5`。
- 管理列表隐藏历史成功操作，用户主动诊断显示为“主动诊断”。
- Android `1.0.35` 主动诊断实际入库：`manufacturer=Google`、`device_model=sdk_gphone64_x86_64`、`os_version=36`。
- 诊断上下文检查未出现 IMEI、MAC、Android ID、序列号字段；Web 无可用 Client Hints 型号时保持为空，不伪造硬件厂商。

## Web 与 Android 验收

- Web 在同一浏览器完成市公司管理员退出后切换一线提单者：管理员显示管理入口；一线提单者不显示入口，直达 `/e-report/admin/dashboard` 显示需要 `management:access` 的 403。
- 角色管理页显示身份角色 `R 只读`、查看员 `R 只读`、人工处理/管理岗位 `RW 读写`；不创建不可独立使用的内置 W-only 人工岗位。
- Android 从已安装 `1.0.32 (33)` 保留数据升级到 `1.0.35 (36)`；旧区县管理员会话、个人信息页和关于页均显示正确身份。
- 模拟器在同一 APP 内完成市公司管理员到一线提单者切换：前者显示“市公司管理员”和管理后台，后者显示“一线提单者”，不显示“系统管理员”或管理入口。
- 模拟器为 `Google sdk_gphone64_x86_64 / Android 16`；网络 DNS 恢复后验证码、登录、统计、个人信息和主动诊断均使用生产 API 完成。

## Android 稳定版发布

| 属性 | 值 |
| --- | --- |
| stable 发布 ID | `019fe56a-feb9-74dc-b7cb-e1da491ae2d3` |
| 版本 | `1.0.35` / code36 / `android-1.0.35-native36` |
| APK 大小 | `76,008,519` 字节 |
| APK SHA-256 | `0d5a5888cb61c24a786e594ea0115db3693a73cfb3d8d395568fb651e35c6db9` |
| 固定入口 | `https://e-report.omnnu.xyz:60443/e-report/download/android` |
| R2 对象 | `android/stable/36/app-release.apk` |
| 升级策略 | `minSupportedVersionCode=0`、非强制升级 |

固定入口返回 code36 和对应 SHA 后重定向到 R2。R2 HEAD 为 76,008,519 字节并声明 `Accept-Ranges: bytes`；Range `0-1023` 返回 206。`scripts/verify-android-release-download.mjs` 仅解析 R2 重定向，不使用 VPS 或 relay fallback，以 APP 相同的 2 MiB、4 并发、每段最多 5 次重试完成 37 段下载；本次所有分段首次成功，6.5 秒合并完成，整包 SHA-256 与发布记录一致。

版本检查对 code35 返回 `updateAvailable=true` 和 `nativeUpgradeRequired=true`，对 code36 返回“当前已是最新版本”。code36 初始发布时仍保留了 R2 失败后的源站灾备；该契约已在同日的 code37 中删除，当前状态以 `docs/migrations/2026-08-09-apk-r2-only-production.md` 为准。

## 运行态

- MySQL、Redis、backend、frontend 均为 `1/1 Ready`，容器重启数 0。
- 后端和前端最近 90 分钟没有 ERROR、FATAL、Redis 连接失败、OOM 或 Nginx error 记录。
- 公网 Web 返回 200，认证挑战业务 API 返回 200；Redis 管理页显示正常，诊断队列为空。

## 回滚

1. 先将后端缩容为 0，停止所有权限和业务写入。
2. 校验备份 SHA-256 后整体恢复 `fault_reporter_db.sql.gz`，不得只手工删除新增角色或权限。
3. 恢复 `deployments-before.yaml` 中的上一组 Web/后端镜像并启动后端，再恢复前端。
4. Android 已发布版本不能降低 `versionCode`；如需客户端回滚，使用更高 code 发布修复版。
5. 只回滚应用或只回滚数据库会造成管理入口、端点权限和角色组合不一致，不属于允许的生产回滚方式。
