/**
 * 高德 JS API 2.0 按需加载。
 *
 * key 与安全密钥由后端随地图接口下发，不写入构建产物：一是避免把凭据固化进镜像，
 * 二是换 key 时无需重新构建前端；三是未配置时能给出明确降级提示而不是空白画布。
 *
 * 注意：安全密钥必须在 maps 脚本加载前通过 window._AMapSecurityConfig 设置，
 * 否则高德接口会返回 INVALID_USER_SCODE，地图与插件全部不可用。
 */

const SCRIPT_ID = 'ereport-amap-jsapi';

let loadPromise = null;
let configuredKey = null;

export class AmapConfigurationError extends Error {
    constructor(message) {
        super(message);
        this.name = 'AmapConfigurationError';
    }
}

export const isAmapLoaded = () => typeof window !== 'undefined' && typeof window.AMap !== 'undefined';

const removeExistingScript = () => {
    const existing = document.getElementById(SCRIPT_ID);
    if (existing) {
        existing.remove();
    }
};

/**
 * 加载高德 JS API。
 *
 * @param {{jsApiKey?: string, securityJsCode?: string}} config 后端下发的地图配置
 * @returns {Promise<object>} window.AMap
 */
export const loadAmap = (config) => {
    const jsApiKey = config?.jsApiKey?.trim();
    if (!jsApiKey) {
        return Promise.reject(new AmapConfigurationError(
            '地图服务未配置。请联系管理员在系统配置中填写高德 Web 端 JS API 密钥。'
        ));
    }

    // 换 key 时旧实例不可复用，需要重新加载。
    if (isAmapLoaded() && configuredKey === jsApiKey) {
        return Promise.resolve(window.AMap);
    }
    if (loadPromise && configuredKey === jsApiKey) {
        return loadPromise;
    }

    removeExistingScript();
    configuredKey = jsApiKey;
    const securityJsCode = config?.securityJsCode?.trim();
    window._AMapSecurityConfig = securityJsCode ? { securityJsCode } : {};

    loadPromise = new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.id = SCRIPT_ID;
        script.async = true;
        script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(jsApiKey)}`;
        script.onload = () => {
            if (isAmapLoaded()) {
                resolve(window.AMap);
            } else {
                loadPromise = null;
                reject(new AmapConfigurationError('地图脚本已加载但未初始化，请检查密钥配置与域名白名单。'));
            }
        };
        script.onerror = () => {
            loadPromise = null;
            reject(new AmapConfigurationError('地图脚本加载失败，请检查网络或高德域名白名单配置。'));
        };
        document.head.appendChild(script);
    });

    return loadPromise;
};

/**
 * 确保指定插件可用。
 */
export const loadAmapPlugins = async (plugins) => {
    const AMap = await loadAmap({ jsApiKey: configuredKey });
    return new Promise((resolve) => {
        AMap.plugin(plugins, () => resolve(AMap));
    });
};

export const resetAmapLoaderForTests = () => {
    loadPromise = null;
    configuredKey = null;
    removeExistingScript();
};
