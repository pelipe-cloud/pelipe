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
  }
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

  document.addEventListener('fullscreenchange', () => {
    if (!fsEl()) { try { screen.orientation.unlock(); } catch (_) { } }
  });
})();
