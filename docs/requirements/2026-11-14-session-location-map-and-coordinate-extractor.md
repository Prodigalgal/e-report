# 登录用户态势地图与报单坐标提取器

- 日期：2026-11-14
- 状态：实施中
- 影响端：Backend、WEB、APP
- 目标版本：`6.12.0`（含新增数据库迁移与新增权限点，按 SemVer 递增 minor）

## 目标

1. 管理员在后台可查看已登录用户的详细信息，含定位、设备、登录时间等。
2. 提供一张地图，同时展示已登录用户分布与工单故障点分布。
3. APP 持续上报登录用户当前位置，使地图反映准实时的在岗人员分布。
4. 把报单的"手动输入经纬度"升级为坐标提取器：地图选点、地址搜索、粘贴坐标自动识别坐标系，解决用户无法到达故障现场时的准确报单问题。

## 范围

### 后端

- `auth_sessions` 增加最新位置与来源信息（经纬度、坐标系、精度、定位时间、上报时间、定位来源、明文 IP）。
- 新增登录态位置上报接口，幂等更新当前会话的最新位置（不做历史轨迹）。
- 新增在线用户 + 故障点地图聚合接口，按操作者数据域裁剪。
- 新增坐标转换与地址解析接口，支持 WGS84 / GCJ-02 / BD-09 识别与转换。
- 新增权限点 `user:session-location:view`，仅分配管理员。
- Liquibase 迁移编号 072。

### WEB

- 高德 JS API 2.0 地图组件（按需加载、密钥从环境变量注入）。
- 在线用户态势地图页面：用户点位 + 故障点图层 + 列表联动。
- 用户详情页补充登录会话位置与设备信息。
- 报单页接入坐标提取器（地图选点 / 地址搜索 / 粘贴坐标识别）。
- 登录后浏览器定位上报（WEB 覆盖）。

### APP

- 新增 Android 前台服务，持续采集并上报位置；登录后启动、退出登录停止。
- 申报单页接入坐标提取器（地图选点 / 地址搜索 / 粘贴坐标识别）。

## 非目标

- 不保留历史轨迹与轨迹回放（仅存最新位置）。
- 不接入 Prometheus/Grafana 等外部平台。
- 不修改工单状态机、SLA、通知与权限判定引擎。
- 不改变 `gpsCoordinates` 既有字符串 `"经度,纬度"` 契约，仍以 WGS84 存储。
- **不部署生产环境**（用户明确要求本轮只推测试环境）。

## 隐私边界变更说明

项目在 5.1.2 与 5.3.2 两版明确立过"不采集原始经纬度、详细地址、硬件唯一标识"的边界（见 `docs/decisions/lightweight-client-observability.md`）。本需求突破该边界，依据如下：

- 采集对象为**公司配发的办公手机**，非员工私人设备。
- 入职劳动合同已包含定位条款，用户明确说明无需在应用内额外告知。
- 用户实行轮班制且工时弹性，因此**不适用**"仅工作日"或"仅上班时段"的采集限制。
- 采集目的为运维工单派单与在岗人员调度，属企业内部生产管理用途。

据此确定的采集边界：

- 仅在用户**处于登录状态且前台服务运行**时上报；退出登录立即停止。
- 只保存**最新位置**，不保留历史轨迹，不构建行为画像。
- 位置数据仅对持有 `user:session-location:view` 权限的账号可见。
- 位置链路与既有客户端观测通道**物理隔离**：观测通道由 `ObservationDataSanitizer` 强制脱敏坐标，不复用、不放宽。
- 坐标与地址写入既有运行日志时仍需脱敏，不进入 `client_observation_events`。

## 关键契约

### 位置上报

- 路径：`POST /auth/session/location`
- 认证：当前会话（Bearer + 会话签名），只允许更新**调用者自己的会话**。
- 请求：`latitude`、`longitude`（数字，WGS84）、`accuracyMeters`（可选）、`coordinateSystem`（原始坐标系，可选）、`provider`（定位来源，可选）、`fixedAt`（定位时刻，可选）。
- 响应：`204 No Content`。
- 幂等：同一会话重复上报为覆盖更新，不产生新行。
- 容错：定位不可用时客户端不得上报伪造坐标；服务端校验经纬度范围与有限性，非法值拒绝。

### 地图聚合

- 路径：`GET /admin/observability/session-locations`
- 返回：在线用户列表（账号、姓名、组织/部门、设备、系统、APP 版本、IP、定位坐标、精度、定位时间、最后活跃时间、登录时间、会话状态、地图上的坐标系为 GCJ-02 用于渲染）与故障点列表（工单号、类型、状态、坐标、地点、时间）。
- 数据域：按操作者授权范围内组织裁剪；坐标一律按授权组织过滤后再返回。
- 坐标系：接口统一返回 **GCJ-02**（高德渲染用），并在字段中标注，避免前端二次猜测。

