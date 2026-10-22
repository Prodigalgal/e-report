# Android APP v1 需求规格

## 目标

开发 `网络运维管理` Android APP，面向移动通信集团一线人员与处理人员，提供移动端报单、定位、附件上传、工单跟踪和回单处理能力。

## 范围

- 登录：复用现有账号、角色和权限，支持数学验证码与 PoW 安全挑战。
- 首页：根据权限展示可用入口，不在 APP 端实现用户、角色、系统配置管理。
- 报单：支持普通故障、未关箱、箱体破损换箱三类工单。
- 定位：采集 GPS 坐标，经度在前、纬度在后；调用后端逆地理接口自动填充乡镇、村镇/街道和地址，允许手动修改。
- 附件：支持拍照、从相册选择图片/视频并随工单提交。
- 我的工单：展示当前用户提交的工单与处理状态。
- 处理回单：处理人员按权限查看待处理工单并提交处理结果、换箱结果。
- 详情：展示工单概览、内容、处理结果和附件预览/下载入口。

## 非目标

- 不在 APP 首版实现后台用户管理、角色管理、系统配置管理。
- 不在 APP 首版实现地图大屏、轨迹回放、群机器人命令解析。
- 不把高德 Key 下发到 APP；地图 Key 池继续放在后端。

## 架构决策

- APP 使用 Expo React Native + Expo Router + TypeScript。
- 网络请求使用 `fetch` 封装，不引入 axios。
- 移动端认证使用后端返回的 Bearer token；Web 端保留现有 Session/Cookie。
- token 存储使用 `expo-secure-store`。
- 查询缓存使用 `@tanstack/react-query`。
- 弱网/离线首版先保留草稿与失败态基础结构，上传队列在后续增强为后台重试。

## 后端影响

- 登录接口继续设置 Session，同时返回可供 APP 使用的 Bearer token。
- 新增 Bearer token 验证过滤器，支持 APP 请求无 Cookie 访问现有接口。
- `/auth/me`、工单列表、详情、报单、回单、附件权限仍沿用后端权限端点。

## 验收标准

- Android APP 可本地启动并访问生产 API。
- 默认账号可完成登录。
- 不同权限账号首页入口不同。
- 一线账号可提交三类工单并上传附件。
- 一线账号可查看自己的工单状态。
- 处理账号可查看授权类型工单并提交回单。
- APP 构建检查通过，Android 可安装包可生成。

## 验证方式

- 后端：`./gradlew test` 或至少 `./gradlew bootJar`。
- APP：`npm run lint`、`npm run typecheck`、`npx expo-doctor`、Android 构建。
- 运行态：对 `https://e-report.fantong.eu.org:60443/api` 做登录和工单 API smoke。
