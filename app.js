import { Cloud } from './auth.js';

(() => {
  const C = window.CONFIG || {};
  const app = document.getElementById('app');
  const modal = document.getElementById('modal');
  const acct = document.getElementById('acct');

  /* ---------- utilidades ---------- */
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const httpsUrl = u => /^https:\/\//i.test(u || '') ? u : '';
  const norm = s => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const gensOf = p => String(p.generos || '').split(',').map(s => s.trim()).filter(Boolean);
  const cap = s => s.charAt(0).toUpperCase() + s.slice(1);
  const hasTmdb = () => !!(C.TMDB_API_KEY && C.TMDB_API_KEY.trim());
  const ms = t => (t && typeof t.toMillis === 'function') ? t.toMillis() : Number(t) || 0;
  const fmtDur = m => { m = Number(m); if (!m) return ''; const h = Math.floor(m / 60), r = m % 60; return h ? `${h} h${r ? ' ' + r + ' min' : ''}` : `${r} min`; };
  const fmtT = s => { s = Math.floor(s); const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60; return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(x).padStart(2, '0'); };
  const G_SVG ='<svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg>';

  let toastT;
  function toast(msg) {
    let t = document.getElementById('toast');
    if (!t) { t = document.createElement('div'); t.id = 'toast'; document.body.appendChild(t); }
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(toastT);
    toastT = setTimeout(() => t.classList.remove('show'), 3800);
  }

  /* ---------- favoritas y listas del usuario ---------- */
  // Con sesión iniciada se guardan en la nube (Firestore); sin sesión, en el navegador.
  const LS_KEY = 'pelipe:v1';
  const LS_REC = 'pelipe:recientes';
  const LS_CAT = 'pelipe:catalogo';
  const LS_SUB = 'pelipe:subtitulo';
  const LS_RANK = 'pelipe:ranking2';
  const LS_BK = 'pelipe:copia';
  const lsGet = k => { try { return localStorage.getItem(k); } catch { return null; } };
  const lsSet = (k, v) => { try { localStorage.setItem(k, v); } catch { } };
  const blank = () => ({ favoritos: [], progreso: [], listas: [{ id: 'ver-despues', nombre: 'Ver más tarde', items: [] }] });
  const mini = p => ({ id: p.id, titulo: p.titulo || '', poster: p.poster || '', anio: p.anio || '', calidad: p.calidad || '', calidades: Array.isArray(p.calidades) ? p.calidades : [], generos: p.generos || '' });
  function normalize(d) {
    const b = blank();
    if (!d || typeof d !== 'object') return b;
    const ok = a => (Array.isArray(a) ? a : []).filter(m => m && m.titulo);
    const out = {
      favoritos: ok(d.favoritos),
      progreso: ok(d.progreso).filter(m => m.d > 0 || m.sig).map(m => m.ep ? { ...m, ep: { t: Number(m.ep.t) || 1, e: Number(m.ep.e) || 1 } } : m).slice(0, 20),
      listas: Array.isArray(d.listas) ? d.listas.map(l => ({ id: String(l.id), nombre: String(l.nombre || 'Lista'), items: ok(l.items) })) : []
    };
    if (!out.listas.some(l => l.id === 'ver-despues')) out.listas.unshift(b.listas[0]);
    return out;
  }
  const readLocal = () => { try { return normalize(JSON.parse(localStorage.getItem(LS_KEY))); } catch { return blank(); } };
  const writeLocal = d => { try { localStorage.setItem(LS_KEY, JSON.stringify(d)); } catch { } };
  const clearLocal = () => { try { localStorage.removeItem(LS_KEY); } catch { } };
  const isEmpty = d => !d.favoritos.length && !d.progreso.length && d.listas.every(l => !l.items.length) && d.listas.length <= 1;
  function merge(a, b) {
    const out = normalize(a);
    for (const m of b.favoritos) if (!out.favoritos.some(x => String(x.id) === String(m.id))) out.favoritos.push(m);
    for (const m of b.progreso) {
      const x = out.progreso.find(y => String(y.id) === String(m.id));
      if (!x) out.progreso.push(m);
      else if ((m.ts || 0) > (x.ts || 0)) Object.assign(x, m);
    }
    out.progreso = out.progreso.sort((p, q) => (q.ts || 0) - (p.ts || 0)).slice(0, 20);
    for (const l of b.listas) {
      const t = out.listas.find(x => x.id === l.id);
      if (!t) out.listas.push(l);
      else for (const m of l.items) if (!t.items.some(x => String(x.id) === String(m.id))) t.items.push(m);
    }
    return out;
  }
  const recents = {
    get() { try { return JSON.parse(localStorage.getItem(LS_REC)) || []; } catch { return []; } },
    push(p) { try { localStorage.setItem(LS_REC, JSON.stringify([p.id, ...this.get().filter(x => x !== p.id)].slice(0, 20))); } catch { } }
  };

  let user = null, data = readLocal(), cloudReady = true, saveTimer, warned = false;
  let leave = null, pager = null, lastWatched = null;

  // quiet = guardado automático (progreso de reproducción): sin avisos
  function persist(quiet) {
    if (user) {
      if (!cloudReady) { if (!quiet) toast('No hay conexión con tu cuenta; los cambios no se guardarán. Recarga la página.'); return; }
      clearTimeout(saveTimer);
      saveTimer = setTimeout(() => Cloud.save(user.uid, data).catch(() => toast('No se pudo guardar en la nube. Revisa tu conexión.')), 500);
    } else {
      writeLocal(data);
      if (!quiet && Cloud.enabled && !warned) { warned = true; toast('Guardado en este navegador. Inicia sesión para conservarlo en la nube.'); }
    }
  }
  const same = (a, b) => String(a) === String(b);
  const isFav = id => data.favoritos.some(m => same(m.id, id));
  function toggleFav(p) {
    data.favoritos = isFav(p.id) ? data.favoritos.filter(x => !same(x.id, p.id)) : [mini(p), ...data.favoritos];
    persist();
  }
  function toggleInList(lid, p) {
    const l = data.listas.find(x => x.id === lid);
    if (!l) return;
    l.items = l.items.some(x => same(x.id, p.id)) ? l.items.filter(x => !same(x.id, p.id)) : [mini(p), ...l.items];
    persist();
  }
  // Progreso de reproducción ("Continuar viendo"): solo para videos propios (.mp4/.webm/HLS), donde se puede medir
  const progOf = id => data.progreso.find(m => same(m.id, id));
  function putProgress(p, t, d, ep) {
    const m = { ...mini(p), t: Math.floor(t), d: Math.floor(d), ts: Date.now() };
    if (ep) m.ep = { t: Number(ep.t) || 1, e: Number(ep.e) || 1 };
    data.progreso = [m, ...data.progreso.filter(x => !same(x.id, p.id))].slice(0, 20);
  }
  const dropProgress = id => { data.progreso = data.progreso.filter(x => !same(x.id, id)); };
  function createList(nombre) {
    const l = { id: 'l' + Date.now().toString(36), nombre: nombre.trim().slice(0, 40), items: [] };
    data.listas.push(l);
    persist();
    return l;
  }

  /* ---------- cuenta y roles ---------- */
  // Administrador: correos en config.js. Editores: los que el administrador agrega desde el panel.
  const isAdmin = () => !!(user && user.email && (C.ADMIN_EMAILS || []).some(e => String(e).toLowerCase() === user.email.toLowerCase()));
  let editor = false;
  const canPublish = () => isAdmin() || editor;

  function renderAcct() {
    document.getElementById('navadmin').hidden = !canPublish();
    if (!Cloud.enabled) { acct.innerHTML = ''; return; }
    acct.innerHTML = user
      ? `${user.photoURL ? `<img class="avatar" src="${esc(user.photoURL)}" alt="" referrerpolicy="no-referrer">` : ''}<span class="uname">${esc((user.displayName || user.email || '').split(' ')[0])}</span><button class="btn ghost sm logout">Salir</button>`
      : `<button class="gbtn login" type="button" aria-label="Iniciar sesión con Google">${G_SVG}<span>Iniciar sesión con Google</span></button>`;
  }
  function note() {
    if (Cloud.enabled && !user) return `<div class="notice slim"><p>Inicia sesión con Google para guardar tus favoritas y listas en la nube y verlas desde cualquier dispositivo.</p><p style="margin-top:10px"><button class="gbtn login" type="button" aria-label="Iniciar sesión con Google">${G_SVG}<span>Iniciar sesión con Google</span></button></p></div>`;
    if (!Cloud.enabled) return '<p class="muted">Tus favoritas y listas se guardan solo en este navegador.</p>';
    return '';
  }

  /* ---------- catálogo ---------- */
  const LICENCIAS = { propia: 'Obra propia', publico: 'Dominio público', cc: 'Creative Commons', permiso: 'Con permiso del titular' };
  const REPORTES = { no_carga: 'El video no carga', se_traba: 'Se corta, se traba o va lento', sin_audio: 'No se escucha / sin audio', audio_mal: 'Audio incorrecto o desfasado', subs_mal: 'Subtítulos mal, desfasados o no aparecen', imagen: 'Mala calidad de imagen', otro: 'Otro problema con la película' };
  const QORD = ['HD']; // una sola calidad: HD (las calidades antiguas —4K, 1080p, 720p…— pasan a HD)
  const nq = () => 'HD';
  const IDIOMAS = { lat: 'Latino (México)', en: 'English', ensub: 'English (sub. español)' };
  const qRank = q => { const i = QORD.indexOf(q); return i < 0 ? 9 : i; };
  // Toda película se muestra como HD (las antiguas con otra calidad guardada siguen funcionando)
  const qualsOf = () => ['HD'];
  // Servidores de video, con su idioma. Sin servidores guardados, se usa el enlace único de siempre.
  function serversOf(p) {
    const has = Array.isArray(p.servidores) && p.servidores.some(x => x && x.u);
    const raw = has ? p.servidores.filter(x => x && x.u) : p.url ? [{ q: nq(p.calidad), i: '', n: '', u: p.url }] : [];
    return raw.map((x, k) => ({ q: nq(x.q), i: IDIOMAS[x.i] ? x.i : '', n: String(x.n || ''), u: x.u, k })).sort((a, b) => qRank(a.q) - qRank(b.q) || a.k - b.k);
  }
  // Banderas en SVG (los emojis de bandera no se ven en Windows)
  const FLAGS = (() => {
    const dots = []; for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) dots.push(`<circle cx="${1.8 + x * 2.2}" cy="${1.5 + y * 1.8}" r=".45"/>`);
    const stripes = [0, 2.46, 4.92, 7.38, 9.85, 12.31, 14.77].map(y => `<rect y="${y}" width="24" height="1.23" fill="#b22234"/>`).join('');
    return {
      en: `<svg class="flag" viewBox="0 0 24 16" role="img" aria-label="English"><rect width="24" height="16" fill="#fff"/>${stripes}<rect width="10.4" height="8.6" fill="#3c3b6e"/><g fill="#fff">${dots.join('')}</g></svg>`,
      lat: `<svg class="flag" viewBox="0 0 24 16" role="img" aria-label="México"><rect width="8" height="16" fill="#006847"/><rect x="8" width="8" height="16" fill="#fff"/><rect x="16" width="8" height="16" fill="#ce1126"/><circle cx="12" cy="8" r="2.1" fill="#9b7a3a" opacity=".85"/></svg>`
    };
  })();
  FLAGS.ensub = FLAGS.en;
  const flagOf = i => FLAGS[i] || '';
  const qBadges = (p, cls = 'badge q') => qualsOf(p).map(q => `<span class="${cls}">${esc(q)}</span>`).join('');
  let cat = null, catT = 0, catS = '';
  const visible = p => !p.publicarEn || p.publicarEn <= Date.now(); // programadas: ocultas hasta su hora
  // Las series se guardan junto a las películas (tipo:'serie'); aquí se separan: catalog() = solo películas, seriesCat() = solo series
  const isSerie = p => !!p && p.tipo === 'serie';
  async function catalog(force) { return (await catalogRaw(force)).filter(visible).filter(p => !isSerie(p)); }
  async function seriesCat(force) { return (await catalogRaw(force)).filter(visible).filter(isSerie); }
  // Temporadas y episodios de una serie, ordenados y sin vacíos
  const epsOf = p => (Array.isArray(p && p.temporadas) ? p.temporadas : [])
    .map((t, i) => ({ n: Number(t.n) || i + 1, episodios: (Array.isArray(t.episodios) ? t.episodios : []).map((e, j) => ({ n: Number(e.n) || j + 1, titulo: String(e.titulo || ''), servidores: e.servidores })).filter(e => serversOf(e).length).sort((a, b) => a.n - b.n) }))
    .filter(t => t.episodios.length).sort((a, b) => a.n - b.n);
  const countEps = p => epsOf(p).reduce((a, t) => a + t.episodios.length, 0);
  // El público lee catalogo.json (archivo estático que genera build.mjs): cero lecturas de Firestore por visita.
  // Si no existe o está vacío, se usa Firestore como respaldo. Quien puede publicar siempre ve los datos en vivo.
  async function staticCat() {
    try {
      const r = await fetch('catalogo.json');
      if (!r.ok) return null;
      const j = await r.json();
      return j && Array.isArray(j.peliculas) && j.peliculas.length ? j.peliculas : null;
    } catch { return null; }
  }
  async function catalogRaw(force) {
    const live = canPublish(); // equipo: nunca usar la copia estática
    const okSrc = s => !(live && s === 'json');
    if (!force && cat && Date.now() - catT < 60000 && okSrc(catS)) return cat;
    // Caché de 5 min en el navegador: abrir el sitio de nuevo no vuelve a pedir todo el catálogo
    if (!force) { try { const c = JSON.parse(lsGet(LS_CAT)); if (c && Array.isArray(c.l) && Date.now() - c.t < 300000 && okSrc(c.s)) { cat = c.l; catS = c.s || 'cloud'; catT = Date.now(); return cat; } } catch { } }
    let l = null, s = 'cloud';
    if (!force && !live) { l = await staticCat(); if (l) s = 'json'; }
    if (!l) l = await Cloud.listPeliculas();
    cat = l.sort((a, b) => (b.creado || 0) - (a.creado || 0));
    catS = s; catT = Date.now();
    lsSet(LS_CAT, JSON.stringify({ t: catT, s, l: cat }));
    return cat;
  }
  const bust = () => { cat = null; try { localStorage.removeItem(LS_CAT); } catch { } };
  function genreMap(list) {
    const m = new Map();
    for (const p of list) for (const g of gensOf(p).slice(0, 1)) { // solo el primer género: cada película va en una sola categoría
      const k = norm(g);
      if (!m.has(k)) m.set(k, { name: cap(g), items: [] });
      m.get(k).items.push(p);
    }
    return m;
  }
  const needCloud = () => '<div class="notice"><h2>Falta activar la nube</h2><p>Las películas se guardan en Firebase. Configura <code>FIREBASE</code> en <code>config.js</code> (pasos en el README).</p></div>';
  const failCloud = e => { app.innerHTML = `<div class="notice"><h2>No se pudo cargar el catálogo</h2><p>${esc(e.code || e.message)}</p></div>`; };

  // Imágenes de TMDB al tamaño que se necesita (en el celular, más pequeñas = carga más rápida)
  const SMALL = !!(window.matchMedia && matchMedia('(max-width: 900px)').matches);
  const tmdbImg = (u, w) => /^https:\/\/image\.tmdb\.org\/t\/p\/w\d+\//.test(u || '') ? u.replace(/\/t\/p\/w\d+\//, `/t/p/w${w}/`) : u;
  const cardLocal = p => `<a class="card" href="${p.href ? esc(p.href) : '#/ver/' + encodeURIComponent(p.id)}"><div class="poster">${httpsUrl(p.poster) ? `<img loading="lazy" src="${esc(tmdbImg(p.poster, SMALL ? 342 : 500))}" alt="${esc(p.titulo)}">` : ''}<i class="pl" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M8.5 5.6v12.8a.8.8 0 0 0 1.2.7l10.2-6.4a.8.8 0 0 0 0-1.4L9.7 4.9a.8.8 0 0 0-1.2.7z"/></svg></i>${gensOf(p)[0] ? `<div class="badge">${esc(cap(gensOf(p)[0]))}</div>` : ''}${isSerie(p) ? `<div class="qs"><span class="badge q">${p.tag ? esc(p.tag) : 'SERIE'}</span></div>` : qualsOf(p).length ? `<div class="qs">${qBadges(p)}</div>` : ''}${p.pct ? `<div class="prog"><i style="width:${Math.max(4, Math.min(100, Math.round(p.pct)))}%"></i></div>` : ''}${p.rank ? `<div class="rank">${p.rank}</div>` : ''}</div><b>${esc(p.titulo)}</b><span>${esc(p.anio || '')}${p.votos ? ` · ♥ ${p.votos}` : ''}</span></a>`;
  const rowLocal = (title, items, link) => items.length ? `<section class="row"><div class="row-h"><h2>${esc(title)}</h2>${link ? `<a href="${link}">Ver todo →</a>` : ''}</div><div class="scroller">${items.map(cardLocal).join('')}</div></section>` : '';
  const gridLocal = items => `<div class="grid">${items.map(cardLocal).join('')}</div>`;
  const gridRm = (items, attrs) => `<div class="grid">${items.map(p => `<div class="rm-wrap">${cardLocal(p)}<button class="rm" data-id="${esc(p.id)}" ${attrs}>Quitar</button></div>`).join('')}</div>`;
  const loading = () => { app.innerHTML = '<div class="wrap"><div class="skel"></div></div>'; };
  const setNav = r => document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('on', a.dataset.r === r));

  function openModal(html) {
    modal.querySelector('.modal-body').innerHTML = html;
    modal.hidden = false;
    wireVideo(modal);
  }
  function closeModal() {
    modal.hidden = true;
    modal.querySelector('.modal-body').innerHTML = '';
  }
  modal.addEventListener('click', e => { if (e.target === modal || e.target.closest('.modal-x')) closeModal(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });

  /* ---------- reproductor ---------- */
  // Reconoce el enlace pegado y devuelve cómo reproducirlo. Solo https y proveedores conocidos.
  function parseSource(raw) {
    let u; try { u = new URL(String(raw).trim()); } catch { return null; }
    if (u.protocol !== 'https:') return null;
    const h = u.hostname.replace(/^www\./, '');
    const ytId = id => /^[\w-]{6,20}$/.test(id || '') ? { tipo: 'youtube', id } : null;
    if (h === 'youtu.be') return ytId(u.pathname.slice(1));
    if (h === 'youtube.com' || h === 'm.youtube.com') {
      if (u.pathname === '/watch') return ytId(u.searchParams.get('v'));
      const m = u.pathname.match(/^\/(embed|shorts|live)\/([\w-]+)/);
      if (m) return ytId(m[2]);
    }
    if (h === 'vimeo.com' || h === 'player.vimeo.com') {
      const m = u.pathname.match(/\/(?:video\/)?(\d+)(?:\/([a-z0-9]+))?/i);
      if (m) return { tipo: 'vimeo', id: m[1], hash: (m[2] || u.searchParams.get('h') || '').replace(/[^a-z0-9]/gi, '') };
    }
    if (h === 'archive.org') {
      const m = u.pathname.match(/^\/(?:details|embed)\/([^/?#]+)/);
      if (m) return { tipo: 'archive', id: m[1] };
    }
    // Otras plataformas de video con reproductor para incrustar (se convierte el enlace normal al de incrustación)
    const emb = src => ({ tipo: 'embed', src, host: new URL(src).hostname });
    let m;
    if (h === 'dailymotion.com' || h === 'dai.ly') {
      m = h === 'dai.ly' ? u.pathname.match(/^\/([a-z0-9]+)/i) : u.pathname.match(/^\/(?:embed\/)?video\/([a-z0-9]+)/i);
      if (m) return emb(`https://www.dailymotion.com/embed/video/${m[1]}`);
    }
    if (h === 'drive.google.com' && (m = u.pathname.match(/^\/file\/d\/([\w-]+)/))) return emb(`https://drive.google.com/file/d/${m[1]}/preview`);
    if (h === 'streamable.com' && (m = u.pathname.match(/^\/(?:e\/)?([a-z0-9]+)$/i))) return emb(`https://streamable.com/e/${m[1]}`);
    if (h === 'rumble.com' && (m = u.pathname.match(/^\/embed\/(v[a-z0-9]+)/i))) return emb(`https://rumble.com/embed/${m[1]}/`);
    if (h === 'odysee.com') {
      m = u.pathname.match(/^\/\$\/embed\/([\w%.-]+)\/([a-f0-9]+)/i);
      if (!m) { const k = (u.pathname.split('/').filter(Boolean).pop() || '').match(/^([\w%.-]+):([a-f0-9]+)$/i); if (k) m = [0, k[1], k[2]]; }
      if (m) return emb(`https://odysee.com/$/embed/${m[1]}/${m[2]}`);
    }
    if (h === 'iframe.mediadelivery.net' && (m = u.pathname.match(/^\/(?:embed|play)\/(\d+)\/([\w-]+)/))) return emb(`https://iframe.mediadelivery.net/embed/${m[1]}/${m[2]}`);
    if (h === 'iframe.videodelivery.net' && (m = u.pathname.match(/^\/([a-f0-9]{32})/i))) return emb(`https://iframe.videodelivery.net/${m[1]}`);
    if (/^[a-z0-9-]+\.cloudflarestream\.com$/.test(h) && (m = u.pathname.match(/^\/([a-f0-9]{32})\/(?:iframe|watch)/i))) return emb(`https://${h}/${m[1]}/iframe`);
    if (/\.m3u8$/i.test(u.pathname)) return { tipo: 'hls', url: u.href };
    if (/\.(mp4|webm|ogv|m4v)$/i.test(u.pathname)) return { tipo: 'file', url: u.href };
    // Sitios que tú autorizas expresamente en config.js (EMBED_HOSTS): se incrustan con permisos reducidos (sandbox)
    const extra = (C.EMBED_HOSTS || []).map(x => String(x).toLowerCase().replace(/^https?:\/\//, '').replace(/^www\./, '').replace(/[/?#].*$/, '')).filter(Boolean);
    if (extra.some(x => h === x || h.endsWith('.' + x))) return { tipo: 'embed', src: u.href, host: h, sandbox: true };
    return null;
  }
  // Imagen del reproductor en pausa: la imagen horizontal de fondo (cubre el reproductor); si no hay, el póster
  const coverOf = p => httpsUrl(p.fondo) ? tmdbImg(p.fondo, SMALL ? 780 : 1280) : p.poster;
  function playerHtml(s, poster) {
    if (!s) return '<p class="empty">El enlace de este video no es válido.</p>';
    const frame = (u, sb) => `<div class="player${/^https:\/\/drive\.google\.com\//.test(u) ? ' tall' : ''}"><iframe src="${esc(u)}" allow="autoplay; fullscreen; picture-in-picture; encrypted-media" allowfullscreen ${sb ? 'sandbox="allow-scripts allow-same-origin allow-presentation allow-forms"' : ''} referrerpolicy="strict-origin-when-cross-origin" title="Reproductor"></iframe></div>`;
    if (s.tipo === 'embed') return frame(s.src, s.sandbox);
    if (s.tipo === 'youtube') return frame(`https://www.youtube-nocookie.com/embed/${s.id}?rel=0`);
    if (s.tipo === 'vimeo') return frame(`https://player.vimeo.com/video/${s.id}${s.hash ? '?h=' + s.hash : ''}`);
    if (s.tipo === 'archive') return frame(`https://archive.org/embed/${encodeURIComponent(s.id)}`);
    const p = httpsUrl(poster) ? ` poster="${esc(poster)}"` : '';
    return `<div class="player"><video controls controlslist="nodownload" playsinline preload="metadata"${p} ${s.tipo === 'hls' ? `data-hls="${esc(s.url)}"` : `src="${esc(s.url)}"`}></video></div>`;
  }
  // Enlaces auxiliares (subtítulos): https o ruta relativa del propio sitio, p. ej. subs/es.srt
  const safeAsset = u => {
    u = String(u || '').trim();
    if (/^https:\/\//i.test(u)) return u;
    if (/^\w[\w\-./ %]*$/.test(u) && !u.includes('..') && !u.includes('//')) { try { return new URL(u, location.href).href; } catch { } }
    return '';
  };
  // Líneas "Nombre | enlace" del panel de publicar
  function parseLines(txt, valid) {
    const out = [];
    let bad = 0;
    for (const l of String(txt || '').split('\n').map(s => s.trim()).filter(Boolean)) {
      const i = l.indexOf('|');
      const n = i < 0 ? '' : l.slice(0, i).trim().slice(0, 40), u = i < 0 ? '' : l.slice(i + 1).trim();
      if (n && valid(u)) out.push({ n, u }); else bad++;
    }
    return { ok: out.slice(0, 8), bad };
  }
  const linesOf = arr => (Array.isArray(arr) ? arr : []).map(x => `${x.n} | ${x.u}`).join('\n');
  // .srt → WebVTT (el navegador solo entiende VTT)
  function srtToVtt(txt) {
    txt = txt.replace(/^﻿/, '').replace(/\r\n?/g, '\n').trim();
    if (/^WEBVTT/.test(txt)) return txt + '\n';
    txt = txt.replace(/(\d{1,2}:\d{2}:\d{2}),(\d{1,3})/g, (m, a, b) => (a.length < 8 ? '0' : '') + a + '.' + b.padEnd(3, '0')).replace(/\{\\an?\d\}/g, '');
    return 'WEBVTT\n\n' + txt + '\n';
  }
  async function fetchSub(u) {
    const r = await fetch(u);
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const buf = await r.arrayBuffer();
    let t = new TextDecoder('utf-8').decode(buf);
    if (t.includes('�')) t = new TextDecoder('windows-1252').decode(buf); // .srt antiguos suelen venir en Latin-1
    return srtToVtt(t);
  }
  let hlsP = null;
  const loadHls = () => hlsP || (hlsP = new Promise((res, rej) => {
    if (window.Hls) return res(window.Hls);
    const s = document.createElement('script');
    s.src = 'https://cdn.jsdelivr.net/npm/hls.js@1.5.17';
    s.onload = () => res(window.Hls);
    s.onerror = () => { hlsP = null; rej(new Error('hls')); };
    document.head.appendChild(s);
  }));
  async function mountSource(v, s, st) {
    if (st.hls) { st.hls.destroy(); st.hls = null; }
    if (s.tipo === 'file') { v.src = s.url; return; }
    if (v.canPlayType('application/vnd.apple.mpegurl')) { v.src = s.url; return; }
    const Hls = await loadHls();
    if (!Hls || !Hls.isSupported()) throw new Error('hls');
    const h = new Hls();
    st.hls = h;
    h.loadSource(s.url);
    h.attachMedia(v);
  }

  // Progreso: se guarda cada ~20 s, al pausar y al salir de la página; se borra al terminar la película
  function trackProgress(v, p, ep, next) {
    let lastFlush = 0;
    // Al terminar: película → se quita; episodio → "Continuar viendo" pasa al siguiente (si hay)
    const finish = () => {
      if (ep && next) data.progreso = [{ ...mini(p), t: 0, d: 1, sig: 1, ep: { t: next.t, e: next.e }, ts: Date.now() }, ...data.progreso.filter(x => !same(x.id, p.id))].slice(0, 20);
      else dropProgress(p.id);
    };
    const mem = () => {
      const d = v.duration, t = v.currentTime;
      if (!isFinite(d) || d < 120 || t < 15) return false;
      if (d - t < 45) finish(); else putProgress(p, t, d, ep);
      return true;
    };
    const flush = () => { if (mem()) persist(true); lastFlush = Date.now(); };
    v.addEventListener('timeupdate', () => { if (!v.paused && Date.now() - lastFlush > 20000) flush(); });
    v.addEventListener('pause', flush);
    v.addEventListener('ended', () => { finish(); persist(true); });
    leave = flush;
  }

  // Conecta el <video>: HLS, retomar donde se quedó, subtítulos (.srt/.vtt), audio y progreso.
  async function wireVideo(root, p, opts = {}) {
    const v = root.querySelector('video');
    if (!v) return;
    const st = { hls: null };
    v._st = st; // para poder detener HLS al cambiar de servidor
    const main = p ? parseSource(p.url) : (v.dataset.hls ? { tipo: 'hls', url: v.dataset.hls } : null);
    const bar = root.querySelector('#pbar');
    const ui = { subs: [], audios: [], sub: -1, aud: 0 };

    const setSub = i => {
      ui.sub = i;
      ui.subs.forEach((s, k) => { s.track.mode = k === i ? 'showing' : 'disabled'; });
      lsSet(LS_SUB, i >= 0 ? ui.subs[i].n : 'off');
      const sel = bar && bar.querySelector('#selsub'); if (sel) sel.value = String(i);
    };
    async function switchTo(s) {
      if (!s) return;
      const t = v.currentTime, was = !v.paused;
      try { await mountSource(v, s, st); } catch { toast('No se pudo cambiar el audio.'); return; }
      v.addEventListener('loadedmetadata', () => { try { v.currentTime = t; } catch { } if (was) v.play().catch(() => { }); }, { once: true });
    }
    function renderBar() {
      if (!bar) return;
      const parts = [];
      if (ui.subs.length) parts.push(`<label>Subtítulos <select id="selsub"><option value="-1">Desactivados</option>${ui.subs.map((s, i) => `<option value="${i}">${esc(s.n)}</option>`).join('')}</select></label>`);
      if (ui.audios.length > 1) parts.push(`<label>Audio <select id="selaud">${ui.audios.map((a, i) => `<option value="${i}">${esc(a.n)}</option>`).join('')}</select></label>`);
      // Enviar a la TV: Chromecast (Chrome/Edge, Remote Playback API). Safari trae AirPlay propio. Solo para videos directos (.mp4/.webm), no HLS por MSE
      const castOk = !!v.remote && !v.webkitShowPlaybackTargetPicker && !(v.src || '').startsWith('blob:'); // Safari ya trae su icono de AirPlay en los controles
      if (castOk) parts.push('<button type="button" id="btncast" class="btn ghost" title="Ver en la TV">📺 Ver en la TV</button>');
      bar.hidden = !parts.length;
      bar.innerHTML = parts.join('');
      const bc = bar.querySelector('#btncast');
      if (bc) bc.onclick = () => {
        const t = v.remote.prompt();
        Promise.resolve(t).catch(e => { if (e && e.name !== 'NotAllowedError' && e.name !== 'AbortError') toast('No se encontró ninguna TV o dispositivo cercano.'); });
      };
      const ss = bar.querySelector('#selsub'), sa = bar.querySelector('#selaud');
      if (ss) { ss.value = String(ui.sub); ss.onchange = () => setSub(Number(ss.value)); }
      if (sa) { sa.value = String(ui.aud); sa.onchange = () => { ui.aud = Number(sa.value); ui.audios[ui.aud].apply(); }; }
    }

    // Audio: versiones que sube el administrador, o pistas incluidas en el propio video (HLS / navegador compatible)
    const vers = p && Array.isArray(p.versiones) ? p.versiones.map(x => ({ n: x.n, s: parseSource(x.u) })).filter(x => x.s && ['file', 'hls'].includes(x.s.tipo)) : [];
    if (vers.length) {
      ui.audios = [{ n: (p.audioNombre || '').trim() || 'Original', apply: () => switchTo(main) }, ...vers.map(x => ({ n: x.n, apply: () => switchTo(x.s) }))];
    }
    const nativeAudio = () => {
      if (vers.length) return;
      const at = v.audioTracks;
      if (!at || at.length < 2) return;
      ui.audios = [...at].map((t, i) => ({ n: t.label || t.language || 'Audio ' + (i + 1), apply: () => { [...at].forEach((x, k) => { x.enabled = k === i; }); } }));
      ui.aud = [...at].findIndex(t => t.enabled);
      renderBar();
    };
    v.addEventListener('loadedmetadata', nativeAudio);
    renderBar();

    if (main && main.tipo === 'hls') {
      mountSource(v, main, st).then(() => {
        const h = st.hls;
        if (h && window.Hls && !vers.length) h.on(window.Hls.Events.AUDIO_TRACKS_UPDATED, () => {
          if (h.audioTracks.length < 2) return;
          ui.audios = h.audioTracks.map((t, i) => ({ n: t.name || t.lang || 'Audio ' + (i + 1), apply: () => { h.audioTrack = i; } }));
          ui.aud = Math.max(0, h.audioTrack);
          renderBar();
        });
      }).catch(() => toast('Tu navegador no puede reproducir este formato.'));
    }

    // Retomar donde se quedó
    // Al cambiar de calidad/servidor se sigue desde el mismo minuto (opts.startAt) en lugar del guardado
    if (opts.startAt > 0) {
      const go = () => { try { v.currentTime = opts.startAt; } catch { } if (opts.keepPlaying) v.play().catch(() => { }); };
      if (v.readyState >= 1) go(); else v.addEventListener('loadedmetadata', go, { once: true });
    }
    const saved0 = !(opts.startAt > 0) && p && opts.track !== false && progOf(p.id);
    const saved = saved0 && (!opts.ep || (saved0.ep && saved0.ep.t === opts.ep.t && saved0.ep.e === opts.ep.e)) ? saved0 : null;
    if (saved && saved.t > 15) {
      const go = () => {
        if (!isFinite(v.duration) || saved.t >= v.duration - 45) return;
        v.currentTime = saved.t;
        const r = root.querySelector('#resume');
        if (r) {
          r.hidden = false;
          r.innerHTML = `Retomando desde ${fmtT(saved.t)} · <button type="button" class="link">Empezar desde el principio</button>`;
          r.querySelector('button').onclick = () => { v.currentTime = 0; dropProgress(p.id); persist(true); r.hidden = true; };
        }
      };
      if (v.readyState >= 1) go(); else v.addEventListener('loadedmetadata', go, { once: true });
    }

    // Subtítulos opcionales: la persona decide si los activa (se recuerda su última elección)
    const subs = p && Array.isArray(p.subtitulos) ? p.subtitulos : [];
    if (subs.length) {
      v.textTracks.addEventListener('change', () => {
        const i = ui.subs.findIndex(s => s.track.mode === 'showing');
        if (i !== ui.sub) { ui.sub = i; lsSet(LS_SUB, i >= 0 ? ui.subs[i].n : 'off'); const sel = bar && bar.querySelector('#selsub'); if (sel) sel.value = String(i); }
      });
      Promise.allSettled(subs.map(async s => { const u = safeAsset(s.u); if (!u) throw new Error('url'); return { n: s.n, vtt: await fetchSub(u) }; })).then(rs => {
        for (const r of rs) {
          if (r.status !== 'fulfilled') { console.warn('Subtítulo no disponible:', r.reason); continue; }
          const el = document.createElement('track');
          el.kind = 'subtitles';
          el.label = r.value.n;
          el.src = URL.createObjectURL(new Blob([r.value.vtt], { type: 'text/vtt' }));
          v.appendChild(el);
          el.track.mode = 'disabled';
          ui.subs.push({ n: r.value.n, track: el.track });
        }
        renderBar();
        const pref = lsGet(LS_SUB), i = ui.subs.findIndex(s => s.n === pref);
        if (i >= 0) setSub(i);
      });
    }
    renderBar();
    if (p && opts.track !== false) trackProgress(v, p, opts.ep, opts.next);
  }
  const openTrailer = url => { const s = parseSource(url); if (s) openModal(playerHtml(s)); };

  /* ---------- páginas ---------- */
  // Conteo de reacciones por película (total y últimos 7 días) para los rankings del inicio
  // Trabajo de segundo plano (conteos, presencia): se hace cuando la persona interactúa o pasan 20 s,
  // para no saturar la red mientras se pinta la portada
  const onActive = (() => {
    const q = []; let fired = false;
    const go = () => { if (fired) return; fired = true; q.splice(0).forEach(f => { try { f(); } catch { } }); };
    ['pointerdown', 'keydown', 'scroll', 'touchstart'].forEach(ev => addEventListener(ev, go, { once: true, passive: true }));
    setTimeout(go, 20000);
    return f => fired ? f() : q.push(f);
  })();
  let rkPending = null;
  const rankCached = () => { try { const c = JSON.parse(lsGet(LS_RANK)); return !!(c && c.tot && Date.now() - c.t < 1800000); } catch { return false; } };
  async function ranking(list) {
    try { const c = JSON.parse(lsGet(LS_RANK)); if (c && c.tot && Date.now() - c.t < 1800000) return c; } catch { }
    try {
      const since = Date.now() - 7 * 864e5, ids = list.slice(0, 150).map(p => p.id), tot = {}, sem = {};
      for (let i = 0; i < ids.length; i += 10) {
        await Promise.all(ids.slice(i, i + 10).map(async id => { [tot[id], sem[id]] = await Promise.all([Cloud.countAll(id), Cloud.countAll(id, since)]); }));
      }
      const r = { t: Date.now(), tot, sem };
      lsSet(LS_RANK, JSON.stringify(r));
      return r;
    } catch { return null; }
  }
  async function home() {
    setNav('inicio');
    if (!Cloud.enabled) { app.innerHTML = needCloud(); return; }
    loading();
    let list;
    try { list = await catalog(); } catch (e) { return failCloud(e); }
    if (!list.length) {
      app.innerHTML = `<div class="notice"><h2>Todavía no hay películas</h2><p>Cuando se publique la primera, aparecerá aquí lista para reproducir.</p>${canPublish() ? '<p style="margin-top:12px"><a class="btn" href="#/admin">Publicar la primera</a></p>' : ''}</div>`;
      return;
    }
    const slides = list.slice(0, 10); // las 10 publicadas más recientemente
    const byId = id => list.find(p => same(p.id, id));
    // Continuar viendo: solo aparece si la persona dejó algo a medias (videos propios, donde se puede medir)
    let serAll = []; try { serAll = await seriesCat(); } catch { }
    const serRecent = serAll.slice(0, 12);
    const cont = data.progreso.map(m => {
      const s = m.ep ? serAll.find(y => same(y.id, m.id)) : null, x = s || byId(m.id);
      if (!x || !m.d) return null;
      return s ? { ...s, pct: m.sig ? 0 : m.t / m.d * 100, href: `#/ver/${encodeURIComponent(s.id)}?t=${m.ep.t}&e=${m.ep.e}`, tag: `T${m.ep.t} · E${m.ep.e}${m.sig ? ' · siguiente' : ''}` } : { ...x, pct: m.t / m.d * 100 };
    }).filter(Boolean);
    const shuffle = a => { const b = [...a]; for (let i = b.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [b[i], b[j]] = [b[j], b[i]]; } return b; };
    app.innerHTML = `
      <section class="hero slider" id="slider" aria-roledescription="carrusel" aria-label="Películas recientes">
        ${slides.map((f, i) => { const bg = httpsUrl(f.fondo) ? tmdbImg(f.fondo, SMALL ? 780 : 1280) : httpsUrl(f.poster); return `<article class="slide ${i === 0 ? 'on' : ''}" ${i ? 'inert' : ''}>
          ${bg ? `<div class="hero-bg ${httpsUrl(f.fondo) ? '' : 'blur'}" ${i === 0 ? `style="background-image:url('${esc(bg)}')"` : `data-bg="${esc(bg)}"`}></div>` : ''}
          <div class="hero-in">
            <span class="tag">${i === 0 ? 'Lo más nuevo' : 'Recién publicada'}</span>
            <h1>${esc(f.titulo)}</h1>
            <div class="meta-line"><span>${esc(f.anio || '')}</span>${f.duracion ? `<span>${esc(fmtDur(f.duracion))}</span>` : ''}${qBadges(f, 'chip q')}${gensOf(f)[0] ? `<span class="chip">${esc(cap(gensOf(f)[0]))}</span>` : ''}</div>
            <p>${esc(f.sinopsis || '')}</p>
            <div class="actions">
              <a class="btn" href="#/ver/${encodeURIComponent(f.id)}">▶ Ver película</a>
              ${parseSource(f.trailer) ? `<button class="btn ghost htrailer" data-i="${i}">Ver tráiler</button>` : ''}
            </div>
          </div>
        </article>`; }).join('')}
        ${slides.length > 1 ? `<button class="arrow prev" aria-label="Anterior">‹</button><button class="arrow next" aria-label="Siguiente">›</button>
        <div class="dots">${slides.map((_, i) => `<button class="${i === 0 ? 'on' : ''}" aria-label="Película ${i + 1}"></button>`).join('')}</div>` : ''}
      </section>
      <div class="wrap">
        ${rowLocal('Continuar viendo', cont)}
        ${rowLocal('Series recientes', serRecent, '#/series')}
        <div id="rk1"></div>
        <div id="rks"></div>
        <div id="rk2"></div>
        ${rowLocal('Te recomendamos…', shuffle(list).slice(0, 10))}
      </div>`;
    // Rankings por reacciones: se rellenan cuando llegan los conteos (en caché 30 min)
    // Se cuentan películas (100 más recientes) y series (50 más recientes) en la misma pasada
    const rankIn = [...list.slice(0, 100), ...serAll.slice(0, 50)];
    const fillRank = () => ranking(rankIn).then(rk => {
      const r1 = document.getElementById('rk1'), r2 = document.getElementById('rk2'), rs = document.getElementById('rks');
      if (!rk || !r1 || !r2) return;
      const n = (id, k) => (rk[k] && rk[k][id]) || 0;
      const top = (arr, extra) => arr.slice(0, 10).map((x, i) => ({ ...x, rank: i + 1, ...extra(x) }));
      const sem = top([...list].sort((a, b) => n(b.id, 'sem') - n(a.id, 'sem') || n(b.id, 'tot') - n(a.id, 'tot') || (b.creado || 0) - (a.creado || 0)), () => ({}));
      const votadas = top([...list].filter(x => n(x.id, 'tot') > 0).sort((a, b) => n(b.id, 'tot') - n(a.id, 'tot') || (b.creado || 0) - (a.creado || 0)), x => ({ votos: n(x.id, 'tot') }));
      const semS = top([...serAll].sort((a, b) => n(b.id, 'sem') - n(a.id, 'sem') || n(b.id, 'tot') - n(a.id, 'tot') || (b.creado || 0) - (a.creado || 0)), () => ({}));
      r1.innerHTML = rowLocal('Top Pelipe Semanales', sem);
      if (rs) rs.innerHTML = rowLocal('Top Series Semanales', semS, '#/series');
      r2.innerHTML = rowLocal('10 más votadas', votadas);
    });
    rkPending = fillRank; // si la página se vuelve a dibujar antes de que se active, solo cuenta la última versión
    if (rankCached()) fillRank(); else onActive(() => { if (rkPending === fillRank) fillRank(); });
    // Carrusel: rota solo cada 8 s; se pausa al pasar el mouse o con la pestaña oculta
    const sl = document.getElementById('slider'), sd = [...sl.querySelectorAll('.slide')], dt = [...sl.querySelectorAll('.dots button')];
    let cur = 0, tm, hold = false;
    // Las imágenes grandes de las demás diapositivas se piden solo cuando están por mostrarse (la página carga más rápido)
    const load = k => { const d = sd[(k + sd.length) % sd.length].querySelector('[data-bg]'); if (d) { d.style.backgroundImage = `url('${d.dataset.bg}')`; d.removeAttribute('data-bg'); } };
    const show = i => {
      cur = (i + sd.length) % sd.length;
      load(cur - 1); load(cur); load(cur + 1);
      sd.forEach((x, k) => { x.classList.toggle('on', k === cur); x.inert = k !== cur; });
      dt.forEach((x, k) => x.classList.toggle('on', k === cur));
    };
    const auto = () => { clearInterval(tm); if (sd.length > 1) tm = setInterval(() => { if (!document.body.contains(sl)) clearInterval(tm); else if (!hold && document.visibilityState === 'visible') show(cur + 1); }, 8000); };
    // Con ratón se pausa al pasar el cursor; con el dedo se pausa solo mientras se toca (en el celular el "mouseenter" se queda pegado)
    const fine = window.matchMedia && matchMedia('(hover:hover) and (pointer:fine)').matches;
    if (fine) { sl.addEventListener('mouseenter', () => { hold = true; }); sl.addEventListener('mouseleave', () => { hold = false; }); }
    // Deslizar con el dedo: izquierda = siguiente, derecha = anterior
    let tx = 0, ty = 0, tt = 0;
    sl.addEventListener('touchstart', e => { const t = e.touches[0]; tx = t.clientX; ty = t.clientY; tt = Date.now(); hold = true; }, { passive: true });
    sl.addEventListener('touchend', e => {
      hold = false;
      const t = e.changedTouches[0], dx = t.clientX - tx, dy = t.clientY - ty;
      if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy) * 1.4 && Date.now() - tt < 800) { show(cur + (dx < 0 ? 1 : -1)); }
      auto();
    }, { passive: true });
    sl.addEventListener('touchcancel', () => { hold = false; }, { passive: true });
    const prev = sl.querySelector('.prev'), next = sl.querySelector('.next');
    if (prev) prev.onclick = () => { show(cur - 1); auto(); };
    if (next) next.onclick = () => { show(cur + 1); auto(); };
    dt.forEach((x, k) => { x.onclick = () => { show(k); auto(); }; });
    sl.querySelectorAll('.htrailer').forEach(b => { b.onclick = () => openTrailer(slides[Number(b.dataset.i)].trailer); });
    auto();
    setTimeout(() => { if (document.body.contains(sl)) load(1); }, 2500); // la 2.ª se adelanta un poco para que el primer cambio sea suave
  }

  function parseQuery() {
    const q = location.hash.split('?')[1] || '';
    return Object.fromEntries(new URLSearchParams(q));
  }

  async function catalogPage(kind) {
    const SER = kind === 'series', base = SER ? '#/series' : '#/catalogo';
    setNav(SER ? 'series' : 'catalogo');
    if (!Cloud.enabled) { app.innerHTML = needCloud(); return; }
    loading();
    let list;
    try { list = SER ? await seriesCat() : await catalog(); } catch (e) { return failCloud(e); }
    const p = parseQuery();
    const gm = [...genreMap(list).values()].sort((a, b) => a.name.localeCompare(b.name, 'es'));
    const years = [...new Set(list.map(x => String(x.anio || '')).filter(Boolean))].sort().reverse();
    const orden = p.orden || 'recientes';
    const items = list.filter(x =>
      (!p.q || norm([x.titulo, x.sinopsis, x.generos].join(' ')).includes(norm(p.q))) &&
      (!p.genero || norm(gensOf(x)[0] || '') === norm(p.genero)) &&
      (!p.anio || String(x.anio) === p.anio) &&
      (!p.hasta || (Number(x.anio) > 0 && Number(x.anio) <= Number(p.hasta))));
    if (orden === 'az') items.sort((a, b) => a.titulo.localeCompare(b.titulo, 'es'));
    else if (orden === 'anio') items.sort((a, b) => (Number(b.anio) || 0) - (Number(a.anio) || 0));
    const titulo = p.q ? `Resultados para “${esc(p.q)}”` : p.genero ? esc(cap(p.genero)) : p.hasta ? `Clásicos (hasta ${esc(p.hasta)})` : SER ? 'Series' : 'Catálogo Películas';
    app.innerHTML = `<div class="wrap">
      <h1 style="margin-bottom:16px">${titulo}</h1>
      <form class="filters" id="f">
        <input type="hidden" name="q" value="${esc(p.q || '')}">
        <input type="hidden" name="hasta" value="${esc(p.hasta || '')}">
        <select name="genero"><option value="">Género</option>${gm.map(g => `<option value="${esc(g.name)}" ${norm(p.genero) === norm(g.name) ? 'selected' : ''}>${esc(g.name)}</option>`).join('')}</select>
        <select name="anio"><option value="">Año</option>${years.map(y => `<option ${p.anio === y ? 'selected' : ''}>${esc(y)}</option>`).join('')}</select>
        <input type="hidden" name="orden" value="${esc(orden === 'recientes' ? '' : orden)}">
        <div class="sorts" role="group" aria-label="Ordenar por">${[['recientes', SER ? 'Agregadas recientemente' : 'Publicadas recientemente'], ['anio', 'Año de estreno'], ['az', 'Título A–Z']].map(([v, t]) => `<button type="button" class="sort ${orden === v ? 'on' : ''}" data-o="${v}" aria-pressed="${orden === v}">${t}</button>`).join('')}</div>
      </form>
      <p class="muted">${items.length} ${SER ? 'serie' : 'película'}${items.length === 1 ? '' : 's'}</p>
      ${items.length ? '<div class="grid" id="g"></div><div class="more" id="more"></div>' : `<p class="empty">${SER ? (p.q || p.genero || p.anio ? 'No hay series con esos criterios.' : 'Aún no hay series publicadas. Vuelve pronto.') : 'No hay películas con esos criterios.'}</p>`}</div>`;
    // Carga progresiva: 24 a la vez; el resto aparece al acercarte al final (o con el botón)
    if (items.length) {
      const PAGE = 24, g = document.getElementById('g'), more = document.getElementById('more');
      let n = 0, io = null;
      const next = () => {
        const chunk = items.slice(n, n + PAGE);
        n += chunk.length;
        g.insertAdjacentHTML('beforeend', chunk.map(cardLocal).join(''));
        const left = items.length - n;
        if (left > 0) { more.innerHTML = `<button class="btn ghost" type="button">Cargar más (${left})</button>`; more.firstChild.onclick = next; }
        else { more.innerHTML = ''; if (io) io.disconnect(); }
      };
      next();
      if (items.length > PAGE && 'IntersectionObserver' in window) {
        io = pager = new IntersectionObserver(es => { if (es[0].isIntersecting) { next(); if (n < items.length) { io.unobserve(more); io.observe(more); } } }, { rootMargin: '700px' });
        io.observe(more);
      }
    }
    const sorts = app.querySelector('.sorts');
    if (sorts) sorts.addEventListener('click', e => {
      const bt = e.target.closest('.sort'); if (!bt) return;
      const f = document.getElementById('f'); f.elements.orden.value = bt.dataset.o === 'recientes' ? '' : bt.dataset.o;
      f.dispatchEvent(new Event('change', { bubbles: true }));
    });
    document.getElementById('f').addEventListener('change', e => {
      const n = new URLSearchParams();
      for (const [k, v] of new FormData(e.currentTarget)) if (v) n.set(k, v);
      location.hash = base + '?' + n.toString();
    });
  }

  /* ---------- próximas: estrenos populares que se acercan (datos de TMDB, los genera build.mjs) ---------- */
  async function proximas() {
    setNav('proximas');
    loading();
    let J = {};
    try { const r = await fetch('proximos.json'); if (r.ok) J = await r.json() || {}; } catch { }
    const q = parseQuery();
    const SER = q.tipo === 'series', ORD = q.orden === 'fecha' ? 'fecha' : 'pop';
    const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
    const day = x => new Date(x.fecha + 'T12:00:00');
    const clean = a => (Array.isArray(a) ? a : []).filter(x => x && x.titulo && /^\d{4}-\d{2}-\d{2}$/.test(x.fecha || '') && day(x) >= hoy); // el archivo puede tener unas horas de retraso
    const lists = { pelis: clean(J.peliculas), series: clean(J.series) };
    const l = SER ? lists.series : lists.pelis;
    const falta = x => Math.round((day(x) - hoy) / 864e5);
    const when = x => { const n = falta(x); return n <= 0 ? 'Estreno hoy' : n === 1 ? 'Estreno mañana' : `Faltan ${n} días`; };
    const fdate = (x, long) => day(x).toLocaleDateString('es', { day: 'numeric', month: long ? 'long' : 'short', ...(day(x).getFullYear() !== hoy.getFullYear() ? { year: 'numeric' } : {}) });
    const card = (x, i) => `<a class="card" href="#/proximas" data-i="${i}"><div class="poster">${httpsUrl(x.poster) ? `<img loading="lazy" src="${esc(x.poster)}" alt="${esc(x.titulo)}">` : ''}<div class="badge">${esc(fdate(x))}</div></div><b>${esc(x.titulo)}</b><span>${esc(when(x))}</span></a>`;
    const go = (t, o) => '#/proximas' + (t === 'series' || o === 'fecha' ? '?' + [t === 'series' ? 'tipo=series' : '', o === 'fecha' ? 'orden=fecha' : ''].filter(Boolean).join('&') : '');
    const pill = (txt, href, on) => `<a class="sort ${on ? 'on' : ''}" href="${href}" aria-current="${on ? 'true' : 'false'}">${txt}</a>`;
    app.innerHTML = `<div class="wrap">
      <h1 style="margin-bottom:6px">${SER ? 'Próximas Series' : 'Próximas Películas'}</h1>
      <p class="muted" style="max-width:70ch;margin-bottom:16px">Los estrenos más populares que se acercan, en cines y plataformas. Aún no están disponibles para ver en Pelipe.online: es solo información.</p>
      <div class="filters prox-f">
        <div class="sorts" role="group" aria-label="Tipo">${pill('Películas', go('pelis', ORD), !SER)}${pill('Series', go('series', ORD), SER)}</div>
        <span class="prox-sep" aria-hidden="true"></span>
        <div class="sorts" role="group" aria-label="Ordenar por">${pill('Populares', go(q.tipo, 'pop'), ORD === 'pop')}${pill('Fecha de estreno', go(q.tipo, 'fecha'), ORD === 'fecha')}</div>
      </div>
      ${l.length ? '<div class="grid" id="pg"></div>' : `<p class="empty" id="pg">${SER ? 'Aún no hay estrenos de series cargados. Vuelve pronto.' : 'Aún no hay estrenos cargados. Vuelve pronto.'}</p>`}
      <p class="muted" style="font-size:.8rem;margin-top:26px">Vía TMDB ¡Prepara tus Palomitas, Crispetas, Pochoclos! 🍿</p></div>`;
    const cur = ORD === 'fecha' ? [...l].sort((a, b) => a.fecha.localeCompare(b.fecha)) : l;
    if (l.length) document.getElementById('pg').innerHTML = cur.map(card).join('');
    if (!l.length) return;
    // En PC: al pasar el cursor sale una ventana flotante con la info y el botón del tráiler (no hace falta hacer clic)
    const desk = window.matchMedia && matchMedia('(hover:hover) and (pointer:fine)').matches && innerWidth > 760;
    const trUrl = x => /^[\w-]{6,20}$/.test(x.trailer || '') ? `https://www.youtube.com/watch?v=${x.trailer}` : '';
    if (desk) {
      let pk = document.getElementById('peekp');
      if (!pk) { pk = document.createElement('div'); pk.id = 'peekp'; pk.className = 'peek act'; pk.hidden = true; document.body.appendChild(pk); }
      let t1 = 0, t2 = 0, over = null;
      const hide = () => { clearTimeout(t1); clearTimeout(t2); over = null; pk.classList.remove('on'); pk.hidden = true; };
      const keep = () => clearTimeout(t2);
      const later = () => { clearTimeout(t1); clearTimeout(t2); t2 = setTimeout(hide, 220); };
      const show = a => {
        const x = cur[Number(a.dataset.i)]; if (!x) return;
        const tr = trUrl(x);
        pk.innerHTML = `<h4>${esc(x.titulo)}</h4><div class="peek-meta"><span>${esc(fdate(x, true))}</span><span class="peek-r" style="font-size:.85rem">${esc(when(x))}</span>${x.generos ? `<span class="chip">${esc(String(x.generos).split(',')[0].trim())}</span>` : ''}</div><p>${esc(x.sinopsis || 'Sin sinopsis disponible.')}</p>${tr ? '<button class="btn sm" id="pktr" type="button">▶ Ver tráiler</button>' : ''}<p class="peek-note">Solo información: ${SER ? 'esta serie' : 'esta película'} aún no está disponible para ver en Pelipe.online.</p>`;
        pk.hidden = false;
        const r = a.getBoundingClientRect(), w = pk.offsetWidth, h = pk.offsetHeight;
        let px = r.right + 6; if (px + w > innerWidth - 8) px = r.left - w - 6; if (px < 8) px = Math.max(8, Math.min(innerWidth - w - 8, r.left));
        let py = r.top + 8; if (py + h > innerHeight - 8) py = innerHeight - h - 8; if (py < 8) py = 8;
        pk.style.left = px + 'px'; pk.style.top = py + 'px';
        requestAnimationFrame(() => pk.classList.add('on'));
        const bt = document.getElementById('pktr'); if (bt) bt.onclick = () => openTrailer(tr);
      };
      const pg = document.getElementById('pg');
      pg.addEventListener('mouseover', e => {
        const a = e.target.closest('.card'); if (!a) return;
        keep(); if (a === over) return;
        clearTimeout(t1); over = a; pk.classList.remove('on'); t1 = setTimeout(() => show(a), 300);
      });
      pg.addEventListener('mouseleave', later);
      pk.onmouseenter = keep; pk.onmouseleave = later;
      addEventListener('hashchange', hide, { once: true });
      addEventListener('scroll', hide, { passive: true });
    }
    document.getElementById('pg').addEventListener('click', e => {
      const a = e.target.closest('.card'); if (!a) return;
      e.preventDefault();
      if (desk) return; // en PC la información sale al pasar el cursor
      const x = cur[Number(a.dataset.i)]; if (!x) return;
      const tr = /^[\w-]{6,20}$/.test(x.trailer || '') ? `https://www.youtube.com/watch?v=${x.trailer}` : '';
      openModal(`<div class="proximo"><h2>${esc(x.titulo)}</h2><p class="muted">${esc(fdate(x, true))} · ${esc(when(x))}${x.generos ? ' · ' + esc(x.generos) : ''}</p><p style="margin:12px 0">${esc(x.sinopsis || 'Sin sinopsis disponible.')}</p>${tr ? '<button class="btn" id="ptr" type="button">▶ Ver tráiler</button>' : ''}<p class="muted" style="font-size:.8rem;margin-top:12px">Solo información: ${SER ? 'esta serie' : 'esta película'} aún no está disponible para ver en Pelipe.online.</p></div>`);
      const b = document.getElementById('ptr'); if (b) b.onclick = () => openTrailer(tr);
    });
  }

  async function genresPage() {
    setNav('generos');
    if (!Cloud.enabled) { app.innerHTML = needCloud(); return; }
    loading();
    try {
      const gm = [...genreMap(await catalog()).values()].sort((a, b) => b.items.length - a.items.length);
      app.innerHTML = `<div class="wrap"><h1 style="margin-bottom:16px">Géneros</h1>
        ${gm.length ? `<div class="genres">${gm.map(g => `<a class="genre" href="#/catalogo?genero=${encodeURIComponent(g.name)}">${esc(g.name)}<br><small class="muted">${g.items.length} película${g.items.length === 1 ? '' : 's'}</small></a>`).join('')}</div>` : '<p class="empty">Aún no hay géneros: se crean al publicar películas.</p>'}</div>`;
    } catch (e) { failCloud(e); }
  }

  /* ---------- vista previa al pasar el cursor por una película (solo con ratón) ---------- */
  (() => {
    if (!window.matchMedia || !matchMedia('(hover:hover) and (pointer:fine)').matches) return;
    const tip = document.createElement('div'); tip.className = 'peek'; tip.setAttribute('aria-hidden', 'true'); tip.hidden = true; document.body.appendChild(tip);
    let timer = 0, cur = null;
    const hide = () => { clearTimeout(timer); cur = null; tip.classList.remove('on'); tip.hidden = true; };
    const show = a => {
      const id = decodeURIComponent((a.getAttribute('href') || '').replace('#/ver/', ''));
      const p = (cat || []).find(x => String(x.id) === id);
      if (!p) return;
      const r = Number(p.calificacion), g = gensOf(p)[0];
      tip.innerHTML = `<h4>${esc(p.titulo)}</h4><div class="peek-meta">${r > 0 ? `<span class="peek-r">${r.toFixed(1)}<small>/10</small></span>` : ''}${p.anio ? `<span>${esc(p.anio)}</span>` : ''}${p.duracion ? `<span>${esc(fmtDur(p.duracion))}</span>` : ''}${g ? `<span class="chip">${esc(cap(g))}</span>` : ''}</div>${p.sinopsis ? `<p>${esc(p.sinopsis)}</p>` : ''}`;
      tip.hidden = false;
      const b = a.getBoundingClientRect(), w = tip.offsetWidth, h = tip.offsetHeight, vw = innerWidth, vh = innerHeight;
      let x = b.right + 10; if (x + w > vw - 8) x = b.left - w - 10; if (x < 8) x = Math.max(8, Math.min(vw - w - 8, b.left));
      let y = b.top + 8; if (y + h > vh - 8) y = vh - h - 8; if (y < 8) y = 8;
      tip.style.left = x + 'px'; tip.style.top = y + 'px';
      requestAnimationFrame(() => tip.classList.add('on'));
    };
    document.addEventListener('mouseover', e => {
      const a = e.target.closest && e.target.closest('a.card[href^="#/ver/"]');
      if (!a) { if (cur) hide(); return; }
      if (a === cur) return;
      hide(); cur = a; timer = setTimeout(() => show(a), 450);
    });
    document.addEventListener('mouseleave', hide);
    addEventListener('scroll', hide, { passive: true }); addEventListener('hashchange', hide); addEventListener('blur', hide);
    document.addEventListener('click', hide);
  })();

  /* ---------- reacciones tipo Facebook (sin necesidad de registro) ---------- */
  const REACCIONES = [
    ['like', '👍', 'Me gusta', '#2d88ff'], ['love', '❤️', 'Me encanta', '#f33e58'], ['haha', '😆', 'Me divierte', '#f7b125'],
    ['wow', '😮', 'Me asombra', '#f7b125'], ['sad', '😢', 'Me entristece', '#f7b125'], ['angry', '😡', 'Me enoja', '#e9710f']
  ];
  const reactHtml = () => `<div class="react" id="react">
      <div class="react-sum" id="rsum"><span class="muted">Cargando reacciones…</span></div>
      <button type="button" class="link-btn" id="report" title="Avísanos si el video, el audio o los subtítulos fallan">⚑ Reportar un problema</button>
      <div class="react-act">
        <div class="react-pop" role="toolbar" aria-label="Reacciones">${REACCIONES.map(r => `<button type="button" data-t="${r[0]}" title="${r[2]}" aria-label="${r[2]}">${r[1]}</button>`).join('')}</div>
        <button type="button" class="btn ghost" id="rmain"><span>👍</span> Me gusta</button>
      </div></div>`;

  async function setupReactions(pid) {
    const box = document.getElementById('react');
    if (!box) return;
    const tipos = REACCIONES.map(r => r[0]);
    const R = t => REACCIONES.find(r => r[0] === t);
    const pop = box.querySelector('.react-pop'), main = box.querySelector('#rmain'), sum = box.querySelector('#rsum'), act = box.querySelector('.react-act');
    let counts = Object.fromEntries(tipos.map(t => [t, 0])), mine = null, busy = false, openT, closeT, longPress = false;

    function paint() {
      const total = tipos.reduce((a, t) => a + counts[t], 0);
      const top = tipos.filter(t => counts[t] > 0).sort((a, b) => counts[b] - counts[a]).slice(0, 3);
      sum.innerHTML = total
        ? `<span class="stack">${top.map(t => `<span>${R(t)[1]}</span>`).join('')}</span><span class="muted">${total}</span>`
        : '<span class="muted">Sé la primera persona en reaccionar</span>';
      sum.title = tipos.filter(t => counts[t] > 0).map(t => `${R(t)[2]}: ${counts[t]}`).join('\n');
      const r = mine && R(mine);
      main.innerHTML = r ? `<span>${r[1]}</span> <span style="color:${r[3]}">${r[2]}</span>` : '<span>👍</span> Me gusta';
      main.classList.toggle('on', !!r);
      pop.querySelectorAll('button').forEach(b => b.classList.toggle('sel', b.dataset.t === mine));
    }
    const openPop = () => { clearTimeout(closeT); box.classList.add('open'); };
    const closePop = () => box.classList.remove('open');

    async function react(t) {
      if (busy) return;
      busy = true;
      const prev = mine, next = prev === t ? null : t;
      if (prev) counts[prev]--;
      if (next) counts[next]++;
      mine = next; paint(); closePop();
      try {
        const uid = await Cloud.ensureUid();
        if (next) await Cloud.setVote(pid, uid, next); else await Cloud.removeVote(pid, uid);
      } catch (e) {
        if (next) counts[next]--;
        if (prev) counts[prev]++;
        mine = prev; paint();
        toast(e && e.code === 'auth/operation-not-allowed' ? 'Falta activar el acceso anónimo en Firebase (ver README).' : 'No se pudo registrar tu reacción.');
      }
      busy = false;
    }

    // Escritorio: al pasar el mouse. Teclado: al enfocar. Móvil: mantener presionado.
    act.addEventListener('mouseenter', () => { clearTimeout(closeT); openT = setTimeout(openPop, 250); });
    act.addEventListener('mouseleave', () => { clearTimeout(openT); closeT = setTimeout(closePop, 350); });
    act.addEventListener('focusin', openPop);
    act.addEventListener('focusout', e => { if (!act.contains(e.relatedTarget)) closePop(); });
    main.addEventListener('pointerdown', e => {
      if (e.pointerType !== 'touch') return;
      longPress = false;
      openT = setTimeout(() => { longPress = true; openPop(); }, 450);
    });
    ['pointerup', 'pointercancel', 'pointerleave'].forEach(ev => main.addEventListener(ev, () => clearTimeout(openT)));
    main.addEventListener('contextmenu', e => e.preventDefault());
    main.addEventListener('click', () => { if (longPress) { longPress = false; return; } react(mine || 'like'); });
    pop.addEventListener('click', e => { const b = e.target.closest('button[data-t]'); if (b) react(b.dataset.t); });
    const outside = e => { if (!document.body.contains(box)) document.removeEventListener('click', outside); else if (!act.contains(e.target)) closePop(); };
    document.addEventListener('click', outside);

    try {
      counts = await Cloud.countVotes(pid, tipos);
      const uid = Cloud.currentUid();
      if (uid) mine = await Cloud.getVote(pid, uid);
    } catch (e) {
      sum.innerHTML = '<span class="muted">Reacciones no disponibles (revisa las reglas de Firestore)</span>';
      return;
    }
    paint();
  }

  /* ---------- película: reproductor ---------- */
  // Compartir: en el celular abre el menú nativo; en computador, un panel con copiar enlace y redes
  async function sharePage(p) {
    // Enlace a la página de la película (tiene vista previa con póster en WhatsApp/Facebook); si aún no existe, 404.html redirige al reproductor
    const url = new URL('p/' + encodeURIComponent(p.id) + '/', new URL('.', location.href)).href;
    const text = `Mira “${p.titulo}” en Pelipe.online`;
    if (navigator.share && matchMedia('(pointer:coarse)').matches) {
      try { await navigator.share({ title: p.titulo, text, url }); return; } catch (e) { if (e && e.name === 'AbortError') return; }
    }
    const q = encodeURIComponent;
    const links = [
      ['WhatsApp', `https://wa.me/?text=${q(text + ' ' + url)}`],
      ['Telegram', `https://t.me/share/url?url=${q(url)}&text=${q(text)}`],
      ['Facebook', `https://www.facebook.com/sharer/sharer.php?u=${q(url)}`],
      ['X', `https://twitter.com/intent/tweet?text=${q(text)}&url=${q(url)}`],
      ['Correo', `mailto:?subject=${q(text)}&body=${q(url)}`]
    ];
    openModal(`<div class="panel">
      <h2>Compartir “${esc(p.titulo)}”</h2>
      <div class="newlist"><input id="surl" readonly value="${esc(url)}" aria-label="Enlace"><button class="btn sm" id="scopy" type="button">Copiar</button></div>
      <div class="sharegrid">${links.map(([n, h]) => `<a class="btn ghost sm" href="${esc(h)}" target="_blank" rel="noopener noreferrer">${n}</a>`).join('')}</div>
    </div>`);
    const inp = document.getElementById('surl');
    inp.onfocus = () => inp.select();
    document.getElementById('scopy').onclick = async () => {
      try { await navigator.clipboard.writeText(url); }
      catch { inp.select(); try { document.execCommand('copy'); } catch { return toast('Copia el enlace manualmente.'); } }
      toast('Enlace copiado.');
    };
  }

  function reportDialog(p, srvLabel = '') {
    openModal(`<div class="panel">
      <h2>Reportar un problema</h2>
      <p class="muted" style="margin-top:6px">¿Qué pasa con “${esc(p.titulo)}”?</p>
      <form class="pform" id="rf" autocomplete="off">
        <label>Tipo de problema<select name="tipo" required><option value="">Elige…</option>${Object.entries(REPORTES).map(([k, t]) => `<option value="${k}">${esc(t)}</option>`).join('')}</select></label>
        <label>Detalles <small class="muted">opcional</small><textarea name="detalle" rows="4" maxlength="500" placeholder="Ej.: desde el minuto 40 se corta el audio"></textarea></label>
        <p class="muted" style="font-size:.8rem;margin:0">Enviamos también tu navegador y el minuto del video para encontrar la falla. No necesitas cuenta.</p>
        <div class="actions"><button class="btn">Enviar reporte</button></div>
      </form></div>`);
    const f = document.getElementById('rf');
    f.addEventListener('submit', async e => {
      e.preventDefault();
      const btn = f.querySelector('button.btn');
      btn.disabled = true;
      const v = app.querySelector('video');
      const src = parseSource(p.url);
      const d = { peli: String(p.id), titulo: String(p.titulo || '').slice(0, 120), tipo: f.elements.tipo.value, detalle: ((srvLabel ? `[${srvLabel}] ` : '') + f.elements.detalle.value.trim()).slice(0, 500), por: p.por || '', ua: navigator.userAgent.slice(0, 150), seg: v && isFinite(v.currentTime) ? Math.floor(v.currentTime) : 0, fuente: src ? src.tipo : '' };
      try {
        const uid = await Cloud.ensureUid();
        await Cloud.sendReporte(d.peli + '_' + uid, { ...d, uid });
        closeModal();
        toast('Gracias, recibimos tu reporte.');
      } catch (err) {
        btn.disabled = false;
        toast(err && err.code === 'auth/operation-not-allowed' ? 'Falta activar el acceso anónimo en Firebase (ver README).' : 'No se pudo enviar el reporte. Inténtalo de nuevo en un momento.');
      }
    });
  }

  function listPicker(p) {
    openModal(`<div class="panel">
      <h2>Añadir “${esc(p.titulo)}” a…</h2>
      <div class="checks">${data.listas.map(l => `<label><input type="checkbox" data-l="${esc(l.id)}" ${l.items.some(x => same(x.id, p.id)) ? 'checked' : ''}> <span>${esc(l.nombre)}</span> <small class="muted">(${l.items.length})</small></label>`).join('')}</div>
      <form class="newlist"><input name="n" maxlength="40" placeholder="Nueva lista…" autocomplete="off"><button class="btn sm">Crear</button></form>
    </div>`);
    const box = modal.querySelector('.panel');
    box.addEventListener('change', e => { if (e.target.dataset.l) toggleInList(e.target.dataset.l, p); });
    box.querySelector('form').addEventListener('submit', e => {
      e.preventDefault();
      const n = e.target.n.value.trim();
      if (!n) return;
      const l = createList(n);
      toggleInList(l.id, p);
      listPicker(p);
    });
  }

  async function watchPage(id, draft) {
    setNav('catalogo');
    if (!Cloud.enabled) { app.innerHTML = needCloud(); return; }
    id = decodeURIComponent(id);
    loading();
    try {
      let p = null;
      if (!draft && !canPublish()) { try { p = (await catalogRaw()).find(x => x.id === id) || null; } catch { } } // catálogo estático: no hace falta esperar a Firebase
      if (!p) { try { p = draft ? await Cloud.getBorrador(id) : await Cloud.getPelicula(id); } catch (e) { if (!draft) throw e; } }
      const mine = canPublish() && p && (isAdmin() || p.por === user.email);
      if (!p || (draft && !mine) || (!draft && !visible(p) && !mine)) { app.innerHTML = '<div class="wrap"><p class="empty">No encontramos esta película.</p></div>'; return; }
      // Series: el episodio sale de la dirección (#/ver/ID?t=temporada&e=episodio); sin indicar, el primero
      const serie = isSerie(p);
      const seasons = serie ? epsOf(p) : [];
      const q0 = parseQuery();
      const season = serie ? (seasons.find(t => t.n === Number(q0.t)) || seasons[0] || null) : null;
      const ep = season ? (season.episodios.find(x => x.n === Number(q0.e)) || season.episodios[0]) : null;
      if (serie) setNav('series');
      const flat = seasons.flatMap(t => t.episodios.map(e => ({ t: t.n, e: e.n })));
      const at = ep ? flat.findIndex(x => x.t === season.n && x.e === ep.n) : -1;
      const epHref = x => `#/ver/${encodeURIComponent(p.id)}?t=${x.t}&e=${x.e}`;
      const epTag = ep ? `T${season.n} · E${ep.n}` : '';
      const live = !draft && visible(p); // borradores y programadas se ven como vista previa, sin reacciones ni compartir
      if (live) { recents.push(p); lastWatched = p.id; }
      document.title = `${p.titulo}${serie && ep ? ' ' + epTag : ''} — Pelipe.online`;
      const gens = gensOf(p);
      const favLabel = () => isFav(p.id) ? '♥ En favoritas' : '♡ Favorita';
      let similar = [];
      try { similar = (serie ? await seriesCat() : await catalog()).filter(x => x.id !== p.id && gens[0] && norm(gensOf(x)[0] || '') === norm(gens[0])).slice(0, 12); } catch { }
      const srvs = serversOf(serie ? (ep || {}) : p);
      const first = srvs[0] ? srvs[0].u : serie ? '' : p.url;
      let curSrv = 0;
      const quals = qualsOf(p);
      // Opciones bajo el video: HD, cada servidor con la bandera de su idioma
      const srvPanel = srvs.length > 1 || srvs.some(s => s.i) ? `<div class="servers" id="servers" role="group" aria-label="Servidor">
          <p class="servers-h">Elige servidor</p>
          ${QORD.filter(q => srvs.some(s => s.q === q)).map(q => `<div class="srv-group"><span class="srv-q">${esc(q)}</span><div class="srv-btns">${srvs.map((s, idx) => ({ s, idx })).filter(x => x.s.q === q).map(({ s, idx }, n) => `<button type="button" class="srv ${idx === 0 ? 'on' : ''}" data-k="${idx}" aria-label="${esc(q + ' ' + (IDIOMAS[s.i] || '') + ' ' + (s.n || 'Servidor ' + (n + 1)))}">${flagOf(s.i)}<span class="srv-t"><b>${esc(IDIOMAS[s.i] || 'Idioma original')}</b><small>${esc(s.n || 'Servidor ' + (n + 1))}</small></span></button>`).join('')}</div></div>`).join('')}
        </div>` : '';
      // Series: idiomas como botones pequeños (solo si hay más de una opción; con una sola se muestra como etiqueta)
      const srvPills = serie && srvs.length > 1 ? `<div class="eps-row" id="servers" role="group" aria-label="Idioma"><span class="eps-lb">Idioma</span><div class="seas">${srvs.map((s, k) => `<button type="button" class="sort ${k === 0 ? 'on' : ''}" data-k="${k}" aria-pressed="${k === 0}">${flagOf(s.i)}${esc(IDIOMAS[s.i] || 'Idioma original')}${srvs.filter(x => x.i === s.i).length > 1 ? ' · ' + esc(s.n || 'Servidor ' + (k + 1)) : ''}</button>`).join('')}</div></div>` : '';
      const oneLang = serie && srvs.length === 1 && srvs[0].i ? `<span class="eps-chip">${flagOf(srvs[0].i)}${esc(IDIOMAS[srvs[0].i])}</span>` : '';
      // Temporadas y episodios (solo series)
      const epsPanel = serie ? (ep ? `<div class="eps" id="eps">
          <div class="eps-h"><b class="eps-now">${esc(epTag)}${ep.titulo ? ' — ' + esc(ep.titulo) : ''}</b>${oneLang}
            <span class="eps-nav">${at > 0 ? `<a class="btn ghost sm" href="${epHref(flat[at - 1])}">‹ Anterior</a>` : ''}${at >= 0 && at < flat.length - 1 ? `<a class="btn sm" href="${epHref(flat[at + 1])}">Siguiente ▶</a>` : ''}</span></div>
          ${srvPills}
          ${seasons.length > 1 ? `<div class="eps-row" id="seasons" role="group" aria-label="Temporada"><span class="eps-lb">Temporada</span><div class="seas">${seasons.map(t => `<button type="button" class="sort ${t.n === season.n ? 'on' : ''}" data-t="${t.n}" aria-pressed="${t.n === season.n}">${t.n}</button>`).join('')}</div></div>` : ''}
          <div class="eps-list" role="list">${season.episodios.map(e => `<a role="listitem" class="ep ${e.n === ep.n ? 'on' : ''}" href="${epHref({ t: season.n, e: e.n })}" ${e.n === ep.n ? 'aria-current="true"' : ''}><b>${e.n}</b><span>${esc(e.titulo || 'Episodio ' + e.n)}</span></a>`).join('')}</div>
        </div>` : '<div class="notice slim"><p>Esta serie aún no tiene episodios disponibles.</p></div>') : '';
      const srvLabel = () => { const s = srvs[curSrv]; return s ? [serie ? epTag : '', s.q, IDIOMAS[s.i], s.n].filter(Boolean).join(' · ') : ''; };
      app.innerHTML = `<div class="theater"><div class="theater-in">
        <h1 class="t-title">${esc(p.titulo)} ${p.anio ? `<small class="muted">(${esc(p.anio)})</small>` : ''}${serie && ep ? ` <small class="muted">${esc(epTag)}</small>` : ''}</h1>
        ${live ? '' : `<div class="notice slim"><p><b>Vista previa.</b> ${draft ? 'Este es un borrador: solo tú puedes verlo.' : 'Programada para el ' + esc(new Date(p.publicarEn).toLocaleString('es', { dateStyle: 'long', timeStyle: 'short' })) + '; hasta entonces solo tú puedes verla.'}</p></div>`}
        ${first ? playerHtml(parseSource(first), coverOf(p)) : ''}
        <div class="resume" id="resume" hidden></div>
        <div class="pbar" id="pbar" hidden></div>
        ${serie ? '' : srvPanel}
        ${epsPanel}
        ${live ? reactHtml() : ''}
        ${gens.length ? `<div class="chips">${gens.slice(0, 1).map(g => `<a class="chip" href="${serie ? '#/series' : '#/catalogo'}?genero=${encodeURIComponent(g)}">${esc(g)}</a>`).join('')}</div>` : ''}
        ${p.sinopsis ? `<p class="overview">${esc(p.sinopsis)}</p>` : ''}
        <dl class="ficha">${[
          ['Año', p.anio],
          ...(serie ? [['Temporadas', seasons.length || ''], ['Episodios', countEps(p) || '']] : [['Duración', fmtDur(p.duracion)]]),
          ['Calidad', quals.join(' · ')],
          ['Calificación', Number(p.calificacion) > 0 ? '★ ' + Number(p.calificacion).toFixed(1) + ' / 10' : ''],
          ['Subtítulos', (Array.isArray(p.subtitulos) ? p.subtitulos : []).map(s => s.n).join(', ')],
          ['Audio', Array.isArray(p.versiones) && p.versiones.length ? [(p.audioNombre || '').trim() || 'Original', ...p.versiones.map(x => x.n)].join(', ') : ''],
          ['Créditos', p.creditos]
        ].filter(r => r[1]).map(([k, val]) => `<div><dt>${k}</dt><dd>${esc(val)}</dd></div>`).join('')}</dl>
        <div class="actions" style="margin-top:14px">
          ${live ? `<button class="btn ghost ${isFav(p.id) ? 'on' : ''}" id="fav">${favLabel()}</button>
          <button class="btn ghost" id="share">↗ Compartir</button>
          <button class="btn ghost" id="addlist">＋ Añadir a lista</button>` : ''}
          ${parseSource(p.trailer) ? '<button class="btn ghost" id="trailer">Ver tráiler</button>' : ''}
        </div>
      </div></div>
        <div class="wrap">${rowLocal('Más como esta', similar)}</div>`;
      if (first) wireVideo(app, { ...p, url: first }, { track: live && (!serie || !!ep), ep: serie && ep ? { t: season.n, e: ep.n } : null, next: serie && at >= 0 ? flat[at + 1] : null });
      // Cambiar de calidad/servidor: se sigue desde el mismo minuto
      const playSrv = k => {
        const s = srvs[k];
        if (!s || k === curSrv) return;
        const old = app.querySelector('.player video');
        const t = old && isFinite(old.currentTime) ? old.currentTime : 0, was = !!old && !old.paused;
        if (leave) { try { leave(); } catch { } leave = null; }
        if (old) { if (old._st && old._st.hls) old._st.hls.destroy(); old.pause(); old.removeAttribute('src'); old.load(); }
        const tmp = document.createElement('div');
        tmp.innerHTML = playerHtml(parseSource(s.u), coverOf(p));
        app.querySelector('.player').replaceWith(tmp.firstElementChild);
        curSrv = k;
        app.querySelectorAll('#servers [data-k]').forEach(b => { const on = Number(b.dataset.k) === k; b.classList.toggle('on', on); if (b.classList.contains('sort')) b.setAttribute('aria-pressed', on); });
        const rs = document.getElementById('resume'), pb = document.getElementById('pbar');
        rs.hidden = true; pb.hidden = true; pb.innerHTML = '';
        wireVideo(app, { ...p, url: s.u }, { track: live && (!serie || !!ep), startAt: t, keepPlaying: was, ep: serie && ep ? { t: season.n, e: ep.n } : null, next: serie && at >= 0 ? flat[at + 1] : null });
      };
      const seaBox = document.getElementById('seasons');
      if (seaBox) seaBox.addEventListener('click', e => { const b = e.target.closest('[data-t]'); if (b && Number(b.dataset.t) !== season.n) location.hash = epHref({ t: b.dataset.t, e: 1 }); });
      const sp = document.getElementById('servers');
      if (sp) sp.addEventListener('click', e => { const b = e.target.closest('[data-k]'); if (b) playSrv(Number(b.dataset.k)); });
      if (live) {
        setupReactions(p.id);
        const fav = document.getElementById('fav');
        fav.onclick = () => { toggleFav(p); fav.classList.toggle('on', isFav(p.id)); fav.textContent = favLabel(); };
        document.getElementById('addlist').onclick = () => listPicker(p);
        document.getElementById('report').onclick = () => reportDialog({ ...p, url: srvs[curSrv] ? srvs[curSrv].u : p.url }, srvLabel());
        document.getElementById('share').onclick = () => sharePage(p);
      }
      const tr = document.getElementById('trailer');
      if (tr) tr.onclick = () => openTrailer(p.trailer);
      window.scrollTo(0, 0);
    } catch (e) { app.innerHTML = `<div class="notice"><h2>No se pudo cargar la película</h2><p>${esc(e.code || e.message)}</p></div>`; }
  }

  // Sorpréndeme: abre una película al azar (prefiere las que aún no has visto)
  async function surprise() {
    if (!Cloud.enabled) { location.replace('#/'); return; }
    loading();
    try {
      const list = await catalog();
      if (!list.length) { location.replace('#/'); return; }
      const seen = new Set(recents.get().map(String));
      const not = x => String(x.id) !== String(lastWatched);
      let pool = list.filter(x => not(x) && !seen.has(String(x.id)));
      if (!pool.length) pool = list.filter(not);
      if (!pool.length) pool = list;
      location.replace('#/ver/' + encodeURIComponent(pool[Math.floor(Math.random() * pool.length)].id));
    } catch (e) { failCloud(e); }
  }

  /* ---------- favoritas y listas ---------- */
  function favoritas() {
    setNav('favoritas');
    app.innerHTML = `<div class="wrap"><h1 style="margin-bottom:16px">Mis favoritas</h1>${note()}
      ${data.favoritos.length ? gridRm(data.favoritos, 'data-k="fav"') : '<p class="empty">Aún no tienes favoritas. Abre una película y pulsa “♡ Favorita”.</p>'}</div>`;
  }

  function listas() {
    setNav('listas');
    app.innerHTML = `<div class="wrap"><h1 style="margin-bottom:16px">Mis listas</h1>${note()}
      <form class="newlist big" id="nl"><input name="n" maxlength="40" placeholder="Nombre de la nueva lista" autocomplete="off"><button class="btn">Crear lista</button></form>
      <div class="genres">${data.listas.map(l => `<a class="genre" href="#/listas/${encodeURIComponent(l.id)}">${esc(l.nombre)}<br><small class="muted">${l.items.length} película${l.items.length === 1 ? '' : 's'}</small></a>`).join('')}</div></div>`;
    document.getElementById('nl').addEventListener('submit', e => {
      e.preventDefault();
      const n = e.target.n.value.trim();
      if (n) location.hash = '#/listas/' + encodeURIComponent(createList(n).id);
    });
  }

  function listaDetalle(id) {
    setNav('listas');
    id = decodeURIComponent(id);
    const l = data.listas.find(x => x.id === id);
    if (!l) { location.hash = '#/listas'; return; }
    app.innerHTML = `<div class="wrap">
      <p><a href="#/listas" class="muted">← Mis listas</a></p>
      <div class="row-h" style="margin:8px 0 16px"><h1>${esc(l.nombre)}</h1>${id !== 'ver-despues' ? '<button class="btn ghost sm dellist">Eliminar lista</button>' : ''}</div>
      ${l.items.length ? gridRm(l.items, `data-k="list" data-l="${esc(l.id)}"`) : '<p class="empty">Esta lista está vacía. Abre una película y pulsa “＋ Añadir a lista”.</p>'}</div>`;
    const del = app.querySelector('.dellist');
    if (del) del.dataset.l = id;
  }

  /* ---------- panel para publicar (administrador y editores) ---------- */
  let editing = null;
  const teamHtml = team => `<section class="panel wide" style="margin-top:28px">
      <h2>Equipo con permiso para publicar</h2>
      <p class="muted" style="font-size:.88rem">Cada persona entra con su cuenta de Google y solo puede publicar, editar y eliminar sus propias películas. Tú puedes gestionar todas. Al quitar el acceso, sus películas ya publicadas se conservan.</p>
      <form class="newlist" id="tf"><input name="email" type="email" required placeholder="correo@gmail.com" autocomplete="off"><button class="btn sm">Dar acceso</button></form>
      <div id="team">${team.length ? team.map(t => `<div class="arow"><span>${esc(t.id)}</span><button class="btn ghost sm" data-rmeditor="${esc(t.id)}">Quitar acceso</button></div>`).join('') : '<p class="muted" style="margin-top:10px">Aún no has dado acceso a nadie.</p>'}</div>
    </section>`;

  async function tmdb(path, params = {}) {
    const key = C.TMDB_API_KEY.trim();
    const u = new URL('https://api.themoviedb.org/3' + path);
    const opts = {};
    if (key.length > 40) opts.headers = { Authorization: 'Bearer ' + key };
    else u.searchParams.set('api_key', key);
    u.searchParams.set('language', C.LANG || 'es-MX');
    for (const [k, v] of Object.entries(params)) if (v !== '' && v != null) u.searchParams.set(k, v);
    const r = await fetch(u, opts);
    if (!r.ok) throw new Error('TMDB ' + r.status);
    return r.json();
  }

  // Busca la película por título (y año) en TMDB y devuelve su póster y fondo oficiales
  async function tmdbArt(titulo, anio, tv) {
    try {
      const y = /^\d{4}$/.test(anio) ? anio : '';
      const ep = tv ? '/search/tv' : '/search/movie', yk = tv ? 'first_air_date_year' : 'year';
      let rs = (await tmdb(ep, { query: titulo, include_adult: 'false', [yk]: y })).results || [];
      if (!rs.some(x => x.poster_path) && y) rs = (await tmdb(ep, { query: titulo, include_adult: 'false' })).results || [];
      const m = rs.find(x => x.poster_path);
      if (!m) return null;
      if (tv) { m.title = m.name; m.release_date = m.first_air_date; }
      return { nombre: `${m.title}${m.release_date ? ' (' + m.release_date.slice(0, 4) + ')' : ''}`, poster: 'https://image.tmdb.org/t/p/w500' + m.poster_path, fondo: m.backdrop_path ? 'https://image.tmdb.org/t/p/w1280' + m.backdrop_path : '' };
    } catch { return null; }
  }
  async function autoThumb(url) {
    const s = parseSource(url);
    if (!s) return {};
    const ok = u => new Promise(res => { const i = new Image(); i.onload = () => res(i.naturalWidth > 200); i.onerror = () => res(false); i.src = u; });
    if (s.tipo === 'youtube') {
      const b = `https://i.ytimg.com/vi/${s.id}/`;
      return { poster: b + 'hqdefault.jpg', fondo: b + ((await ok(b + 'maxresdefault.jpg')) ? 'maxresdefault.jpg' : 'hqdefault.jpg') };
    }
    if (s.tipo === 'archive') return { poster: `https://archive.org/services/img/${encodeURIComponent(s.id)}` };
    const dm = s.tipo === 'embed' && s.src.match(/dailymotion\.com\/embed\/video\/([a-z0-9]+)/i);
    if (dm) { const t = `https://www.dailymotion.com/thumbnail/video/${dm[1]}`; return { poster: t, fondo: t }; }
    return {};
  }

  // Una fila del formulario de servidores: calidad, idioma, nombre opcional y enlace
  const srvRow = s => `<div class="srow"><div class="srow-top"><select class="si" aria-label="Idioma"><option value="lat" ${s.i === 'lat' ? 'selected' : ''}>Latino (México)</option><option value="en" ${s.i === 'en' ? 'selected' : ''}>English</option><option value="ensub" ${s.i === 'ensub' ? 'selected' : ''}>English (sub. español)</option><option value="" ${!s.i ? 'selected' : ''}>Sin indicar</option></select><input class="sn" maxlength="30" placeholder="Nombre (opcional)" value="${esc(s.n || '')}"><button type="button" class="btn ghost sm srow-rm" aria-label="Quitar este servidor" title="Quitar">✕</button></div><input class="su" type="url" placeholder="Enlace del video (https)" value="${esc(s.u || '')}"><p class="muted sinfo"></p></div>`;

  async function adminPage() {
    setNav('admin');
    if (!Cloud.enabled) { app.innerHTML = needCloud(); return; }
    if (!user) { app.innerHTML = `<div class="wrap"><h1>Publicar películas</h1>${note()}</div>`; return; }
    if (!canPublish()) { app.innerHTML = '<div class="wrap"><div class="notice"><h2>Sin permiso</h2><p>Tu cuenta no tiene permiso para publicar. Pídele acceso al administrador del sitio.</p></div></div>'; return; }
    loading();
    let list = [], drafts = [], team = [];
    try {
      list = await catalogRaw(true);
      if (isAdmin()) team = await Cloud.listEditores();
      else list = list.filter(p => p.por === user.email); // un editor solo gestiona lo suyo
    } catch (e) { toast('No se pudo leer el catálogo: ' + (e.code || e.message)); }
    try { drafts = (await Cloud.listBorradores(isAdmin() ? null : user.email)).sort((a, b) => (b.actualizado || 0) - (a.actualizado || 0)); } catch (e) { /* sin reglas de borradores */ }
    const seriesL = list.filter(isSerie); list = list.filter(p => !isSerie(p)); // las series tienen su propio formulario y lista
    const now = Date.now();
    const pubs = list.filter(p => !(p.publicarEn > now)), sched = list.filter(p => p.publicarEn > now).sort((a, b) => a.publicarEn - b.publicarEn);
    let reps = [];
    try { reps = (await Cloud.listReportes(isAdmin() ? null : user.email)).sort((a, b) => ms(b.t) - ms(a.t)); } catch (e) { /* sin reglas de reportes: no se muestra el panel */ }
    const curDraft = editing ? drafts.find(x => x.id === editing) : null;
    const curPub = editing && !curDraft ? list.find(x => x.id === editing) : null;
    const curSerie = editing ? seriesL.find(x => x.id === editing) : null;
    const serieText = sr => epsOf(sr).flatMap(t => t.episodios.flatMap(e => serversOf(e).map(sv => `${t.n}x${String(e.n).padStart(2, '0')} | ${e.titulo || ''} | ${sv.i || ''} | ${sv.u}${sv.n ? ' | ' + sv.n : ''}`))).join('\n');
    const cur = curDraft || curPub || null;
    const modoIni = curDraft ? 'borrador' : curPub && curPub.publicarEn > now ? 'programar' : 'ahora';
    const localDT = ms0 => { const d = new Date(ms0 - new Date(ms0).getTimezoneOffset() * 60000); return d.toISOString().slice(0, 16); };
    const v = k => esc(cur?.[k] ?? '');
    app.innerHTML = `<div class="wrap admin">
      <h1>Publicar películas y series <span class="chip" style="vertical-align:middle;font-size:.8rem">${isAdmin() ? 'Administrador' : 'Editor'}</span></h1>
      <p class="muted" style="max-width:70ch;margin:6px 0 20px">Publica solo obras tuyas, de dominio público, con licencia libre o con permiso escrito del titular.</p>
      <p class="muted" style="max-width:70ch;margin:-10px 0 20px;font-size:.85rem">Tú ves los cambios al instante. El público los ve cuando se actualiza el sitio (automático cada hora; ver README, “Actualizar el sitio”).</p>
      ${reps.length ? `<section class="panel wide" id="reps">
        <h2>Reportes de problemas (${reps.length})</h2>
        ${reps.map(r => `<div class="rep">
          <div><a href="#/ver/${encodeURIComponent(r.peli)}"><b>${esc(r.titulo)}</b></a> · ${esc(REPORTES[r.tipo] || r.tipo)}</div>
          ${r.detalle ? `<p>${esc(r.detalle)}</p>` : ''}
          <div class="muted" style="font-size:.8rem">${r.t ? esc(new Date(ms(r.t)).toLocaleString('es')) : ''}${r.seg ? ' · minuto ' + fmtT(r.seg) : ''}${r.fuente ? ' · ' + esc(r.fuente) : ''}<br>${esc(r.ua || '')}</div>
          <button class="btn ghost sm" data-rres="${esc(r.id)}">Marcar como resuelto</button>
        </div>`).join('')}
      </section>` : ''}
      ${hasTmdb() ? `<section class="panel wide">
        <h2>Autocompletar datos desde TMDB (opcional)</h2>
        <form class="newlist" id="ts"><input name="q" placeholder="Título de la película…"><button class="btn sm">Buscar</button></form>
        <div id="tres"></div>
        <p class="muted" style="font-size:.82rem;margin-top:10px">Solo rellena título, sinopsis, póster y tráiler. Tú pones el enlace del video y confirmas la licencia.</p>
      </section>` : ''}
      <form id="pf" class="panel wide pform" autocomplete="off">
        <h2>${cur ? 'Editar película' : 'Nueva película'}</h2>
        <label>Título *<input name="titulo" required maxlength="120" value="${v('titulo')}"></label>
        <div class="three">
          <label>Año<input name="anio" inputmode="numeric" maxlength="4" value="${v('anio')}"></label>
          <label>Duración (min)<input name="duracion" inputmode="numeric" maxlength="4" value="${v('duracion')}"></label>
          <label>Calificación (0–10)<input name="calificacion" inputmode="decimal" maxlength="4" value="${v('calificacion')}"></label>
        </div>
        <label>Género(s) <small class="muted">separados por coma; solo el primero cuenta: es la categoría única de la película</small><input name="generos" maxlength="120" value="${v('generos')}"></label>
        <label>Sinopsis<textarea name="sinopsis" rows="4" maxlength="1500">${v('sinopsis')}</textarea></label>
        <fieldset class="srvs"><legend>Servidores de video *</legend>
          <p class="muted" style="font-size:.84rem">Agrega uno o más servidores. Bajo el video se muestran en HD, cada uno con la bandera de su idioma. Enlaces: YouTube, Vimeo, Dailymotion, Google Drive, Streamable, Odysee, Rumble, Bunny, Cloudflare, Internet Archive, .mp4/.webm/.m3u8 o un sitio autorizado en config.js (https).</p>
          <div id="srvlist">${(cur && serversOf(cur).length ? serversOf(cur) : [{ q: 'HD', i: 'lat', n: '', u: '' }]).map(srvRow).join('')}</div>
          <button type="button" class="btn ghost sm" id="addsrv">＋ Agregar servidor</button>
        </fieldset>
        <label>Póster (enlace https a una imagen vertical) <small class="muted">si lo dejas vacío, se busca la portada oficial por título (TMDB)</small><input name="poster" type="url" value="${v('poster')}"></label>
        <label>Imagen de fondo para la portada <small class="muted">opcional, horizontal; vacío = también se busca en TMDB</small><input name="fondo" type="url" value="${v('fondo')}"></label>
        <label>Tráiler <small class="muted">opcional, enlace de YouTube o Vimeo</small><input name="trailer" type="url" value="${v('trailer')}"></label>
        <label>Licencia *<select name="licencia" required><option value="">Elige…</option>${Object.entries(LICENCIAS).map(([k, t]) => `<option value="${k}" ${cur?.licencia === k ? 'selected' : ''}>${t}</option>`).join('')}</select></label>
        <label>Créditos / atribución / enlace a la licencia<input name="creditos" maxlength="300" value="${v('creditos')}"></label>
        <label class="chk"><input type="checkbox" name="ok" required> <span>Confirmo que tengo los derechos para publicar esta película o que es de dominio público / licencia libre.</span></label>
        <fieldset class="when">
          <legend>¿Cuándo publicarla?</legend>
          <label class="chk"><input type="radio" name="modo" value="ahora" ${modoIni === 'ahora' ? 'checked' : ''}> <span>${curPub && modoIni === 'ahora' ? 'Publicada (visible ahora)' : 'Publicar ahora'}</span></label>
          <label class="chk"><input type="radio" name="modo" value="programar" ${modoIni === 'programar' ? 'checked' : ''}> <span>Programar una fecha y hora</span></label>
          <label id="cuandoBox" hidden>Fecha y hora <small class="muted">tu hora local; hasta entonces nadie más la ve</small><input type="datetime-local" name="cuando" value="${curPub && curPub.publicarEn > now ? localDT(curPub.publicarEn) : ''}"></label>
          <label class="chk"><input type="radio" name="modo" value="borrador" ${modoIni === 'borrador' ? 'checked' : ''}> <span>Guardar como borrador (solo tú la ves; puedes dejarla incompleta)</span></label>
        </fieldset>
        <div class="actions"><button class="btn">${cur ? 'Guardar cambios' : 'Publicar'}</button>${cur ? '<button type="button" class="btn ghost" id="cancel">Cancelar</button>' : ''}</div>
      </form>
      ${hasTmdb() ? `<section class="panel wide" id="tsp">
        <h2>Autocompletar serie desde TMDB (opcional)</h2>
        <form class="newlist" id="tss"><input name="q" placeholder="Título de la serie…"><button class="btn sm">Buscar</button></form>
        <div id="tsres"></div>
        <div class="pform"><label>Temporadas para generar la lista de episodios <small class="muted">vacío = todas; o escribe por ejemplo 2 o 1-3</small><input id="tsn" maxlength="30" placeholder="todas" autocomplete="off"></label></div>
        <p class="muted" style="font-size:.82rem;margin-top:10px">Rellena título, año, calificación, géneros, sinopsis, póster y fondo, y deja los episodios con sus títulos listos (una línea por episodio, sin enlace). Tú agregas el enlace del video de cada línea y confirmas la licencia.</p>
      </section>` : ''}
      <form id="sf" class="panel wide pform" autocomplete="off">
        <h2>${curSerie ? 'Editar serie' : 'Nueva serie'}</h2>
        <label>Título *<input name="titulo" required maxlength="120" value="${esc(curSerie?.titulo ?? '')}"></label>
        <div class="three">
          <label>Año de estreno<input name="anio" inputmode="numeric" maxlength="4" value="${esc(curSerie?.anio ?? '')}"></label>
          <label>Calificación (0–10)<input name="calificacion" inputmode="decimal" maxlength="4" value="${esc(curSerie?.calificacion ?? '')}"></label>
        </div>
        <label>Género(s) <small class="muted">separados por coma; solo el primero cuenta: es la categoría de la serie</small><input name="generos" maxlength="120" value="${esc(curSerie?.generos ?? '')}"></label>
        <label>Sinopsis<textarea name="sinopsis" rows="4" maxlength="1500">${esc(curSerie?.sinopsis ?? '')}</textarea></label>
        <label>Episodios * <small class="muted">una línea por servidor: <b>TEMPORADAxEPISODIO | Título | idioma | enlace</b>. Idioma: <b>lat</b> (Latino), <b>en</b> (English), <b>ensub</b> (English sub. español) o vacío. Para ofrecer varios idiomas del mismo episodio, repite la línea con otro idioma. Opcional: un 5.º campo con el nombre del servidor.</small><textarea name="episodios" rows="10" required spellcheck="false" placeholder="1x01 | Piloto | lat | https://…/ep1-latino.mp4&#10;1x01 | Pilot | en | https://…/ep1-en.mp4&#10;1x02 | Segundo episodio | lat | https://…/ep2.mp4">${esc(curSerie ? serieText(curSerie) : '')}</textarea></label>
        <label>Póster (enlace https a una imagen vertical) <small class="muted">si lo dejas vacío, se busca por título en TMDB</small><input name="poster" type="url" value="${esc(curSerie?.poster ?? '')}"></label>
        <label>Imagen de fondo para la portada <small class="muted">opcional, horizontal</small><input name="fondo" type="url" value="${esc(curSerie?.fondo ?? '')}"></label>
        <label>Licencia *<select name="licencia" required><option value="">Elige…</option>${Object.entries(LICENCIAS).map(([k, t]) => `<option value="${k}" ${curSerie?.licencia === k ? 'selected' : ''}>${t}</option>`).join('')}</select></label>
        <label>Créditos / atribución / enlace a la licencia<input name="creditos" maxlength="300" value="${esc(curSerie?.creditos ?? '')}"></label>
        <label class="chk"><input type="checkbox" name="ok" required> <span>Confirmo que tengo los derechos para publicar esta serie o que es de dominio público / licencia libre.</span></label>
        <div class="actions"><button class="btn">${curSerie ? 'Guardar cambios' : 'Publicar serie'}</button>${curSerie ? '<button type="button" class="btn ghost" id="scancel">Cancelar</button>' : ''}</div>
      </form>
      ${(() => {
        const row = (p, k) => `<div class="arow"><a href="#/${k === 'draft' ? 'vista' : 'ver'}/${encodeURIComponent(p.id)}"><b>${esc(p.titulo || '(sin título)')}</b></a><span class="muted">${k === 'sched' ? '🕒 ' + esc(new Date(p.publicarEn).toLocaleString('es', { dateStyle: 'medium', timeStyle: 'short' })) : k === 'draft' ? 'Borrador' : esc(LICENCIAS[p.licencia] || '')}${isAdmin() && p.por ? ' · ' + esc(p.por) : ''}</span>${k !== 'pub' ? `<button class="btn ghost sm" data-now="${esc(p.id)}" data-k="${k}">Publicar ahora</button>` : ''}<button class="btn ghost sm" data-edit="${esc(p.id)}">Editar</button><button class="btn ghost sm" data-del="${esc(p.id)}" data-k="${k}">Eliminar</button></div>`;
        return `<h2 style="margin-top:28px">Publicadas (${pubs.length})</h2><div class="adminlist" id="lpub">${pubs.map(p => row(p, 'pub')).join('') || '<p class="muted">Aún no hay nada.</p>'}</div>
        <h2 style="margin-top:24px">Series (${seriesL.length})</h2><div class="adminlist" id="lser">${seriesL.map(p => `<div class="arow"><a href="#/ver/${encodeURIComponent(p.id)}"><b>${esc(p.titulo || '(sin título)')}</b></a><span class="muted">${epsOf(p).length} temp. · ${countEps(p)} ep. · ${esc(LICENCIAS[p.licencia] || '')}${isAdmin() && p.por ? ' · ' + esc(p.por) : ''}</span><button class="btn ghost sm" data-edit="${esc(p.id)}">Editar</button><button class="btn ghost sm" data-del="${esc(p.id)}" data-k="pub">Eliminar</button></div>`).join('') || '<p class="muted">Aún no hay series.</p>'}</div>
        ${sched.length ? `<h2 style="margin-top:24px">Programadas (${sched.length})</h2><div class="adminlist" id="lsched">${sched.map(p => row(p, 'sched')).join('')}</div>` : ''}
        ${drafts.length ? `<h2 style="margin-top:24px">Borradores (${drafts.length})</h2><div class="adminlist" id="ldraft">${drafts.map(p => row(p, 'draft')).join('')}</div>` : ''}`;
      })()}
      <section class="panel wide" id="bkp" style="margin-top:28px">
        <h2>Copia de seguridad</h2>
        <p class="muted" style="font-size:.88rem">Descarga ${isAdmin() ? 'todas las películas, los borradores y el equipo' : 'tus películas y borradores'} en un archivo .json. No incluye las reacciones, las favoritas de las personas, los reportes ni los videos. <span id="bklast"></span></p>
        <div class="actions"><button type="button" class="btn ghost" id="bk">⬇ Descargar copia</button>${isAdmin() ? '<button type="button" class="btn ghost" id="bkr">⬆ Restaurar desde un archivo…</button><input type="file" id="bkfile" accept=".json,application/json" hidden>' : ''}</div>
      </section>
      ${isAdmin() ? teamHtml(team) : ''}
    </div>`;

    const f = document.getElementById('pf');
    const NOMBRES = { youtube: 'YouTube', vimeo: 'Vimeo', archive: 'Internet Archive', file: 'archivo de video', hls: 'streaming HLS' };
    const list_ = document.getElementById('srvlist');
    const updRow = row => {
      const val = row.querySelector('.su').value.trim(), s = val ? parseSource(val) : null;
      row.querySelector('.sinfo').textContent = !val ? '' : s ? '✓ Detectado: ' + (s.tipo === 'embed' ? 'reproductor de ' + s.host : NOMBRES[s.tipo]) : '✗ Enlace no reconocido. Debe ser https y de un sitio compatible o autorizado en config.js (EMBED_HOSTS).';
    };
    list_.addEventListener('input', e => { const row = e.target.closest('.srow'); if (row) updRow(row); });
    list_.addEventListener('click', e => {
      const b = e.target.closest('.srow-rm'); if (!b) return;
      const rows = list_.querySelectorAll('.srow');
      if (rows.length > 1) b.closest('.srow').remove(); else { const r = rows[0]; r.querySelector('.su').value = ''; r.querySelector('.sn').value = ''; updRow(r); }
    });
    document.getElementById('addsrv').onclick = () => {
      if (list_.querySelectorAll('.srow').length >= 12) return toast('Máximo 12 servidores por película.');
      const last = list_.querySelector('.srow:last-child');
      list_.insertAdjacentHTML('beforeend', srvRow({ q: 'HD', i: last ? last.querySelector('.si').value : 'lat', n: '', u: '' }));
      list_.querySelector('.srow:last-child .su').focus();
    };
    list_.querySelectorAll('.srow').forEach(updRow);
    const set = (n, val) => { f.elements[n].value = val ?? ''; };

    // Modo de publicación: ahora / programada / borrador (el borrador puede quedar incompleto)
    const applyModo = () => {
      const m = f.elements.modo.value, bor = m === 'borrador';
      for (const n of ['licencia', 'ok']) f.elements[n].required = !bor;
      f.elements.cuando.required = m === 'programar';
      document.getElementById('cuandoBox').hidden = m !== 'programar';
      f.querySelector('.actions .btn').textContent = bor ? 'Guardar borrador' : m === 'programar' ? 'Programar' : curPub && modoIni === 'ahora' ? 'Guardar cambios' : 'Publicar';
    };
    f.querySelectorAll('[name=modo]').forEach(r => { r.onchange = applyModo; });
    applyModo();

    f.addEventListener('submit', async e => {
      e.preventDefault();
      const g = n => f.elements[n].value.trim();
      const modo = f.elements.modo.value, bor = modo === 'borrador';
      const rows = [...list_.querySelectorAll('.srow')].map(r => ({ q: 'HD', i: r.querySelector('.si').value, n: r.querySelector('.sn').value.trim().slice(0, 30), u: r.querySelector('.su').value.trim() })).filter(x => x.u);
      if (!bor && !rows.length) return toast('Agrega al menos un servidor con el enlace del video.');
      if (rows.some(x => !parseSource(x.u))) return toast('Hay un enlace de video no compatible. Usa YouTube, Vimeo, Dailymotion, Google Drive, Streamable, Odysee, Rumble, Bunny, Cloudflare Stream, Internet Archive, un .mp4/.webm/.m3u8, o autoriza tu sitio en config.js (EMBED_HOSTS).');
      const sorted = serversOf({ servidores: rows });
      const mainUrl = sorted[0] ? sorted[0].u : '';
      const calidades = ['HD'];
      if (!bor && !g('licencia')) return toast('Elige la licencia.');
      let when = 0;
      if (modo === 'programar') { when = new Date(g('cuando')).getTime(); if (!(when > Date.now() + 60000)) return toast('Elige una fecha y hora futuras para programar.'); }
      for (const [n, t] of [['poster', 'El póster'], ['fondo', 'La imagen de fondo']]) if (g(n) && !httpsUrl(g(n))) return toast(t + ' debe ser un enlace https.');
      if (g('trailer')) { const s = parseSource(g('trailer')); if (!s || !['youtube', 'vimeo'].includes(s.tipo)) return toast('El tráiler debe ser un enlace de YouTube o Vimeo.'); }
      if (!bor && ['cc', 'permiso'].includes(g('licencia')) && !g('creditos')) return toast('Indica los créditos o el enlace de la licencia.');
      const cal = g('calificacion').replace(',', '.');
      if (cal && !(Number(cal) >= 0 && Number(cal) <= 10)) return toast('La calificación va de 0 a 10.');
      // Sin imágenes: 1.º la portada oficial buscada en TMDB por título (y año); 2.º la miniatura del video (YouTube / Internet Archive)
      if (!g('poster') && !g('fondo')) {
        let t = hasTmdb() && g('titulo') ? await tmdbArt(g('titulo'), g('anio')) : null;
        if (t) toast(`Portada oficial tomada de TMDB: “${t.nombre}”. Si no es la película correcta, cámbiala editando.`);
        else if (mainUrl) { t = await autoThumb(mainUrl); if (t.poster || t.fondo) toast('No se halló la portada oficial; se usó la miniatura del video.'); }
        if (t && (t.poster || t.fondo)) { f.elements.poster.value = t.poster || ''; f.elements.fondo.value = t.fondo || ''; }
      }
      const id = editing || 'p' + Date.now().toString(36);
      const d = { subtitulos: cur?.subtitulos || [], versiones: cur?.versiones || [], audioNombre: cur?.audioNombre || '', calificacion: cal ? Number(Number(cal).toFixed(1)) : '', titulo: g('titulo'), anio: g('anio'), duracion: g('duracion'), generos: g('generos'), calidad: calidades[0] || '', calidades, servidores: rows, sinopsis: g('sinopsis'), poster: g('poster'), fondo: g('fondo'), trailer: g('trailer'), url: mainUrl, licencia: g('licencia'), creditos: g('creditos'), creado: modo === 'programar' ? when : (cur && (bor || (curPub && !(curPub.publicarEn > Date.now())))) ? (cur.creado || Date.now()) : Date.now(), publicarEn: when, actualizado: Date.now(), por: cur?.por || user.email };
      try {
        if (bor) { await Cloud.saveBorrador(id, d); if (curPub) await Cloud.deletePelicula(id); }
        else { await Cloud.savePelicula(id, d); if (curDraft) await Cloud.deleteBorrador(id); }
        bust(); editing = null; toast(bor ? 'Borrador guardado.' : modo === 'programar' ? 'Película programada.' : 'Película publicada.'); adminPage();
      }
      catch (err) { toast('No se pudo guardar: ' + (err.code || err.message)); }
    });

    // ---- Series: formulario ----
    const sf = document.getElementById('sf');
    const LANG = { lat: 'lat', latino: 'lat', en: 'en', english: 'en', ingles: 'en', ensub: 'ensub', sub: 'ensub', subtitulado: 'ensub', '': '' };
    const parseEps = txt => {
      const temps = new Map(), errs = [];
      txt.split(/\n/).map(l => l.trim()).filter(Boolean).forEach((line, i) => {
        const c = line.split('|').map(x => x.trim());
        const m = (c[0] || '').match(/^(?:s|t)?\s*(\d{1,2})\s*[xe]\s*(\d{1,3})$/i);
        if (!m || c.length < 4) return errs.push(`Línea ${i + 1}: usa “1x01 | Título | idioma | enlace”.`);
        const lang = LANG[norm(c[2])];
        if (lang === undefined) return errs.push(`Línea ${i + 1}: idioma no válido (usa lat, en, ensub o déjalo vacío).`);
        if (!parseSource(c[3])) return errs.push(`Línea ${i + 1}: enlace de video no válido o no compatible (debe ser https).`);
        const T = Number(m[1]), E = Number(m[2]);
        if (!temps.has(T)) temps.set(T, new Map());
        const eps = temps.get(T);
        if (!eps.has(E)) eps.set(E, { n: E, titulo: c[1], servidores: [] });
        const ep = eps.get(E);
        if (c[1] && !ep.titulo) ep.titulo = c[1];
        ep.servidores.push({ q: 'HD', i: lang, n: c[4] || '', u: c[3] });
      });
      return { errs, temporadas: [...temps.entries()].sort((a, b) => a[0] - b[0]).map(([n, eps]) => ({ n, episodios: [...eps.values()].sort((a, b) => a.n - b.n) })) };
    };
    if (sf) {
      const sc = document.getElementById('scancel');
      if (sc) sc.onclick = () => { editing = null; adminPage(); };
      sf.addEventListener('submit', async e => {
        e.preventDefault();
        const btn = sf.querySelector('button.btn');
        btn.disabled = true;
        try {
          const g = n => sf.elements[n].value.trim();
          if (!g('titulo')) return toast('Falta el título.');
          if (!g('licencia')) return toast('Elige la licencia.');
          if (['cc', 'permiso'].includes(g('licencia')) && !g('creditos')) return toast('Indica los créditos o el enlace de la licencia.');
          for (const [n, t] of [['poster', 'El póster'], ['fondo', 'La imagen de fondo']]) if (g(n) && !httpsUrl(g(n))) return toast(t + ' debe ser un enlace https.');
          const cal = g('calificacion').replace(',', '.');
          if (cal && !(Number(cal) >= 0 && Number(cal) <= 10)) return toast('La calificación va de 0 a 10.');
          const { errs, temporadas } = parseEps(g('episodios'));
          if (errs.length) return toast(errs[0]);
          if (!temporadas.length) return toast('Agrega al menos un episodio.');
          if (!g('poster') && !g('fondo') && hasTmdb()) {
            const t = await tmdbArt(g('titulo'), g('anio'), true);
            if (t) { sf.elements.poster.value = t.poster || ''; sf.elements.fondo.value = t.fondo || ''; toast(`Portada oficial tomada de TMDB: “${t.nombre}”. Si no es la serie correcta, cámbiala editando.`); }
          }
          const id = curSerie ? curSerie.id : 's' + Date.now().toString(36);
          const d = { tipo: 'serie', titulo: g('titulo'), anio: g('anio'), calificacion: cal ? Number(Number(cal).toFixed(1)) : '', generos: g('generos'), sinopsis: g('sinopsis'), poster: g('poster'), fondo: g('fondo'), licencia: g('licencia'), creditos: g('creditos'), temporadas, calidad: 'HD', calidades: ['HD'], url: temporadas[0].episodios[0].servidores[0].u, creado: curSerie ? (curSerie.creado || Date.now()) : Date.now(), publicarEn: 0, actualizado: Date.now(), por: curSerie?.por || user.email };
          await Cloud.savePelicula(id, d);
          bust(); editing = null; toast(curSerie ? 'Serie actualizada.' : 'Serie publicada.'); adminPage();
        } catch (err) { toast('No se pudo guardar: ' + (err.code || err.message)); }
        finally { btn.disabled = false; }
      });
    }

    app.querySelectorAll('.adminlist').forEach(box => box.addEventListener('click', async e => {
      const b = e.target.closest('button');
      if (!b) return;
      if (b.dataset.edit) { editing = b.dataset.edit; adminPage(); window.scrollTo(0, 0); }
      else if (b.dataset.now) {
        const draftK = b.dataset.k === 'draft', src = (draftK ? drafts : list).find(x => x.id === b.dataset.now);
        if (!src) return;
        if (draftK && (!parseSource(src.url) || !src.licencia)) return toast('Antes de publicar, edita el borrador: falta un enlace de video válido o la licencia.');
        if (draftK && !confirm('¿Confirmas que tienes los derechos para publicar esta película o que es de dominio público / licencia libre?')) return;
        const d = { ...src, publicarEn: 0, creado: Date.now(), actualizado: Date.now() };
        delete d.id;
        try { await Cloud.savePelicula(src.id, d); if (draftK) await Cloud.deleteBorrador(src.id); bust(); toast('Publicada.'); adminPage(); }
        catch (err) { toast('No se pudo publicar: ' + (err.code || err.message)); }
      }
      else if (b.dataset.del && confirm(b.dataset.k === 'draft' ? '¿Eliminar este borrador?' : '¿Eliminar esta película del catálogo?')) {
        try { if (b.dataset.k === 'draft') await Cloud.deleteBorrador(b.dataset.del); else await Cloud.deletePelicula(b.dataset.del); bust(); toast('Eliminada.'); adminPage(); }
        catch (err) { toast('No se pudo eliminar: ' + (err.code || err.message)); }
      }
    }));

    // Copia de seguridad (descargar) y restauración (solo administrador)
    const BK = ['titulo', 'anio', 'duracion', 'calificacion', 'generos', 'calidad', 'calidades', 'servidores', 'sinopsis', 'poster', 'fondo', 'trailer', 'url', 'licencia', 'creditos', 'creado', 'actualizado', 'por', 'subtitulos', 'versiones', 'audioNombre', 'publicarEn', 'tipo', 'temporadas'];
    const pick = x => { const o = {}; for (const k of BK) if (x[k] !== undefined) o[k] = x[k]; return o; };
    const bkLast = () => { const t = Number(lsGet(LS_BK)); document.getElementById('bklast').textContent = t ? `Última copia: hace ${Math.max(0, Math.floor((Date.now() - t) / 864e5))} día(s).` : 'Aún no has descargado ninguna copia.'; };
    bkLast();
    document.getElementById('bk').onclick = () => {
      const out = { app: 'pelipe', version: 1, exportado: new Date().toISOString(), peliculas: [...list, ...seriesL].map(p => ({ id: p.id, ...pick(p) })), borradores: drafts.map(p => ({ id: p.id, ...pick(p) })), editores: isAdmin() ? team.map(t => ({ id: t.id, email: t.email || t.id, por: t.por || '' })) : [] };
      const a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([JSON.stringify(out, null, 2)], { type: 'application/json' }));
      a.download = `pelipe-copia-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 5000);
      lsSet(LS_BK, String(Date.now())); bkLast();
    };
    const bkf = document.getElementById('bkfile');
    if (bkf) {
      document.getElementById('bkr').onclick = () => bkf.click();
      bkf.onchange = async () => {
        const file = bkf.files[0];
        bkf.value = '';
        if (!file) return;
        let j;
        try { j = JSON.parse(await file.text()); } catch { return toast('El archivo no es una copia válida.'); }
        if (!j || j.app !== 'pelipe' || !Array.isArray(j.peliculas)) return toast('El archivo no es una copia de Pelipe.');
        const okId = x => x && /^[\w-]{1,60}$/.test(String(x.id || '')) && typeof x.titulo === 'string';
        const P = j.peliculas.filter(okId), B = (Array.isArray(j.borradores) ? j.borradores : []).filter(okId);
        const E = (Array.isArray(j.editores) ? j.editores : []).map(x => String((x && (x.email || x.id)) || '').toLowerCase()).filter(m => /^[^\s@/]+@[^\s@/]+\.[^\s@/]+$/.test(m));
        if (!confirm(`Restaurar ${P.length} película(s), ${B.length} borrador(es) y ${E.length} editor(es)?\nSi ya existen con el mismo ID, se reemplazan.`)) return;
        let n = 0;
        try {
          for (const x of P) { await Cloud.savePelicula(String(x.id), pick(x)); n++; }
          for (const x of B) { await Cloud.saveBorrador(String(x.id), pick(x)); n++; }
          for (const m of E) { await Cloud.addEditor(m, user.email); n++; }
        } catch (err) { toast(`Se restauraron ${n} elemento(s) y luego falló: ${err.code || err.message}`); bust(); adminPage(); return; }
        bust(); toast(`Copia restaurada (${n} elementos).`); adminPage();
      };
    }
    const repsEl = document.getElementById('reps');
    if (repsEl) repsEl.addEventListener('click', async e => {
      const b = e.target.closest('[data-rres]');
      if (!b) return;
      try { await Cloud.deleteReporte(b.dataset.rres); toast('Reporte resuelto.'); adminPage(); }
      catch (err) { toast('No se pudo cerrar el reporte: ' + (err.code || err.message)); }
    });
    const cancel = document.getElementById('cancel');
    if (cancel) cancel.onclick = () => { editing = null; adminPage(); };

    const tf = document.getElementById('tf');
    if (tf) {
      tf.addEventListener('submit', async e => {
        e.preventDefault();
        const em = tf.elements.email.value.trim().toLowerCase();
        if (!/^[^\s@/]+@[^\s@/]+\.[^\s@/]+$/.test(em)) return toast('Escribe un correo válido.');
        try { await Cloud.addEditor(em, user.email); toast('Acceso concedido a ' + em); adminPage(); }
        catch (err) { toast('No se pudo dar acceso: ' + (err.code || err.message)); }
      });
      document.getElementById('team').addEventListener('click', async e => {
        const b = e.target.closest('[data-rmeditor]');
        if (!b || !confirm('¿Quitar el acceso a ' + b.dataset.rmeditor + '?')) return;
        try { await Cloud.removeEditor(b.dataset.rmeditor); toast('Acceso retirado.'); adminPage(); }
        catch (err) { toast('No se pudo quitar: ' + (err.code || err.message)); }
      });
    }


    // Autocompletar desde TMDB (solo si hay clave)
    const ts = document.getElementById('ts');
    if (ts) {
      ts.addEventListener('submit', async e => {
        e.preventDefault();
        const q = ts.elements.q.value.trim();
        if (!q) return;
        const box = document.getElementById('tres');
        box.innerHTML = '<p class="muted">Buscando…</p>';
        try {
          const rs = (await tmdb('/search/movie', { query: q, include_adult: 'false' })).results.slice(0, 8);
          box.innerHTML = rs.length
            ? rs.map(m => `<div class="arow"><span><b>${esc(m.title)}</b> <span class="muted">${esc((m.release_date || '').slice(0, 4))}</span></span><button class="btn ghost sm" data-tm="${m.id}">Usar</button></div>`).join('')
            : '<p class="muted">Sin resultados.</p>';
        } catch (err) { box.innerHTML = '<p class="muted">No se pudo consultar TMDB. Revisa tu clave.</p>'; }
      });
      document.getElementById('tres').addEventListener('click', async e => {
        const b = e.target.closest('[data-tm]');
        if (!b) return;
        try {
          const m = await tmdb('/movie/' + b.dataset.tm, { append_to_response: 'videos', include_video_language: 'es,en,null' });
          const vids = (m.videos?.results || []).filter(x => x.site === 'YouTube' && x.type === 'Trailer');
          const tr = vids.find(x => x.iso_639_1 === 'es') || vids[0];
          set('titulo', m.title);
          set('anio', (m.release_date || '').slice(0, 4));
          set('duracion', m.runtime || '');
          set('calificacion', m.vote_count > 0 && m.vote_average ? m.vote_average.toFixed(1) : '');
          set('generos', (m.genres || []).map(x => x.name).join(', '));
          set('sinopsis', m.overview || '');
          set('poster', m.poster_path ? 'https://image.tmdb.org/t/p/w500' + m.poster_path : '');
          set('fondo', m.backdrop_path ? 'https://image.tmdb.org/t/p/w1280' + m.backdrop_path : '');
          set('trailer', tr ? 'https://www.youtube.com/watch?v=' + tr.key : '');
          f.scrollIntoView({ behavior: 'smooth' });
        } catch (err) { toast('No se pudieron leer los datos de TMDB.'); }
      });
    }

    // Autocompletar series desde TMDB: datos de la ficha + lista de episodios con sus títulos (sin enlaces)
    const tss = document.getElementById('tss');
    if (tss && sf) {
      const setS = (n, val) => { sf.elements[n].value = val ?? ''; };
      tss.addEventListener('submit', async e => {
        e.preventDefault();
        const q = tss.elements.q.value.trim();
        if (!q) return;
        const box = document.getElementById('tsres');
        box.innerHTML = '<p class="muted">Buscando…</p>';
        try {
          const rs = (await tmdb('/search/tv', { query: q, include_adult: 'false' })).results.slice(0, 8);
          box.innerHTML = rs.length
            ? rs.map(m => `<div class="arow"><span><b>${esc(m.name)}</b> <span class="muted">${esc((m.first_air_date || '').slice(0, 4))}</span></span><button class="btn ghost sm" data-ts="${m.id}">Usar</button></div>`).join('')
            : '<p class="muted">Sin resultados.</p>';
        } catch (err) { box.innerHTML = '<p class="muted">No se pudo consultar TMDB. Revisa tu clave.</p>'; }
      });
      // "2" → [2]; "1-3" → [1,2,3]; "1,3" → [1,3]; vacío → todas
      const pickSeasons = (txt, all) => {
        const t = txt.trim();
        if (!t) return all;
        const out = new Set();
        for (const part of t.split(/[,;\s]+/).filter(Boolean)) {
          const r = part.match(/^(\d+)\s*-\s*(\d+)$/);
          if (r) for (let i = Number(r[1]); i <= Number(r[2]) && i < 100; i++) out.add(i);
          else if (/^\d+$/.test(part)) out.add(Number(part));
        }
        return all.filter(n => out.has(n));
      };
      document.getElementById('tsres').addEventListener('click', async e => {
        const b = e.target.closest('[data-ts]');
        if (!b) return;
        b.disabled = true;
        try {
          const m = await tmdb('/tv/' + b.dataset.ts);
          setS('titulo', m.name);
          setS('anio', (m.first_air_date || '').slice(0, 4));
          setS('calificacion', m.vote_count > 0 && m.vote_average ? m.vote_average.toFixed(1) : '');
          setS('generos', (m.genres || []).map(x => x.name).join(', '));
          setS('sinopsis', m.overview || '');
          setS('poster', m.poster_path ? 'https://image.tmdb.org/t/p/w500' + m.poster_path : '');
          setS('fondo', m.backdrop_path ? 'https://image.tmdb.org/t/p/w1280' + m.backdrop_path : '');
          const all = (m.seasons || []).map(x => x.season_number).filter(n => n >= 1);
          const want = pickSeasons(document.getElementById('tsn').value, all);
          let note = '';
          const cur = sf.elements.episodios.value;
          if (cur.trim()) {
            // Ya hay episodios escritos: se conservan los enlaces y solo se ponen los nombres oficiales donde el título falta o es genérico ("Episodio 3")
            const rows = cur.split('\n');
            const ts = [...new Set(rows.map(l => (l.trim().split('|')[0].trim().match(/^(?:s|t)?\s*(\d{1,2})\s*[xe]\s*\d{1,3}$/i) || [])[1]).filter(Boolean).map(Number))].filter(n => all.includes(n));
            const seas = await Promise.all(ts.map(n => tmdb('/tv/' + m.id + '/season/' + n).catch(() => null)));
            const names = new Map();
            seas.forEach((t, i) => t && (t.episodes || []).forEach(x => names.set(ts[i] + 'x' + x.episode_number, String(x.name || '').replace(/\|/g, '-').trim())));
            let n = 0;
            sf.elements.episodios.value = rows.map(l => {
              const c = l.split('|'), k = (c[0] || '').trim().match(/^(?:s|t)?\s*(\d{1,2})\s*[xe]\s*(\d{1,3})$/i);
              if (!k || c.length < 4) return l;
              const nm = names.get(Number(k[1]) + 'x' + Number(k[2])), old = (c[1] || '').trim();
              if (!nm || (old && !/^episodio\s*\d+$/i.test(old))) return l;
              n++; c[1] = ' ' + nm + ' '; return c.join('|');
            }).join('\n');
            note = n ? ` Puse el nombre oficial a ${n} episodio(s); los enlaces no cambiaron.` : ' Tus episodios ya tenían nombre; no cambié nada.';
          } else if (want.length) {
            const seas = await Promise.all(want.map(n => tmdb('/tv/' + m.id + '/season/' + n).catch(() => null)));
            const lines = seas.flatMap((t, i) => t ? (t.episodes || []).map(x => `${want[i]}x${String(x.episode_number).padStart(2, '0')} | ${String(x.name || '').replace(/\|/g, '-').trim()} | lat | `) : []);
            setS('episodios', lines.join('\n'));
            note = lines.length ? ` Dejé ${lines.length} episodio(s) con su título: agrega el enlace al final de cada línea (y borra las que no subirás).` : '';
          }
          toast('Datos de TMDB cargados.' + note);
          sf.scrollIntoView({ behavior: 'smooth' });
        } catch (err) { toast('No se pudieron leer los datos de TMDB.'); }
        b.disabled = false;
      });
    }
  }

  /* ---------- acciones globales ---------- */
  document.addEventListener('click', e => {
    const t = e.target.closest('.login,.logout,.rm,.dellist');
    if (!t) return;
    if (t.classList.contains('login')) {
      Cloud.signIn().catch(err => {
        if (err && (err.code === 'auth/popup-closed-by-user' || err.code === 'auth/cancelled-popup-request')) return;
        toast('No se pudo iniciar sesión (' + ((err && (err.code || err.message)) || 'error') + ').');
      });
    } else if (t.classList.contains('logout')) {
      Cloud.signOut();
    } else if (t.classList.contains('rm')) {
      const id = String(t.dataset.id);
      if (t.dataset.k === 'fav') data.favoritos = data.favoritos.filter(x => String(x.id) !== id);
      else { const l = data.listas.find(x => x.id === t.dataset.l); if (l) l.items = l.items.filter(x => String(x.id) !== id); }
      persist();
      route();
    } else if (t.classList.contains('dellist')) {
      if (!confirm('¿Eliminar esta lista?')) return;
      data.listas = data.listas.filter(x => x.id !== t.dataset.l);
      persist();
      location.hash = '#/listas';
    }
  });

  /* ---------- router ---------- */
  const routes = [
    [/^#\/ver\/([^?]+)/, m => watchPage(m[1])],
    [/^#\/vista\/([^?]+)/, m => watchPage(m[1], true)],
    [/^#\/catalogo/, () => catalogPage()],
    [/^#\/series/, () => catalogPage('series')],
    [/^#\/(?:explorar|pelicula)/, () => { location.replace('#/catalogo'); }],
    [/^#\/sorpresa/, surprise],
    [/^#\/proximas/, proximas],
    [/^#\/generos/, genresPage],
    [/^#\/favoritas/, favoritas],
    [/^#\/listas\/([^?]+)/, m => listaDetalle(m[1])],
    [/^#\/listas/, listas],
    [/^#\/admin/, adminPage],
  ];
  window.addEventListener('pagehide', () => { if (leave) leave(); });
  function route() {
    if (leave) { try { leave(); } catch { } leave = null; } // guarda el progreso de la película que se deja
    if (pager) { pager.disconnect(); pager = null; }
    closeModal();
    document.title = 'Pelipe.online — Películas y series libres para ver en español';
    const h = location.hash || '#/';
    for (const [re, fn] of routes) { const m = h.match(re); if (m) { fn(m); window.scrollTo(0, 0); return; } }
    home();
  }
  window.addEventListener('hashchange', route);

  // Buscador con sugerencias del catálogo publicado (títulos que empiezan o contienen lo escrito)
  {
    const form = document.getElementById('search'), inp = form.q, box = document.getElementById('sugs');
    let t, act = -1, items = [], seq = 0;
    const close = () => { box.hidden = true; box.innerHTML = ''; inp.setAttribute('aria-expanded', 'false'); inp.removeAttribute('aria-activedescendant'); act = -1; items = []; };
    const mark = i => {
      act = i;
      box.querySelectorAll('.sug').forEach((a, k) => { a.classList.toggle('on', k === i); a.setAttribute('aria-selected', k === i ? 'true' : 'false'); });
      if (i >= 0) { inp.setAttribute('aria-activedescendant', 'sug-' + i); const el = document.getElementById('sug-' + i); if (el) el.scrollIntoView({ block: 'nearest' }); } else inp.removeAttribute('aria-activedescendant');
    };
    const score = (p, q) => {
      const ti = norm(p.titulo || ''), words = ti.split(/\s+/);
      if (ti.startsWith(q)) return 0;
      if (words.some(w => w.startsWith(q))) return 1;
      if (ti.includes(q)) return 2;
      if (norm(p.generos || '').includes(q)) return 3;
      return norm(p.sinopsis || '').includes(q) ? 4 : -1;
    };
    async function suggest() {
      const raw = inp.value.trim(), q = norm(raw), my = ++seq;
      if (q.length < 2) return close();
      let list = [];
      try { list = [...await catalog(), ...await seriesCat()]; } catch { }
      if (my !== seq) return;
      items = list.map(p => ({ p, s: score(p, q) })).filter(x => x.s >= 0).sort((a, b) => a.s - b.s || (a.p.titulo || '').localeCompare(b.p.titulo || '', 'es')).slice(0, 6).map(x => x.p);
      box.innerHTML = (items.length
        ? items.map((p, i) => `<a class="sug" role="option" id="sug-${i}" aria-selected="false" href="#/ver/${encodeURIComponent(p.id)}">${httpsUrl(p.poster) ? `<img src="${esc(tmdbImg(p.poster, 154))}" alt="" loading="lazy">` : '<span class="sug-ph"></span>'}<span class="sug-t"><b>${esc(p.titulo)}</b><small>${esc([p.anio, gensOf(p)[0] && cap(gensOf(p)[0]), qualsOf(p).join(' · ')].filter(Boolean).join(' · '))}</small></span></a>`).join('')
        : '<p class="sug-none">No hay películas con ese nombre en el catálogo.</p>')
        + `<a class="sug-all" href="#/catalogo?q=${encodeURIComponent(raw)}">Ver todos los resultados para “${esc(raw)}”</a>`;
      box.hidden = false; inp.setAttribute('aria-expanded', 'true'); act = -1;
    }
    inp.addEventListener('input', () => { clearTimeout(t); t = setTimeout(suggest, 130); });
    inp.addEventListener('focus', () => { if (inp.value.trim().length >= 2) suggest(); });
    inp.addEventListener('keydown', e => {
      if (box.hidden) return;
      if (e.key === 'Escape') { close(); return; }
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        if (!items.length) return;
        e.preventDefault();
        mark(e.key === 'ArrowDown' ? (act + 1) % items.length : (act <= 0 ? items.length - 1 : act - 1));
      }
    });
    form.addEventListener('submit', e => {
      e.preventDefault();
      clearTimeout(t); seq++;
      const q = inp.value.trim();
      if (act >= 0 && items[act]) { location.hash = '#/ver/' + encodeURIComponent(items[act].id); inp.blur(); close(); return; }
      close();
      if (q) location.hash = '#/catalogo?q=' + encodeURIComponent(q);
    });
    box.addEventListener('mousedown', e => e.preventDefault()); // no pierde el foco al hacer clic
    box.addEventListener('click', e => { if (e.target.closest('a')) { inp.blur(); close(); } });
    document.addEventListener('click', e => { if (!e.target.closest('#search')) close(); });
    window.addEventListener('hashchange', close);
  }

  /* ---------- arranque ---------- */
  const legal = [];
  if (hasTmdb()) legal.push('');
  if (C.CONTACT_EMAIL) legal.push(`Anuncia con nosotros, escribe a: <a href="mailto:${esc(C.CONTACT_EMAIL)}"><u>${esc(C.CONTACT_EMAIL)}</u></a>.`);
  document.getElementById('legal').innerHTML = legal.join('<br>');

  /* ---------- instalar como app ---------- */
  const installEl = document.getElementById('install');
  const isStandalone = () => matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
  const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  let deferredInstall = null;
  const showInstall = () => { if (installEl && !isStandalone()) installEl.hidden = false; };
  window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); deferredInstall = e; showInstall(); });
  window.addEventListener('appinstalled', () => { deferredInstall = null; if (installEl) installEl.hidden = true; toast('¡Pelipe instalada!'); });
  if (isIOS) showInstall();
  if (installEl) installEl.onclick = async () => {
    if (deferredInstall) { const d = deferredInstall; deferredInstall = null; installEl.hidden = true; d.prompt(); try { await d.userChoice; } catch { } }
    else openModal('<div class="panel"><h2>Instalar Pelipe</h2><p class="muted" style="margin-top:8px">En Safari toca el botón <b>Compartir</b> (el cuadro con una flecha hacia arriba) y elige <b>“Añadir a pantalla de inicio”</b>.</p></div>');
  };
  if ('serviceWorker' in navigator && location.protocol.startsWith('http')) navigator.serviceWorker.register('sw.js').catch(() => { });

  /* ---------- pie: personas en línea y totales ---------- */
  // En línea = visitas que dieron señal de vida en los últimos 6 min (cada pestaña visible avisa cada 4 min).
  const LS_STATS = 'pelipe:stats';
  const statsEl = document.getElementById('stats');
  let lastBeat = 0, presenceOn = false;
  async function beat() {
    if (document.visibilityState !== 'visible' || Date.now() - lastBeat < 240000) return;
    lastBeat = Date.now(); // si falla, no se reintenta hasta dentro de 4 min
    try { await Cloud.beat(await Cloud.ensureUid()); } catch { }
  }
  async function refreshStats() {
    if (!Cloud.enabled || !statsEl) return;
    let s = null;
    try { const c = JSON.parse(lsGet(LS_STATS)); if (c && 'series' in c && Date.now() - c.t < 600000) s = c; } catch { }
    if (!s) {
      s = { t: Date.now(), pelis: null, series: null, reacc: null };
      try { s.pelis = (await catalog()).length; s.series = (await seriesCat()).length; } catch { }
      try { s.reacc = await Cloud.countReacciones(); }
      catch {
        try { const rk = await ranking(await catalog()); if (rk) s.reacc = Object.values(rk.tot).reduce((a, b) => a + b, 0); } catch { }
      }
      if (s.pelis != null || s.series != null || s.reacc != null) lsSet(LS_STATS, JSON.stringify(s));
    }
    let on = null;
    try { on = await Cloud.countOnline(6); } catch { }
    const f = n => n == null ? '—' : Number(n).toLocaleString('es');
    statsEl.innerHTML = `<span title="Películas publicadas">🎬 ${f(s.pelis)} películas</span><span title="Series publicadas">📺 ${f(s.series)} series</span><span title="Personas con el sitio abierto en los últimos minutos">🟢 ${f(on)} en línea</span><span title="Reacciones en total">❤️ ${f(s.reacc)} reacciones</span>`;
  }
  function startPresence() {
    if (presenceOn) return;
    presenceOn = true;
    onActive(() => {
      beat().then(refreshStats);
      setInterval(() => { beat().then(refreshStats); }, 120000);
      document.addEventListener('visibilitychange', beat);
    });
  }

  renderAcct();
  if (!Cloud.enabled) {
    route();
  } else {
    // Las páginas públicas se pintan ya, con el catálogo estático, sin esperar a Firebase.
    // Las que dependen de tu sesión (favoritas, listas, publicar, vista previa) esperan a saber quién eres.
    const needsUser = /^#\/(favoritas|listas|admin|vista)/.test(location.hash);
    if (needsUser) loading(); else route();
    Cloud.ready.then(ok => { if (!ok) { renderAcct(); if (needsUser) route(); } }); // Firebase no cargó (bloqueado, sin red): las páginas públicas siguen con el catálogo estático
    let started = false;
    Cloud.onUser(async raw => {
      const first = !started;
      startPresence(); // ya se conoce el estado de la sesión: evita crear una cuenta anónima encima de una de Google
      // Las sesiones anónimas (visitantes que reaccionan) no cuentan como "sesión iniciada".
      const u = raw && !raw.isAnonymous ? raw : null;
      if (started && ((u && user && u.uid === user.uid) || (!u && !user))) return;
      started = true;
      user = u;
      if (u) {
        const local = readLocal();
        cloudReady = true;
        try {
          data = normalize(await Cloud.load(u.uid));
        } catch (err) {
          cloudReady = false;
          data = blank();
          toast('No se pudieron cargar tus listas de la nube.');
        }
        if (cloudReady && !isEmpty(local)) {
          // Lo guardado antes de iniciar sesión se une a la cuenta; se borra del navegador solo si la nube lo recibió.
          data = merge(data, local);
          try { await Cloud.save(u.uid, data); clearLocal(); }
          catch (err) { toast('No se pudo pasar tus listas locales a la nube.'); }
        } else if (cloudReady) clearLocal();
      } else {
        data = readLocal();
      }
      editor = false;
      if (u && u.email && !isAdmin()) {
        try { editor = await Cloud.isEditor(u.email.toLowerCase()); } catch (err) { editor = false; }
      }
      renderAcct();
      // Un visitante sin sesión que está viendo inicio, catálogo o próximas ya tiene todo pintado: no se repite
      if (first && !needsUser && !u && !editor && /^#?\/?(catalogo|proximas)?$/.test(location.hash)) return;
      route();
    });
  }
})();
