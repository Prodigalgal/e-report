#!/usr/bin/env node

import { createHash, randomBytes, randomUUID } from 'node:crypto';
import {
  assertIsolatedE2eTarget,
  assertIsolationReady,
} from './lib/e2e-environment-guard.mjs';

const baseUrl = assertIsolatedE2eTarget(process.env.E_REPORT_E2E_BASE_URL);
const webOrigin = new URL(baseUrl).origin;
const adminUsername = process.env.E_REPORT_E2E_ADMIN_USERNAME;
const adminPassword = process.env.E_REPORT_E2E_ADMIN_PASSWORD;
const adminNextPassword = process.env.E_REPORT_E2E_ADMIN_NEXT_PASSWORD;

if (!adminUsername || !adminPassword) {
  throw new Error('必须通过 E_REPORT_E2E_ADMIN_USERNAME 和 E_REPORT_E2E_ADMIN_PASSWORD 提供隔离环境管理员凭据。');
}

const runId = `${new Date().toISOString().replace(/[-:TZ.]/g, '').slice(2, 14)}-${randomBytes(2).toString('hex')}`;
const testPassword = `${randomBytes(18).toString('base64url')}Aa1!`;
const activatedTestPassword = `${randomBytes(18).toString('base64url')}Zz9!`;
const createdUsers = [];
const createdRoleBundles = [];
const trackedOrders = new Map();
const checks = [];
const cleanupFailures = [];
let adminToken;
let rootOrgId;
let userPhoneSequence = 0;
const userPhoneRunPrefix = String(randomBytes(4).readUInt32BE() % 1_000_000).padStart(6, '0');
let createdRoleBundleCount = 0;
let adminPasswordChanged = false;

const WORK_ORDER_ROLE_PREFIX = Object.freeze({
  NETWORK_FAULT: 'NETWORK_FAULT',
  BOX_DOOR_OPEN: 'BOX_DOOR_OPEN',
  DAMAGED_BOX_REPLACEMENT: 'DAMAGED_BOX_REPLACEMENT',
});
const RETIRED_ADMIN_IDENTITY_ROLES = Object.freeze([
  'ROLE_ADMIN',
  'ROLE_COUNTY_ADMIN',
  'ROLE_PLATFORM_ADMIN',
]);
const REQUIRED_CITY_ADMIN_PERMISSIONS = Object.freeze([
  'management:access',
  'user:manage:assign_roles',
  'tenant:manage:view',
  'tenant:manage:create',
  'tenant:manage:update-status',
]);

function workOrderRole(type, duty) {
  const prefix = WORK_ORDER_ROLE_PREFIX[type];
  if (!prefix) {
    throw new Error(`未知工单类型：${type}`);
  }
  return `ROLE_${prefix}_${duty}`;
}

function rolesForDuty(duty) {
  return Object.keys(WORK_ORDER_ROLE_PREFIX).map((type) => workOrderRole(type, duty));
}

function roleGrant(roleName, orgUnitId) {
  if (roleName.endsWith('_REPORTER') || roleName === 'ROLE_USER') {
    return { roleName, dataScope: 'ORG', scopeOrgId: orgUnitId };
  }
  if (roleName.endsWith('_WORKER')) {
    return { roleName, dataScope: 'ORG_TREE', scopeOrgId: orgUnitId };
  }
  return { roleName, dataScope: 'TENANT_ALL', scopeOrgId: null };
}

class ApiError extends Error {
  constructor(message, status, payload) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.payload = payload;
  }
}

function check(condition, message, details) {
  if (!condition) {
    throw new Error(`${message}${details === undefined ? '' : `: ${JSON.stringify(details)}`}`);
  }
  checks.push(message);
  console.log(`[通过] ${message}`);
}

function sorted(values = []) {
  return [...values].sort();
}

function checkActions(order, expected, message) {
  check(JSON.stringify(sorted(order.allowedActions)) === JSON.stringify(sorted(expected)), message, {
    expected: sorted(expected),
    actual: sorted(order.allowedActions),
  });
}

function createWebSession() {
  return { cookies: new Map() };
}

function captureResponseCookies(response, session) {
  if (!session) {
    return;
  }
  const cookieHeaders = typeof response.headers.getSetCookie === 'function'
    ? response.headers.getSetCookie()
    : [response.headers.get('set-cookie')].filter(Boolean);
  for (const cookieHeader of cookieHeaders) {
    const [cookiePair] = cookieHeader.split(';', 1);
    const separator = cookiePair.indexOf('=');
    if (separator <= 0) {
      continue;
    }
    const name = cookiePair.slice(0, separator).trim();
    const value = cookiePair.slice(separator + 1).trim();
    if (value) {
      session.cookies.set(name, value);
    } else {
      session.cookies.delete(name);
    }
  }
}

function sessionHeaders(session, method) {
  const headers = {
    Origin: webOrigin,
    'X-Client-Platform': 'WEB',
  };
  if (!session || session.cookies.size === 0) {
    return headers;
  }
  headers.Cookie = [...session.cookies.entries()]
    .map(([name, value]) => `${name}=${value}`)
    .join('; ');
  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    const csrfToken = session.cookies.get('XSRF-TOKEN');
    if (csrfToken) {
      headers['X-XSRF-TOKEN'] = decodeURIComponent(csrfToken);
    }
  }
  return headers;
}

async function api(path, { method = 'GET', token, json, body, headers = {}, expected = [200] } = {}) {
  const normalizedMethod = method.toUpperCase();
  const requestHeaders = {
    Accept: 'application/json',
    ...sessionHeaders(token, normalizedMethod),
    ...headers,
  };
  let requestBody = body;
  if (json !== undefined) {
    requestHeaders['Content-Type'] = 'application/json';
    requestBody = JSON.stringify(json);
  }
  const response = await fetch(`${baseUrl}${path}`, {
    method: normalizedMethod,
    headers: requestHeaders,
    body: requestBody,
    signal: AbortSignal.timeout(45_000),
  });
  captureResponseCookies(response, token);
  const responseText = normalizedMethod === 'HEAD' ? '' : await response.text();
  let payload = responseText;
  if (responseText) {
    try {
      payload = JSON.parse(responseText);
    } catch {
      // Binary and plain-text endpoints intentionally keep their raw body.
    }
  }
  if (!expected.includes(response.status)) {
    throw new ApiError(`${normalizedMethod} ${path} 返回 HTTP ${response.status}`, response.status, payload);
  }
  return { status: response.status, headers: response.headers, payload };
}

