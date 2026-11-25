/* Pantalla completa del reproductor con la marca de agua (la "P") incluida.
   Los videos propios (.mp4/.webm/HLS) se maximizan junto con su marco, no solo el video. */
(() => {
  const fsEl = () => document.fullscreenElement || document.webkitFullscreenElement;
  const ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

  function mejorar(p) {
    if (p.dataset.pfs) return;
    const v = p.querySelector('video');
    if (!v) return;
    p.dataset.pfs = '1';
    // Chrome/Edge: quita el botón de pantalla completa del propio video y usamos el nuestro
    const cl = (v.getAttribute('controlslist') || 'nodownload').split(/\s+/).filter(Boolean);
    if (!cl.includes('nofullscreen')) cl.push('nofullscreen');
    v.setAttribute('controlslist', cl.join(' '));
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'pfs';
    b.setAttribute('aria-label', 'Pantalla completa');
    b.title = 'Pantalla completa';
    b.innerHTML = ICON;
    p.appendChild(b);
    // Adelantar / atrasar 10 s (botones laterales, aviso en pantalla, teclado y doble toque)
    const mk = (cls, label, back) => {
      const k = document.createElement('button');
      k.type = 'button';
      k.className = 'pskip ' + cls;
      k.setAttribute('aria-label', label);
      k.title = label;
      k.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"' + (back ? '' : ' style="transform:scaleX(-1)"') + '><path d="M4.6 12.6A7.6 7.6 0 1 0 12 4.4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/><path d="M12.6 1.4 8.4 4.5l4.2 3.1" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/></svg><span>10</span>';
      k.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); saltar(v, back ? -10 : 10); });
      p.appendChild(k);
    };
    mk('pback', 'Atrasar 10 segundos', true);
    mk('pfwd', 'Adelantar 10 segundos', false);
    const h = document.createElement('div');
    h.className = 'phint';
    h.setAttribute('aria-hidden', 'true');
    p.appendChild(h);
    // Los botones aparecen al mover el mouse y se ocultan solos a los 3 s sin moverlo (también en pantalla completa)
    let idle;
    const act = () => { p.classList.add('act'); clearTimeout(idle); idle = setTimeout(() => p.classList.remove('act'), 3000); };
    p.addEventListener('mousemove', act);
    p.addEventListener('pointerdown', act);
    p.addEventListener('mouseleave', () => { clearTimeout(idle); p.classList.remove('act'); });
    // Doble toque en el celular: tercio izquierdo atrasa, tercio derecho adelanta
    let lastTap = 0, lastX = 0;
    p.addEventListener('pointerup', e => {
      if (e.pointerType !== 'touch' || e.target.closest('button')) return;
      const r = p.getBoundingClientRect(), x = (e.clientX - r.left) / r.width, now = Date.now();
      if (now - lastTap < 320 && Math.abs(e.clientX - lastX) < 60 && (x < 0.35 || x > 0.65)) { saltar(v, x < 0.5 ? -10 : 10); lastTap = 0; } else { lastTap = now; lastX = e.clientX; }
    });
  }
  // Mueve el video `d` segundos y muestra el aviso ⏪ 10 s / 10 s ⏩
  function saltar(v, d) {
    if (!v) return;
    const dur = isFinite(v.duration) ? v.duration : Infinity;
    v.currentTime = Math.max(0, Math.min(dur - 0.2, (v.currentTime || 0) + d));
    const h = v.closest('.player') && v.closest('.player').querySelector('.phint');
    if (!h) return;
    h.textContent = d < 0 ? '⏪ 10 s' : '10 s ⏩';
    h.classList.remove('on');
    void h.offsetWidth;
    h.classList.add('on');
  }
  // Teclado: ← y → mueven 10 s (sobre el reproductor activo)
  let activo = null;
  document.addEventListener('pointerover', e => { const p = e.target.closest && e.target.closest('.player'); if (p && p.querySelector('video')) activo = p; });
  document.addEventListener('focusin', e => { const p = e.target.closest && e.target.closest('.player'); if (p && p.querySelector('video')) activo = p; });
  document.addEventListener('keydown', e => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return;
    const t = e.target, tag = t && t.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || (t && t.isContentEditable)) return;
    const full = fsEl();
    const p = full && full.classList && full.classList.contains('player') ? full : (activo && document.contains(activo) ? activo : document.querySelector('.player'));
    const v = p && p.querySelector('video');
    if (!v) return;
    e.preventDefault();
    e.stopPropagation();
    saltar(v, e.key === 'ArrowLeft' ? -10 : 10);
  }, true);
  // La "P" de marca de agua (con su play, que guiña cada par de minutos)
  const P_SVG = '<svg viewBox="168 112 236 288" aria-hidden="true"><path fill="#ff5a36" fill-rule="evenodd" d="M168 112H292A112 112 0 0 1 292 336H240V400H168ZM240 176V272H292A48 48 0 0 0 292 176Z"/><path class="tri" fill="#ffb347" d="M258 197V251L302 224Z"/></svg>';
  function marca(p) {
    if (p.querySelector('.pmark')) return;
    const m = document.createElement('span');
    m.className = 'pmark';
    m.setAttribute('aria-hidden', 'true');
    m.innerHTML = P_SVG;
    p.appendChild(m);
  }
  function guinar() {
    document.querySelectorAll('.pmark').forEach(m => {
      m.classList.remove('wink');
      void m.getBoundingClientRect(); // reinicia la animación
      m.classList.add('wink');
      setTimeout(() => m.classList.remove('wink'), 900);
    });
  }
  setTimeout(guinar, 15000);          // primer guiño poco después de abrir el video
  setInterval(guinar, 120000);        // luego cada 2 minutos
  const buscar = () => document.querySelectorAll('.player').forEach(p => { marca(p); mejorar(p); });
  new MutationObserver(buscar).observe(document.documentElement, { childList: true, subtree: true });
  buscar();

  document.addEventListener('click', e => {
    const b = e.target.closest && e.target.closest('.pfs');
    if (!b) return;
    e.preventDefault();
    e.stopPropagation();
    const p = b.closest('.player'), v = p.querySelector('video');
    if (fsEl()) { (document.exitFullscreen || document.webkitExitFullscreen).call(document); return; }
    const rq = p.requestFullscreen || p.webkitRequestFullscreen;
    if (rq) {
      let r;
      try { r = rq.call(p); } catch (_) { r = null; }
      if (r && r.then) {
        r.then(() => { try { screen.orientation.lock('landscape').catch(() => { }); } catch (_) { } })
          .catch(() => { if (v && v.webkitEnterFullscreen) v.webkitEnterFullscreen(); });
      }
    } else if (v && v.webkitEnterFullscreen) {
      v.webkitEnterFullscreen(); // iPhone: solo permite maximizar el video
    }
  }, true);

  // Doble clic sobre el video: el navegador maximiza solo el <video> (se pierden la P y la caja de siguiente episodio).
  // Lo desviamos a nuestro botón, que maximiza el reproductor completo.
  document.addEventListener('dblclick', e => {
    const v = e.target && e.target.closest && e.target.closest('.player video');
    if (!v) return;
    const b = v.closest('.player').querySelector('.pfs');
    if (!b) return;
    e.preventDefault();
    e.stopPropagation();
    b.click();
  }, true);
  // Red de seguridad: si algo maximiza solo el <video> (menú del navegador, extensiones), se pasa al reproductor completo
  document.addEventListener('fullscreenchange', () => {
    const f = fsEl();
    if (f && f.tagName === 'VIDEO' && f.closest && f.closest('.player')) {
      const p = f.closest('.player');
      const rq = p.requestFullscreen || p.webkitRequestFullscreen;
      try { const r = rq.call(p); if (r && r.catch) r.catch(() => { }); } catch (_) { }
    }
  });
  document.addEventListener('fullscreenchange', () => {
    if (!fsEl()) { try { screen.orientation.unlock(); } catch (_) { } }
  });
})();
