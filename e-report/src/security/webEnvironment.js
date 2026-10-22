const WEB_ENVIRONMENT_SCHEMA = 'web-env-v1';
const COLLECTION_TIMEOUT_MS = 2000;
const HASH_PATTERN = /^[a-f0-9]{64}$/;
const FONT_CANDIDATES = Object.freeze([
    'Arial', 'Calibri', 'Cambria', 'Consolas', 'Courier New', 'Georgia', 'Microsoft YaHei',
    'Microsoft JhengHei', 'PingFang SC', 'SimHei', 'SimSun', 'Tahoma', 'Times New Roman',
    'Verdana', 'Noto Sans CJK SC', 'Noto Sans SC', 'Source Han Sans CN', 'HarmonyOS Sans SC',
]);
const HIGH_CONFIDENCE_AUTOMATION_SIGNALS = new Set([
    'WEBDRIVER', 'HEADLESS_USER_AGENT', 'AUTOMATION_GLOBAL',
]);

let environmentPromise;

export function getWebEnvironment() {
    if (!environmentPromise) {
        environmentPromise = withTimeout(collectWebEnvironment(), COLLECTION_TIMEOUT_MS)
            .catch(async () => minimalEnvironment('COLLECTION_TIMEOUT'));
    }
    return environmentPromise;
}

export async function getWebAuthenticationEnvironment() {
    const environment = await getWebEnvironment();
    return {
        schemaVersion: environment.schemaVersion,
        fingerprintHash: environment.fingerprintHash,
        browserName: environment.browserName,
        browserVersion: environment.browserVersion,
        osName: environment.osName,
        osVersion: environment.osVersion,
        deviceName: environment.deviceName,
        manufacturer: environment.manufacturer,
        model: environment.model,
        localeTag: environment.localeTag,
        timeZone: environment.timeZone,
        appVersion: environment.appVersion,
        canvasHash: environment.canvasHash,
        webglHash: environment.webglHash,
        audioHash: environment.audioHash,
        fontHash: environment.fontHash,
        securitySignals: environment.securitySignals,
    };
}

export async function collectWebEnvironment(runtime = globalThis) {
    const navigatorObject = runtime.navigator;
    if (!navigatorObject || !runtime.crypto?.subtle) {
        return minimalEnvironment('SECURE_BROWSER_API_UNAVAILABLE', runtime);
    }

    const documentObject = runtime.document;
    const windowObject = runtime.window || runtime;
    const userAgent = String(navigatorObject.userAgent || '');
    const clientHints = await collectClientHints(navigatorObject);
    const browser = parseBrowser(userAgent, clientHints);
    const operatingSystem = parseOperatingSystem(userAgent, clientHints);
    const canvas = collectCanvasFingerprint(documentObject);
    const graphics = collectWebGlFingerprint(documentObject);
    const audio = await collectAudioFingerprint(runtime);
    const fonts = collectFonts(documentObject);
    const locale = collectLocale(navigatorObject);
    const basic = collectBasicAttributes(navigatorObject, windowObject);
    const automation = collectAutomationSignals(navigatorObject, runtime, userAgent);
    const securitySignals = uniqueSorted([
        ...automation.signals,
        ...clientHints.signals,
        ...canvas.signals,
        ...graphics.signals,
        ...audio.signals,
        ...fonts.signals,
    ]);
    const fingerprintComponents = {
        schema: WEB_ENVIRONMENT_SCHEMA,
        browser,
        operatingSystem,
        clientHints: clientHints.value,
        basic,
        locale,
        canvasHash: await hashOptional(canvas.value),
        webglHash: await hashOptional(graphics.fingerprintSource),
        audioHash: await hashOptional(audio.value),
        fontHash: await hashOptional(fonts.detected.join('|')),
    };
    const fingerprintHash = await sha256Hex(stableStringify(fingerprintComponents));
    return {
        schemaVersion: 1,
        schema: WEB_ENVIRONMENT_SCHEMA,
        fingerprintHash,
        browserName: browser.name,
        browserVersion: browser.version,
        osName: operatingSystem.name,
        osVersion: operatingSystem.version,
        deviceName: basic.mobile ? '移动浏览器' : '桌面浏览器',
        manufacturer: '',
        model: clientHints.value.model || extractAndroidModel(userAgent),
        localeTag: locale.locale,
        timeZone: locale.timeZone,
        appVersion: webAppVersion(),
        canvasHash: fingerprintComponents.canvasHash,
        webglHash: fingerprintComponents.webglHash,
        audioHash: fingerprintComponents.audioHash,
        fontHash: fingerprintComponents.fontHash,
        securitySignals,
        highRiskSignals: securitySignals.filter(signal => HIGH_CONFIDENCE_AUTOMATION_SIGNALS.has(signal)),
        attributes: {
            schema: WEB_ENVIRONMENT_SCHEMA,
            browser,
            operatingSystem,
            clientHints: clientHints.value,
            basic,
            locale,
            graphics: graphics.value,
            fontMatches: fonts.detected,
            capabilities: {
                canvas: Boolean(fingerprintComponents.canvasHash),
                webgl: Boolean(fingerprintComponents.webglHash),
                webAudio: Boolean(fingerprintComponents.audioHash),
                fontProbe: fonts.detected.length > 0,
                clientHints: clientHints.available,
            },
            fingerprints: {
                overall: fingerprintHash,
                canvas: fingerprintComponents.canvasHash,
                webgl: fingerprintComponents.webglHash,
                audio: fingerprintComponents.audioHash,
                fonts: fingerprintComponents.fontHash,
            },
            automation: automation.attributes,
            securitySignals,
        },
    };
}