async function expectApiError(action, expectedStatuses, message) {
  try {
    await action();
  } catch (error) {
    if (error instanceof ApiError && expectedStatuses.includes(error.status)) {
      check(true, message);
      return error;
    }
    throw error;
  }
  throw new Error(`${message}: 请求意外成功`);
}

function captchaAnswer(question) {
  const match = question.match(/(\d+)\s*([+-])\s*(\d+)/);
  if (!match) {
    throw new Error(`无法解析数学验证码: ${question}`);
  }
  const left = Number(match[1]);
  const right = Number(match[3]);
  return String(match[2] === '+' ? left + right : left - right);
}

function solvePow(challenge) {
  const target = '0'.repeat(Number(challenge.powDifficulty) || 0);
  for (let nonce = 0; nonce < Number.MAX_SAFE_INTEGER; nonce += 1) {
    const hash = createHash('sha256')
      .update(`${challenge.challengeId}:${challenge.powPrefix}:${nonce}`)
      .digest('hex');
    if (hash.startsWith(target)) {
      return String(nonce);
    }
  }
  throw new Error('安全挑战计算失败。');
}

async function login(username, password) {
  const session = createWebSession();
  const challenge = (await api('/auth/challenge', {
    method: 'POST',
    token: session,
    json: {
      username,
      clientType: 'WEB',
      clientInstanceId: `e2e-${runId}`,
    },
  })).payload;
  const response = await api('/auth/login', {
    method: 'POST',
    token: session,
    json: {
      username,
      password,
      challengeId: challenge.challengeId,
      captchaAnswer: challenge.captchaRequired ? captchaAnswer(challenge.captchaQuestion) : null,
      powNonce: solvePow(challenge),
      clientType: 'WEB',
      clientInstanceId: `e2e-${runId}`,
    },
  });
  check(Boolean(response.payload.username) && session.cookies.has('EREPORT_SESSION'), `${username} 登录成功`);
  await api('/auth/csrf', { token: session });
  check(session.cookies.has('XSRF-TOKEN'), `${username} WEB 会话 CSRF 已初始化`);
  return { ...response.payload, token: session };
}

async function activatePassword(username, currentPassword, newPassword, loginResponse) {
  check(loginResponse.passwordChangeRequired === true, `${username} 首次登录被强制改密`);
  await api('/auth/password', {
    method: 'POST',
    token: loginResponse.token,
    expected: [204],
    json: { currentPassword, newPassword },
  });
  await expectApiError(
    () => api('/auth/me', { token: loginResponse.token }),
    [401],
    `${username} 旧会话在改密后失效`,
  );
  const activated = await login(username, newPassword);
  check(activated.passwordChangeRequired === false, `${username} 改密后解除强制改密`);
  return activated;
}

function username(suffix) {
  return `e2e-${runId}-${suffix}`.slice(0, 50);
}

async function createUser(suffix, name, roleNames, orgUnitId = rootOrgId) {
  userPhoneSequence += 1;
  const roleGrants = roleNames.map((roleName) => roleGrant(roleName, orgUnitId));
  const created = (await api('/admin/users', {
    method: 'POST',
    token: adminToken,
    expected: [201],
    json: {
      username: username(suffix),
      password: testPassword,
      name: `E2E-${runId}-${name}`,
      phone: `189${userPhoneRunPrefix}${String(userPhoneSequence).padStart(2, '0')}`,
      email: null,
      status: 'ACTIVE',
      roleNames,
      roleGrants,
      orgUnitId,
    },
  })).payload;
  createdUsers.push(created);
  check(
    JSON.stringify(sorted(created.roles)) === JSON.stringify(sorted(roleNames)),
    `${name}临时账号角色已精确分配`,
    created.roles,
  );
  const persistedGrants = (await api(`/admin/users/${created.id}/role-grants`, {
    token: adminToken,
  })).payload;
  check(
    JSON.stringify(sorted(persistedGrants.map((grant) => grant.roleName))) === JSON.stringify(sorted(roleNames)),
    `${name}临时账号显式 Grant 已完整保存`,
    persistedGrants,
  );
  const initialSession = await login(created.username, testPassword);
  const session = await activatePassword(
    created.username,
    testPassword,
    activatedTestPassword,
    initialSession,
  );
  return { ...created, token: session.token };
}

const pngBytes = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64',
);

async function uploadAttachment(token, label) {
  const sha256 = createHash('sha256').update(pngBytes).digest('hex');
  const session = (await api('/file-transfers/uploads', {
    method: 'POST',
    token,
    expected: [201],
    json: {
      purpose: 'WORK_ORDER_ATTACHMENT',
      fileName: `E2E-${runId}-${label}.png`,
      contentType: 'image/png',
      totalSize: pngBytes.length,
      sha256,
      chunkSize: pngBytes.length,
    },
  })).payload;
  await api(`/file-transfers/uploads/${session.uploadId}/chunks/0`, {
    method: 'PUT',
    token,
    headers: {
      'Content-Type': 'application/octet-stream',
      'Content-Range': `bytes 0-${pngBytes.length - 1}/${pngBytes.length}`,
    },
    body: pngBytes,
  });
  await api(`/file-transfers/uploads/${session.uploadId}/complete`, {
    method: 'POST',
    token,
    json: {},
  });
  return session.uploadId;
}

function baseOrderPayload(type, label, attachmentId) {
  return {
    location: `E2E-${runId}-${label}-测试点`,
    gpsCoordinates: '117.650000,24.520000',
    reason: `E2E-${runId}-${label}-测试原因`,
    phone: '13900000000',
    contactPerson: 'E2E测试联系人',
    reportType: type,
    priority: 'ROUTINE',
    boxQuantity: type === 'BOX_DOOR_OPEN' ? 2 : null,
    uploadedAttachmentIds: [attachmentId],
  };
}

