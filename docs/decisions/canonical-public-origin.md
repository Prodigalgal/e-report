# 统一公网域名与根路径契约

- 日期：2026-08-13
- 状态：已接受，取代旧 `omnnu.xyz` 与 `/e-report` 前缀契约

## 决策

- 唯一公网 origin 为 `https://e-report.fantong.eu.org:60443`，DNS 由 Cloudflare 310 账号管理。
- WEB/PWA 使用根路径 `/`，API 使用 `/api`，Android 固定下载入口使用 `/download/android`。
- 旧 `e-report.omnnu.xyz` 与 `e-report-relay.omnnu.xyz` 直接下架，不跳转、不保留运行时兼容路由。
- Android 默认不内置备用 API origin；故障转移抽象保留，可通过环境变量配置未来的独立备用域名。
- `http://36.212.129.231:60080` 仅作为运维直连入口，不对最终用户公开。
- APK 对象仍由 Cloudflare 348 账号的 R2 保存和分发，不随公网入口域名迁移。

## 破坏性边界

- `1.0.47` 是新旧连接契约分界线；旧版 APP 在旧 DNS 删除后不能联网或完成应用内更新，需要从新 WEB 入口手动安装。
- 不提供 `/e-report`、`/e-report-backend/api` 或旧 hostname 的 rewrite、redirect、双写或兼容代码；新域名收到旧路径时直接返回 `410 Gone`，防止 SPA catch-all 伪装成功。
- 数据库表、业务数据、R2 Bucket、对象 key、K8S 资源名和主机数据目录不在破坏性范围内。

## 流量路径

1. `e-report.fantong.eu.org:60443` 以 DNS-only A 记录指向专用 VPS。
2. Envoy Gateway 使用 `e-report.fantong.eu.org` 精确证书终止 TLS，并把流量交给前端 Nginx。
3. Nginx 直接提供根路径 SPA/PWA，把 `/api` 代理至 Spring Boot，把 `/download/android` 代理至最新 stable APK 解析接口。
4. 下载接口返回到 348 账号 R2 不可变对象的 `302`，APK 字节流不经过 VPS。

## 回滚

- 回滚应用时恢复上一版前后端镜像、旧 HTTPRoute、旧 Cloudflare DNS 记录和原 `APP_PUBLIC_BASE_URL`。
- 共享的 `*.omnnu.xyz` 证书/listener 不删除；它可能仍被集群其他系统使用。
- 348 账号 R2 与发布记录在正向和回滚过程中均不迁移、不删除。
