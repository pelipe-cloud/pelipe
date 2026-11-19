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
  const buscar = () => document.querySelectorAll('.player').forEach(mejorar);
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
