import type { AppReleasePolicyUpdatePayload } from '@/api/admin-api';

export function normalizeAppReleasePolicy(
  draft: { forceUpdate: boolean; minSupportedVersionCode: string; releaseNotes: string },
  versionCode: number,
): AppReleasePolicyUpdatePayload {
  const minSupportedVersionCode = Number(draft.minSupportedVersionCode);
  if (!Number.isInteger(minSupportedVersionCode) || minSupportedVersionCode < 0) {
    throw new Error('最低支持构建号必须是大于或等于0的整数。');
  }
  if (minSupportedVersionCode > versionCode) {
    throw new Error('最低支持构建号不能高于当前发布版本的构建号。');
  }
  const releaseNotes = draft.releaseNotes.trim();
  if (releaseNotes.length > 2000) {
    throw new Error('更新说明不能超过2000字符。');
  }
  return {
    forceUpdate: draft.forceUpdate,
    minSupportedVersionCode,
    releaseNotes,
  };
}