async function createOrder(token, type, label, options = {}) {
  const attachmentId = options.attachmentId || await uploadAttachment(token, `${label}-report`);
  const payload = options.payload || baseOrderPayload(type, label, attachmentId);
  const response = (await api('/work-orders', {
    method: 'POST',
    token,
    expected: [201],
    headers: { 'Idempotency-Key': options.idempotencyKey || randomUUID() },
    json: payload,
  })).payload;
  trackedOrders.set(response.id, response.workOrderNo);
  check(response.statusCode === 'SUBMITTED', `${label}工单创建为已提交状态`, response.statusCode);
  check(response.reportingOrgId !== null, `${label}工单具有报单组织归属`);
  check(response.attachments?.length === 1, `${label}工单绑定一份附件`, response.attachments);
  return response;
}

async function getOrder(token, id) {
  return (await api(`/work-orders/${id}`, { token })).payload;
}

async function command(token, order, action, json = {}, expected = [200]) {
  return (await api(`/work-orders/${order.id}/${action}`, {
    method: 'POST',
    token,
    expected,
    headers: { 'Idempotency-Key': randomUUID() },
    json: { expectedVersion: order.version, ...json },
  })).payload;
}

async function assign(token, order, assignee, reason, action = 'assignments') {
  return command(token, order, action, {
    assigneeMembershipId: assignee.id,
    responsibleOrgId: rootOrgId,
    reason,
  });
}

async function accept(token, order, assignmentId) {
  return command(token, order, `assignments/${assignmentId}/acceptance`);
}

async function rejectAssignment(token, order, assignmentId, reason) {
  return command(token, order, `assignments/${assignmentId}/rejection`, { reason });
}

async function respond(token, order, label, type = 'NETWORK_FAULT') {
  const attachmentId = await uploadAttachment(token, `${label}-response`);
  return command(token, order, 'responses', {
    resolutionDetails: `${label}处理完成`,
    resolutionMethod: '现场处理',
    replacementCompleted: type === 'DAMAGED_BOX_REPLACEMENT' ? true : null,
    replacementResult: type === 'DAMAGED_BOX_REPLACEMENT' ? '箱体已更换并恢复正常' : null,
    materialUsages: [],
    uploadedAttachmentIds: [attachmentId],
  });
}

async function verify(token, order, decision, comment) {
  return command(token, order, 'verifications', {
    responseId: order.currentResponseId,
    decision,
    comment,
  });
}

async function checkAttachmentTransport(order, token) {
  const attachment = order.attachments[0];
  const path = `/work-orders/attachments/${order.id}/file/${encodeURIComponent(attachment.id)}`;
  const authenticatedHead = await api(path, { method: 'HEAD', token });
  check(authenticatedHead.status === 200, '已认证附件 HEAD 返回 200');
  const range = await api(path, {
    token,
    headers: { Range: 'bytes=0-15' },
    expected: [206],
  });
  check(range.status === 206, '已认证附件 Range 返回 206');
  await expectApiError(() => api(path, { method: 'HEAD' }), [401], '匿名附件请求被拒绝');
}

async function testValidationAndIdempotency(users) {
  console.log('\n[场景] 必填校验与幂等');
  const attachmentId = await uploadAttachment(users.reporter.token, 'validation');
  const valid = baseOrderPayload('NETWORK_FAULT', '必填校验', attachmentId);
  const invalidCases = [
    ['故障地点必填', { ...valid, location: '' }],
    ['定位坐标必填', { ...valid, gpsCoordinates: '' }],
    ['故障原因必填', { ...valid, reason: '' }],
    ['联系电话必填', { ...valid, phone: '' }],
    ['联系人必填', { ...valid, contactPerson: '' }],
    ['工单类型必填', { ...valid, reportType: null }],
    ['优先级必填', { ...valid, priority: null }],
    ['现场附件至少一份', { ...valid, uploadedAttachmentIds: [] }],
  ];
  for (const [label, payload] of invalidCases) {
    await expectApiError(() => api('/work-orders', {
      method: 'POST', token: users.reporter.token, expected: [201], json: payload,
    }), [400], label);
  }

  const idempotencyKey = randomUUID();
  const first = await createOrder(users.reporter.token, 'NETWORK_FAULT', '幂等提交', {
    attachmentId,
    payload: valid,
    idempotencyKey,
  });
  const replay = (await api('/work-orders', {
    method: 'POST',
    token: users.reporter.token,
    expected: [201],
    headers: { 'Idempotency-Key': idempotencyKey },
    json: valid,
  })).payload;
  check(replay.id === first.id, '相同幂等键与请求体只创建一个工单');
  await expectApiError(() => api('/work-orders', {
    method: 'POST',
    token: users.reporter.token,
    expected: [201],
    headers: { 'Idempotency-Key': idempotencyKey },
    json: { ...valid, reason: `${valid.reason}-不同请求` },
  }), [409], '相同幂等键的不同请求被拒绝');
  const cancelled = await command(users.reporter.token, first, 'cancellations', { reason: 'E2E幂等验证完成' });
  check(cancelled.statusCode === 'CANCELLED', '幂等测试工单已取消');
}

