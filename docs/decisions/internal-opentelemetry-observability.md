# ADR：内部观测模型对齐 OpenTelemetry

- 状态：已批准，5.3.0 测试候选实施中
- 日期：2026-08-24

## 背景

现有系统已经具备 WEB 内存队列、APP SQLite 队列、Backend Redis 缓冲、PostgreSQL 持久化、租户/组织隔离和运行诊断页面。问题不在于缺少采集链路，而在于事件字段、Trace 标识、环境属性和服务端操作记录均为项目私有格式，客户端请求仅以 `X-Request-Id` 关联，后续跨端分析和标准工具接入成本高。

## 决策

1. 所有新观测数据使用 `ereport-otel/1` 内部协议，语义对齐 OpenTelemetry Logs、Traces、Resource、InstrumentationScope 与 Semantic Conventions。
2. Backend 引入 Spring Boot OpenTelemetry starter，以 Micrometer Observation/Tracing API 作为服务端统一观测门面，使用 Spring 自动生成和传播 W3C `traceparent`。
3. 显式关闭 OTLP traces、metrics、logs 导出，不部署 Collector、Jaeger、Tempo、Prometheus、Grafana、Sentry、PostHog 或其他第三方服务。
4. 继续使用现有轻量链路：客户端本地队列 -> `/observability/events/batch` -> Redis 可靠缓冲 -> PostgreSQL -> 管理端查询。
5. 新协议为加法式升级。旧客户端请求仍可接收，但进入 Redis 前必须转换为统一的内部 OTel 模型；数据库原有查询投影字段暂时保留，避免历史记录和管理页面失效。
6. `X-Request-Id` 仅表示业务请求编号，不再冒充 Trace ID。Trace ID 固定为 32 位小写十六进制，Span ID 固定为 16 位小写十六进制，全零值非法。
7. 平台公共属性使用 OTel 命名；项目专有属性统一使用 `ereport.*` 命名空间。用户、租户、组织等安全上下文以服务端认证结果为准，拒绝信任客户端自报值。

## 信号映射

- `ERROR`、`WARNING`、`DIAGNOSTIC` 映射为 Log Record。
- 用户操作、客户端 API 请求和服务端关键写操作映射为 Span。
- 原有 breadcrumbs 和分阶段操作轨迹映射为 Span Events；错误栈同时投影到 `exception.*` 属性。
- 原有 `eventType/category/eventName/route/operationName/outcome` 继续作为业务查询投影，而非另一套观测语义。

## Resource 与 Scope

必备 Resource 属性：

- `service.name`：`e-report-web`、`e-report-android` 或 `e-report-backend`
- `service.namespace`：`e-report`
- `service.version`
- `service.instance.id`
- `deployment.environment.name`
- `os.type`、`os.name`、`os.version`
- `device.manufacturer`、`device.model.identifier`（可获取时）
- `user_agent.original`、`network.connection.type`（客户端可获取时）
- `app.build.id`
- `telemetry.sdk.name`：`e-report-internal-otel`
- `telemetry.sdk.language`
- `telemetry.sdk.version`

InstrumentationScope 固定包含 `name`、`version` 和可选属性。WEB、APP 和 Backend 使用独立 Scope 名称，以便追踪产生数据的模块。

## 安全与性能

- 既有双层脱敏、字段长度限制、批量上报、采样、离线队列、Redis 重试和死信机制保持不变。
- 不采集 IMEI、MAC、序列号、Android ID、坐标、地址、密码、Token、Cookie 和联系方式。
- 正常用户操作继续采样；错误、失败操作和定位诊断全量保留。
- OTel 属性集合设定数量、层级和单值长度上限，避免高基数或恶意负载拖垮序列化与数据库。
- Actuator 端点不向公网暴露；Micrometer 内存指标仅供进程内诊断和测试断言。

## 后果

- 现在即可使用标准 trace/span/resource 属性完成跨 WEB、APP、Backend 的内部追踪。
- 未来若确需外接 OTLP，只需新增受控 exporter，不需要再次改造业务埋点和数据契约。
- 历史事件没有完整 OTel 字段，查询层需明确显示为“旧协议”，不得伪造 Trace/Span。

