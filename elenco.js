/* Dirección y reparto bajo la sinopsis (datos de TMDB). No toca app.js: se engancha a la ficha cuando aparece.
   Usa la clave TMDB_API_KEY y el idioma LANG de config.js. Si TMDB no responde, simplemente no se muestra. */
(() => {
  const C = () => window.CONFIG || {};
  const key = () => String(C().TMDB_API_KEY || '').trim();
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const MEM = new Map(); // título+año → promesa con los datos
  const MAXCAST = 12;

  async function tm(path, params) {
    const k = key();
    const u = new URL('https://api.themoviedb.org/3' + path);
    const o = {};
    if (k.length > 40) o.headers = { Authorization: 'Bearer ' + k }; else u.searchParams.set('api_key', k);
    u.searchParams.set('language', C().LANG || 'es-MX');
    Object.entries(params || {}).forEach(([a, b]) => b !== '' && b != null && u.searchParams.set(a, b));
    const r = await fetch(u, o);
    if (!r.ok) throw new Error('TMDB ' + r.status);
    return r.json();
  }

  async function datos(titulo, anio, serie) {
    const k = (serie ? 'tv:' : 'm:') + titulo + '|' + anio;
    try { const c = sessionStorage.getItem('elenco:' + k); if (c) return JSON.parse(c); } catch (_) { }
    if (MEM.has(k)) return MEM.get(k);
    const pr = (async () => {
      const tipo = serie ? 'tv' : 'movie';
      const yk = serie ? 'first_air_date_year' : 'year';
      let rs = (await tm('/search/' + tipo, { query: titulo, include_adult: 'false', [yk]: anio })).results || [];
      if (!rs.length && anio) rs = (await tm('/search/' + tipo, { query: titulo, include_adult: 'false' })).results || [];
      if (!rs.length) return null;
      const id = rs[0].id;
      const cr = await tm('/' + tipo + '/' + id + '/credits');
      const dir = serie
        ? ((await tm('/tv/' + id)).created_by || []).map(x => ({ n: x.name, f: x.profile_path, r: 'Creación' }))
        : (cr.crew || []).filter(x => x.job === 'Director').map(x => ({ n: x.name, f: x.profile_path, r: 'Dirección' }));
      const rep = (cr.cast || []).slice(0, MAXCAST).map(x => ({ n: x.name, f: x.profile_path, r: x.character || '' }));
      const out = { dir: dir.slice(0, 3), rep };
      try { sessionStorage.setItem('elenco:' + k, JSON.stringify(out)); } catch (_) { }
      return out;
    })().catch(() => null);
    MEM.set(k, pr);
    return pr;
  }

  const ICO_DIR = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="7" width="18" height="12" rx="2"/><path d="M3 11h18M7 7l3 4M12 7l3 4M17 7l3 4"/></svg>';
  const ICO_REP = '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.6"/><path d="M5 20c.8-4 3.5-6 7-6s6.200 2 7 6"/></svg>';
  const foto = (p, n) => p
    ? `<img src="https://image.tmdb.org/t/p/w185${esc(p)}" alt="${esc(n)}" loading="lazy" decoding="async">`
    : `<span class="el-ph" aria-hidden="true">${esc((n || '?').trim().charAt(0).toUpperCase())}</span>`;
  const card = x => `<div class="el-c">${foto(x.f, x.n)}<div class="el-t"><b>${esc(x.n)}</b>${x.r ? `<small>${esc(x.r)}</small>` : ''}</div></div>`;

  function html(d) {
    return (d.dir.length ? `<h3 class="el-h">${ICO_DIR}<span>${d.dir[0].r === 'Creación' ? 'Creada por' : 'Dirección'}</span></h3><div class="el-grid el-dir">${d.dir.map(x => card({ ...x, r: x.r === 'Creación' ? 'Creador de la serie' : 'Director de la película' })).join('')}</div>` : '')
      + (d.rep.length ? `<h3 class="el-h">${ICO_REP}<span>Reparto principal</span></h3><div class="el-grid">${d.rep.map(card).join('')}</div>` : '');
  }

  let busy = false;
  async function revisar() {
    if (busy || !key()) return;
    const th = document.querySelector('.theater-in');
    const h1 = th && th.querySelector('.t-title');
    if (!h1) return;
    const small = h1.querySelector('small.muted');
    const titulo = [...h1.childNodes].filter(n => n.nodeType === 3).map(n => n.textContent).join('').trim();
    const m = small && small.textContent.match(/\((\d{4})\)/);
    const anio = m ? m[1] : '';
    if (!titulo) return;
    const serie = !!th.querySelector('.eps');
    const id = (serie ? 'tv:' : 'm:') + titulo + '|' + anio;
    const ya = th.querySelector('.elenco');
    if (ya && ya.dataset.k === id) return;
    busy = true;
    try {
      const d = await datos(titulo, anio, serie);
      const th2 = document.querySelector('.theater-in');
      if (!d || !th2 || !(d.dir.length || d.rep.length)) return;
      const t2 = th2.querySelector('.t-title');
      if (!t2 || t2 !== h1) return; // la ficha cambió mientras tanto
      th2.querySelectorAll('.elenco').forEach(e => e.remove());
      const sec = document.createElement('section');
      sec.className = 'elenco';
      sec.dataset.k = id;
      sec.innerHTML = html(d);
      const ref = th2.querySelector('.ficha') || th2.querySelector('.syn'); // debajo de Género/Año/Duración…
      if (ref) ref.after(sec); else th2.appendChild(sec);
    } finally { busy = false; }
  }

  let t;
  new MutationObserver(() => { clearTimeout(t); t = setTimeout(revisar, 120); }).observe(document.documentElement, { childList: true, subtree: true });
  window.addEventListener('hashchange', () => setTimeout(revisar, 300));
  revisar();
})();