async function testRoleMatrixAndRework(users) {
  console.log('\n[场景] 角色动作矩阵、派单拒绝、返工和闭环');
  const created = await createOrder(users.reporter.token, 'NETWORK_FAULT', '角色矩阵');
  check(created.slaStage === 'CLAIM' && Boolean(created.slaDueAt), '提交后进入接单 SLA 阶段');
  checkActions(created, ['WITHDRAW'], '提单者在已提交状态仅可撤回');
  check(
    JSON.stringify(sorted(created.allowedViews)) === JSON.stringify(['ATTACHMENTS', 'DETAIL', 'HISTORY']),
    '提单者可查看本人工单详情、附件和流转历史，但不可查看审计身份',
    created.allowedViews,
  );
  checkActions(await getOrder(users.normalA.token, created.id), ['CLAIM'], '对应类型处理人在已提交状态仅可接单');
  checkActions(await getOrder(users.dispatcher.token, created.id), ['ASSIGN'], '调度员在已提交状态仅可派单');
  checkActions(await getOrder(users.verifier.token, created.id), [], '验收员在已提交状态无操作');
  checkActions(await getOrder(users.controller.token, created.id), ['CANCEL'], '管控员在已提交状态仅可取消');
  const auditorView = await getOrder(users.auditor.token, created.id);
  checkActions(auditorView, [], '审计员始终只读');
  check(auditorView.allowedViews.includes('AUDIT'), '审计员可查看完整审计信息');
  await expectApiError(
    () => command(users.auditor.token, created, 'claims'),
    [403],
    '审计员主动接单被拒绝',
  );

  const emptyCandidates = (await api(`/work-orders/${created.id}/assignment-candidates?searchTerm=`, {
    token: users.dispatcher.token,
  })).payload;
  check(emptyCandidates.length === 0, '未输入搜索词时不返回长人员列表');
  const searchedCandidates = (await api(
    `/work-orders/${created.id}/assignment-candidates?searchTerm=${encodeURIComponent(users.normalA.username)}`,
    { token: users.dispatcher.token },
  )).payload;
  check(searchedCandidates.some((candidate) => candidate.membershipId === users.normalA.id), '输入账号后返回合格派单人员');

  const pendingA = await assign(users.dispatcher.token, created, users.normalA, 'E2E首次派单');
  checkActions(pendingA, ['REASSIGN'], '调度员在待接收状态仅可调单');
  checkActions(await getOrder(users.normalA.token, created.id), ['ACCEPT_ASSIGNMENT', 'REJECT_ASSIGNMENT'], '被派人员可接收或拒绝');
  await expectApiError(
    () => getOrder(users.normalB.token, created.id),
    [403],
    '非被派人员不能查看待接收工单',
  );
  await expectApiError(
    () => accept(users.normalB.token, pendingA, pendingA.pendingAssignment.id),
    [403],
    '非被派人员不能接受派单',
  );
  await expectApiError(
    () => rejectAssignment(users.normalA.token, pendingA, pendingA.pendingAssignment.id, ' '),
    [400],
    '拒绝派单必须填写原因',
  );
  const rejected = await rejectAssignment(
    users.normalA.token, pendingA, pendingA.pendingAssignment.id, 'E2E现场任务冲突',
  );
  check(rejected.statusCode === 'SUBMITTED' && rejected.pendingAssignment === null, '拒单后恢复未分配状态');
  await expectApiError(
    () => accept(users.normalA.token, rejected, pendingA.pendingAssignment.id),
    [409],
    '已失效派单不能再次接受',
  );

  const pendingB = await assign(users.dispatcher.token, rejected, users.normalB, 'E2E拒单后改派');
  const accepted = await accept(users.normalB.token, pendingB, pendingB.pendingAssignment.id);
  check(accepted.statusCode === 'IN_PROGRESS' && accepted.currentAssignment.membershipId === users.normalB.id, '改派人员接收后成为唯一当前处理人');
  check(accepted.slaStage === 'RESPONSE', '接单后进入处理回单 SLA 阶段');
  checkActions(accepted, ['RESPOND', 'HOLD'], '当前处理人仅可回单或挂起');
  checkActions(await getOrder(users.dispatcher.token, created.id), ['REASSIGN'], '处理中调度员可调单');
  checkActions(await getOrder(users.controller.token, created.id), ['CANCEL'], '处理中管控员可取消');
  checkActions(await getOrder(users.verifier.token, created.id), [], '处理中验收员无操作');

  const firstResponse = await respond(users.normalB.token, accepted, '角色矩阵首次');
  check(firstResponse.statusCode === 'PENDING_VERIFICATION', '首次回单进入待验收');
  check(firstResponse.slaStage === 'VERIFICATION', '回单后进入验收 SLA 阶段');
  checkActions(firstResponse, [], '处理人回单后无继续操作');
  checkActions(await getOrder(users.dispatcher.token, created.id), [], '待验收时调度员无操作');
  checkActions(await getOrder(users.controller.token, created.id), ['CANCEL'], '待验收时管控员仍可取消');
  checkActions(await getOrder(users.reporter.token, created.id), ['VERIFY_APPROVE', 'VERIFY_REJECT'], '提单者可验收本人提交的回单');
  const verifierView = await getOrder(users.verifier.token, created.id);
  checkActions(verifierView, ['VERIFY_APPROVE', 'VERIFY_REJECT'], '验收员可通过或驳回');
  await expectApiError(
    () => verify(users.verifier.token, verifierView, 'REJECTED', ' '),
    [400],
    '驳回返工必须填写验收意见',
  );
  const rework = await verify(users.verifier.token, verifierView, 'REJECTED', 'E2E请补充现场材料');
  check(rework.statusCode === 'IN_PROGRESS' && rework.reworkCount === 1, '驳回后回到处理中并累计返工次数');
  check(rework.currentAssignment.membershipId === users.normalB.id, '驳回后原处理人继续负责');

  const secondResponse = await respond(users.normalB.token, rework, '角色矩阵二次');
  const closed = await verify(users.verifier.token, secondResponse, 'APPROVED', 'E2E验收通过');
  check(closed.statusCode === 'CLOSED' && closed.allowedActions.length === 0, '二次回单验收通过后闭环且无后续动作');
  check(closed.slaStage === 'COMPLETED' && closed.slaDueAt === null, '闭环后 SLA 完成且不再存在截止时间');

  const responses = (await api(`/work-orders/${created.id}/responses`, { token: users.auditor.token })).payload;
  check(
    JSON.stringify(responses.map((response) => response.verificationDecision)) === JSON.stringify(['REJECTED', 'APPROVED']),
    '回单历史完整保留驳回和通过两轮记录',
    responses,
  );
  const events = (await api(`/work-orders/${created.id}/events?page=0&size=100`, {
    token: users.auditor.token,
  })).payload;
  const eventTypes = new Set(events.content.map((event) => event.eventType));
  for (const required of ['SUBMITTED', 'ASSIGNED', 'ASSIGNMENT_REJECTED', 'ASSIGNMENT_ACCEPTED', 'RESPONSE_SUBMITTED', 'VERIFICATION_REJECTED', 'VERIFICATION_APPROVED']) {
    check(eventTypes.has(required), `审计事件包含 ${required}`);
  }
  await expectApiError(
    () => command(users.controller.token, closed, 'cancellations', { reason: 'E2E重复终态操作' }),
    [409],
    '闭环工单不能再次取消',
  );
  await checkAttachmentTransport(created, users.reporter.token);
}

