// Crea teasers de 10 s (sin audio, 480p, ~1-2 MB) para las peliculas/series que aun no tienen,
// y los sube a R2 en teasers/<id>.mp4. Solo lee el trozo necesario del video, no lo baja entero.
import { execFileSync } from 'node:child_process';
import { mkdirSync, existsSync, statSync } from 'node:fs';

const BASE = 'https://pelipe.online/catalogo.json';
const MEDIA = 'https://media.pelipe.online/teasers/';
const MAX = parseInt(process.env.MAX || '40', 10);
const SEG = 10, DESDE = 0.25;
const SOLO = (process.env.SOLO || '').toLowerCase();
const directo = (u) => /\.(mp4|m4v|webm|mkv|mov|m3u8)(\?|$)/i.test(u || '') || /^https:\/\/(media\.pelipe\.online|[a-z0-9-]+\.r2\.dev)\//i.test(u || '');

const cat = await (await fetch(BASE + '?t=' + Date.now())).json();
let lista = (cat.peliculas || []).filter((p) => p.id);
if (SOLO) lista = lista.filter((p) => (p.titulo || '').toLowerCase().includes(SOLO));

function origenes(p) {
  let srv = [];
  if (p.tipo === 'serie') {
    for (const t of p.temporadas || []) {
      for (const e of t.episodios || []) srv = srv.concat(e.servidores || []);
      if (srv.length) break;
    }
  } else srv = p.servidores || [];
  const r = srv.filter((x) => x && directo(x.u)).map((x) => x.u);
  if (directo(p.url)) r.push(p.url);
  return [...new Set(r)].map((u) => { try { return new URL(u).href; } catch { return u; } });
}

async function existe(id) {
  try { const r = await fetch(MEDIA + id + '.mp4?c=' + Date.now(), { method: 'HEAD' }); return r.ok && Number(r.headers.get('content-length') || 0) > 20000; } catch { return false; }
}

// Azar para que un titulo con problemas no bloquee a los demas
lista.sort(() => Math.random() - 0.5);
mkdirSync('out', { recursive: true });
let hechos = 0, fallos = 0;
const problemas = [];
for (const p of lista) {
  if (hechos >= MAX) break;
  if (await existe(p.id)) continue;
  const us = origenes(p);
  if (!us.length) { problemas.push(`${p.titulo} [${p.id}]: sin enlace de video directo`); continue; }
  let ok = false, motivo = '';
  for (const u of us) {
    try {
      let dur = parseFloat(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'default=nw=1:nk=1', u], { timeout: 90000 }).toString());
      if (!(dur > 0)) dur = 0;
      const ini = dur > 60 ? Math.max(5, Math.floor(dur * DESDE)) : 0;
      const out = `out/${p.id}.mp4`;
      execFileSync('ffmpeg', ['-y', '-v', 'error', '-ss', String(ini), '-i', u, '-t', String(SEG), '-an',
        '-vf', `scale=-2:480,fade=t=in:st=0:d=0.5,fade=t=out:st=${SEG - 0.7}:d=0.7`,
        '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '29', '-maxrate', '900k', '-bufsize', '1800k',
        '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out], { timeout: 240000 });
      if (!existsSync(out) || statSync(out).size < 20000) throw new Error('salida vacia');
      console.log('ok', p.titulo, (statSync(out).size / 1048576).toFixed(1) + ' MB');
      ok = true;
      break;
    } catch (e) {
      motivo = String((e.stderr && e.stderr.toString()) || e.message).trim().split('\n').slice(-1)[0].slice(0, 160);
    }
  }
  if (ok) hechos++;
  else { fallos++; problemas.push(`${p.titulo} [${p.id}]: ${motivo}`); }
}
console.log(`Teasers creados: ${hechos}, fallos: ${fallos}`);
if (problemas.length) console.log('PROBLEMAS:\n- ' + problemas.join('\n- '));
