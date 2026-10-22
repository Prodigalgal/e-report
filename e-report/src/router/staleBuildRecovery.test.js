import assert from 'node:assert/strict';
import test from 'node:test';
import { recoverStaleBuild } from './staleBuildRecovery.js';

const staleChunk = () => new TypeError('Failed to fetch dynamically imported module: https://test.invalid/assets/page-old.js');
function fixture(release = { version: '6.8.3', sourceSha: 'a'.repeat(40) }) {
    const values = new Map();
    let reloads = 0;
    let fetches = 0;
    return {
        options: {
            currentVersion: '6.8.2',
            fetchRelease: async () => { fetches++; return Response.json(release); },
            storage: { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value) },
            reload: () => { reloads++; },
        },
        reloads: () => reloads,
        fetches: () => fetches,
    };
}

test('reloads a removed page module only once for a confirmed newer deployed build', async () => {
    const state = fixture();
    assert.equal(await recoverStaleBuild(staleChunk(), state.options), true);
    assert.equal(await recoverStaleBuild(staleChunk(), state.options), false);
    assert.equal(state.reloads(), 1);
});

test('does not reload ordinary application errors or invalid current versions', async () => {
    const state = fixture();
    assert.equal(await recoverStaleBuild(new Error('Invalid work order state'), state.options), false);
    assert.equal(await recoverStaleBuild(staleChunk(), { ...state.options, currentVersion: 'development' }), false);
    assert.equal(state.fetches(), 0);
});

test('does not reload the same or an older deployment or invalid release metadata', async () => {
    for (const release of [
        { version: '6.8.2', sourceSha: 'a'.repeat(40) },
        { version: '6.8.1', sourceSha: 'a'.repeat(40) },
        { version: '6.9', sourceSha: 'a'.repeat(40) },
        { version: '6.8.3', sourceSha: '' },
    ]) {
        const state = fixture(release);
        assert.equal(await recoverStaleBuild(staleChunk(), state.options), false);
        assert.equal(state.reloads(), 0);
    }
});

test('retains manual recovery for offline, HTML fallback and unavailable storage', async () => {
    for (const override of [
        { fetchRelease: async () => { throw new TypeError('Offline'); } },
        { fetchRelease: async () => new Response('<html/>', { headers: { 'content-type': 'text/html' } }) },
        { fetchRelease: async () => new Response('', { status: 503 }) },
        { storage: { getItem: () => { throw new Error('Storage disabled'); } } },
        { storage: { getItem: () => null, setItem: () => { throw new Error('Storage quota'); } } },
    ]) {
        const state = fixture();
        assert.equal(await recoverStaleBuild(staleChunk(), { ...state.options, ...override }), false);
        assert.equal(state.reloads(), 0);
    }
});
