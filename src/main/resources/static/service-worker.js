const CACHE_NAME = 'jsp-pwa-cache-v1';
const urlsToCache = [
  '/',
  '/css/fontawesome.css',
  '/css/mdb.min.css',
  '/css/style.css',
  '/js/mdb.min.js',
  '/manifest.json',
  '/images/favicon/favicon.ico'
];

// Install event
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(urlsToCache))
  );
});

// Fetch event - cache all static resources, let navigation go to server
self.addEventListener('fetch', event => {
  // Skip cross-origin requests
  if (!event.request.url.startsWith(self.location.origin)) {
    return;
  }

  // For navigation requests, always go to network
  if (event.request.mode === 'navigate') {
    event.respondWith(fetch(event.request));
    return;
  }

  // Cache all static resources (CSS, JS, images, fonts, icons, etc.)
  if (isStaticResource(event.request.url)) {
    event.respondWith(
      caches.match(event.request)
        .then(response => {
          if (response) {
            return response;
          }
          return fetch(event.request).then(networkResponse => {
            // Cache the response for future use
            if (networkResponse.ok) {
              const responseClone = networkResponse.clone();
              caches.open(CACHE_NAME).then(cache => {
                cache.put(event.request, responseClone);
              });
            }
            return networkResponse;
          });
        })
    );
  } else {
    // For API calls and other dynamic requests, go to network
    event.respondWith(fetch(event.request));
  }
});

// Helper function to identify static resources
function isStaticResource(url) {
  const staticExtensions = [
    '.css', '.js', '.png', '.jpg', '.jpeg', '.gif', '.svg', '.ico',
    '.woff', '.woff2', '.ttf', '.eot', '.json', '.webmanifest'
  ];

  const staticPaths = [
    '/css/', '/js/', '/images/', '/webfonts/', '/icons/',
    '/favicon', '/apple-touch-icon', '/manifest.json', '/service-worker.js'
  ];

  return staticExtensions.some(ext => url.includes(ext)) ||
         staticPaths.some(path => url.includes(path));
}
