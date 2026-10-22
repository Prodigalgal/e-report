# 2026-10-20 构建依赖审查

## 已升级

- WEB / APP：`source-map-js 1.2.1 -> 1.2.2`，对应 [GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q)。
- APP：`shell-quote 1.9.0 -> 1.12.0`，对应 [GHSA-pqg4-j6r4-53mv](https://github.com/advisories/GHSA-pqg4-j6r4-53mv)。
- 不使用 `npm audit fix --force` 降级 Expo，也不改变 CI 的 High/Critical 拦截阈值。

## 后端运行依赖

完整 OSV 扫描发现 `spring-webmvc 7.0.8` 的两项告警：
[GHSA-j9f9-w8pj-32f8](https://github.com/advisories/GHSA-j9f9-w8pj-32f8)、
[GHSA-pc63-qcmh-9cmg](https://github.com/advisories/GHSA-pc63-qcmh-9cmg)。
通过现有 Spring Boot BOM 属性将 Spring Framework 统一提升到修复版本 `7.0.9`，同步锁文件和校验元数据，并重新运行后端回归；不对运行时告警增加例外。

Expo Doctor 同时发现 SDK 57 的 7 个补丁版本差异，已按官方兼容清单修正，未更换 SDK 主版本。

SDK 57 的 Android 预编译 AAR 随 npm 包携带。补丁升级后必须同步 `android/app/gradle.lockfile` 和校验元数据，否则 Gradle 会按旧锁去远端查找已不存在的版本。新增 `npm run native:contract`，使用 Expo 自身的 autolinking 结果核对 release runtime 及其他变体的锁；GitHub 审计和 APK 构建在编译前执行该检查。

## 无可用补丁的两项

2026-10-20 查询 npm 发布版本及 GitHub Advisory Database，两项均未发布修复。按现有 npm 审查例外机制，将范围限定为准确版本、直接父依赖和 2026-11-03 到期日；新增告警、依赖关系变化或超期仍阻断发布。

| 告警 | 实际使用路径 | 本项目的暴露边界 |
| --- | --- | --- |
| [braces GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm)，3.0.3 | micromatch -> Metro / Expo 构建文件匹配 | 漏洞输入是深层嵌套的模式。模式来自受控仓库和 Metro 配置；业务附件、API 参数不进入构建匹配器。配置只观察 APP 和 shared 源目录。CI 仅对 main 不可变提交进行构建，并设有任务超时。 |
| [node-forge GHSA-86w9-cpqp-85rv](https://github.com/advisories/GHSA-86w9-cpqp-85rv)，1.4.0 | @expo/cli -> utils/codesigning.js、run/ios/codeSigning/Security.js；@expo/code-signing-certificates | 漏洞影响特定 RSA 签名验证。相关路径用于开发 manifest 证书及 iOS 工具；本项目发布 Android，使用 Gradle/apksigner。app.json 没有 EAS projectId 或 expo-updates 证书签名配置，APP / shared 源码没有引入这些 Node 包。 |

## 风险和退出条件

- GitHub 审计发现上游容器底包的 8 项新增告警。Nginx / Temurin 更新到官方仓库当前不可变 digest；发布门禁使用正式 runtime Dockerfile 构建同样的系统运行层，再用 Trivy 直接拒绝可修复的 High/Critical 告警，不对发布运行层使用基础镜像例外。审计占位文件不含业务代码，应用依赖继续由锁文件、OSV、npm 和后端测试验证。
- runtime Dockerfile 强制最低版本：`libexpat 2.9.0-r0`；WEB 同时要求 `libuuid 2.42.3-r1`、`libx11 1.8.14-r0`、`pcre2 10.49-r0`、`tiff 4.7.2-r0`。定期 Inventory 仍按独立基础镜像清单审计。

- 例外是针对当前使用路径的风险接受，不代表依赖已无漏洞。开发服务器若暴露到不可信网络，或后续启用 EAS / expo-updates 签名、iOS 发布或用户可配 glob，必须重新审查。
- 不把 Node 构建工具部署为业务后端；不将业务上传文件纳入 APP 源目录或触发构建。
- 上游发布补丁后更新锁文件并删除对应例外；到期前未修复则门禁重新阻断，不自动延期。
- 移除已到期且本次 audit 未再报告的 image-size 例外；原图片资源格式校验仍保留。

## 2026-10-21 OTA 接入复核

- 新增 SDK 57 `expo-updates` 后，`node-forge` 的同一 GHSA 新增一条包传播路径；未引入新的根漏洞或扩大允许父依赖、版本及到期范围。
- OTA 签名使用 Node `crypto.sign`，Backend 与 Android 客户端使用 Java/JCA `Signature`；不使用 `node-forge` 校验外部签名或证书。Expo CLI 仅处理仓库内受控配置与证书。
- `braces` 与 `node-forge` 继续保留原有限时策略；没有将安全扫描失败改成放行。
- OTA 的 Android 运行依赖引入了 `bcprov-jdk15to18` / `bcutil-jdk15to18 1.81`。根据 [Bouncy Castle 官方发布](https://www.bouncycastle.org/download/bouncy-castle-java/) 将两者在全部 Android 项目配置中统一约束到 `1.86`，同步锁和校验文件，移除不再使用的 `1.81` 校验项；发布运行依赖扫描无未处理发现。
- Lite 的构建依赖逐个对比了现有包、版本和告警集合，均已在原 Android 工具链范围内。OTA 新增 Kotlin Gradle Plugin `2.2.0` 及测试/开发配置中的 Guava `31.1-jre`、Okio `2.8.0`、JUnit `4.12` 校验项；这些版本未进入 APK 的 release runtime。Kotlin 增量编译继续关闭，CI 不处理外部不可信源码。更新准确来源与告警指纹到 450 条工具链发现，原 `2026-10-25` 复核期限不延长，运行依赖仍禁止未处理告警。