export function parseBrowser(userAgent, clientHints = { value: {} }) {
    const brands = Array.isArray(clientHints?.value?.fullVersionList)
        ? clientHints.value.fullVersionList
        : (clientHints?.value?.brands || []);
    const preferredBrand = brands.find(item => /Microsoft Edge/i.test(item.brand))
        || brands.find(item => /Google Chrome/i.test(item.brand))
        || brands.find(item => /Chromium/i.test(item.brand));
    if (preferredBrand) {
        return { name: normalizeBrowserBrand(preferredBrand.brand), version: String(preferredBrand.version || '') };
    }
    const candidates = [
        [/Edg\/([\d.]+)/, 'Microsoft Edge'],
        [/OPR\/([\d.]+)/, 'Opera'],
        [/Firefox\/([\d.]+)/, 'Firefox'],
        [/Chrome\/([\d.]+)/, 'Google Chrome'],
        [/Version\/([\d.]+).*Safari\//, 'Safari'],
    ];
    for (const [pattern, name] of candidates) {
        const match = String(userAgent || '').match(pattern);
        if (match) return { name, version: match[1] };
    }
    return { name: '未知浏览器', version: '' };
}

export function parseOperatingSystem(userAgent, clientHints = { value: {} }) {
    const platform = String(clientHints?.value?.platform || '');
    const platformVersion = String(clientHints?.value?.platformVersion || '');
    if (/Windows/i.test(platform) || /Windows NT/i.test(userAgent)) {
        return { name: 'Windows', version: windowsDisplayVersion(platformVersion, userAgent) };
    }
    if (/Android/i.test(platform) || /Android/i.test(userAgent)) {
        return { name: 'Android', version: platformVersion || matchVersion(userAgent, /Android\s+([\d.]+)/) };
    }
    if (/iOS/i.test(platform) || /iPhone|iPad|iPod/i.test(userAgent)) {
        return { name: 'iOS', version: platformVersion || matchVersion(userAgent, /OS\s([\d_]+)/).replaceAll('_', '.') };
    }
    if (/macOS|Mac/i.test(platform) || /Mac OS X/i.test(userAgent)) {
        return { name: 'macOS', version: platformVersion || matchVersion(userAgent, /Mac OS X\s([\d_]+)/).replaceAll('_', '.') };
    }
    if (/Linux/i.test(platform) || /Linux/i.test(userAgent)) return { name: 'Linux', version: platformVersion };
    return { name: platform || '未知系统', version: platformVersion };
}

export function windowsDisplayVersion(platformVersion, userAgent = '') {
    const major = Number.parseInt(String(platformVersion || '').split('.')[0], 10);
    if (Number.isFinite(major) && major >= 13) return `11 (${platformVersion})`;
    if (Number.isFinite(major) && major >= 10) return `10 (${platformVersion})`;
    const ntVersion = matchVersion(userAgent, /Windows NT\s([\d.]+)/);
    if (ntVersion === '10.0') return '10/11';
    const labels = { '6.3': '8.1', '6.2': '8', '6.1': '7' };
    return labels[ntVersion] || (ntVersion ? `NT ${ntVersion}` : '');
}

export async function sha256Hex(value, cryptoObject = globalThis.crypto) {
    if (!cryptoObject?.subtle) return '';
    const bytes = new TextEncoder().encode(String(value || ''));
    const digest = await cryptoObject.subtle.digest('SHA-256', bytes);
    return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

function collectBasicAttributes(navigatorObject, windowObject) {
    const screenObject = windowObject?.screen || {};
    return {
        platform: String(navigatorObject.platform || ''),
        vendor: String(navigatorObject.vendor || ''),
        mobile: Boolean(navigatorObject.userAgentData?.mobile || /Mobile|Android|iPhone|iPad/i.test(navigatorObject.userAgent || '')),
        hardwareConcurrency: numberOrNull(navigatorObject.hardwareConcurrency),
        deviceMemoryGb: numberOrNull(navigatorObject.deviceMemory),
        maxTouchPoints: numberOrNull(navigatorObject.maxTouchPoints),
        cookieEnabled: navigatorObject.cookieEnabled !== false,
        doNotTrack: String(navigatorObject.doNotTrack || ''),
        pdfViewerEnabled: navigatorObject.pdfViewerEnabled === true,
        screen: {
            width: numberOrNull(screenObject.width),
            height: numberOrNull(screenObject.height),
            availableWidth: numberOrNull(screenObject.availWidth),
            availableHeight: numberOrNull(screenObject.availHeight),
            colorDepth: numberOrNull(screenObject.colorDepth),
            pixelDepth: numberOrNull(screenObject.pixelDepth),
            pixelRatio: numberOrNull(windowObject?.devicePixelRatio),
        },
        input: {
            touch: Number(navigatorObject.maxTouchPoints || 0) > 0,
            coarsePointer: mediaMatches(windowObject, '(pointer: coarse)'),
            reducedMotion: mediaMatches(windowObject, '(prefers-reduced-motion: reduce)'),
            darkMode: mediaMatches(windowObject, '(prefers-color-scheme: dark)'),
        },
    };
}

async function collectClientHints(navigatorObject) {
    const userAgentData = navigatorObject.userAgentData;
    if (!userAgentData) return { available: false, value: {}, signals: ['CLIENT_HINTS_UNAVAILABLE'] };
    const base = {
        brands: normalizeBrands(userAgentData.brands),
        mobile: Boolean(userAgentData.mobile),
        platform: String(userAgentData.platform || ''),
    };
    if (typeof userAgentData.getHighEntropyValues !== 'function') {
        return { available: true, value: base, signals: ['HIGH_ENTROPY_HINTS_UNAVAILABLE'] };
    }
    try {
        const highEntropy = await userAgentData.getHighEntropyValues([
            'architecture', 'bitness', 'formFactors', 'fullVersionList', 'model',
            'platformVersion', 'uaFullVersion', 'wow64',
        ]);
        return {
            available: true,
            value: {
                ...base,
                architecture: String(highEntropy.architecture || ''),
                bitness: String(highEntropy.bitness || ''),
                formFactors: stringArray(highEntropy.formFactors, 10),
                fullVersionList: normalizeBrands(highEntropy.fullVersionList),
                model: String(highEntropy.model || ''),
                platformVersion: String(highEntropy.platformVersion || ''),
                uaFullVersion: String(highEntropy.uaFullVersion || ''),
                wow64: highEntropy.wow64 === true,
            },
            signals: [],
        };
    } catch {
        return { available: true, value: base, signals: ['HIGH_ENTROPY_HINTS_BLOCKED'] };
    }
}

function collectCanvasFingerprint(documentObject) {
    if (!documentObject?.createElement) return { value: '', signals: ['CANVAS_UNAVAILABLE'] };
    try {
        const canvas = documentObject.createElement('canvas');
        canvas.width = 280;
        canvas.height = 80;
        const context = canvas.getContext('2d');
        if (!context) return { value: '', signals: ['CANVAS_UNAVAILABLE'] };
        context.textBaseline = 'alphabetic';
        context.fillStyle = '#f60';
        context.fillRect(8, 8, 120, 46);
        context.fillStyle = '#069';
        context.font = '17px Arial';
        context.fillText('漳州工单 e-report 1.0', 12, 34);
        context.globalCompositeOperation = 'multiply';
        context.fillStyle = 'rgba(40,180,99,0.72)';
        context.beginPath();
        context.arc(162, 34, 24, 0, Math.PI * 2);
        context.fill();
        return { value: canvas.toDataURL('image/png'), signals: [] };
    } catch {
        return { value: '', signals: ['CANVAS_BLOCKED'] };
    }
}

function collectWebGlFingerprint(documentObject) {
    if (!documentObject?.createElement) return { value: {}, fingerprintSource: '', signals: ['WEBGL_UNAVAILABLE'] };
    try {
        const canvas = documentObject.createElement('canvas');
        const gl = canvas.getContext('webgl2') || canvas.getContext('webgl');
        if (!gl) return { value: {}, fingerprintSource: '', signals: ['WEBGL_UNAVAILABLE'] };
        const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
        const value = {
            vendor: debugInfo ? String(gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL) || '') : '',
            renderer: debugInfo ? String(gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) || '') : '',
            version: String(gl.getParameter(gl.VERSION) || ''),
            shadingLanguageVersion: String(gl.getParameter(gl.SHADING_LANGUAGE_VERSION) || ''),
            maxTextureSize: numberOrNull(gl.getParameter(gl.MAX_TEXTURE_SIZE)),
            maxRenderBufferSize: numberOrNull(gl.getParameter(gl.MAX_RENDERBUFFER_SIZE)),
        };
        return {
            value,
            fingerprintSource: stableStringify(value),
            signals: debugInfo ? [] : ['WEBGL_RENDERER_MASKED'],
        };
    } catch {
        return { value: {}, fingerprintSource: '', signals: ['WEBGL_BLOCKED'] };
    }
}

