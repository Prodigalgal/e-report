const CACHE_NAME = 'e-report-app-shell-0742b1b3c6e0';
const BRAND_REVISION = '0742b1b3c6e0';
const APP_BASE = '/';
const STATIC_ASSETS = [
  APP_BASE,
  ...[
    'manifest.webmanifest',
    'favicon.svg',
    'e-report-logo.svg',
    'icons/pwa-icon-192.png',
    'icons/pwa-icon-512.png',
    'icons/pwa-maskable-512.png',
  ].map(path => `${APP_BASE}${path}?v=${BRAND_REVISION}`),
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(STATIC_ASSETS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((cacheNames) => Promise.all(
        cacheNames
          .filter((cacheName) => cacheName !== CACHE_NAME)
          .map((cacheName) => caches.delete(cacheName))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') {
    return;
  }

  const requestUrl = new URL(request.url);
  if (requestUrl.origin !== self.location.origin) {
    return;
  }
  if (requestUrl.pathname === '/api' || requestUrl.pathname.startsWith('/api/')) {
    return;
  }
  if (requestUrl.pathname === '/download/android') {
    return;
  }
  if (requestUrl.pathname === `${APP_BASE}service-worker.js`) {
    return;
  }

  if (request.mode === 'navigate' || request.headers.get('accept')?.includes('text/html')) {
    event.respondWith(networkFirst(request, APP_BASE));
    return;
  }

  if (requestUrl.pathname.startsWith(`${APP_BASE}assets/`) || requestUrl.pathname.startsWith(`${APP_BASE}icons/`)) {
    event.respondWith(cacheFirst(request));
    return;
  }

  if (requestUrl.pathname.startsWith(APP_BASE)) {
    event.respondWith(networkFirst(request));
  }
});

function putCache(request, response) {
  if (!response || !response.ok) {
    return response;
  }
  const responseToCache = response.clone();
  caches.open(CACHE_NAME).then((cache) => cache.put(request, responseToCache));
  return response;
}

function cacheFirst(request) {
  return caches.match(request).then((cachedResponse) => {
    if (cachedResponse) {
      return cachedResponse;
    }
    return fetch(request).then((networkResponse) => putCache(request, networkResponse));
  });
}

function networkFirst(request, fallbackUrl) {
  return fetch(request)
    .then((networkResponse) => putCache(request, networkResponse))
    .catch(async () => {
      const cachedResponse = await caches.match(request);
      if (cachedResponse) {
        return cachedResponse;
      }
      if (fallbackUrl) {
        const fallbackResponse = await caches.match(fallbackUrl);
        if (fallbackResponse) {
          return fallbackResponse;
        }
      }
      return Response.error();
    });
}
