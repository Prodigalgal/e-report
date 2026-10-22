# Android 安装包由 R2 主分发

## 决策

- Cloudflare R2 存储不可变 APK 对象，并在统一下载入口之后承担安装包主体流量。
- VPS 仅保存发布记录、原始 APK、版本检查 API 和灾备下载接口，避免约 10MB 出口带宽成为批量升级瓶颈。
- 固定入口 `/download/android` 不直接输出 APK，而是由后端解析当前 `stable` 最高版本并重定向到 348 账号的 R2 不可变对象。
- R2 不保存重复的 `latest.apk`。每个版本只保存 `android/{channel}/{versionCode}/app-release.apk`，固定入口通过数据库动态解析最新对象。
- `APP_UPDATE_STABLE_DOWNLOAD_URL=https://e-report.fantong.eu.org:60443/download/android` 是版本检查、Web 和发布结果统一使用的对外下载 URL。
- `APP_UPDATE_R2_PUBLIC_BASE_URL` 只用于后端生成固定入口的 R2 重定向目标，不作为客户端配置或版本检查返回值；`apkFallbackUrl` 继续指向 VPS `/app-updates/{id}/apk`。
- Cloudflare token 只保存在 `D:\WorkSpace\Project\服务器管理\private\cloudflare\tokens.env` 和生产 K8S Secret `e-report-r2`，仓库只引用 Secret key。

## 管理边界

- 管理后台新增“安装包发布”页面，需要管理员角色和固定权限 `app-update:manage`。
- 后台可查看数据库发布记录与 R2 对象的同步、缺失、大小不一致和未登记状态。
- 后台可从 VPS 源文件补传或重新上传到 R2，也可删除非当前版本及未登记对象。
- 当前通道最高已发布版本禁止从 R2 删除，防止在线升级链接立即失效。
- R2 外部 HTTP 调用不进入数据库事务。

## 发布顺序

1. APK 通过现有分片接口上传到 VPS 并完成校验。
2. 发布脚本从“服务器管理”读取 Cloudflare R2 配置，将本地 APK 直接上传到不可变对象路径。
3. R2 成功后才登记发布元数据；失败时不发布新版本，并清理本次新建的 R2 对象。
4. 管理后台用于运行态核对、缺失补传和历史对象清理。

## 回滚

- 清空 `APP_UPDATE_R2_PUBLIC_BASE_URL` 后，既有固定入口会退回 VPS 原始文件下载，无需修改 App 或 Web 中的下载 URL。
- 回滚应用镜像时保留 `/data/e-report/app-releases` 和数据库发布记录，R2 不可变对象无需移动。
