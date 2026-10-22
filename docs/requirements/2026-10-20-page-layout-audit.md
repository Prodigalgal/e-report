# WEB / APP 逐页布局审计与测试验收

## 目标

继续验收 6.5.0 测试环境，在 6.6.1 候选中重新设计 WEB / APP。用户已授权调整 UI / UX 结构；采用任务导向的信息分区、就近操作、主次操作层级、桌面列表与手机内容适配，沿用 C 版标识和品牌蓝。

## 范围

- WEB 路由中的认证、首页、报单、回单、工单查询/详情、个人资料、全部管理页和弹窗。
- APP 对应页面的布局与共用组件；构建验证与真机目视验收分别记录。
- 测试环境邮件个人订阅、工单触发、发件池组织策略、日报预约及发布制品验收。

## 非目标

不改组织/权限/API/数据库契约，不删字段或业务入口，不改打码服务模型，不发布生产。

## 影响文件

WEB 的页面和 layout、work-orders、Admin、Profile 组件；APP 的页面和 ui 组件；版本契约、当前任务及本次验收报告。

## 验收标准

- 长页面按用户任务分区，切换分类保留表单草稿；当前操作的结果和错误出现在所属区域。
- 一组任务只有清楚的主操作；批量操作紧邻列表，危险操作与普通操作区分，按钮不挤压标题或溢出。
- 核心导航不放在统计或业务列表之后；APP 后台的工单查询在标题旁直接进入，WEB 保持导航和页面工具栏可达。选择组织、部门、模板只显示当前值，长选项在弹层内查找。
- WEB / APP 使用一致的操作语义：实色主操作、柔和底色的次操作、克制的危险操作；导航、标签、筛选、列表行允许透明或白底，通过位置、文字、选中标记、分隔和交互反馈体现用途，不强制所有控件填色加框。操作文字对比度至少 4.5:1，手机业务按钮至少 48，轻量导航和分页触达至少 44。
- 视觉采用白色内容、浅灰画布、清晰的字号层级、留白与细分隔；减少粗边框、彩色装饰条和大面积高饱和色，不引入与操作任务无关的营销式动效。
- 桌面摘要与趋势优先；列表有清楚的筛选、结果和分页，横向滚动限于表格容器；手机控件可触达、无整页横向溢出。
- 保留现有校验、权限态、空态、加载态、失败重试及全部可配置项。
- 真实邮件只允许已批准的测试收件人；结束后恢复测试配置；SMTP 接受与收件箱确认分开记录。

## 测试方式

逐页浏览器检查桌面与窄屏，记录高度/溢出/操作布局与截图；WEB test/lint/build；APP test/typecheck/native/version/assets contract 和 Android bundle；测试环境 API、数据库只读记录、Pod/调度/邮件日志。整改集中完成后一次提交新候选，GitHub 后台构建，生产等待验收。

## 设计依据

- IBM Carbon Data table：查询和列表操作归入工具栏，详情渐进展示，分页贴近列表。
  https://www.carbondesignsystem.com/building-blocks/core/components/data-table/guidelines
- Android canonical layouts：按可用宽度组织列表、详情和辅助内容，手机避免照搬桌面多栏。
  https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts
- UI UX Pro Max：采用渐进展示、主要操作可达、导航分层、长列表虚拟化和 React Native 触达规范。已核对源仓库 `56ea9cf174ed86786187daa9de0e6247cb0747e4`，按本项目的操作型后台场景应用；用户已授权 UI / UX 结构调整，不机械采用搜索返回的营销布局、配色或字体。
  https://github.com/nextlevelbuilder/ui-ux-pro-max-skill
- Vercel Web Design Guidelines：检查语义、焦点、表单、长文本、响应式及操作状态；已核对 Skill `063bee94c3f4df8453406c830b0a7df0f2860278` 及当日规则原文。
  https://github.com/vercel-labs/agent-skills/tree/main/skills/web-design-guidelines
