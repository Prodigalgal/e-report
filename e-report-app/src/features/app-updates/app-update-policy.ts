import type { AppUpdateCheckResponse } from '@/types/api';

export function shouldPromptNativeUpgrade(update: AppUpdateCheckResponse | undefined): boolean {
  return !!update?.updateAvailable && !!update.versionCode
    && (update.forceUpdate || update.nativeUpgradeRequired);
}
