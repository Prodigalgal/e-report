# ops 宿主机服务公网接入

## 目标

通过现有公网 HTTPS 入口 `https://ops.fantong.eu.org:60443` 访问宿主机 Nginx/Vue 服务，并保留 Nginx 到本机后端的 `/api` 代理。

## 范围

- Cloudflare 新增 DNS-only 精确 A 记录：`ops.fantong.eu.org -> 36.212.129.231`。
- cert-manager 使用既有 `letsencrypt-prod-fantong` 签发 `*.fantong.eu.org` 通配符证书；DNS 记录仍保持精确，不创建或依赖通配符 DNS。
- Envoy Gateway 新增精确 HTTPS Listener `https-fantong-ops`。
- `liangruishan` namespace 新增无 selector Service、EndpointSlice 和 HTTPRoute，将流量转发至宿主机 `192.168.118.3:9002`。

## 非目标

- 不修改全局 `*.fantong.eu.org` DNS；该记录继续指向既有主机。
- 不修改 HAProxy `60080/60443` 入口。
- 不修改宿主机 Nginx、前端静态文件、9001 后端或数据库。
- 不开放新的公网端口。

## 影响文件

- `deploy/dedicated-cloud/envoy-platform-tls.yaml`
- `deploy/dedicated-cloud/cert-manager-fantong-resources.yaml.tpl`
- `deploy/dedicated-cloud/enable-fantong-tls.sh`
- `deploy/dedicated-cloud/ops-host-service.yaml`

## 验收标准

- `Certificate/fantong-wildcard` 为 `Ready=True`。
- `Gateway/platform-gateway` 为 `Programmed=True`。
- `HTTPRoute/liangruishan/ops-web` 为 `Accepted=True`、`ResolvedRefs=True`。
- 集群内通过 Service 访问前端返回 HTTP 200。
- 公网 `https://ops.fantong.eu.org:60443/` 返回 Vue 首页，证书主机名匹配。
- 原 `e-report.fantong.eu.org:60443` 继续返回正常响应。

## 回滚

删除 `HTTPRoute/ops-web`、`EndpointSlice/ops-web-host`、`Service/ops-web`、Gateway Listener `https-fantong-ops` 和对应精确 DNS 记录。证书切换异常时，从部署快照恢复 Gateway 的旧精确证书引用；不删除既有精确证书回滚资源。
