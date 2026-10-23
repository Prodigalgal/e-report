export function deriveAndroidVersionMetadata(versionName, edition = 'NATIVE', compatibleRuntime) {
    if (!['NATIVE', 'LITE'].includes(edition)) throw new Error('客户端类型无效。');
    const match = /^([1-9]\d*)\.(0|[1-9]\d{0,2})\.(0|[1-9]\d{0,2})$/.exec(String(versionName || '').trim());
    if (!match) throw new Error('版本号必须使用 x.y.z 格式，且主版本号从 1 开始。');
    const major = Number(match[1]);
    const minor = Number(match[2]);
    const patch = Number(match[3]);
    const versionCode = (major - 1) * 1_000_000 + minor * 1_000 + patch;
    if (!Number.isSafeInteger(versionCode) || versionCode <= 0 || versionCode > 2_147_483_647) {
        throw new Error('版本号超出 Android 构建号可用范围。');
    }
    const normalizedVersionName = `${major}.${minor}.${patch}`;
    let runtimeVersion = `android-${edition === 'LITE' ? 'lite-' : ''}${normalizedVersionName}-native${versionCode}`;
    if (edition === 'NATIVE' && compatibleRuntime) {
        const runtime = /^android-([0-9]+\.[0-9]+\.[0-9]+)-native([0-9]+)$/.exec(compatibleRuntime.trim());
        if (!runtime) throw new Error('原生兼容运行时格式不正确。');
        const identity = deriveAndroidVersionMetadata(runtime[1]);
        if (String(identity.versionCode) !== runtime[2] || identity.versionCode > versionCode) {
            throw new Error('原生兼容运行时构建号不一致或高于安装包版本。');
        }
        runtimeVersion = compatibleRuntime.trim();
    }
    return {
        versionName: normalizedVersionName,
        versionCode,
        runtimeVersion,
    };
}
