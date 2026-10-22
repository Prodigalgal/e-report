# 1.0.44 软件供应链安全审计

## 结论

1.0.44 已建立本仓库可执行的依赖来源、完整性、漏洞、密钥、镜像和制品证据门禁。2026-08-12 本地全量审计通过：release 运行依赖图为 0 个未审查发现，Git 历史和工作树为 0 个密钥发现，七个固定 digest 的基础镜像扫描结果全部满足逐项例外策略，CycloneDX SBOM 已生成。

构建工具链不是“零风险”：OSV 数据库在 1.0.46 发布日新增两个公告后，Android/Gradle 全配置报告当前有 255 个“来源 + 包版本 + advisory”发现，涉及 53 个唯一包版本和 90 个 advisory。它们不在 release runtime 图中，但已纳入 2026-09-30 到期的精确指纹基线；任一新增、删除、版本或漏洞库变化都会让 CI 失败并要求重新审查。

1.0.44 候选未单独部署、上传或登记；其安全能力最终随 1.0.45 统一完成生产迁移、R2 正式包登记和强制更新发布。

## 威胁模型

- 上游 registry、依赖包、Gradle wrapper、GitHub Action 或扫描工具被同名替换、标签漂移或下载劫持。
- 直接与传递依赖出现已知漏洞，或 build-only 依赖被误带入 release runtime。
- 容器 tag 被重指向其他内容，基础镜像或打包工具携带高危组件。
- CI 中恶意 lifecycle script 获取凭据或利用可写 GitHub Token 修改仓库。
- 本地/历史源码误提交 Token、密码、私钥或云端密钥。
- 构建完成后的 JAR、WEB 静态文件、OCI archive、SBOM 或 APK 在下游传输中被替换。

## 控制设计

### 依赖来源和完整性

- WEB/APP 只使用 `https://registry.npmjs.org/`，`package-lock.json` 为 v3；所有远程 tarball 必须来自官方 registry 且包含 SHA-512 integrity；`npm ci --ignore-scripts` 禁止依赖安装脚本执行。
- 后端和 Android 对全部 configuration 启用 Gradle dependency locking；Gradle wrapper 固定版本、官方分发地址和 `distributionSha256Sum`；verification metadata 对下载的 POM/JAR/AAR 逐件保存 SHA-256。
- Maven 仓库只允许 `mavenCentral()`，Android 额外允许 `google()`；已移除 JitPack 和自定义远程 Maven 源。
- GitHub Action 必须固定 40 位 commit SHA，`contents: read`，checkout 禁止持久化凭据。
- OSV、Syft、Gitleaks、Trivy 和 actionlint 的版本、下载 URL 与 archive SHA-256 固定在审计脚本中。

### 漏洞与例外

- release 图同时扫描 WEB lock、APP lock、后端 lock 和 Android `releaseRuntimeClasspath`，任何未审查发现直接失败。
- APP 的 `npm audit` 14 个受影响传递包、28 条路径都收敛到 Metro build-only 的 `image-size@1.2.1` 两个 advisory。例外精确限制 advisory、版本、父依赖 `metro` 和 2026-09-30 到期日；release 构建前还会拒绝 ICNS/JXL/HEIF/AVIF 魔数输入。
- build tooling 独立扫描，不用宽泛 ignore。`security/osv-build-tooling-baseline.json` 保存集合计数、审查理由和 SHA-256 指纹，2026-09-30 前必须重新评审；release runtime 仍保持独立零发现门禁。
- `GHSA-r937-wjx7-w2jp` 影响 `kotlin-gradle-plugin 2.1.20` 的构建缓存反序列化。正式构建禁用 Kotlin incremental，构建输入和依赖校验和锁定，插件不进入 APK；待 Expo/React Native 支持稳定修复版后升级。
- `GHSA-hf6x-8p5f-cgmf` 影响 `httpcore5 5.4` 的 HTTP/1 头解析。该版本仅残留在 Backend verification metadata，不在 `gradle.lockfile`、运行 JAR 依赖图或 SBOM 中，生产请求路径不可达；后续整理 verification metadata 时移除。
- Trivy 只接受 `HIGH/CRITICAL`、已有修复的数据；七个镜像均固定 digest。43 条发现逐项匹配目标、包、版本、CVE、控制文件和到期日，未匹配或过期即失败。

