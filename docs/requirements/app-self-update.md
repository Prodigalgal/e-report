# App 自建升级能力

## 目标

- 后端自建 Android APK 版本发布、查询和下载能力，不依赖第三方热更新或应用商店。
- Android App 启动后检查最新版本，发现新版本时下载 APK、校验文件完整性，并拉起系统安装器。
- K8S 单节点仅保存发布归档和版本元数据；R2 是唯一 APK 下载数据面，不允许回退到 VPS 或 relay 传输 APK 字节。

## 范围

- 后端提供 `app_release` 版本元数据、发布管理接口、公开版本检查接口和到 R2 的稳定重定向。
- 安装包权限拆分为 `app-update:view` / `publish` / `delete` / `storage`，由窄职责角色组合授权。
- 新增 Android App 更新检查、下载进度、SHA-256 校验、安装器跳转和用户可理解的错误提示。
- 更新本地 K8S manifest，为 APK 发布目录增加 hostPath 持久化卷。

## 非目标

- 不接入应用商店、EAS Update、CodePush 或第三方热更新。
- 不实现 iOS 安装包分发。
- 不在 Web 页面内直接构建 APK；生产构建仍由本地 Android 工具链完成。

## API 契约

- `GET /app-updates/latest`：公开接口，参数为 `platform`、`channel`、`currentVersionCode`、`runtimeVersion`，返回是否需要升级、APK URL、大小和 SHA-256；Android stable 的 `apkUrl` 固定为 `https://e-report.fantong.eu.org:60443/download/android`。
- `GET|HEAD /app-updates/latest/apk`：公开稳定别名，只返回到当前不可变 R2 对象的 `302`、版本号和 SHA-256；不读取本地 APK。
- 不提供公开的 `/app-updates/{id}/apk` 源站下载接口；R2 未配置时统一入口返回 `503`，不降级为 VPS 文件流。
- `GET /admin/app-updates`：需要 `app-update:view`，分页查看发布记录。
- `POST /file-transfers/uploads`、`PUT /file-transfers/uploads/{uploadId}/chunks/{chunkIndex}`、`POST /file-transfers/uploads/{uploadId}/complete`：需要登录，用于分片上传 APK 并得到 `completedUploadId`。
- `POST /admin/app-updates`：需要 `app-update:publish`，提交 JSON 元数据和 `completedUploadId` 登记版本。
- `DELETE /admin/app-updates/{id}`：需要 `app-update:delete`，撤销发布并删除安装包文件。
- `GET /admin/app-updates/r2`：查看发布记录与 R2 对象同步状态。
- `POST /admin/app-updates/r2/releases/{id}`：需要 `app-update:storage`，从发布归档补传指定版本到 R2；该能力只用于运维修复，不是用户下载回源。
- `DELETE /admin/app-updates/r2/objects?key=...`：删除非当前版本或未登记的 R2 对象。

## 验收标准

- 后端测试和构建通过，启动后能自动同步新权限。
- Android typecheck 和 release APK 构建通过。
- App 能在启动后检查版本；当后端发布更高 `versionCode` 时，显示升级提示，下载完成后校验 SHA-256 并打开系统安装器。
- APP 对同一 R2 地址以 2 MiB 分片、4 路并发、单片最多 5 次退避重试，并精确校验 `Content-Range`、分片长度、整包长度和 SHA-256。
- R2 不可用时升级失败并保留用户重试能力；不请求 VPS/relay APK 源文件。
- K8S manifest 将 `/app/app_releases` 持久化到主机 `/data/e-report/app-releases`。

## 测试方式

- `./gradlew test`
- `npm run typecheck`
- `npm run build:android:local`
- 通过 `curl` 调用 `/app-updates/latest` 和管理发布接口做 smoke 验证。

## 生产发布步骤

固定浏览器下载入口：

- 主线路：`https://e-report.fantong.eu.org:60443/download/android`
- 默认业务 API：`https://e-report.fantong.eu.org:60443/api`；备用 API 不再内置，可由部署环境显式配置。

用户只使用固定下载入口；发布新版本后无需更换。relay 只用于动态业务 API 故障转移，不是第二个 APK 下载链接。

R2 是唯一实际 APK 分发层。R2 对象域名只出现在固定入口的 HTTP 重定向中，不写入 App 固定配置、不展示在 Web 页面，也不作为版本检查的 `apkUrl` 返回。`apkFallbackUrl` 已从 API 契约删除。

1. 构建 Android APK：

   ```powershell
   cd D:\WorkSpace\Project\nanjing\e-report\e-report-app
   npm run build:android:local
   ```

2. 使用管理员登录接口获取 token 后发布 APK。脚本会自动执行 VPS 分片上传、R2 上传、自动重试和版本登记：

   ```powershell
   npm run publish:android:release -- `
     -Token "<admin bearer token>" `
     -ApkPath "D:\WorkSpace\Project\nanjing\e-report\output\网络运维管理-android-v1.0.2-code3.apk" `
     -VersionCode 3 `
     -VersionName "1.0.2" `
     -RuntimeVersion "android-1.0.2-native3" `
     -ReleaseNotes "升级文件传输链路：工单附件、附件ZIP、APK发布与端内升级均支持分片传输和自动重试。"
   ```

3. 检查公开升级接口：

   ```powershell
   curl.exe "https://e-report.fantong.eu.org:60443/api/app-updates/latest?platform=ANDROID&channel=stable&currentVersionCode=2&runtimeVersion=android-1.0.1-native2"
   ```

4. 上线 K8S 前确认主机目录存在并可写：

   ```bash
   mkdir -p /data/e-report/app-releases
   ```

## 回滚方式

- 如果新包有问题，调用 `DELETE /admin/app-updates/{id}` 撤销该发布记录并删除 APK 文件。
- App 端只会提示 `versionCode` 高于当前版本的最新已发布包；删除问题版本后，用户不会继续收到该版本升级提示。
