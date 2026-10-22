const SERVICE_WORKER_VERSION = 'v4';

export function registerServiceWorker() {
    if (!import.meta.env.PROD || !('serviceWorker' in navigator)) {
        return;
    }

    window.addEventListener('load', () => {
        const serviceWorkerUrl = `${import.meta.env.BASE_URL}service-worker.js?v=${SERVICE_WORKER_VERSION}`;
        navigator.serviceWorker.register(serviceWorkerUrl, {updateViaCache: 'none'}).catch((error) => {
            console.warn('Service worker registration failed:', error);
        });
    });
}
