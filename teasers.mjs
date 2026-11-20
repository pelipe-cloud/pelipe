// Crea teasers de 10 s (sin audio, 480p, ~1-2 MB) para las peliculas/series que aun no tienen,
// y los sube a R2 en teasers/<id>.mp4. Solo lee el trozo necesario del video, no lo baja entero.
import { execFileSync } from 'node:child_process';
import { mkdirSync, existsSync, statSync } from 'node:fs';

const BASE = 'https://pelipe.online/catalogo.json';
const MEDIA = 'https://media.pelipe.online/teasers/';
const MAX = parseInt(process.env.MAX || '40', 10);
const SEG = 10, DESDE = 0.25;
const SOLO = (process.env.SOLO || '').toLowerCase();
const directo = (u) => /\.(mp4|m4v|webm|mkv|mov)(\?|$)/i.test(u || '') || (u || '').startsWith('https://media.pelipe.online/');

const cat = await (await fetch(BASE + '?t=' + Date.now())).json();
let lista = (cat.peliculas || []).filter((p) => p.id);
if (SOLO) lista = lista.filter((p) => (p.titulo || '').toLowerCase().includes(SOLO));

function origen(p) {
  let srv = [];
  if (p.tipo === 'serie') {
    for (const t of p.temporadas || []) {
      for (const e of t.episodios || []) srv = srv.concat(e.servidores || []);
      if (srv.length) break;
    }
  } else srv = p.servidores || [];
  const s = srv.find((x) => x && directo(x.u));
  return s ? s.u : directo(p.url) ? p.url : '';
}

async function existe(id) {
  try { return (await fetch(MEDIA + id + '.mp4', { method: 'HEAD' })).ok; } catch { return false; }
}

// Azar para que un titulo con problemas no bloquee a los demas
lista.sort(() => Math.random() - 0.5);
mkdirSync('out', { recursive: true });
let hechos = 0, fallos = 0;
for (const p of lista) {
  if (hechos >= MAX) break;
  if (await existe(p.id)) continue;
  const u = origen(p);
  if (!u) { console.log('sin video directo:', p.titulo); continue; }
  try {
    const dur = parseFloat(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'default=nw=1:nk=1', u], { timeout: 60000 }).toString());
    if (!(dur > 30)) throw new Error('duracion rara');
    const ini = Math.max(5, Math.floor(dur * DESDE));
    const out = `out/${p.id}.mp4`;
    execFileSync('ffmpeg', ['-y', '-v', 'error', '-ss', String(ini), '-i', u, '-t', String(SEG), '-an',
      '-vf', `scale=-2:480,fade=t=in:st=0:d=0.5,fade=t=out:st=${SEG - 0.7}:d=0.7`,
      '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '29', '-maxrate', '900k', '-bufsize', '1800k',
      '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out], { timeout: 180000 });
    if (!existsSync(out) || statSync(out).size < 20000) throw new Error('salida vacia');
    console.log('ok', p.titulo, (statSync(out).size / 1048576).toFixed(1) + ' MB');
    hechos++;
  } catch (e) {
    fallos++;
    console.log('fallo', p.titulo, String(e.message).split('\n')[0]);
  }
}
console.log(`Teasers creados: ${hechos}, fallos: ${fallos}`);
