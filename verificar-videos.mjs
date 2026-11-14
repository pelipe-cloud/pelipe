// Revisa que los videos del catálogo existan y puedan reproducirse.
// Uso:  node verificar-videos.mjs            (usa https://pelipe.online/catalogo.json)
//       node verificar-videos.mjs URL_CATALOGO
// Termina con código 1 si encuentra videos con problemas (así GitHub Actions te avisa).
import fs from 'fs';

const CAT = process.argv[2] || process.env.CATALOGO_URL || 'https://pelipe.online/catalogo.json';
const PARALELO = 6, TOPE = 2 * 1024 * 1024; // lee 2 MB al inicio y 2 MB al final
const DIRECTO = /\.(mp4|m4v|mov|webm|m3u8)(\?|$)/i;

const dec = b => Buffer.from(b);
async function pide(url, rango) {
  const c = new AbortController(), t = setTimeout(() => c.abort(), 25000);
  try {
    const r = await fetch(url, { headers: { Range: rango }, signal: c.signal, redirect: 'follow' });
    const buf = dec(await r.arrayBuffer());
    return { r, buf };
  } finally { clearTimeout(t); }
}

async function revisa(url) {
  const out = { problemas: [], avisos: [] };
  let ini;
  try { ini = await pide(url, `bytes=0-${TOPE - 1}`); }
  catch (e) { out.problemas.push('No responde (' + (e.name === 'AbortError' ? 'tiempo agotado' : e.message) + ')'); return out; }
  const { r, buf } = ini;
  if (r.status !== 200 && r.status !== 206) { out.problemas.push('HTTP ' + r.status + (r.status === 404 ? ' (el archivo no existe en el bucket)' : '')); return out; }
  const ct = (r.headers.get('content-type') || '').toLowerCase();
  if (!/^(video\/|application\/octet-stream|application\/(vnd\.apple\.)?mpegurl|audio\/mpegurl|binary\/octet-stream)/.test(ct)) out.problemas.push('Tipo de archivo raro: ' + (ct || 'sin tipo'));
  if (!buf.length) { out.problemas.push('Archivo vacío'); return out; }
  const total = Number((r.headers.get('content-range') || '').split('/')[1]) || Number(r.headers.get('content-length')) || buf.length;
  if (total < 100 * 1024) out.problemas.push('Archivo diminuto (' + total + ' bytes): subida incompleta');
  if (/\.m3u8(\?|$)/i.test(url)) { if (!buf.toString('utf8', 0, 20).includes('#EXTM3U')) out.problemas.push('La lista .m3u8 no es válida'); return out; }
  if (/\.(mp4|m4v|mov)(\?|$)/i.test(url)) {
    if (buf.length > 12 && buf.toString('latin1', 4, 8) !== 'ftyp') { out.problemas.push('No es un MP4 válido (sin cabecera)'); return out; }
    if (buf.includes('moov')) return out;               // índice al inicio: ideal
    if (total <= TOPE) { out.problemas.push('MP4 dañado: sin índice (moov)'); return out; }
    try {
      const fin = await pide(url, 'bytes=-' + TOPE);
      if (fin.buf.includes('moov')) out.avisos.push('MP4 sin “Web Optimized”: reproduce, pero tarda en empezar');
      else out.problemas.push('MP4 dañado o incompleto: no tiene índice (moov). Suele pasar si la conversión o la subida se cortó');
    } catch (e) { out.avisos.push('No pude leer el final del archivo: ' + e.message); }
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

const malos = [], avisos = [];
let i = 0, hechos = 0;
await Promise.all(Array.from({ length: PARALELO }, async () => {
  while (i < directos.length) {
    const u = directos[i++];
    const res = await revisa(u);
    hechos++;
    if (res.problemas.length) malos.push({ u, donde: items.get(u), p: res.problemas });
    if (res.avisos.length) avisos.push({ u, donde: items.get(u), p: res.avisos });
    process.stdout.write(`\r  ${hechos}/${directos.length}`);
  }
}));
console.log('\n');

const fmt = a => a.map(x => `• ${[...new Set(x.donde)].join(', ')}\n    ${x.p.join('; ')}\n    ${x.u}`).join('\n');
let txt = `Informe de videos — ${new Date().toLocaleString('es-CO', { timeZone: 'America/Bogota' })}\n`;
txt += malos.length ? `\n❌ ${malos.length} VIDEO(S) CON PROBLEMAS:\n${fmt(malos)}\n` : '\n✅ Todos los videos revisados responden y tienen un MP4 válido.\n';
if (avisos.length) txt += `\n⚠️ ${avisos.length} aviso(s):\n${fmt(avisos)}\n`;
console.log(txt);
try { fs.writeFileSync('videos-con-problemas.txt', txt); } catch { }
process.exit(malos.length ? 1 : 0);