async function collectAudioFingerprint(runtime) {
    const OfflineContext = runtime.OfflineAudioContext || runtime.webkitOfflineAudioContext;
    if (!OfflineContext) return { value: '', signals: ['WEB_AUDIO_UNAVAILABLE'] };
    try {
        const context = new OfflineContext(1, 4096, 44100);
        const oscillator = context.createOscillator();
        const compressor = context.createDynamicsCompressor();
        oscillator.type = 'triangle';
        oscillator.frequency.value = 10000;
        compressor.threshold.value = -50;
        compressor.knee.value = 40;
        compressor.ratio.value = 12;
        compressor.attack.value = 0;
        compressor.release.value = 0.25;
        oscillator.connect(compressor);
        compressor.connect(context.destination);
        oscillator.start(0);
        const rendered = await withTimeout(context.startRendering(), 700);
        const samples = rendered.getChannelData(0);
        const sample = [];
        for (let index = 0; index < samples.length; index += 64) sample.push(samples[index].toFixed(8));
        return { value: sample.join(','), signals: [] };
    } catch {
        return { value: '', signals: ['WEB_AUDIO_BLOCKED'] };
    }
}

function collectFonts(documentObject) {
    if (!documentObject?.createElement || !documentObject.body) {
        return { detected: [], signals: ['FONT_PROBE_UNAVAILABLE'] };
    }
    const testText = 'mmmmmmmmmmlli漳州工单0123456789';
    const baseFonts = ['monospace', 'sans-serif', 'serif'];
    const probe = documentObject.createElement('span');
    probe.textContent = testText;
    probe.style.cssText = 'position:absolute;left:-10000px;top:-10000px;font-size:72px;visibility:hidden;white-space:nowrap';
    documentObject.body.appendChild(probe);
    try {
        const baseline = Object.fromEntries(baseFonts.map(base => {
            probe.style.fontFamily = base;
            return [base, [probe.offsetWidth, probe.offsetHeight]];
        }));
        const detected = FONT_CANDIDATES.filter(font => baseFonts.some(base => {
            probe.style.fontFamily = `"${font}",${base}`;
            return probe.offsetWidth !== baseline[base][0] || probe.offsetHeight !== baseline[base][1];
        }));
        return { detected, signals: [] };
    } catch {
        return { detected: [], signals: ['FONT_PROBE_BLOCKED'] };
    } finally {
        probe.remove();
    }
}

