// AppSphere Service Worker v1.2.0
// Bump CACHE_NAME ad ogni modifica: l'handler 'activate' cancella le cache
// vecchie. Le NAVIGAZIONI/HTML vanno SEMPRE in rete (mai dalla cache del SW),
// così l'app non resta mai su un app-launcher vecchio (login rotto).
const CACHE_NAME = 'appsphere-v3';

// Solo asset statici nella cache (NON l'HTML).
const PRECACHE = [
  '/manifest.json',
  '/icons/icon-192.png',
  '/icons/icon-512.png'
];

// ── Install: pre-cacha solo gli asset statici ──────────────────────────────
self.addEventListener('install', function(event) {
  event.waitUntil(
    caches.open(CACHE_NAME).then(function(cache) {
      return cache.addAll(PRECACHE);
    })
  );
  self.skipWaiting();
});

// ── Activate: rimuove TUTTE le cache vecchie ───────────────────────────────
self.addEventListener('activate', function(event) {
  event.waitUntil(
    caches.keys().then(function(keys) {
      return Promise.all(
        keys.filter(function(k) { return k !== CACHE_NAME; })
            .map(function(k) { return caches.delete(k); })
      );
    }).then(function() { return self.clients.claim(); })
  );
});

// ── Fetch ──────────────────────────────────────────────────────────────────
self.addEventListener('fetch', function(event) {
  var req = event.request;
  var url = req.url;

  // Supabase/Google/CDN: passa senza intercettare.
  if (url.includes('supabase.co') ||
      url.includes('googleapis.com') ||
      url.includes('googlefonts') ||
      url.includes('jsdelivr.net') ||
      url.includes('cdnjs.cloudflare.com')) {
    return;
  }

  // NAVIGAZIONI e HTML: sempre rete, mai cache (niente shell stale).
  var isHtml = req.mode === 'navigate' ||
               url.endsWith('/') ||
               url.endsWith('.html') ||
               (req.headers.get('accept') || '').includes('text/html');
  if (isHtml) {
    event.respondWith(fetch(req));
    return;
  }

  // Altri GET (asset statici): network-first, fallback cache.
  event.respondWith(
    fetch(req)
      .then(function(response) {
        if (response && response.status === 200 && req.method === 'GET') {
          var clone = response.clone();
          caches.open(CACHE_NAME).then(function(cache) { cache.put(req, clone); });
        }
        return response;
      })
      .catch(function() {
        return caches.match(req);
      })
  );
});
