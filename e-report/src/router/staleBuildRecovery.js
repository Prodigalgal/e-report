const RECOVERY_KEY = 'e_report_build_recovery_v1';
const CHUNK_ERROR = /Failed to fetch dynamically imported module|Importing a module script failed|Loading (?:CSS )?chunk .+ failed|Unable to preload CSS/i;

function versionParts(value) {
    return typeof value === 'string' && /^[1-9]\d*\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(value)
        ? value.split('.').map(Number) : null;
}

function newerThan(candidate, current) {
    for (let index = 0; index < current.length; index++) {
        if (candidate[index] !== current[index]) return candidate[index] > current[index];
    }
    return false;
}

export function isChunkLoadError(error) {
    return error instanceof Error && (error.name === 'ChunkLoadError' || CHUNK_ERROR.test(error.message));
}

export async function recoverStaleBuild(error, { currentVersion, fetchRelease, storage, reload }) {
    const current = versionParts(currentVersion);
    if (!isChunkLoadError(error) || !current) return false;
    try {
        const response = await fetchRelease();
        if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) return false;
        const release = await response.json();
        const published = versionParts(release.version);
        if (!published || !newerThan(published, current) || !/^[a-f0-9]{40}$/.test(release.sourceSha)) return false;
        const recovery = `${currentVersion}:${release.version}`;
        if (storage.getItem(RECOVERY_KEY) === recovery) return false;
        storage.setItem(RECOVERY_KEY, recovery);
        reload();
        return true;
    } catch {
        // Keep the diagnosed error screen and manual retry if detection or the loop guard is unavailable.
        return false;
    }
}
