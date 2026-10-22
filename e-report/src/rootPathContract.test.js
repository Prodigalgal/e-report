import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import test from 'node:test';

import { API_BASE_URL } from './config.js';

const readProjectFile = relativePath => readFileSync(new URL(relativePath, import.meta.url), 'utf8');

test('品牌入口、PWA 图标和离线缓存随几何资源同时更新', () => {
    const geometry = readProjectFile('../../shared/brand-mark.json').replaceAll('\r\n', '\n').trimEnd();
    const revision = createHash('sha256').update(geometry).digest('hex').slice(0, 12);
    const index = readProjectFile('../index.html');
    const manifest = JSON.parse(readProjectFile('../public/manifest.webmanifest'));
    const worker = readProjectFile('../public/service-worker.js');
    for (const path of ['/e-report-logo.svg', '/favicon.svg', '/icons/pwa-icon-192.png', '/manifest.webmanifest']) {
        assert.ok(index.includes(`${path}?v=${revision}`));
    }
    for (const icon of [...manifest.icons, ...manifest.shortcuts.flatMap(shortcut => shortcut.icons)]) {
        assert.ok(icon.src.endsWith(`?v=${revision}`));
    }
    assert.ok(worker.includes(`const CACHE_NAME = 'e-report-app-shell-${revision}';`));
    assert.ok(worker.includes(`const BRAND_REVISION = '${revision}';`));
});

test('WEB 使用根路径并只通过 /api 访问后端', () => {
    const manifest = JSON.parse(readProjectFile('../public/manifest.webmanifest'));
    const index = readProjectFile('../index.html');
    const nginx = readProjectFile('../nginx.conf');
    const runtimeDockerfile = readProjectFile('../../deploy/dedicated-cloud/Dockerfile.frontend-runtime');
    const serviceWorker = readProjectFile('../public/service-worker.js');

    assert.equal(API_BASE_URL, '/api');
    assert.equal(manifest.start_url, '/');
    assert.equal(manifest.scope, '/');
    assert.doesNotMatch(index, /(?:href|src)="\/e-report(?:\/|")/);
    assert.doesNotMatch(nginx, /proxy_pass[^\n;]*\/e-report(?:-backend)?\//);
    assert.match(nginx, /location \^~ \/api\//);
    assert.match(nginx, /location = \/download\/android/);
    assert.match(nginx, /location ~ \^\/\(\?:e-report\|e-report-backend\)/);
    assert.match(nginx, /return 410;/);
    assert.match(nginx, /map \$http_x_envoy_external_address \$trusted_client_address/);
    assert.match(nginx, /proxy_set_header X-Forwarded-For \$trusted_client_address/);
    assert.doesNotMatch(nginx, /proxy_set_header X-Forwarded-For \$remote_addr/);
    assert.match(serviceWorker, /pathname\.startsWith\('\/api\/'\)/);
    assert.match(serviceWorker, /pathname === '\/download\/android'/);
    assert.match(runtimeDockerfile, /COPY dist \/usr\/share\/nginx\/html(?:\r?\n)/);
    assert.doesNotMatch(runtimeDockerfile, /COPY dist \/usr\/share\/nginx\/html\/e-report/);
});
