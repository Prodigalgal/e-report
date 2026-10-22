import { apiFetch } from '@/api/http';
import { solveProofOfWork } from '@/auth/proof-of-work';
import { getDeviceIdentity, signDevicePayload } from '@/security/device-security';
import { sha256Hex } from '@/security/request-signature';
import type { AuthChallenge, AuthSessionPage, LoginResponse, PasswordPolicy } from '@/types/api';

const AUTH_CHALLENGE_TIMEOUT_MS = 30000;
const AUTH_LOGIN_TIMEOUT_MS = 45000;
const AUTH_CURRENT_USER_TIMEOUT_MS = 12000;

export type LoginProgressStage = 'SECURITY_CHECK' | 'AUTHENTICATING';

export async function fetchAuthChallenge(username: string) {
  const identity = await getDeviceIdentity();
  return apiFetch<AuthChallenge>('/auth/challenge', {
    method: 'POST',
    skipAuth: true,
    timeoutMs: AUTH_CHALLENGE_TIMEOUT_MS,
    body: JSON.stringify({
      username: username.trim(),
      clientType: 'ANDROID',
      deviceKeyId: identity.keyId,
      clientInstanceId: identity.installationId,
    }),
  });
}

export async function loginWithChallenge(
  username: string,
  password: string,
  captchaAnswer: string,
  challenge: AuthChallenge,
  onProgress?: (stage: LoginProgressStage) => void,
) {
  if (!challenge.challengeId || !challenge.serverNonce || !challenge.powPrefix) {
    const seconds = Number(challenge.retryAfterSeconds) || 0;
    throw new Error(seconds > 0 ? `登录保护已启用，请在${seconds}秒后重试。` : '安全验证数据不完整。');
  }
  if (challenge.captchaRequired && !captchaAnswer.trim()) {
    throw new Error('请输入数学验证码答案。');
  }
  onProgress?.('SECURITY_CHECK');
  const identity = await getDeviceIdentity();
  const powNonce = await solveProofOfWork(challenge);
  const usernameHash = await sha256Hex(username.trim().toLowerCase());
  const loginProof = [
    'e-report-login-v1',
    challenge.challengeId,
    challenge.serverNonce,
    usernameHash,
    identity.installationId,
    identity.keyId,
  ].join('\n');
  const deviceSignature = await signDevicePayload(loginProof);
  onProgress?.('AUTHENTICATING');
  return apiFetch<LoginResponse>('/auth/login', {
    method: 'POST',
    skipAuth: true,
    timeoutMs: AUTH_LOGIN_TIMEOUT_MS,
    body: JSON.stringify({
      username: username.trim(),
      password,
      challengeId: challenge.challengeId,
      captchaAnswer: challenge.captchaRequired ? captchaAnswer.trim() : null,
      powNonce,
      clientType: 'ANDROID',
      clientInstanceId: identity.installationId,
      deviceKeyId: identity.keyId,
      devicePublicKey: identity.publicKey,
      deviceSignature,
      deviceContext: identity.context,
    }),
  });
}

export function fetchCurrentUser() {
  return apiFetch<LoginResponse>('/auth/me', {
    timeoutMs: AUTH_CURRENT_USER_TIMEOUT_MS,
    telemetryExpectedStatuses: [401],
  });
}

export function fetchPasswordPolicy() {
  return apiFetch<PasswordPolicy>('/auth/password-policy', {
    telemetryExpectedStatuses: [401, 403],
  });
}

export function updateCurrentUserProfile(name: string, phone: string, email?: string | null) {
  return apiFetch<LoginResponse>('/auth/me', {
    method: 'PUT',
    body: JSON.stringify({ name, phone, email }),
  });
}

export function changeCurrentPassword(currentPassword: string, newPassword: string) {
  return apiFetch<void>('/auth/password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

export function fetchAuthSessions(page = 0, size = 10) {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort: 'createdAt,desc' });
  return apiFetch<AuthSessionPage>(`/auth/sessions/page?${params.toString()}`);
}

export function revokeAuthSession(sessionId: string) {
  return apiFetch<void>(`/auth/sessions/${sessionId}`, { method: 'DELETE' });
}

export function revokeOtherAuthSessions() {
  return apiFetch<void>('/auth/sessions/others', { method: 'DELETE' });
}

export function logoutCurrentSession() {
  return apiFetch<void>('/auth/logout', {
    method: 'POST',
    telemetryExpectedStatuses: [401, 403],
  });
}