async function testImmediateReassignment(users) {
  console.log('\n[场景] 待接收和处理中即时调单');
  const created = await createOrder(users.reporter.token, 'NETWORK_FAULT', '即时调单');
  const pending = await assign(users.dispatcher.token, created, users.normalA, 'E2E首次派单');
  const reassignedPending = await assign(users.dispatcher.token, pending, users.normalB, 'E2E调整待接收人员', 'reassignments');
  check(
    reassignedPending.statusCode === 'IN_PROGRESS'
      && reassignedPending.pendingAssignment === null
      && reassignedPending.currentAssignment.membershipId === users.normalB.id,
    '待接收工单调单后立即由新人员处理中',
    reassignedPending,
  );
  const reassignedCurrent = await assign(users.dispatcher.token, reassignedPending, users.normalA, 'E2E调整当前处理人员', 'reassignments');
  check(
    reassignedCurrent.currentAssignment.membershipId === users.normalA.id
      && reassignedCurrent.pendingAssignment === null,
    '处理中工单调单后当前处理人真实变更',
    reassignedCurrent,
  );
  checkActions(await getOrder(users.normalB.token, created.id), [], '原处理人在调单后立即失去操作');
  const blankCandidates = (await api(`/work-orders/${created.id}/assignment-candidates?searchTerm=`, {
    token: users.dispatcher.token,
  })).payload;
  check(blankCandidates.length === 0, '调单候选人在空搜索词时保持为空');
  const currentCandidate = (await api(
    `/work-orders/${created.id}/assignment-candidates?searchTerm=${encodeURIComponent(users.normalA.username)}`,
    { token: users.dispatcher.token },
  )).payload;
  check(currentCandidate.length === 0, '当前处理人不重复出现在调单候选人中');
  const previousCandidate = (await api(
    `/work-orders/${created.id}/assignment-candidates?searchTerm=${encodeURIComponent(users.normalB.username)}`,
    { token: users.dispatcher.token },
  )).payload;
  check(previousCandidate.some((candidate) => candidate.membershipId === users.normalB.id), '原处理人可被搜索并重新调入');
  const response = await respond(users.normalA.token, reassignedCurrent, '即时调单');
  const closed = await verify(users.verifier.token, response, 'APPROVED', 'E2E调单验收通过');
  check(closed.statusCode === 'CLOSED', '即时调单工单最终闭环');
}

async function testHoldReassignResume(users) {
  console.log('\n[场景] 挂起、调单和恢复');
  const created = await createOrder(users.reporter.token, 'NETWORK_FAULT', '挂起调单');
  const claimed = await command(users.normalA.token, created, 'claims');
  const held = await command(users.normalA.token, claimed, 'holds', { reason: 'E2E等待施工条件' });
  check(held.statusCode === 'ON_HOLD' && held.slaPaused === true, '挂起后状态与 SLA 均暂停');
  checkActions(held, ['RESUME'], '挂起时当前处理人仅可恢复');
  await expectApiError(
    () => respond(users.normalA.token, held, '挂起期间错误回单'),
    [409],
    '挂起期间禁止回单',
  );
  const reassigned = await assign(users.dispatcher.token, held, users.normalB, 'E2E转交可到场人员', 'reassignments');
  check(
    reassigned.statusCode === 'ON_HOLD'
      && reassigned.slaPaused === true
      && reassigned.currentAssignment.membershipId === users.normalB.id,
    '挂起中调单保留挂起状态并真实切换处理人',
  );
  await expectApiError(
    () => command(users.normalA.token, reassigned, 'resumptions'),
    [403],
    '原处理人不能恢复已调走工单',
  );
  const resumed = await command(users.normalB.token, reassigned, 'resumptions');
  check(resumed.statusCode === 'IN_PROGRESS' && resumed.slaPaused === false, '新处理人恢复后继续处理且 SLA 恢复');
  const response = await respond(users.normalB.token, resumed, '挂起恢复');
  const closed = await verify(users.reporter.token, response, 'APPROVED', 'E2E提单者验收通过');
  check(closed.statusCode === 'CLOSED', '提单者完成挂起调单工单闭环');
}

async function testDedicatedTypesAndIsolation(users) {
  console.log('\n[场景] 三类工单专属处理角色');
  const boxDoorOpen = await createOrder(users.reporter.token, 'BOX_DOOR_OPEN', '箱门未关');
  checkActions(await getOrder(users.dispatcher.token, boxDoorOpen.id), ['ASSIGN'], '箱门未关调度员可派单');
  checkActions(await getOrder(users.verifier.token, boxDoorOpen.id), [], '箱门未关验收员在已提交状态无操作');
  checkActions(await getOrder(users.controller.token, boxDoorOpen.id), ['CANCEL'], '箱门未关管控员可异常取消');
  const boxDoorOpenAuditView = await getOrder(users.auditor.token, boxDoorOpen.id);
  checkActions(boxDoorOpenAuditView, [], '箱门未关审计员保持只读');
  check(boxDoorOpenAuditView.allowedViews.includes('AUDIT'), '箱门未关审计员可查看审计信息');
  await expectApiError(() => getOrder(users.normalA.token, boxDoorOpen.id), [403], '网络故障处理人不能查看箱门未关工单');
  await expectApiError(() => command(users.normalA.token, boxDoorOpen, 'claims'), [403], '网络故障处理人不能接箱门未关工单');
  const wrongCandidates = (await api(
    `/work-orders/${boxDoorOpen.id}/assignment-candidates?searchTerm=${encodeURIComponent(users.normalA.username)}`,
    { token: users.dispatcher.token },
  )).payload;
  check(wrongCandidates.length === 0, '错误专业人员不出现在派单搜索结果');
  await expectApiError(
    () => assign(users.dispatcher.token, boxDoorOpen, users.normalA, 'E2E错误专业派单'),
    [422],
    '错误专业人员不能被派单',
  );
  const pending = await assign(users.dispatcher.token, boxDoorOpen, users.boxDoorOpen, 'E2E正确专业派单');
  const accepted = await accept(users.boxDoorOpen.token, pending, pending.pendingAssignment.id);
  const response = await respond(users.boxDoorOpen.token, accepted, '箱门未关', 'BOX_DOOR_OPEN');
  checkActions(await getOrder(users.verifier.token, boxDoorOpen.id), ['VERIFY_APPROVE', 'VERIFY_REJECT'], '箱门未关验收员可验收回单');
  const boxDoorOpenClosed = await verify(users.verifier.token, response, 'APPROVED', 'E2E箱门未关验收');
  check(boxDoorOpenClosed.statusCode === 'CLOSED' && boxDoorOpenClosed.boxQuantity === 2, '箱门未关专属角色处理并保留箱体数量');

  const replacement = await createOrder(users.reporter.token, 'DAMAGED_BOX_REPLACEMENT', '破损箱体更换');
  checkActions(await getOrder(users.dispatcher.token, replacement.id), ['ASSIGN'], '破损箱体更换调度员可派单');
  checkActions(await getOrder(users.verifier.token, replacement.id), [], '破损箱体更换验收员在已提交状态无操作');
  checkActions(await getOrder(users.controller.token, replacement.id), ['CANCEL'], '破损箱体更换管控员可异常取消');
  const replacementAuditView = await getOrder(users.auditor.token, replacement.id);
  checkActions(replacementAuditView, [], '破损箱体更换审计员保持只读');
  check(replacementAuditView.allowedViews.includes('AUDIT'), '破损箱体更换审计员可查看审计信息');
  const claimed = await command(users.box.token, replacement, 'claims');
  const replaced = await respond(users.box.token, claimed, '破损箱体更换', 'DAMAGED_BOX_REPLACEMENT');
  checkActions(await getOrder(users.verifier.token, replacement.id), ['VERIFY_APPROVE', 'VERIFY_REJECT'], '破损箱体更换验收员可验收回单');
  const replacementClosed = await verify(users.verifier.token, replaced, 'APPROVED', 'E2E换箱验收');
  check(
    replacementClosed.statusCode === 'CLOSED'
      && replacementClosed.replacementCompleted === true
      && replacementClosed.replacementResult === '箱体已更换并恢复正常',
    '破损箱体更换专属角色处理并保留换箱结果',
  );
}

