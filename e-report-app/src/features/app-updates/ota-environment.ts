import { NativeModules, Platform } from 'react-native';
import type { ApiEnvironment } from '@/config/public-endpoints';

type NativeUpdateEnvironment = {
  startupEnvironment: ApiEnvironment;
  startupApiBaseUrl: string;
  save(environment: ApiEnvironment, apiBaseUrl: string): Promise<boolean>;
  restart(): Promise<void>;
};

const nativeEnvironment = NativeModules?.EReportUpdateEnvironment as NativeUpdateEnvironment | undefined;

export async function synchronizeOtaEnvironment(environment: ApiEnvironment, apiBaseUrl: string): Promise<void> {
  if (Platform.OS !== 'android' || !nativeEnvironment) return;
  if (await nativeEnvironment.save(environment, apiBaseUrl)) {
    await nativeEnvironment.restart();
    // Hold initialization until the new process selects the corresponding update scope.
    await new Promise<void>(() => {});
  }
}
