# APK R2-only 分发生产发布记录

> 状态：已完成。本次无数据库结构或数据变更，不执行 Liquibase。

## 发布内容

- 删除版本检查响应中的 `apkFallbackUrl`。
- 删除公开 `/app-updates/{id}/apk` 源站下载路由和对应的本地文件流服务。
- `/app-updates/latest/apk` 只返回 R2 `302`；R2 公开基址未配置时返回 `503`。
- APP 只向 `apkUrl` 重试分片，不构造或请求 VPS/relay fallback；`Content-Range` 改为精确匹配起止、总长度。
- 新增 `scripts/verify-android-release-download.mjs`，校验固定入口必须跳转到不同主机，后续请求禁止任何重定向。

## 生产版本

| 项目 | 值 |
| --- | --- |
| Backend 镜像 | `localhost/e-report-backend:20260809-r2-direct-1` |
| Frontend 镜像 | `localhost/e-report-frontend:20260809-rbac-observability-1`（未变） |
| Backend JAR SHA-256 | `698df8e44ca4c09e3ba5ed561e5106ae1ae9e23586459e002b718987c29de796` |
| 回滚目录 | `/opt/e-report/releases/20260809-r2-direct-1` |
| Android stable ID | `019fe5ae-9414-7849-819a-fe0566cd9007` |
| Android 版本 | `1.0.36` / code37 / `android-1.0.36-native37` |
| APK 大小 | `76,007,975` 字节 |
| APK SHA-256 | `092b486e420aa305bbd37cc869d31152b282ebdcbddf2d8afcbbc7a1fcc38461` |
| R2 对象 | `android/stable/37/app-release.apk` |
| 升级策略 | `minSupportedVersionCode=0`、非强制升级 |

## 验证证据

- Backend 完整 `test + bootJar` 成功；下载控制器和 URL Builder 覆盖 R2 重定向与未配置 `503` 失败路径。
- APP 10 项测试、TypeScript 检查、Expo Doctor `19/19` 通过；正式发布脚本 `BUILD SUCCESSFUL`。
- APK 使用与 code36 一致的生产证书指纹 `c6306184...71cb2d27`，v2 签名校验通过。
- `emulator-5554` 先从已安装 code36 保留数据覆盖本地 code37 候选包，证明生产签名升级兼容；正式发布后又回到 code36，由 APP 完成版本检查、R2 分片下载、整包校验和系统安装器更新，最终为 code37 并冷启动进入登录页。
- 生产版本 API 不再包含 `apkFallbackUrl`；旧按 ID 源站地址匿名请求返回 `401`，不再返回 APK 字节。
- 固定入口返回 code37 和对应 SHA 后 `302` 到 R2。直连 R2 完成 37 个 2 MiB 分片、4 路并发下载，所有分片重试数为 0，6.7 秒合并完成，大小和 SHA-256 一致。
- Backend、Frontend、MySQL、Redis 均 Ready，容器重启数 0；新 Backend 发布后无 `ERROR` / `FATAL` / OOM / Redis 连接错误。

## 回滚

1. 使用 `/opt/e-report/releases/20260809-r2-direct-1/deployment-before.yaml` 或将 Backend 镜像恢复为 `20260809-rbac-observability-1`。
2. 本次没有数据库变更，不恢复数据库。
3. Android 不降低 `versionCode`；需要修复时使用相同生产密钥发布 code38 或更高版本。