async function testSelfVerification(users) {
  console.log('\n[场景] 普通用户自验限制与管理员自验审计');
  const selfOrder = await createOrder(users.combined.token, 'NETWORK_FAULT', '普通用户自验');
  const claimed = await command(users.combined.token, selfOrder, 'claims');
  const response = await respond(users.combined.token, claimed, '普通用户自验');
  checkActions(response, [], '普通复合角色本人回单后无验收动作');
  await expectApiError(
    () => verify(users.combined.token, response, 'APPROVED', 'E2E本人自验'),
    [422],
    '普通复合角色不能验收自己的回单',
  );
  const closedByVerifier = await verify(users.verifier.token, response, 'APPROVED', 'E2E独立验收通过');
  check(closedByVerifier.statusCode === 'CLOSED', '独立验收员可闭环普通用户回单');

  const adminOrder = await createOrder(users.reporter.token, 'NETWORK_FAULT', '管理员自验');
  const adminClaimed = await command(adminToken, adminOrder, 'claims');
  const adminResponse = await respond(adminToken, adminClaimed, '管理员自验');
  check(!Object.hasOwn(adminResponse, 'verificationOverrideRequired'), '管理员自验不向客户端暴露特殊覆盖字段');
  const adminClosed = await verify(adminToken, adminResponse, 'APPROVED', 'E2E管理员验收意见');
  check(adminClosed.statusCode === 'CLOSED', '管理员只填写一个验收意见即可完成自验闭环');
}

async function testCancellation(users) {
  console.log('\n[场景] 待接收和处理中取消');
  const pendingOrder = await createOrder(users.reporter.token, 'NETWORK_FAULT', '待接收取消');
  const pending = await assign(users.dispatcher.token, pendingOrder, users.normalA, 'E2E取消前派单');
  const cancelledPending = await command(users.reporter.token, pending, 'cancellations', { reason: 'E2E现场自行恢复' });
  check(
    cancelledPending.statusCode === 'CANCELLED'
      && cancelledPending.currentAssignment === null
      && cancelledPending.pendingAssignment === null
      && cancelledPending.allowedActions.length === 0,
    '提单者取消待接收工单并释放派单槽位',
  );
  await expectApiError(
    () => accept(users.normalA.token, cancelledPending, pending.pendingAssignment.id),
    [409],
    '取消后不能接受旧派单',
  );

  const activeOrder = await createOrder(users.reporter.token, 'NETWORK_FAULT', '处理中取消');
  const active = await command(users.normalA.token, activeOrder, 'claims');
  const cancelledActive = await command(users.controller.token, active, 'cancellations', { reason: 'E2E风险管控终止' });
  check(cancelledActive.statusCode === 'CANCELLED' && cancelledActive.currentAssignment === null, '管控员取消处理中工单并释放当前处理人');
  await expectApiError(
    () => respond(users.normalA.token, cancelledActive, '取消后错误回单'),
    [409],
    '取消后禁止回单',
  );
}

async function testConcurrentClaim(users) {
  console.log('\n[场景] 并发主动接单');
  const created = await createOrder(users.reporter.token, 'NETWORK_FAULT', '并发接单');
  const attempts = await Promise.allSettled([
    command(users.normalA.token, created, 'claims'),
    command(users.normalB.token, created, 'claims'),
  ]);
  const successes = attempts.filter((attempt) => attempt.status === 'fulfilled');
  const failures = attempts.filter((attempt) => attempt.status === 'rejected');
  check(successes.length === 1 && failures.length === 1, '并发接单仅一个请求成功');
  check(failures[0].reason instanceof ApiError && failures[0].reason.status === 409, '并发接单失败方收到版本冲突而非静默成功');
  const current = await getOrder(adminToken, created.id);
  check(current.currentAssignment !== null && current.pendingAssignment === null, '并发接单后仅保留一个当前处理人');
  const cancelled = await command(adminToken, current, 'cancellations', { reason: 'E2E并发验证完成' });
  check(cancelled.statusCode === 'CANCELLED', '并发测试工单已取消');
}

