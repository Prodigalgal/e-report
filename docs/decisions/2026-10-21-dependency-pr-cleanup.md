# 依赖 PR 整合与处置

## 目标和边界

处理本仓库全部 17 个未关闭 Dependabot PR。基线为 `09c546f` / `6.7.1`；兼容更新在一个候选 `6.7.2` / Android `5007002` 中整合验证，通过后统一进入 main。WEB、Backend、APP 使用同一业务版本。

复用现有 GitHub Actions → 配置仓库 → Argo CD 测试发布链路。生产提升仍需用户验收。不修改业务/API/数据库模型、安全例外或分支保护，不以过期 CI 结果代替当前组合验证。

## 处置计划

| PR | 更新 | 处置及理由 |
| --- | --- | --- |
| #50 | setup-node 7.1.0 | 核对官方不可变提交后整合，保留 Node 24.19.0 |
| #51 | upload-artifact 7.0.2 | 核对官方不可变提交后整合，保留制品路径和哈希约束 |
| #34 | setup-java 6.0.1 | 核对运行条件和官方提交后整合，保留 JDK 21/25 选择 |
| #46 | WEB React/MUI/Router/SHA-256 | 整合后执行 WEB 测试、lint、build 与哈希兼容验证 |
| #24 | TanStack Query 5.104.0 | 解决旧锁文件冲突，验证 APP 查询与 mutation 类型 |
| #9 | js-sha256 1.0.0 | 验证 WEB/APP 的 PoW、请求签名及流式摘要兼容 |
| #20 | HttpClient 5.6.4 | 同步构建依赖锁及校验元数据，核对上游 Core 版本要求 |
| #15 | Bouncy Castle 1.85.2 | 同步依赖锁及校验元数据，运行 Backend 安全和完整回归 |
| #13 | jackson-databind 2.22.2 | 整合后修正为已修复漏洞的 2.22.3，与 #8 协调整套 Jackson 2 BOM |
| #8 | jackson-datatype-jsr310 2.22.2 | 同步提升 2.22.3，与 #13 一并验证时间及 DTO 序列化契约 |
| #52 | Expo 58 / RN 0.88 / Gesture Handler 3 | 关闭：需要 SDK 和原生工程整套迁移，当前采用 Expo 57 / RN 0.86 基线 |
| #31 | datetimepicker 9.2.1 | 关闭：Expo 57 的 bundledNativeModules 固定 9.1.0，单独变更偏离 SDK 基线 |
| #41 | AMap 11.3.000 | 关闭：PR 缺少原生依赖锁和校验元数据更新，属于定位 SDK 集成升级 |
| #49 | Android Gradle Wrapper 9.8.1 | 关闭：需要与 AGP、RN Gradle 插件及原生构建一并迁移，本批保留既有 Wrapper |
| #11 | Backend Gradle Wrapper 9.7.0 | 关闭：本批更新应用/插件依赖，保留已验证的 Gradle 9.6.1 构建器 |
| #12 | TypeScript 7.0.2 | 关闭：属于编译器大版本迁移，本批保留 SDK 57 已验证的 TypeScript 6 基线 |
| #14 | Vite 8 / ESLint 10 / React plugin 6 | 关闭：需要工具链迁移；PR 的 Vite 8.3.2 还命中已公布漏洞，当前 6.4.4 已完成修补 |

关闭表示本次不采纳该升级，不表示上游版本必然不兼容。核心 SDK、编译器、构建器升级应作为独立迁移，提供完整原生/工具链验证。

## 安全审查修正

第一次 OSV 预检发现，PR 指定的 Jackson `2.22.2` 命中以下四条已披露漏洞；当前 main 的 `2.21.7` 已修复这些问题，不能在整合时带回漏洞。因此 Jackson 2 BOM、databind、core、jsr310 统一使用 `2.22.3`（annotations 由 BOM 管理为 `2.22`），重新生成依赖锁及校验哈希后复测，不新增安全例外。

