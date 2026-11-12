#!/usr/bin/env node
// Pelipe.online — genera los archivos estáticos del sitio a partir de las películas publicadas en Firestore.
//   node build.mjs
// Crea/actualiza:
//   catalogo.json      → lo lee el inicio (cero lecturas de Firestore por visita)
//   p/<id>/index.html  → una página por película (Google la indexa; vista previa al compartir)
//   sitemap.xml, robots.txt
//   index.html         → solo lo que está entre <!--build:head--> y <!--/build:head-->, y la URL de og:image
// Sin dependencias: necesita Node 18 o superior. Lee las películas por la API pública de Firestore
// (las reglas del README ya permiten leer "peliculas" sin iniciar sesión).
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const rd = f => fs.readFileSync(path.join(ROOT, f), 'utf8');
const wr = (f, s) => { fs.mkdirSync(path.dirname(path.join(ROOT, f)), { recursive: true }); fs.writeFileSync(path.join(ROOT, f), s); };

// --- configuración (config.js) ---
const sandbox = { window: {} };
vm.runInNewContext(rd('config.js'), sandbox);
const CFG = sandbox.window.CONFIG || {};
const projectId = process.env.FIREBASE_PROJECT_ID || (CFG.FIREBASE && CFG.FIREBASE.projectId);
if (!projectId) { console.error('Falta FIREBASE.projectId en config.js'); process.exit(1); }

function siteUrl() {
  let u = process.env.SITE_URL || CFG.SITE_URL || '';
  if (!u && process.env.GITHUB_REPOSITORY) {
    const [owner, repo] = process.env.GITHUB_REPOSITORY.split('/');
    u = repo.toLowerCase() === `${owner.toLowerCase()}.github.io` ? `https://${owner}.github.io` : `https://${owner}.github.io/${repo}`;
  }
  return u.replace(/\/+$/, '');
}
const SITE = siteUrl();

// --- Firestore REST → objetos simples ---
const val = v => {
  if ('stringValue' in v) return v.stringValue;
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return v.doubleValue;
  if ('booleanValue' in v) return v.booleanValue;
  if ('timestampValue' in v) return Date.parse(v.timestampValue);
  if ('nullValue' in v) return null;
  if ('arrayValue' in v) return (v.arrayValue.values || []).map(val);
  if ('mapValue' in v) return obj(v.mapValue.fields || {});
  return null;
};
const obj = f => Object.fromEntries(Object.entries(f).map(([k, v]) => [k, val(v)]));

async function fetchAll() {
  const base = (process.env.FIRESTORE_URL || 'https://firestore.googleapis.com').replace(/\/+$/, '');
  const out = []; let token = '';
  do {
    const u = `${base}/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/peliculas?pageSize=300${token ? '&pageToken=' + encodeURIComponent(token) : ''}`;
    const r = await fetch(u);
    if (!r.ok) throw new Error(`Firestore respondió ${r.status}: ${(await r.text()).slice(0, 200)}`);
    const j = await r.json();
    for (const d of j.documents || []) out.push({ id: d.name.split('/').pop(), ...obj(d.fields || {}) });
    token = j.nextPageToken || '';
  } while (token);
  return out;
}

// --- utilidades ---
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const https = u => { try { const x = new URL(String(u || '')); return x.protocol === 'https:' ? x.href : ''; } catch { return ''; } };
const gens = p => String(p.generos || '').split(/[,/;]/).map(s => s.trim()).filter(Boolean);
const cut = (s, n) => { s = String(s || '').replace(/\s+/g, ' ').trim(); return s.length > n ? s.slice(0, n - 1).replace(/\s+\S*$/, '') + '…' : s; };
const LIC = { propia: 'Obra propia', publico: 'Dominio público', cc: 'Creative Commons', permiso: 'Con permiso del titular' };
const dur = m => { const n = Number(m); return n > 0 ? `${Math.floor(n / 60) ? Math.floor(n / 60) + ' h ' : ''}${n % 60 ? (n % 60) + ' min' : ''}`.trim() : ''; };
const iso = m => { const n = Number(m); return n > 0 ? `PT${Math.floor(n / 60)}H${n % 60}M` : ''; };
const visible = p => !p.publicarEn || p.publicarEn <= Date.now();
const safeId = id => /^[A-Za-z0-9_-]+$/.test(id);

// --- página de una película ---
const logo = (rd('index.html').match(/<a class="logo"[\s\S]*?<\/a>/) || ['<a class="logo" href="#/">Pelipe.online</a>'])[0].replace(/href="[^"]*"/, 'href="../../"');

