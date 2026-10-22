# e-report 独立云主机迁移

## 目标

将 e-report 从现有共享 Kubernetes 集群迁移到独立单节点云主机，部署 kubeadm Kubernetes、Cilium、cert-manager 和 Envoy Gateway，并在完整验收后下线旧集群中的 e-report 资源。

## 范围

- 初始化单节点 Kubernetes 1.35.x 和 containerd。
- 安装 Cilium 1.20、cert-manager 1.21.1、Envoy Gateway 1.8.2。
- 通过主机端口转发将公网允许的 `60080/60443` 引导到 Envoy NodePort。
- 迁移 MySQL、附件、App 发布文件、Kubernetes Secret 和业务工作负载。
- 完成双端数据校验、API/Web/App smoke、证书和公网链路验收后再清理旧集群。

## 非目标

- 不将 SSH 密码、Cloudflare token、数据库密码、证书私钥或其他凭据写入仓库。
- ACME 联系邮箱由部署时的 `ACME_EMAIL` 注入，仓库仅保留无敏感值模板。
- 不在新环境验收前修改生产 DNS 或删除旧集群资源。
- 不改变工单模型、权限、API 契约和 App 更新协议。
- `e-report-relay.omnnu.xyz` 作为同一 Envoy Gateway 的域名别名，不再部署 e-report relay 工作负载。

## 影响文件

- `deploy/dedicated-cloud/bootstrap-kubernetes.sh`
- `deploy/dedicated-cloud/install-platform.sh`
- `deploy/dedicated-cloud/envoy-platform.yaml`
- `deploy/dedicated-cloud/configure-edge-ports.sh`
- 后续迁移阶段新增的数据备份、恢复和 e-report workload 清单。

## 验收标准

- 单节点 Ready，CoreDNS、Cilium、cert-manager、Envoy Gateway 全部可用且零异常重启。
- 主机 `60080/60443` 能分别转发至 Envoy HTTP/HTTPS NodePort。
- cert-manager 颁发目标域名证书，Gateway `Programmed=True`。
- 新环境业务表计数、管理员、附件和 App 发布文件与迁移快照一致。
- Web、主 API、文件上传/下载、App 更新和 relay/API smoke 全部通过。
- DNS 切换并观察稳定后，才从旧集群删除 e-report Namespace、HTTPRoute、持久化数据和相关 Secret。

## 回滚

生产已完成切换，旧 Kubernetes Namespace 和旧 relay 运行态均已下线。若新环境发生严重故障，先停止新环境写入，再使用旧主机保留的带时间戳 hostPath 快照及迁移前数据库备份重建旧运行态；禁止同时开放新旧环境写入。

## 实施结果

- 新主机实际系统为 Ubuntu 24.04.4 LTS，并非申请信息中的 Ubuntu 22.04。
- 已部署 Kubernetes 1.35.6、containerd 2.2.1、Cilium 1.20.0、cert-manager 1.21.1 和 Envoy Gateway 1.8.2。
- wildcard 证书签发成功，Gateway、HTTP/HTTPS Route 和主机高端口转发在节点内验证通过。
- 已迁移 16 张业务表、19 条 App 发布记录和 19 个 APK；逐表行数与 APK 聚合 SHA-256 对照一致。
- 主域名和兼容域名已切换为新服务器的精确 DNS A 记录，旧 relay 容器已停止且禁止自动重启。
- 旧 K8S 的 e-report namespace 已删除；旧 hostPath 数据保留为带时间戳的回滚快照，未直接销毁。
- 公网只允许 `60080/60443`；服务器不开放或监听 `80/443`。Web、认证 API、TLS、CORS 和 APK Range 下载均通过显式 `60443` 提供。
- MySQL 每日 `02:30` 执行单事务逻辑备份，写入 hostPath 备份目录并保留 14 天。
- Android `1.0.20 (versionCode 21)` 是高端口迁移阶段版本，CDN APK SHA-256 为 `8598a628f402aa57161927798639fea6ee1fda5d1ed0e1125aedaacb56a804a3`。
- 浏览器安装入口保持 `https://e-report.omnnu.xyz:60443/e-report/download/android`，由 VPS 动态解析最新版本并重定向到 `download.omnnu.xyz` 的 R2 不可变对象；VPS 原始 APK 接口继续支持 Range 下载作为灾备。
- App 使用带 `60443` 的主 API 和 relay 备用 API，保留端内自动检查、分片下载和重试。
