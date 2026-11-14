// Revisa que los videos del catálogo existan y puedan reproducirse.
// Uso:  node verificar-videos.mjs            (usa https://pelipe.online/catalogo.json)
//       node verificar-videos.mjs URL_CATALOGO
// Termina con código 1 si encuentra videos con problemas (así GitHub Actions te avisa).
import fs from 'fs';

const args = process.argv.slice(2);
const PROFUNDO = args.includes('--profundo');          // además revisa el índice (moov) del MP4: más lento
const CAT = args.find(x => !x.startsWith('--')) || process.env.CATALOGO_URL || 'https://pelipe.online/catalogo.json';
const PARALELO = PROFUNDO ? 2 : 4, TOPE = 2 * 1024 * 1024;
const DIRECTO = /\.(mp4|m4v|mov|webm|m3u8)(\?|$)/i;
const TIPO_OK = /^(video\/|application\/octet-stream|application\/(vnd\.apple\.)?mpegurl|audio\/mpegurl|binary\/octet-stream)/;

class Lento extends Error { }
async function pide(url, { metodo = 'GET', rango, ms }) {
  const c = new AbortController(), t = setTimeout(() => c.abort(), ms);
  try {
    const r = await fetch(url, { method: metodo, headers: rango ? { Range: rango } : {}, signal: c.signal, redirect: 'follow' });
    const buf = metodo === 'HEAD' ? Buffer.alloc(0) : Buffer.from(await r.arrayBuffer());
    return { r, buf };
  } catch (e) {
    if (e.name === 'AbortError') throw new Lento('tardó más de ' + ms / 1000 + ' s en responder');
    throw e;
  } finally { clearTimeout(t); }
}

// problemas = el video seguro está mal; avisos = no se pudo comprobar o detalle menor
async function revisa(url) {
  const out = { problemas: [], avisos: [], lento: false };
  const esMp4 = /\.(mp4|m4v|mov)(\?|$)/i.test(url);
  try {
    // 1) Solo cabeceras: ¿existe, qué tipo es y cuánto pesa? (no descarga el video)
    let { r } = await pide(url, { metodo: 'HEAD', ms: 20000 });
    if (r.status === 405 || r.status === 501) ({ r } = await pide(url, { rango: 'bytes=0-1023', ms: 20000 }));
    if (r.status < 200 || r.status >= 300) { out.problemas.push('HTTP ' + r.status + (r.status === 404 ? ' (el archivo no existe en el bucket)' : '')); return out; }
    const ct = (r.headers.get('content-type') || '').toLowerCase();
    if (!TIPO_OK.test(ct)) out.problemas.push('Tipo de archivo raro: ' + (ct || 'sin tipo'));
    const total = Number((r.headers.get('content-range') || '').split('/')[1]) || Number(r.headers.get('content-length')) || 0;
    if (total === 0) { out.problemas.push('Archivo vacío'); return out; }
    if (total < 100 * 1024) { out.problemas.push('Archivo diminuto (' + total + ' bytes): subida incompleta'); return out; }
    if (!esMp4) return out;
    // 2) Un trozo pequeño del inicio: ¿es de verdad un MP4?
    try {
      const h = await pide(url, { rango: 'bytes=0-65535', ms: 20000 });
      if (h.buf.length > 12 && h.buf.toString('latin1', 4, 8) !== 'ftyp') { out.problemas.push('No es un MP4 válido (sin cabecera)'); return out; }
      if (!h.buf.length) { out.problemas.push('Archivo vacío'); return out; }
      if (h.buf.includes('moov') && !PROFUNDO) return out;
      if (!PROFUNDO) return out;
    } catch (e) { if (e instanceof Lento) { out.lento = true; out.avisos.push('Existe, pero no pude leer su contenido: ' + e.message); return out; } throw e; }
    // 3) Revisión profunda (solo con --profundo): busca el índice (moov) al inicio o al final
    const ini = await pide(url, { rango: `bytes=0-${TOPE - 1}`, ms: 120000 });
    if (ini.buf.includes('moov')) return out;
    if (total <= TOPE) { out.problemas.push('MP4 dañado: sin índice (moov)'); return out; }
    const fin = await pide(url, { rango: 'bytes=-' + TOPE, ms: 120000 });
    if (fin.buf.includes('moov')) out.avisos.push('MP4 sin “Web Optimized”: reproduce, pero tarda en empezar');
    else out.problemas.push('MP4 dañado o incompleto: no tiene índice (moov). Suele pasar si la conversión o la subida se cortó');
  } catch (e) {
    if (e instanceof Lento) { out.lento = true; out.avisos.push('No pude comprobarlo: ' + e.message); }
    else out.problemas.push('No responde (' + e.message + ')');
  }
  return out;
}