### 坐标系识别

支持三种标准：`WGS84`（国际通用 / GPS 原始）、`GCJ-02`（国测局火星坐标 / 高德）、`BD-09`（百度）。

- 自动识别策略：按数值范围与偏移特征判定，无法可靠判定时以"中国大陆范围 + 偏移量比对"给出候选与置信度，由用户在界面上确认，不做静默猜测。
- 粘贴解析：支持 `经度,纬度`、`纬度,经度`、带标签（如 `lat:` / `lng:`）、`POINT(x y)`、高德/百度分享链接参数等常见形态。
- 输出：一律转换并回填为系统契约 `"经度,纬度"`（WGS84 存储），同时展示转换前后对比供用户核对。

## 影响文件

### Backend

- `model/pojo/AuthSession.java`、新增 `AuthSessionLocation`（或等价字段承载）。
- `controller/AuthController.java`（位置上报）、新增 `AdminSessionLocationController.java`。
- `service/AuthSessionService.java`、新增 `SessionLocationService.java`、`SessionGeoQueryService.java`。
- `service/MapService.java`（扩展 BD-09 转换与自动识别）。
- `controller/LocationController.java`（坐标识别与解析）。
- `security/PermissionCatalog.java`、`db/changelog/changes/2026-11-14-072-*.sql`、`db.changelog-master.yaml`。
- 相关测试。

### WEB

- 新增 `src/components/map/AmapCanvas.jsx`、`src/components/location/CoordinatePicker.jsx`、`src/utils/coordinateStandards.js`。
- 新增 `src/pages/AdminSessionMapPage/`、`src/services/sessionLocationApiService.js`。
- `src/pages/AdminUserDetailPage/`、`src/pages/CreateWorkOrderPage/`、`src/pages/RespondWorkOrderPage/`。
- `src/constants/permissions.js`、`src/components/layout/AdminLayout.jsx`、`src/utils/authRedirects.js`、`src/router/AppRoutes.jsx`、`src/router/routeModulePreload.js`。
- 浏览器定位上报：`src/services/` + 认证态接入。

### APP

- 新增 `android/app/src/main/java/com/mnnu/ereport/location/EReportLocationTrackingService.kt`、`EReportLocationTrackerModule.kt` + Package。
- 新增 `src/location/location-tracking.ts`、`src/location/coordinate-standards.ts`。
- `app/report/[type].tsx`、`app/_layout.tsx`、`android/app/src/main/AndroidManifest.xml`、`app.json`。
- `src/api/session-location-api.ts`。

## 验收标准

### 定位上报

- 登录后 APP 前台服务启动并周期上报，地图可见该用户点位；退出登录后不再新增上报。
- 断网时本地不丢失、不伪造坐标；恢复网络后补报最新位置。
- 定位权限被拒或系统定位关闭时，不产生虚假点位，接口拒绝非法坐标。
- 同一会话多次上报只保留最新一条，不产生轨迹表膨胀。

### 地图与详情

- 地图同时渲染在线用户与故障点，可区分图层、可点击查看详情。
- 只展示操作者数据域内的用户与故障点；越权账号返回 403 或空集。
- 无 `user:session-location:view` 权限的账号看不到菜单、路由不可达、接口 403。
- 未知坐标或超出范围的点位不产生"假点位"，明确标注为定位异常。

### 坐标提取器

- 地图点选后可回填坐标与地址。
- 地址搜索能定位到 POI 并回填。
- 粘贴三种坐标系的字符串均能正确识别与转换，转换前后对比可见，WGS84 与 GCJ-02 差异约 500 米量级。
- 无法判定坐标系时给出候选让用户确认，不静默选错。
- 回填结果满足既有报单校验（`|lon|<=180`、`|lat|<=90`、中国大陆范围提示）。

### 工程与发布

- 三端测试、lint/typecheck、构建、版本契约全部通过。
- Liquibase 迁移首跑成功且幂等复跑变更数为 0。
- 仅部署 `e-report-test`；生产环境保持不变。

## 测试方式

- Backend：`gradlew test bootJar`；迁移在 PostgreSQL 首跑与幂等复跑；越权与数据域回归测试。
- WEB：`npm test`、`npm run lint`、`npm run build`；真实浏览器验证地图渲染、坐标提取器三种输入路径。
- APP：`npm test`、`npm run typecheck`、`npm run doctor`；Android release 构建与模拟器验证前后台上报行为。
- 高德凭据：已在本地用 Playwright + Chromium 验证 JS API key 与安全密钥可用（地图/逆地理/POI/坐标转换/浏览器定位全部通过，移除安全密钥返回 `INVALID_USER_SCODE`）。