function page(p) {
  const title = `${p.titulo}${p.anio ? ` (${p.anio})` : ''} — Ver película | Pelipe.online`;
  const desc = cut(p.sinopsis, 155) || `Mira “${p.titulo}” completa en Pelipe.online.`;
  const img = https(p.poster) || https(p.fondo);
  const url = SITE ? `${SITE}/p/${encodeURIComponent(p.id)}/` : '';
  const play = `../../#/ver/${encodeURIComponent(p.id)}`;
  const ld = {
    '@context': 'https://schema.org', '@type': 'Movie', name: p.titulo, description: cut(p.sinopsis, 500) || undefined,
    image: img || undefined, datePublished: p.anio || undefined, duration: iso(p.duracion) || undefined,
    genre: gens(p).length ? gens(p) : undefined, inLanguage: undefined, isAccessibleForFree: true, url: url || undefined,
    potentialAction: SITE ? { '@type': 'WatchAction', target: `${SITE}/#/ver/${encodeURIComponent(p.id)}` } : undefined
  };
  const quals = ['HD'];
  const chips = [p.anio, dur(p.duracion), ...quals, ...gens(p)].filter(Boolean).map(x => `<span class="chip">${esc(x)}</span>`).join('');
  return `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#0b0b10">
<title>${esc(title)}</title>
<meta name="description" content="${esc(desc)}">
${url ? `<link rel="canonical" href="${esc(url)}">\n<meta property="og:url" content="${esc(url)}">` : ''}
<meta property="og:type" content="video.movie">
<meta property="og:site_name" content="Pelipe.online">
<meta property="og:title" content="${esc(p.titulo)}${p.anio ? ` (${esc(p.anio)})` : ''}">
<meta property="og:description" content="${esc(desc)}">
${img ? `<meta property="og:image" content="${esc(img)}">` : ''}
<meta name="twitter:card" content="${img ? 'summary_large_image' : 'summary'}">
<link rel="icon" href="../../icon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="../../apple-touch-icon.png">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Bebas+Neue&display=swap">
<link rel="stylesheet" href="../../styles.css">
<style>.film{display:grid;grid-template-columns:minmax(0,230px) 1fr;gap:28px;max-width:980px;margin:0 auto}.film img{width:100%;aspect-ratio:2/3;object-fit:cover;border-radius:var(--r);background:var(--bg2)}.film h1{font-size:clamp(1.7rem,4.5vw,2.8rem);line-height:1.1;margin-bottom:10px}.film p{color:#d3d3e0;margin:14px 0;max-width:68ch}.film .lic{color:var(--mute);font-size:.9rem}@media(max-width:640px){.film{grid-template-columns:1fr}.film img{max-width:200px}}</style>
<script type="application/ld+json">${JSON.stringify(ld).replace(/</g, '\\u003c')}</script>
</head>
<body>
<header class="top">${logo}</header>
<main class="wrap">
<article class="film">
${img ? `<img src="${esc(img)}" alt="Póster de ${esc(p.titulo)}" width="230" height="345">` : '<div></div>'}
<div>
<h1>${esc(p.titulo)}</h1>
<div class="chips">${chips}</div>
<p>${esc(p.sinopsis || '')}</p>
${p.creditos ? `<p class="lic">${esc(p.creditos)}</p>` : ''}
<p><a class="btn" href="${play}">▶ Ver película</a> &nbsp; <a class="btn ghost" href="../../#/catalogo">Más películas</a></p>
</div>
</article>
</main>
<footer><div class="foot-l"><p>Pelipe.online es un proyecto personal sin fines de lucro. Todas las películas publicadas son obras propias, de dominio público o con licencia libre.</p></div></footer>
</body>
</html>
`;
}

// --- ejecución ---
const all = (await fetchAll()).sort((a, b) => (b.creado || 0) - (a.creado || 0));
const pub = all.filter(visible).filter(p => p.titulo && safeId(p.id));

// catalogo.json: incluye programadas (la app las oculta hasta su hora, igual que con Firestore)
wr('catalogo.json', JSON.stringify({ peliculas: all.filter(p => safeId(p.id)) }));

