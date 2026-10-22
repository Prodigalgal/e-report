import { NativeModules } from 'react-native';

export type DeviceSecurityContext = {
  installationId: string;
  manufacturer?: string;
  brand?: string;
  model?: string;
  deviceName?: string;
  productName?: string;
  hardwareName?: string;
  boardName?: string;
  supportedAbis?: string[];
  supported32BitAbis?: string[];
  supported64BitAbis?: string[];
  socManufacturer?: string;
  socModel?: string;
  cpuCoreCount?: number;
  lowRamDevice?: boolean;
  osRelease?: string;
  sdkInt?: number;
  securityPatch?: string;
  buildFingerprint?: string;
  screenWidth?: number;
  screenHeight?: number;
  screenDensity?: number;
  memoryMbBucket?: number;
  storageMbBucket?: number;
  localeTag?: string;
  timeZone?: string;
  appVersion?: string;
  appBuild?: string;
  installerPackage?: string;
  appSignatureSha256?: string;
  hardwareBacked: boolean;
  securitySignals: string[];
  environmentSchemaVersion?: number;
  environmentFingerprintHash?: string;
};

export type DeviceIdentity = {
  installationId: string;
  keyId: string;
  publicKey: string;
  hardwareBacked: boolean;
  context: DeviceSecurityContext;
};

type NativeDeviceSecurityModule = {
  getOrCreateIdentity(): Promise<DeviceIdentity>;
  sign(payload: string): Promise<string>;
};

let identityPromise: Promise<DeviceIdentity> | null = null;

export function getDeviceIdentity(): Promise<DeviceIdentity> {
  if (!identityPromise) {
    identityPromise = nativeModule().getOrCreateIdentity().then(validateIdentity).catch((error) => {
      identityPromise = null;
      throw error;
    });
  }
  return identityPromise;
}

export function signDevicePayload(payload: string): Promise<string> {
  return nativeModule().sign(payload);
}

function nativeModule(): NativeDeviceSecurityModule {
  const module = NativeModules.EReportDeviceSecurity as NativeDeviceSecurityModule | undefined;
  if (!module?.getOrCreateIdentity || !module?.sign) {
    throw new Error('当前安装包缺少设备安全模块，请升级应用后重试。');
  }
  return module;
}

function validateIdentity(identity: DeviceIdentity): DeviceIdentity {
  if (!identity?.installationId || !identity.keyId || !identity.publicKey || !identity.context) {
    throw new Error('设备安全身份返回数据不完整。');
  }
  return {
    ...identity,
    context: {
      ...identity.context,
      installationId: identity.installationId,
      hardwareBacked: identity.hardwareBacked === true,
      securitySignals: Array.isArray(identity.context.securitySignals)
        ? identity.context.securitySignals.filter(value => typeof value === 'string')
        : [],
    },
  };
}
