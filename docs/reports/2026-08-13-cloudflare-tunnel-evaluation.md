# e-report Cloudflare Tunnel 候选评估报告

## 结论

本轮不将生产入口从 `https://e-report.fantong.eu.org:60443/` 切换到 Cloudflare Tunnel。

Tunnel 可以通过标准 HTTPS 443 提供服务并隐藏 `36.212.129.231`，但国内专用 VPS 和当前中国移动客户端都被 Cloudflare 免费网络路由到洛杉矶边缘。国内多节点和真实移动出口均显示明显的跨境延迟：可达率略好，但日常交互速度相对现有直连显著退化。

保留独立测试入口 `https://e-report-tunnel-test.fantong.eu.org/`，用于后续真机在不同运营商网络复测；生产 DNS、Gateway、Deployment、数据库、R2 和通知链路均未修改。

## 候选拓扑

```text
用户
  -> Cloudflare Anycast 443
  -> Named Tunnel e-report-tunnel-test
  -> cloudflared (e-report-tunnel-test Namespace)
  -> e-report-frontend.e-report.svc.cluster.local:80
  -> Backend / R2 跳转
```

- Tunnel ID：`662ef98b-b3d9-49cc-a091-13ad7c452510`。
- 测试 DNS：代理 CNAME 指向 `662ef98b-b3d9-49cc-a091-13ad7c452510.cfargotunnel.com`。
- 公网解析只返回 Cloudflare Anycast IPv4/IPv6，不返回生产 VPS 地址。
- K8S：独立 Namespace `e-report-tunnel-test`，单副本 `cloudflared:2026.6.0`，非 root、只读根文件系统、禁用 ServiceAccount token。
- Cloudflare 创建操作使用临时最小权限管理 token；创建完成后已撤销。Tunnel token 仅存放于 K8S Secret，没有写入仓库或测试输出。

## 功能验证

| 检查项 | 结果 |
| --- | --- |
| `/` | `200` |
| `/admin/users` SPA 深层路由 | `200` |
| 未登录 `/api/auth/me` | `401`，符合预期 |
| 旧 `/e-report/` | `410`，符合破坏性迁移契约 |
| `/download/android` | `302` 到 348 账号 R2 |
| APK 版本/哈希响应头 | code 47，SHA-256 `6b746085cf7abf9db97fd4ec9cb219bc299ebfc2db313f7b4bbd099fe14e13b0` |
| 浏览器页面 | 自动进入 `/login`，页面和静态资源正常 |
| 浏览器请求 | 10 个请求均有响应，无 request failure、无 page error |
| Tunnel 回源错误 | 指标 `cloudflared_tunnel_request_errors=0` |
| 生产 Pod | Backend/Frontend 均 Ready，重启数 0 |

APK 文件本体不经过 Tunnel，固定入口仍只负责把客户端重定向到 R2，因此不会让 VPS 的 10 MB 出口承担 APK 下载流量。

## 性能结果

### 国内 261 节点

使用 ITDOG 同一批电信、联通、移动和其他节点分别测试现有直连与 Tunnel：

| 链路 | 成功/失败 | 全部平均 | 电信平均 | 联通平均 | 移动平均 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 现有 DNS-only `:60443` | 254 / 7 | `0.821s` | `0.912s` | `0.875s` | `0.682s` |
| Tunnel 标准 443 | 260 / 1 | `3.295s` | `4.022s` | `2.565s` | `3.532s` |

Tunnel 的节点可达率更高，但平均耗时分别是直连的约 4.0 倍、4.4 倍、2.9 倍和 5.2 倍，不适合作为当前生产交互入口。

### 本机真实中国移动出口

关闭 V2rayN TUN 后，本机出口为 `AS9808 China Mobile`。Cloudflare trace 显示入口落点为 `LAX`。交替采样 20 次：

| 链路 | 成功率 | P50 | P95 | 最大值 |
| --- | ---: | ---: | ---: | ---: |
| 现有 DNS-only `:60443` | 20/20 | `0.103s` | `0.127s` | `0.128s` |
| Tunnel 标准 443 | 19/20 | `1.610s` | `3.763s` | `3.763s` |

Tunnel IPv4 10/10 成功，P50 `1.975s`、P95 `3.482s`；IPv6 10/10 成功，P50 `1.680s`、P95 `5.545s`。IPv6 没有解决跨境延迟。

### Connector 协议对照

- 默认 QUIC 的 4 条 Connector 均连接 `LAX01/LAX08/LAX09/LAX11`。
- 强制 HTTP/2 后仍连接 `LAX01/LAX08/LAX10`，15 次请求成功 14 次，P50 `1.968s`、P95 `4.694s`。
- 已恢复默认 QUIC；协议切换没有改变跨境路由，瓶颈不是 QUIC。

## 切换前置条件

即使后续网络结果改善，也不能直接替换主 DNS：

1. 当前后端 CORS 只允许 `https://e-report.fantong.eu.org:60443`。测试 hostname 的登录预检返回 `403`；无端口正式 Origin 必须先加入生产配置并完成登录验证。
2. Android 1.0.47 仍使用带 `:60443` 的 API 和下载地址。应先发布指向标准 443 的新版本，并在过渡期保留旧入口，避免存量客户端失联。
3. 主 hostname 改成 Cloudflare 代理 Tunnel 后，现有非常规端口 `:60443` 不能视为仍然可用；切换必须按破坏性 DNS 变更处理。
4. 正式使用至少需要两个 `cloudflared` 副本、断连监控和 24 至 72 小时三网真机观察。

## 建议

- 当前继续使用 `https://e-report.fantong.eu.org:60443/`。
- 若“去端口”优先级高于“隐藏源站”，优先评估在 VPS/安全组/主机转发层开放标准 443；这条路径更接近现有直连性能。
- 若“隐藏源站”是硬要求，则需要接受免费 Cloudflare 跨境延迟，或另行评估具有中国大陆节点和合规接入条件的 CDN/边缘网络。
- 测试入口只用于只读和登录链路复测，不作为用户发布地址。

## 测试资源与回滚

保留资源：

- Cloudflare Tunnel：`e-report-tunnel-test`。
- DNS：`e-report-tunnel-test.fantong.eu.org`。
- K8S Namespace：`e-report-tunnel-test`。

回滚顺序：先删除测试 DNS，再删除 K8S Namespace，最后删除 Named Tunnel。上述资源与生产 `e-report` Namespace 和正式 DNS 相互独立。
