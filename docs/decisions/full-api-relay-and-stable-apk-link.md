# 双域名直连与稳定 APK 下载链接

> 状态：已于 2026-08-13 被 `canonical-public-origin.md` 取代。以下内容仅保留历史决策背景，不再代表现行公网契约。

## 决策

- 主入口为 `https://e-report.omnnu.xyz:60443/e-report-backend/api`。
- 备用入口为 `https://e-report-relay.omnnu.xyz:60443/e-report-backend/api`，它进入同一 Envoy Gateway，不再部署 relay 服务。
- 两个域名由同一 `HTTPRoute` 承接，公开、认证、查询、写入、上传和下载端点使用同一契约和同一数据源。
- App 可保留两个 API base URL 以兼容已发布版本，但两者不再构成独立故障域。
- 固定下载入口为 `https://e-report.omnnu.xyz:60443/e-report/download/android`，由 VPS 动态解析最新 stable 版本并重定向到 R2，浏览器原生下载器直接保存 APK。
- IP 直连入口为 `http://36.212.129.231:60080/e-report/`，同样覆盖 Web、API 和固定下载路径。
- `/app-updates/latest/apk` 始终解析 Android `stable` 通道最新已发布版本；R2 正常时返回不可变对象重定向，R2 未配置时可回退 VPS 原始文件的 HEAD 与 Range 能力。

## 验收

- 两个域名均通过公网 `60443` 进入新服务器的 Envoy Gateway；服务器不开放或监听 `80/443`，K8S 中不存在 e-report relay 工作负载。
- 两个域名的 Web、认证 API、文件上传和下载行为一致。
- 主入口故障时，兼容入口不作为独立基础设施兜底；容灾应由后续多节点或第二站点承担。