// Próximas: estrenos populares que se acercan (TMDB). Se actualiza una vez al día; sin clave de TMDB se omite.
async function proximos() {
  const key = (process.env.TMDB_API_KEY || CFG.TMDB_API_KEY || '').trim();
  if (!key) return console.log('Próximas: sin clave de TMDB, se omite (ver README).');
  const hoy = new Date().toISOString().slice(0, 10);
  try { if (JSON.parse(rd('proximos.json')).actualizado === hoy) return console.log('Próximas: ya actualizadas hoy.'); } catch { }
  const base = (process.env.TMDB_URL || 'https://api.themoviedb.org/3').replace(/\/+$/, '');
  const headers = key.length > 40 ? { Authorization: `Bearer ${key}` } : {};
  const api = async (p, q = {}) => {
    const u = new URL(base + p);
    for (const [k, v] of Object.entries({ language: CFG.LANG || 'es-MX', ...q })) u.searchParams.set(k, v);
    if (!headers.Authorization) u.searchParams.set('api_key', key);
    const r = await fetch(u, { headers });
    if (!r.ok) throw new Error(`TMDB ${p} → ${r.status}`);
    return r.json();
  };
  try {
    const hasta = new Date(Date.now() + 180 * 864e5).toISOString().slice(0, 10);
    const [gl, d] = await Promise.all([
      api('/genre/movie/list'),
      api('/discover/movie', { include_adult: 'false', sort_by: 'popularity.desc', 'primary_release_date.gte': hoy, 'primary_release_date.lte': hasta, page: '1' })
    ]);
    const gm = new Map((gl.genres || []).map(g => [g.id, g.name]));
    const out = [];
    for (const m of (d.results || []).filter(m => m.poster_path && m.title && m.release_date).slice(0, 20)) {
      let trailer = '';
      try {
        const v = (await api(`/movie/${m.id}/videos`, { include_video_language: 'es,en,null' })).results || [];
        const yt = v.filter(x => x.site === 'YouTube' && x.type === 'Trailer' && /^[\w-]{6,20}$/.test(x.key));
        trailer = (yt.find(x => x.iso_639_1 === 'es') || yt[0] || {}).key || '';
      } catch { }
      out.push({ id: m.id, titulo: m.title, fecha: m.release_date, sinopsis: cut(m.overview, 400), poster: 'https://image.tmdb.org/t/p/w342' + m.poster_path, generos: (m.genre_ids || []).map(i => gm.get(i)).filter(Boolean).slice(0, 3).join(', '), trailer });
    }
    if (out.length) { wr('proximos.json', JSON.stringify({ actualizado: hoy, peliculas: out })); console.log(`Próximas: ${out.length} estreno(s).`); }
    else console.warn('Próximas: TMDB no devolvió estrenos; se conserva el archivo anterior.');
  } catch (e) { console.warn('Próximas: no se pudo actualizar (' + e.message + '); se conserva el archivo anterior.'); }
}
await proximos();

// páginas por película (se regeneran enteras: la carpeta p/ es 100 % generada)
fs.rmSync(path.join(ROOT, 'p'), { recursive: true, force: true });
for (const p of pub) wr(`p/${p.id}/index.html`, page(p));

// sitemap + robots
if (SITE) {
  const d = m => new Date(m || Date.now()).toISOString().slice(0, 10);
  wr('sitemap.xml', `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n  <url><loc>${SITE}/</loc><lastmod>${d(pub[0] && (pub[0].actualizado || pub[0].creado))}</lastmod></url>\n${pub.map(p => `  <url><loc>${SITE}/p/${encodeURIComponent(p.id)}/</loc><lastmod>${d(p.actualizado || p.creado)}</lastmod></url>`).join('\n')}\n</urlset>\n`);
  wr('robots.txt', `User-agent: *\nAllow: /\n\nSitemap: ${SITE}/sitemap.xml\n`);
} else console.warn('Aviso: sin SITE_URL (config.js) no se generan sitemap.xml, robots.txt ni enlaces canónicos.');

// index.html: bloque de cabecera + og:image
let html = rd('index.html');
const first = pub[0];
const hero = first && (https(first.fondo) || https(first.poster));
const block = [
  SITE ? `<link rel="canonical" href="${SITE}/">\n  <meta property="og:url" content="${SITE}/">` : '',
  '<link rel="preload" href="catalogo.json" as="fetch" crossorigin="anonymous">',
  // Portada: en el celular se precarga la versión de 780 px (la misma que usa app.js); en pantallas grandes, la de 1280 px
  hero ? (/image\.tmdb\.org\/t\/p\/w\d+\//.test(hero)
    ? `<link rel="preload" href="${esc(hero.replace(/\/t\/p\/w\d+\//, '/t/p/w780/'))}" as="image" fetchpriority="high" media="(max-width: 900px)">\n  <link rel="preload" href="${esc(hero.replace(/\/t\/p\/w\d+\//, '/t/p/w1280/'))}" as="image" fetchpriority="high" media="(min-width: 901px)">`
    : `<link rel="preload" href="${esc(hero)}" as="image" fetchpriority="high">`) : '',
  SITE ? `<script type="application/ld+json">${JSON.stringify({ '@context': 'https://schema.org', '@type': 'WebSite', name: 'Pelipe.online', url: SITE + '/', inLanguage: 'es' })}</script>` : ''
].filter(Boolean).join('\n  ');
html = html.replace(/<!--build:head-->[\s\S]*?<!--\/build:head-->/, `<!--build:head-->\n  ${block}\n  <!--/build:head-->`);
if (SITE) html = html.replace(/(property="og:image" content=")[^"]*(")/, `$1${SITE}/og.jpg$2`);
wr('index.html', html);

console.log(`Listo: ${all.length} película(s) en catalogo.json, ${pub.length} página(s) en p/${SITE ? ', sitemap.xml y robots.txt' : ''}.`);