async function testOrganizationIsolation(users, orgA, orgB) {
  console.log('\n[场景] 跨区县组织隔离');
  const reporterA = await createUser('org-a-reporter', `${orgA.name}提单者`, [workOrderRole('NETWORK_FAULT', 'REPORTER')], orgA.id);
  const workerB = await createUser('org-b-worker', `${orgB.name}处理人`, ['ROLE_NETWORK_FAULT_WORKER'], orgB.id);
  const created = await createOrder(reporterA.token, 'NETWORK_FAULT', '组织隔离');
  check(created.reportingOrgId === orgA.id, '工单自动归属提单人所在组织');
  await expectApiError(() => getOrder(workerB.token, created.id), [403], '其他区县处理人不能查看本区县外工单');
  await expectApiError(() => command(workerB.token, created, 'claims'), [403], '其他区县处理人不能跨区县接单');
  const adminView = await getOrder(adminToken, created.id);
  check(adminView.id === created.id, '市公司管理员可在租户范围查看区县工单');
  const cancelled = await command(adminToken, adminView, 'cancellations', { reason: 'E2E组织隔离验证完成' });
  check(cancelled.statusCode === 'CANCELLED', '组织隔离测试工单已取消');
}

async function testRoleBundleCustomization() {
  console.log('\n[场景] 可自定义快捷岗位模板');
  const page = (await api('/admin/role-bundles?size=100&sort=sortOrder,asc', {
    token: adminToken,
  })).payload;
  const builtIn = page.content.find((bundle) => bundle.builtIn === true);
  check(Boolean(builtIn), '租户已同步只读内置岗位模板');
  await expectApiError(() => api(`/admin/role-bundles/${builtIn.id}`, {
    method: 'PUT',
    token: adminToken,
    json: {
      displayName: `${builtIn.displayName}-禁止修改`,
      description: builtIn.description,
      roleNames: builtIn.roleNames,
      active: true,
      sortOrder: builtIn.sortOrder,
    },
  }), [409], '内置岗位模板禁止直接修改');

  const customName = `E2E-${runId}-自定义岗位`;
  const custom = (await api('/admin/role-bundles', {
    method: 'POST',
    token: adminToken,
    expected: [201],
    json: {
      displayName: customName,
      description: '隔离 E2E 自定义岗位模板',
      roleNames: [
        workOrderRole('NETWORK_FAULT', 'REPORTER'),
        workOrderRole('NETWORK_FAULT', 'WORKER'),
      ],
      active: true,
      sortOrder: 9010,
    },
  })).payload;
  createdRoleBundles.push(custom);
  createdRoleBundleCount += 1;
  check(custom.builtIn === false && custom.roleNames.length === 2, '可创建包含多个细职责角色的自定义岗位模板');

  const searched = (await api(`/admin/role-bundles?size=20&searchTerm=${encodeURIComponent(customName)}`, {
    token: adminToken,
  })).payload;
  check(searched.content.some((bundle) => bundle.id === custom.id), '自定义岗位模板可按名称搜索');

  const updated = (await api(`/admin/role-bundles/${custom.id}`, {
    method: 'PUT',
    token: adminToken,
    json: {
      displayName: `${customName}-已调整`,
      description: '隔离 E2E 已调整并停用',
      roleNames: [workOrderRole('NETWORK_FAULT', 'REPORTER')],
      active: false,
      sortOrder: 9020,
    },
  })).payload;
  check(updated.active === false && updated.roleNames.length === 1, '自定义岗位模板可调整角色组合并停用');

  const copied = (await api(`/admin/role-bundles/${builtIn.id}/copies`, {
    method: 'POST',
    token: adminToken,
    expected: [201],
    json: {
      displayName: `E2E-${runId}-内置副本`,
      description: '隔离 E2E 内置模板副本',
      roleNames: [workOrderRole('BOX_DOOR_OPEN', 'AUDITOR')],
      active: true,
      sortOrder: 9030,
    },
  })).payload;
  createdRoleBundles.push(copied);
  createdRoleBundleCount += 1;
  check(copied.builtIn === false && copied.roleNames.includes(workOrderRole('BOX_DOOR_OPEN', 'AUDITOR')),
    '内置模板可复制为自定义模板并原子覆盖角色组合');

  await api(`/admin/role-bundles/${copied.id}`, {
    method: 'DELETE',
    token: adminToken,
    expected: [204],
  });
  createdRoleBundles.splice(createdRoleBundles.findIndex((bundle) => bundle.id === copied.id), 1);
  await expectApiError(() => api(`/admin/role-bundles/${copied.id}`, {
    token: adminToken,
  }), [404], '自定义岗位模板可删除');
}

async function testAtomicUserAdministration() {
  console.log('\n[场景] 用户详情与角色原子保存');
  const target = await createUser('atomic-user', '角色原子保存', [
    'ROLE_USER',
    workOrderRole('NETWORK_FAULT', 'REPORTER'),
    workOrderRole('NETWORK_FAULT', 'WORKER'),
  ]);

  const initial = (await api(`/admin/users/${target.id}/detail`, {
    token: adminToken,
  })).payload;
  check(initial.user.id === target.id, '用户详情返回目标用户资料');
  check(initial.roleGrants.length === 3, '用户详情返回完整初始角色授权');
  check(initial.performance.reportedTotal === 0, '新用户详情返回零值提单统计');
  check(initial.performance.responsesSubmitted === 0, '新用户详情返回零值回单统计');

  const retainedRole = workOrderRole('NETWORK_FAULT', 'WORKER');
  const updated = (await api(`/admin/users/${target.id}/detail`, {
    method: 'PUT',
    token: adminToken,
    json: {
      details: {
        name: `E2E-${runId}-角色保存已更新`,
        phone: target.phone,
        email: `e2e-${runId}@example.com`,
        orgUnitId: rootOrgId,
      },
      status: 'ACTIVE',
      roleGrants: [{
        roleName: retainedRole,
        dataScope: 'ORG_TREE',
        scopeOrgId: rootOrgId,
      }],
    },
  })).payload;
  check(
    JSON.stringify(updated.user.roles) === JSON.stringify([retainedRole]),
    '取消勾选后仅保留指定角色',
    updated.user.roles,
  );
  check(updated.roleGrants.length === 1, '原子保存后仅存在一条角色授权');
  check(updated.roleGrants[0].dataScope === 'ORG_TREE', '原子保存同步更新数据范围');
  check(updated.roleGrants[0].scopeOrgId === rootOrgId, '原子保存同步更新范围组织');

  const reloaded = (await api(`/admin/users/${target.id}/detail`, {
    token: adminToken,
  })).payload;
  check(
    JSON.stringify(reloaded.user.roles) === JSON.stringify([retainedRole]),
    '重新读取详情后角色精确集合保持不变',
    reloaded.user.roles,
  );
  check(reloaded.roleGrants.length === 1, '重新读取详情后不存在历史重复授权');
}

