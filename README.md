# 网络运维管理系统

面向漳州市网络运维场景的工单闭环系统，包含 Web 管理端、安卓应用、Spring Boot 后端，以及独立 Kubernetes 生产部署清单。

## 目录

- `e-report-backen/`：Spring Boot 后端、Liquibase 迁移和自动化测试。
- `e-report/`：React + Vite Web 管理端。
- `e-report-app/`：Expo / React Native 安卓应用。
- `shared/`：Web、APP 与后端共用的工单目录契约。
- `deploy/dedicated-cloud/`：独立云主机 Kubernetes 生产部署资源。
- `docs/requirements/`：长期需求与详细设计。
- `docs/migrations/`：生产迁移、验证与回滚记录。

## 本地验证

后端使用 JDK 25：

```powershell
cd e-report-backen
.\gradlew.bat test bootJar
```

Web：

```powershell
cd e-report
npm install
npm test
npm run lint
npm run build
```

安卓应用：

```powershell
cd e-report-app
npm install
npm test
npm run typecheck
npm run doctor
npm run build:android:local
```

签名、地图密钥、服务器凭据和 GitHub 令牌只从仓库外的服务器管理目录读取，不进入版本库。生产发布必须先完成数据库备份、Liquibase 演练、候选版本验证和回滚点登记。

`e-report-app/android/` 包含项目自研的定位和地图导航原生模块，是需要版本控制的权威源码，不由每次 Expo Prebuild 覆盖生成。修改 `app.json` 中的版本、权限或插件配置时，必须同步检查 Android 工程并通过签名 release 构建验证。