function collectLocale(navigatorObject) {
    const resolved = Intl.DateTimeFormat().resolvedOptions();
    return {
        language: String(navigatorObject.language || ''),
        languages: stringArray(navigatorObject.languages, 10),
        locale: String(resolved.locale || navigatorObject.language || ''),
        timeZone: String(resolved.timeZone || ''),
        calendar: String(resolved.calendar || ''),
        numberingSystem: String(resolved.numberingSystem || ''),
        timeZoneOffsetMinutes: new Date().getTimezoneOffset(),
    };
}

function collectAutomationSignals(navigatorObject, runtime, userAgent) {
    const signals = [];
    if (navigatorObject.webdriver === true) signals.push('WEBDRIVER');
    if (/HeadlessChrome|PhantomJS|SlimerJS/i.test(userAgent)) signals.push('HEADLESS_USER_AGENT');
    if (runtime.callPhantom || runtime._phantom || runtime.__nightmare || runtime.domAutomation) signals.push('AUTOMATION_GLOBAL');
    if (!navigatorObject.languages?.length) signals.push('LANGUAGES_EMPTY');
    if (!/Mobile|Android|iPhone|iPad/i.test(userAgent) && navigatorObject.plugins?.length === 0) signals.push('DESKTOP_PLUGINS_EMPTY');
    return {
        signals,
        attributes: {
            webdriver: navigatorObject.webdriver === true,
            pluginCount: numberOrNull(navigatorObject.plugins?.length),
            mimeTypeCount: numberOrNull(navigatorObject.mimeTypes?.length),
            automationGlobalDetected: signals.includes('AUTOMATION_GLOBAL'),
        },
    };
}

