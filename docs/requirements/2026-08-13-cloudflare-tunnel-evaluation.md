# e-report Cloudflare Tunnel 候选验证

## 目标

- 验证 `cloudflared` 从国内专用 VPS 主动连接 Cloudflare 后，用户是否可以通过标准 HTTPS 443 访问 e-report。
- 对比现有 DNS-only `:60443` 入口与 Tunnel 候选在延迟、稳定性、API、静态资源和下载跳转方面的差异。
- 验证 Tunnel 路径不暴露生产 VPS 地址，且不改变 R2 作为 APK 实际分发层的既有架构。

## 范围

- Cloudflare 310 账号创建独立 Named Tunnel 和代理测试 DNS：`e-report-tunnel-test.fantong.eu.org`。
- 专用生产 K8S 创建独立测试 Namespace、Secret 和单副本 `cloudflared` Deployment。
- Tunnel 仅回源现有 `e-report-frontend.e-report.svc.cluster.local:80`，不修改生产 Deployment、Service、HTTPRoute 或 Gateway。
- 执行 DNS、TLS、HTTP、API、静态资源、APK 跳转、并发、连续请求和延迟对比测试。

## 非目标

- 不切换或修改 `e-report.fantong.eu.org`。
- 不登录生产账号，不创建、更新或删除业务数据，不触发邮件或通知。
- 不让 APK 文件流量经过 Tunnel；`/download/android` 仍只负责跳转到 348 账号 R2。
- 本轮不宣称完成真实电信、移动、联通三网验证；没有三张独立运营商网络时，只记录当前网络与可获得的外部探测证据。

## 验收标准

- 测试域名通过标准 443 返回 Web 页面，源站公网 IP 不出现在测试 DNS 记录中。
- `/api/auth/me` 保持未登录 `401`，`/download/android` 保持 `302` 到现有 R2 对象。
- 连续请求和并发请求无 5xx，Tunnel Connector 保持连接，生产 Pod 状态与重启次数不变化。
- 给出直连 `:60443` 与 Tunnel 的 P50/P95/最大耗时及结论，明确是否值得切换生产。

## 回滚

- 删除 `e-report-tunnel-test.fantong.eu.org` DNS 记录。
- 删除 Cloudflare Named Tunnel `e-report-tunnel-test`。
- 删除 K8S Namespace `e-report-tunnel-test`，不影响 `e-report` Namespace。
