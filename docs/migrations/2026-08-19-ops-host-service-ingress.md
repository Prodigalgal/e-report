# ops 宿主机服务公网接入记录

## 变更结果

- 公网入口：`https://ops.fantong.eu.org:60443/`。
- Cloudflare 新增 DNS-only 精确 A 记录 `ops.fantong.eu.org -> 36.212.129.231`，TTL 120 秒；记录 ID 为 `19111168129a563539d7149ed3385c34`。
- 未修改既有 `*.fantong.eu.org -> 217.142.229.2` 通配符记录。
- 初次接入使用独立精确证书完成链路验收；用户随后澄清 DNS 应保持精确、Envoy TLS 应使用通配符证书。
- cert-manager 最终新增共享 `Certificate/gateway-system/fantong-wildcard`，Secret 为 `fantong-wildcard-tls`，SAN 为 `*.fantong.eu.org`。
- Envoy Gateway 的 `https-fantong`、`https-fantong-test`、`https-fantong-ops` Listener 均保持精确 hostname，仅共享通配符 TLS Secret。
- `liangruishan` namespace 新增 `Service/ops-web`、`EndpointSlice/ops-web-host` 和 `HTTPRoute/ops-web`，目标为宿主机 `192.168.118.3:9002`。

## 生产证据

- 部署前快照：`/opt/e-report/deploy-snapshots/20260819-175351-ops-ingress/`。
- 通配符证书切换前快照：`/opt/e-report/deploy-snapshots/20260819-181301-fantong-wildcard-tls/`。
- `Certificate/fantong-wildcard`：`Ready=True`，SAN 为 `*.fantong.eu.org`，到期时间 `2026-11-17T09:16:00Z`；精确证书暂时保留但不再被 Listener 引用，作为快速回滚点。
- `Gateway/platform-gateway`：`Programmed=True`；新增 Listener 为 `Accepted=True/ResolvedRefs=True`。
- `HTTPRoute/liangruishan/ops-web`：`Accepted=True/ResolvedRefs=True`。
- DNS 切换前通过 VPS 本机 `--resolve` 验证完整 `60443` 链路，首页返回 HTTP 200、`text/html`，内容为 Vue 构建页面。
- DNS 缓存过期后，1.1.1.1 与 8.8.8.8 均解析到 `36.212.129.231`；公网不带强制解析访问首页返回 HTTP 200，主入口 JavaScript 资源返回 HTTP 200、`application/javascript`。
- 切换通配符证书后，`ops`、`e-report`、`e-report-test` 三个精确域名均通过公开 TLS 校验并返回 HTTP 200。
- `https://ops.fantong.eu.org:60443/api/` 返回后端 HTTP 404，说明 Nginx `/api` 代理可达但后端未定义 API 根路由；具体业务 API 需按应用实际路径验收。
- 原 `https://e-report.fantong.eu.org:60443/` 回归返回 HTTP 200。

## DNS 传播

创建精确记录前，`ops.fantong.eu.org` 命中既有通配符并解析到 `217.142.229.2`，缓存 TTL 为 300 秒。精确记录创建后，在该缓存窗口内部分递归解析器仍会返回旧地址；缓存自然过期后应解析到 `36.212.129.231`。

Cloudflare 元数据显示既有 `*.fantong.eu.org -> 217.142.229.2` DNS-only A 记录创建于 `2022-11-26T02:53:06Z`、最后修改于 `2025-07-07T14:04:29Z`。本次没有创建、修改或删除该通配符 DNS 记录；通配符仅用于 TLS 证书 SAN。

## 回滚

1. 删除 Cloudflare 精确 A 记录 `19111168129a563539d7149ed3385c34`。
2. 删除 `liangruishan` namespace 的 `HTTPRoute/ops-web`、`EndpointSlice/ops-web-host`、`Service/ops-web`。
3. 从部署前快照恢复 `Gateway/platform-gateway`，移除 `https-fantong-ops` Listener或恢复原精确证书引用。
4. 确认没有 Listener 引用后再删除 `Certificate/gateway-system/fantong-wildcard` 和 `Secret/fantong-wildcard-tls`；精确证书在回滚窗口结束前保留。
5. 验证 e-report 原入口仍为 HTTP 200。