- [GHSA-7hhh-6rmp-j9qf](https://github.com/advisories/GHSA-7hhh-6rmp-j9qf)：DataInput JSON 异常消息增长导致拒绝服务。
- [GHSA-p6pp-m3f8-5c89](https://github.com/advisories/GHSA-p6pp-m3f8-5c89)：数字识别正则回溯导致拒绝服务。
- [GHSA-cxp5-3px4-pw24](https://github.com/advisories/GHSA-cxp5-3px4-pw24)：前向对象引用解析的二次复杂度。
- [GHSA-wv8q-qhhj-9h54](https://github.com/advisories/GHSA-wv8q-qhhj-9h54)：未知类型标识缓存无界增长。

## 实施和验收

- 固定待整合 PR 的 head SHA；保留现有功能，解决冲突后重建 NPM/Gradle 锁及 SHA-256 校验。
- 对整合组合运行 WEB test/lint/build、APP test/typecheck/native contract/Android bundle、Backend 全量测试与制品构建，以及供应链策略、npm/OSV/密钥/SBOM 审计。
- 已纳入 main 的 PR 标记合并，未采纳的 PR 关闭；清理已处置的机器人分支，最终重新查询未关闭 PR 数量。
- 回滚 WEB/Backend 使用上一不可变制品；APP 发布更高版本修复。所有新制品仍经测试后才可提升生产。

## 结果

整合候选为 `6.7.2` / Android `5007002` / `android-6.7.2-native5007002`。十个采纳 PR 的 head 均已保留在合并历史中，七个迁移类 PR 已关闭并删除远端分支；提交范围仅包含批准的依赖、锁文件、校验元数据、Actions 版本、三端版本元数据和本记录。

### 变更清单

- GitHub Actions：setup-node `7.1.0`、upload-artifact `7.0.2`、setup-java `6.0.1`，均固定官方完整提交 SHA；保留 Node `24.19.0`、JDK `21/25`、审计门禁、制品校验和 GitOps 触发条件。
- WEB：React/React DOM `19.3.1`、MUI `9.5.0`、Router `7.18.4`、js-sha256 `1.0.0` 和相应类型依赖；保留已修补的 Vite `6.4.4` 及现有 ESLint/React plugin 工具链。
- APP：TanStack Query `5.104.0`、js-sha256 `1.0.0`；保持 Expo `57.0.30`、React Native `0.86.3`、原生依赖锁和地图 SDK 基线。
- Backend：Jackson 2 BOM/核心模块/日期模块 `2.22.3`、Bouncy Castle `1.85.2`、构建 HttpClient `5.6.4`；重新生成 Gradle 锁和 SHA-256 校验元数据，保留 Jackson 3 `3.1.7` 与 HttpCore `5.4.3`。
- 三端版本统一递增补丁版本。业务源码、API、数据库、模型/推理源码、Dependabot 策略及安全例外均无变化。

### 验证

| 项目 | 结果 |
| --- | --- |
| Backend 全量回归 | 133 个测试类、495 项测试；失败/错误/跳过均为 0 |
| Backend 制品与严格校验 | bootJar、databaseMigrationJar、databaseAdminJar 成功；生成哈希后再次使用默认严格验证和离线锁文件构建成功 |
| WEB | 81 项测试、lint、生产 build 通过；匿名登录页面浏览器渲染正常 |
| APP | 75 项测试、typecheck、native/version/assets 契约通过；Android Hermes bundle 导出成功 |
| SHA-256 兼容性 | WEB/APP 的浏览器 ESM、浏览器 CJS、Node 入口共 48 个断言通过；覆盖空值、中文/emoji、块边界、2 MiB 以上二进制及分块摘要，与 Node crypto 对照 |
| 供应链与发布策略 | 31 项策略测试、actionlint、源码 pinning 合同、统一版本契约通过 |
| npm 审计 | WEB 0 告警；APP 原有 19 个包、22 条传播告警通过既有精确版本/父依赖/限时例外策略，未扩大例外 |
| OSV | releaseFindings=0；构建工具 272 条发现、51 个受影响包版本、93 项 advisory 通过既有基线 |
| 密钥扫描 | 干净独立检出完整扫描 92 个提交约 11.44 MB，工作目录约 27.28 MB，均未发现密钥 |
| SBOM | 已生成 `6.7.2` CycloneDX；检查 `git diff --check` 通过 |

验证命令与证据位于检出的忽略目录 `output/pr-cleanup/`。WEB 主入口仍有现有的大于 500 kB chunk 提示，不影响构建结果。本地预览未启动 API，因此匿名渲染不等同于真实登录或业务全链路验收；本次未进行原生 APK 安装/手势实测，也未在本机运行 Docker 容器审计，后两项不能用 JS bundle 导出代替。

### 发布和回滚

统一推送 main 后，由既有 Actions 执行云端审计和三端构建，再自动更新配置仓库、触发 Argo CD 测试部署。流水线在后台正常执行；不手动取消任务、改集群或上传制品。生产提升继续等待测试验收。

源码回滚参考基线 `09c546f`；WEB/Backend 运行态回滚使用此前不可变制品。APP 不覆盖或降级已发布版本，出现问题发布更高版本修复。大版本迁移 PR 的关闭理由见上表；仍保留 Dependabot 定期检查。