async function minimalEnvironment(signal, runtime = globalThis) {
    const userAgent = String(runtime.navigator?.userAgent || '');
    const browser = parseBrowser(userAgent);
    const operatingSystem = parseOperatingSystem(userAgent);
    const fingerprintHash = await sha256Hex(stableStringify({ schema: WEB_ENVIRONMENT_SCHEMA, userAgent, signal }), runtime.crypto);
    return {
        schemaVersion: 1,
        schema: WEB_ENVIRONMENT_SCHEMA,
        fingerprintHash: HASH_PATTERN.test(fingerprintHash) ? fingerprintHash : '0'.repeat(64),
        browserName: browser.name,
        browserVersion: browser.version,
        osName: operatingSystem.name,
        osVersion: operatingSystem.version,
        deviceName: /Mobile|Android|iPhone|iPad/i.test(userAgent) ? '移动浏览器' : '桌面浏览器',
        manufacturer: '',
        model: extractAndroidModel(userAgent),
        localeTag: String(runtime.navigator?.language || ''),
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || '',
        appVersion: webAppVersion(),
        canvasHash: '',
        webglHash: '',
        audioHash: '',
        fontHash: '',
        securitySignals: [signal],
        highRiskSignals: [],
        attributes: { schema: WEB_ENVIRONMENT_SCHEMA, capabilities: {}, securitySignals: [signal] },
    };
}

function stableStringify(value) {
    if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
    if (value && typeof value === 'object') {
        return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`;
    }
    return JSON.stringify(value ?? null);
}

function normalizeBrands(value) {
    return (Array.isArray(value) ? value : [])
        .filter(item => item && !/Not.?A.?Brand/i.test(String(item.brand || '')))
        .slice(0, 10)
        .map(item => ({ brand: String(item.brand || '').slice(0, 80), version: String(item.version || '').slice(0, 50) }));
}

function normalizeBrowserBrand(brand) {
    if (/Edge/i.test(brand)) return 'Microsoft Edge';
    if (/Chrome/i.test(brand)) return 'Google Chrome';
    return String(brand || 'Chromium');
}

function extractAndroidModel(userAgent) {
    const match = String(userAgent || '').match(/Android[^;)]*;\s*(?:[a-z]{2}[-_][a-z]{2};\s*)?([^;)]+?)(?:\s+Build\/|[;)])/i);
    return match ? match[1].trim().slice(0, 120) : '';
}

function matchVersion(value, pattern) {
    return String(value || '').match(pattern)?.[1] || '';
}

function mediaMatches(windowObject, query) {
    try { return windowObject?.matchMedia?.(query)?.matches === true; } catch { return false; }
}

function numberOrNull(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
}

function stringArray(value, maximumLength) {
    return (Array.isArray(value) ? value : []).slice(0, maximumLength).map(item => String(item).slice(0, 80));
}

function uniqueSorted(values) {
    return [...new Set(values.filter(Boolean).map(value => String(value).slice(0, 50)))].sort();
}

async function hashOptional(value) {
    return value ? sha256Hex(value) : '';
}

function withTimeout(promise, timeoutMs) {
    return Promise.race([
        promise,
        new Promise((_, reject) => globalThis.setTimeout(() => reject(new Error('timeout')), timeoutMs)),
    ]);
}

function webAppVersion() {
    return import.meta.env?.VITE_APP_VERSION || '';
}
