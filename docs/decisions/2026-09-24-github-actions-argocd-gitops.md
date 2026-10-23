# GitHub Actions 构建与 Argo CD 部署

## 背景

原发布流程在本机编译，再上传构建上下文到 `ecs-zhouzhen`，用 Buildah 打包并通过 `kubectl set image` 更新。每次发布需要人工串行操作，GitHub 的“供应链安全”工作流不包含部署。

## 决定

- 源码保留在私有 `Prodigalgal/e-report`；私有 `Prodigalgal/e-report-config` 保存从 `ecs-zhouzhen` 实际运行资源迁移的测试、生产配置，按 Deployment、Service、ConfigMap、Secret、CronJob、HTTPRoute 和测试 RBAC 拆分。Secret `data` 以 Kubernetes 原样 Base64 值入库；持久化数据、历史一次性迁移 Job 和平台级 Gateway 不入库。Namespace 基线单独存档，不由 Application 同步。
- 源码推送到 `main` 后先运行“供应链安全”；通过后分别触发 `build-backend.yml`、`build-frontend.yml`、`build-android.yml` 三条独立流水线。Backend/WEB 镜像由 GitHub runner 打包并推送私有 GHCR，镜像 tag 包含日期、正式三段版本、源码 SHA 与 run id，Deployment 只引用镜像摘要；APK 在 GitHub runner 签名并上传 artifact。
- 三条构建流水线仅接收 `main` 的安全工作流完成事件；PR/计划审计使用独立并发键，不能取消正在进行的构建。`release-gitops.yml` 在 Android 构建成功后等待同一源码提交的 Backend/WEB 流水线成功，核对三份制品清单、镜像摘要和 APK SHA-256，再执行提升。
- 推送和 PR 的容器门禁扫描本次发布实际使用的 Temurin/Nginx 基础镜像；每日计划审计仍扫描全部历史镜像。发布镜像在构建时执行 `apk upgrade`，并要求安装包含已知修复的 `libexpat`/`libuuid` 最低版本；未满足要求则构建失败。旧 MySQL、Redis、构建器的清单告警继续由计划审计暴露，不阻断与它们无关的 Backend/WEB 发布。
- Actions 使用独立的写入部署密钥更新配置仓库；`ecs-zhouzhen` 上的 Argo CD Core 使用只读部署密钥，每 30 秒检查配置仓库。应用 Secret、GHCR 拉取凭据在配置仓库按环境独立管理；Android 发布账号另以源码仓库 Actions Secret 注入。
- 自动发布只更新测试环境，并确认 `release.json` 的源码 SHA 与 CSRF API 正常，然后将同一 APK 登记到独立测试发布中心；测试流程不修改生产配置或生产 APK。用户验收后，通过 `workflow_dispatch` 选择 `production` 并提交已验收源码 SHA；流水线确认该 SHA 仍在测试环境且制品来自同一源码，再更新生产。生产 smoke 失败时恢复前一组镜像摘要；生产验证通过后，下载已验签 APK、复核 SHA-256，再登记 stable 包并确认 R2 同步。选择 `test` 可以重新提升已构建制品；手动提升要求源码属于 `main` 历史，不强制与 CI 定义提交相同。
- Argo CD AppProject 限制来源仓库、目标 namespace 和当前清单实际使用的 namespaced 资源类型；自动同步启用 self-heal，禁用 prune。兼容性数据库迁移由同一 Backend 镜像的 init container 在应用启动前幂等执行，详见 `database-migration-with-release.md`。

## CI 维护与自动恢复

- 纯流水线维护提交使用 `ci-only:` 前缀；供应链工作流先对实际 Git diff 做文件白名单校验，业务源码、版本、依赖、镜像构建文件和数据库变更均不得混入。校验通过后仅运行统一版本、CI 工具测试和供应链契约，不生成新的业务制品或 Android `versionCode`。普通业务提交继续完整审计与三端构建。
- CI 维护使用独立构建并发键，不取消已有业务构建。无效或跳过的 Android 完成事件使用独立发布并发键，避免挤掉待执行的有效发布。发布按目标环境排队；测试环境的配置锁只覆盖 GitOps 提交，部署等待和客户端上传不占用跨环境配置锁。生产提升仍在配置锁内完成原有验证和失败回滚。
- APK 发布统一使用 Ubuntu runner，job 时限 90 分钟，发布步骤时限 80 分钟，为慢速分片上传和对象存储同步留出时间。每片记录进度；HTTP 分片重试继续保留。工作流从自身固定 SHA 检出发布工具，并明确传入已批准业务源码的 APP 目录，工具修复不改变制品版本或校验基准。
- 发布前查询同一平台、渠道和 `versionCode` 的已登记记录；版本名、runtime、大小和 SHA-256 必须全部相同才能复用。已同步的包直接确认；同步中等待；失败或待同步时调用现有重试接口。重试不覆盖 APK，不重复创建记录，不改已有升级策略。
- 发布失败恢复工作流只允许相同仓库、`main`、指定发布工作流的 `failure` 事件。客户端发布或测试就绪验证失败等待 60 秒，测试 GitOps 提交失败等待 15 分钟，再重试原任务的失败 job；同一 run 最多执行三次。人工取消、制品校验失败、生产部署失败及未知错误不自动重启，生产回滚仍由原发布流程处理。

