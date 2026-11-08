// Service worker mínimo: permite instalar Pelipe como app y abrir la estructura del sitio sin conexión.
// Siempre intenta primero la red (así las actualizaciones se ven al instante); la copia guardada solo se usa si no hay conexión.
// Nunca guarda videos ni audio, ni peticiones a Firebase u otros sitios.
const V = 'pelipe-v1';
const SHELL = ['./', 'index.html', 'app.js', 'auth.js', 'config.js', 'styles.css', 'icon.svg'];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(V).then(c => Promise.allSettled(SHELL.map(u => c.add(u)))).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== V).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const r = e.request, u = new URL(r.url);
  if (r.method !== 'GET' || u.origin !== location.origin || r.headers.has('range') || ['video', 'audio'].includes(r.destination)) return;
  e.respondWith(fetch(r).then(res => {
    if (res.ok) { const cp = res.clone(); caches.open(V).then(c => c.put(r, cp)); }
    return res;
  }).catch(() => caches.match(r).then(m => m || caches.match('index.html'))));
});