### 下游制品

- Syft 生成 CycloneDX 1.7 SBOM；扫描排除本地产物和历史审计输出，防止 SBOM 自包含造成证据漂移；最终包含 1268 个组件、355 条依赖关系。
- 专有云构建脚本要求 SBOM 存在，生成 backend/frontend OCI archive 后写入 `SHA256SUMS` 并立即执行 `sha256sum -c`。
- 生产/测试目标清单的外部 MySQL、Redis 等镜像固定 digest；本地应用镜像使用带日期和版本的不可变发布 tag。
- Android Release 强制正式签名、R8 和资源压缩；本次候选 APK SHA-256 为 `8708C283E0C8BCBECCA9E5671E3F99F7DA4DA8FBF4CE507E7B54C7FB29D8FB45`，签名证书 SHA-256 为 `c63061845c0ca95444e9e9507338cc25284f2b5b022f6662bf85045371cb2d27`。

## 扫描结果

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| 来源/锁定契约 | 通过 | 官方 npm registry、SHA-512 integrity、Gradle lock/verification、Action SHA、Docker/K8S digest 均满足 |
| WEB npm audit | 通过 | 286 个依赖，0 个漏洞包 |
| APP npm audit | 受控通过 | 620 个依赖；14 个传递包/28 条路径均归因于 2 个精确 build-only 例外 |
| OSV release | 通过 | WEB 285、APP 576、后端 173、Android release runtime 239；过滤 2 个已审查例外后 0 个发现 |
| OSV build tooling | 受控通过 | 255 个集合项、53 个唯一包版本、90 个 advisory；新增两个公告已完成可达性审查，精确指纹与期限匹配 |
| Gitleaks | 通过 | Git 历史和工作树均为 0 个密钥发现 |
| Trivy 镜像 | 受控通过 | 7 个 digest 镜像、43 个已审查发现；nginx/redis/busybox 为 0 |
| CycloneDX SBOM | 通过 | 排除 `output`、`logs`、测试和浏览器产物后包含 1268 个组件、355 条依赖关系 |
| 策略负向测试 | 通过 | 13/13；覆盖未知 advisory、错误 digest、过期例外、父依赖漂移、registry 替换和 mutable Action/image |

## 自动化

`.github/workflows/supply-chain-security.yml` 在 `main` push、PR、手工触发和每日北京时间 06:00 执行：clean install、三端测试/构建、13 项策略测试、依赖与镜像扫描、密钥扫描、SBOM 生成，并保留 30 天 JSON 证据。Dependabot 分别跟踪 WEB npm、APP npm、后端 Gradle、Android Gradle 和 GitHub Actions。

本地等价入口：

```powershell
./scripts/security/run-supply-chain-audit.ps1 -OutputDirectory output/security
```

## 残余风险与复审

- `image-size` 两个 build-only 例外在 2026-09-30 到期；上游发布修复版本后应升级并删除例外。
- build tooling 精确基线在 2026-09-30 到期；不能只延长期限，必须结合 Expo/React Native/Gradle 可升级版本重新生成 release runtime 和 R8 候选。
- 容器例外分别在 2026-08-31 或 2026-09-15 到期；优先更新基础镜像 digest，不能把 `apk upgrade` 当永久豁免。
- 当前 `SHA256SUMS` 证明完整性但不提供签名身份。正式发布流水线下一阶段可在不引入常驻重量组件的前提下增加离线签名或 keyless attestation；本版本不对未实现能力作安全承诺。

## 工具依据

- [OSV-Scanner source scan](https://google.github.io/osv-scanner/usage/scan-source)
- [Gitleaks v8.30.1](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1)
- [Syft v1.51.0](https://github.com/anchore/syft/releases/tag/v1.51.0)
- [Trivy v0.73.0](https://github.com/aquasecurity/trivy/releases/tag/v0.73.0)