### 2026-10-23 测试部署等待与可用性修复

- 当前构建为 Backend、WEB、Android、Lite、Expo OTA 五条独立流水线；Android 完成后自动收集同一源码的五份不可变制品，全部校验通过才提交测试 GitOps。
- 测试镜像首次从 GHCR 下载可能超过原有十分钟窗口。测试 Deployment 改用 `RollingUpdate`，设置 `maxUnavailable: 0`、`maxSurge: 1`、`minReadySeconds: 10`、`progressDeadlineSeconds: 4500`，保留旧实例直至新实例可用；不改变生产策略或 Pod 模板。
- `promote-test` 仅更新私有配置仓库，独立 `verify-test` job 在 75 分钟内并发检查 WEB `release.json` 源码 SHA、CSRF JSON 以及 Native/Lite API 身份。单次 HTTP 最长八秒，每十秒重试，状态变化及每分钟记录脱敏摘要，超时明确失败；不输出 CSRF token。
- `publish-test-android` 必须依赖验证输出 `ready=true`，之后才发布两款 APK 和 OTA。等待工具从工作流固定 SHA 检出，支持修复 CI 后复用已构建的业务制品；不复用业务候选版本来构建新制品。集群的镜像摘要、迁移退出码、Pod Ready 和 Argo 健康状态仍属于独立运行态验收证据，HTTP smoke 不替代这些检查。

## 取舍与回滚

- GitHub runner 承担编译负载，专用节点只拉取镜像与同步运行配置，缩短人工步骤和节点构建时间；首次镜像拉取仍受 GHCR 网络影响。
- 发布依赖 GitHub Actions、GHCR、私有配置仓库及 Argo CD。任何一步失败时不得直接跳过安全门禁；可在配置仓库恢复上一组摘要，Argo CD 会自动回滚 Backend/WEB。
- Secret 的 Base64 不是加密。配置仓库必须保持私有并限制协作者和部署密钥；轮换凭据时须同时提交新的 Secret YAML，避免 Argo CD 将集群中的单独修改恢复为旧值。
- 现有 `localhost/...` 镜像暂留作 GitOps 接管基线。首个 GHCR 版本验证后，不再复用本机 OCI 导入作为正常发布路径。
- APP 与服务版本统一为同一个 `x.y.z`；APK 的 `versionCode` 严格递增。5.8.5 已在测试环境使用；5.8.6 因 CI 静态检查失败而弃用，5.8.7、5.8.8 在补齐目标机器配置和 Secret 迁移前取消；5.8.9 因运行时依赖漏洞被安全门禁拒绝，5.8.10 因历史容器清单告警被阻断，5.8.11 因 PR 审计触发的并发取消未完成，5.8.12 因 Android Gradle 校验元数据缺少两份 Maven POM 的摘要而未发布，5.8.13 因 Linux 版 `aapt2` 校验摘要缺失而未发布，5.8.14 因构建后证书摘要读取兼容问题未发布；后续完整 GitOps 发布候选为 5.8.15。

## 验收

- 配置仓库保持私有，Secret `data` 与目标集群一致；Argo CD 的两个 Application 状态为 `Synced/Healthy`，生产管理 26 个、测试管理 30 个现行资源，迁移前后业务工作负载和入口保持一致。
- GitHub 安全工作流通过；Backend/WEB 镜像由 Actions 产生且有不可变摘要；源码推送后仅测试环境自动更新，并通过 SHA 与 CSRF smoke。
- Android APK 由 Actions 签名并校验包名、版本号、证书指纹，作为 artifact 保存；用户验收并手动启动生产流水线后才登记 stable APK、确认 R2 已同步。
- 记录配置仓库 commit、源码 commit、镜像摘要、APK 哈希、smoke 结果与回滚点。