// --- recoger los enlaces del catálogo ---
let cat;
try {
  const r = await fetch(CAT); if (!r.ok) throw new Error('HTTP ' + r.status);
  cat = await r.json();
} catch (e) { console.error('No pude leer el catálogo (' + CAT + '): ' + e.message); process.exit(2); }
const lista = Array.isArray(cat) ? cat : (cat.peliculas || []);
const items = new Map(); // url -> [dónde]
const add = (u, donde) => { u = String(u || '').trim(); if (!/^https?:\/\//i.test(u)) return; if (!items.has(u)) items.set(u, []); items.get(u).push(donde); };
for (const p of lista) {
  const t = p.titulo || p.id;
  add(p.url, t);
  (p.servidores || []).forEach(s => add(s.u, t));
  (p.versiones || []).forEach(s => add(s.u, t + ' (versión)'));
  (p.temporadas || []).forEach(te => (te.episodios || []).forEach(e => (e.servidores || []).forEach(s => add(s.u, `${t} ${te.n}x${String(e.n).padStart(2, '0')}`))));
}
const todos = [...items.keys()];
const directos = todos.filter(u => DIRECTO.test(u) || /(^|\/\/)media\.pelipe\.online/i.test(u));
const omitidos = todos.length - directos.length;
console.log(`Revisando ${directos.length} video(s) directo(s)${omitidos ? ` (${omitidos} enlace(s) de OK.ru/YouTube/otros no se revisan)` : ''}...\n`);

const malos = [], avisos = [], lentos = [];
let i = 0, hechos = 0;
await Promise.all(Array.from({ length: PARALELO }, async () => {
  while (i < directos.length) {
    const u = directos[i++];
    const res = await revisa(u);
    hechos++;
    if (res.problemas.length) malos.push({ u, donde: items.get(u), p: res.problemas });
    if (res.lento) lentos.push({ u, donde: items.get(u), p: res.avisos });
    else if (res.avisos.length) avisos.push({ u, donde: items.get(u), p: res.avisos });
    process.stdout.write(`\r  ${hechos}/${directos.length}`);
  }
}));
console.log('\n');

const fmt = a => a.map(x => `• ${[...new Set(x.donde)].join(', ')}\n    ${x.p.join('; ')}\n    ${x.u}`).join('\n');
let txt = `Informe de videos — ${new Date().toLocaleString('es-CO', { timeZone: 'America/Bogota' })}\n`;
txt += malos.length ? `\n❌ ${malos.length} VIDEO(S) CON PROBLEMAS:\n${fmt(malos)}\n` : '\n✅ Ningún video con problemas confirmados.\n';
if (avisos.length) txt += `\n⚠️ ${avisos.length} aviso(s):\n${fmt(avisos)}\n`;
if (lentos.length) txt += `\n⏱️ ${lentos.length} video(s) existen pero tardaron demasiado en responder, no se pudo comprobar su contenido (no es necesariamente un error):\n${lentos.map(x => '• ' + [...new Set(x.donde)].join(', ')).join('\n')}\n`;
console.log(txt);
try { fs.writeFileSync('videos-con-problemas.txt', txt); } catch { }
process.exit(malos.length ? 1 : 0);
