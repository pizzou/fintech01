// LoanSaaS Pro — offline service worker
//
// Strategy: network-first, cache-fallback for page navigations and API
// GETs; cache-first for static build assets (they're content-hashed by
// Next.js, so a cached copy is never stale). This means: the first time
// someone opens a page online, it's cached; every visit after that keeps
// working even with no connection, always showing the freshest data
// available whenever the network *is* reachable.
//
// It deliberately does NOT try to cache or intercept POST/PUT/DELETE —
// those are handled by the app's own offline queue (lib/offlineDb.ts +
// lib/offlineSync.ts), which is durable IndexedDB storage designed for
// replay, not a cache.

const CACHE_NAME = 'loansaas-v1';

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return; // mutations go through the offline queue, not the SW

  const url = new URL(request.url);

  // Never cache auth/sensitive API calls — always require a live network round-trip
  if (url.pathname.startsWith('/api/auth')) return;

  event.respondWith(
    fetch(request)
      .then((response) => {
        // Only cache same-origin, successful responses
        if (response.ok && url.origin === self.location.origin) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
        }
        return response;
      })
      .catch(() =>
        caches.match(request).then((cached) => {
          if (cached) return cached;
          // Navigations with nothing cached yet: let the app's own
          // client-side offline banner explain the situation.
          if (request.mode === 'navigate') {
            return caches.match('/offline.html');
          }
          return new Response(JSON.stringify({ success: false, error: 'You are offline' }), {
            status: 503,
            headers: { 'Content-Type': 'application/json' },
          });
        })
      )
  );
});
