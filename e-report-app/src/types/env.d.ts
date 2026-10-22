declare namespace NodeJS {
  type ProcessEnv = {
    EXPO_PUBLIC_ORIGIN?: string;
    EXPO_PUBLIC_FALLBACK_ORIGIN?: string;
    EXPO_PUBLIC_API_BASE_URL?: string;
    EXPO_PUBLIC_API_FALLBACK_BASE_URL?: string;
    EXPO_PUBLIC_TEST_ORIGIN?: string;
    EXPO_PUBLIC_TEST_FALLBACK_ORIGIN?: string;
    EXPO_PUBLIC_TEST_API_BASE_URL?: string;
    EXPO_PUBLIC_TEST_API_FALLBACK_BASE_URL?: string;
    EXPO_PUBLIC_APP_UPDATE_CHANNEL?: string;
    EXPO_PUBLIC_DEPLOYMENT_ENVIRONMENT?: string;
    E_REPORT_AMAP_ANDROID_KEY?: string;
  };
}

declare const process: {
  env: NodeJS.ProcessEnv;
};
