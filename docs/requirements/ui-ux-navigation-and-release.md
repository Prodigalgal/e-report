# UI/UX 导航与版本发布任务契约

## 目标

将 Web 与 Android 的入口从按工单类型堆叠改为按用户任务组织，统一报单、待处理、我的工单和管理后台动线，并发布 Android `versionCode 22` 候选与 Web 新镜像。

## 范围

- Web：工作台、公共头部、管理后台侧栏、统一工单列表和类型筛选。
- Android：工作台任务入口、后台统一工单列表、报单/处理/我的工单术语和路由。
- 发布：Web 不可变镜像、Android 候选 APK、stable 发布中心登记。

## 非目标

- 不修改后端工单 API、数据库结构和权限端点。
- 不创建或修改生产工单数据。
- 不新增第三方地图、通知或微信能力。

## 验收标准

- 有报单权限的类型才显示在统一报单选择页。
- 有回单或查看权限的用户才显示对应待处理/管理入口。
- 管理列表只出现一个一级入口，三类工单通过类型 Tabs/筛选切换。
- 搜索、分页、权限、附件、详情、导航和分享行为保持可用。
- Web lint/build、Playwright 桌面/移动 smoke、App typecheck/Expo Doctor、Android 候选构建通过。
- 主备 API、APK HEAD/Range/SHA-256、升级元数据和 stable 发布登记全部通过后才报告发布完成。

## 回滚点

- Web 回滚到上一稳定 frontend 镜像。
- Android 保留上一 stable APK 和发布记录，不删除旧版本。

## 已完成验收

- Web 统一任务导航已发布为 `localhost/e-report-frontend:20260805-uiux-1`，单节点 K8S Pod 就绪且重启数为 0；旧镜像和发布前部署 YAML 保留在目标机 `/opt/e-report/releases/20260805-uiux-1/`。
- Web `npm run lint`、`npm run build` 和 Playwright 桌面 `1440x900` / 移动 `390x844` smoke 通过；未登录后台跳转登录页，工单管理、我的工单、管理设置和移动抽屉入口按管理员权限显示，配置接口敏感字段保留 `sensitive=true` 并默认掩码，console error 为 0，两个视口无横向溢出。
- App `npm run typecheck`、Expo Doctor `20/20`、Android release 构建和 `emulator-5554` 覆盖安装通过；产物为 `versionCode 22`、`versionName 1.0.21`、runtime `android-1.0.21-native22`。本轮没有创建生产工单。
- Android stable release ID 为 `24`，历史 code22 的固定下载入口为 `https://e-report.omnnu.xyz:60443/e-report/download/android`，APK 大小 `72929349`，SHA-256 为 `83ca22138bc1dd9088b7f45339e2a4fafd517f7b90094400d2893bfafbbd5321`。主备 update、HEAD、Range `206` 和全量 SHA-256 已验证通过。
- 本轮只保留兼容性风险：模拟器人工登录受输入法/验证码时序影响未形成成功登录闭环；后端同账号挑战登录 API 已独立返回 200，Web 浏览器登录态及权限页面已通过，未因此修改生产认证逻辑。