async function cleanup() {
  if (!adminToken) {
    return;
  }
  for (const [id, workOrderNo] of [...trackedOrders.entries()].reverse()) {
    try {
      const order = await getOrder(adminToken, id);
      if (!['CLOSED', 'CANCELLED'].includes(order.statusCode)) {
        await command(adminToken, order, 'cancellations', { reason: `E2E-${runId}-异常清理` });
        console.log(`[清理] 已取消 ${workOrderNo}`);
      }
    } catch (error) {
      cleanupFailures.push(`工单 ${workOrderNo}: ${error.message}`);
    }
  }
  for (const user of [...createdUsers].reverse()) {
    try {
      await api(`/admin/users/${user.id}`, {
        method: 'DELETE', token: adminToken, expected: [204],
      });
      console.log(`[清理] 已软删除临时账号 ${user.username}`);
    } catch (error) {
      cleanupFailures.push(`账号 ${user.username}: ${error.message}`);
    }
  }
  for (const bundle of [...createdRoleBundles].reverse()) {
    try {
      await api(`/admin/role-bundles/${bundle.id}`, {
        method: 'DELETE', token: adminToken, expected: [204, 404],
      });
      console.log(`[清理] 已删除临时岗位模板 ${bundle.displayName}`);
    } catch (error) {
      cleanupFailures.push(`岗位模板 ${bundle.displayName}: ${error.message}`);
    }
  }
}

async function main() {
  let failure;
  try {
    console.log(`[开始] 隔离环境工单生命周期 E2E ${runId}`);
    let admin = await login(adminUsername, adminPassword);
    if (admin.passwordChangeRequired === true) {
      if (!adminNextPassword) {
        throw new Error('测试管理员首次登录必须通过 E_REPORT_E2E_ADMIN_NEXT_PASSWORD 提供符合策略的新密码。');
      }
      admin = await activatePassword(adminUsername, adminPassword, adminNextPassword, admin);
      adminPasswordChanged = true;
    }
    adminToken = admin.token;
    const missingAdminPermissions = REQUIRED_CITY_ADMIN_PERMISSIONS
      .filter((permission) => !admin.permissions.includes(permission));
    check(missingAdminPermissions.length === 0, '市公司管理员具备隔离 E2E 所需管理能力', {
      missingAdminPermissions,
      permissions: admin.permissions,
    });
    const assignedRetiredRoles = admin.roles
      .filter((role) => RETIRED_ADMIN_IDENTITY_ROLES.includes(role));
    check(assignedRetiredRoles.length === 0, '市公司管理员未保留已退役空壳身份角色', assignedRetiredRoles);

    const isolationStatus = (await api('/admin/e2e-isolation', { token: adminToken })).payload;
    assertIsolationReady(isolationStatus);
    check(true, '服务端隔离能力与外部副作用开关校验通过');

    const orgUnits = (await api('/admin/org-units', { token: adminToken })).payload;
    const rootOrg = orgUnits.find((org) => org.parentId === null);
    check(Boolean(rootOrg), '找到当前租户根组织');
    rootOrgId = rootOrg.id;
    const countyOrgs = orgUnits.filter((org) => org.parentId === rootOrgId && org.status === 'ACTIVE');
    check(countyOrgs.length >= 2, '至少存在两个可用于组织隔离验证的区县组织', countyOrgs.map((org) => org.name));

    await testRoleBundleCustomization();
    await testAtomicUserAdministration();

    const users = {
      reporter: await createUser('reporter', '三类工单提单者', rolesForDuty('REPORTER')),
      normalA: await createUser('normal-a', '故障处理甲', [workOrderRole('NETWORK_FAULT', 'WORKER')]),
      normalB: await createUser('normal-b', '故障处理乙', [workOrderRole('NETWORK_FAULT', 'WORKER')]),
      boxDoorOpen: await createUser('boxDoorOpen', '箱门未关处理人', [workOrderRole('BOX_DOOR_OPEN', 'WORKER')]),
      box: await createUser('box', '破损箱体更换处理人', [workOrderRole('DAMAGED_BOX_REPLACEMENT', 'WORKER')]),
      dispatcher: await createUser('dispatcher', '三类工单调度员', rolesForDuty('DISPATCHER')),
      verifier: await createUser('verifier', '三类工单验收员', rolesForDuty('VERIFIER')),
      controller: await createUser('controller', '三类工单管控员', rolesForDuty('CONTROLLER')),
      auditor: await createUser('auditor', '三类工单审计员', rolesForDuty('AUDITOR')),
      combined: await createUser('combined', '提单处理复合角色', [
        workOrderRole('NETWORK_FAULT', 'REPORTER'),
        workOrderRole('NETWORK_FAULT', 'WORKER'),
      ]),
    };

    await testValidationAndIdempotency(users);
    await testRoleMatrixAndRework(users);
    await testImmediateReassignment(users);
    await testHoldReassignResume(users);
    await testDedicatedTypesAndIsolation(users);
    await testSelfVerification(users);
    await testCancellation(users);
    await testConcurrentClaim(users);
    await testOrganizationIsolation(users, countyOrgs[0], countyOrgs[1]);
  } catch (error) {
    failure = error;
    console.error(`[失败] ${error.stack || error.message}`);
  } finally {
    await cleanup();
  }

  const summary = {
    runId,
    baseUrl,
    passed: !failure && cleanupFailures.length === 0,
    checkCount: checks.length,
    createdUserCount: createdUsers.length,
    createdRoleBundleCount,
    adminPasswordChanged,
    workOrderCount: trackedOrders.size,
    workOrders: [...trackedOrders.entries()].map(([id, workOrderNo]) => ({ id, workOrderNo })),
    cleanupFailures,
    failure: failure ? failure.message : null,
  };
  console.log(`E2E_SUMMARY=${JSON.stringify(summary)}`);
  if (!summary.passed) {
    process.exitCode = 1;
  }
}

await main();
