'use strict';
// Cache the launcher only. Cross-origin website content remains online.
const CACHE = 'superyau-launcher-v1';
const FILES = ['./', './index.html', './app.js', './install.html', './install.js', './manifest.webmanifest', './icons/icon-192.png', './icons/icon-512.png', './icons/maskable-512.png'];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('superyau-launcher-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin) return;
  // Unknown paths are not cached, avoiding accidental unbounded cache growth.
  event.respondWith(fetch(event.request).catch(() => caches.match(event.request).then(response => response || Response.error())));
});
