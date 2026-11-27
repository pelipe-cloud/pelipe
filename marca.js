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
    // Botón grande de play en el centro (estilo Netflix): aparece con el video en pausa y se oculta al reproducir
    const pp = document.createElement('button');
    pp.type = 'button';
    pp.className = 'pplay';
    pp.setAttribute('aria-label', 'Reproducir o pausar');
    pp.title = 'Reproducir / pausar';
    pp.innerHTML = '<svg class="i-play" viewBox="1.5 0 24 24" aria-hidden="true"><path fill="currentColor" d="M7 4.5v15a1 1 0 0 0 1.5.86l12.4-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5z"/></svg><svg class="i-pause" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M7 4h3.4v16H7zM13.6 4H17v16h-3.4z"/></svg>';
    p.appendChild(pp);
    let tp;
    const sync = () => { clearTimeout(tp); tp = setTimeout(() => p.classList.toggle('paused', v.paused || v.ended), v.paused ? 180 : 0); };
    ['play', 'playing', 'pause', 'ended', 'loadedmetadata'].forEach(ev => v.addEventListener(ev, sync));
    p.classList.toggle('paused', v.paused);
    pp.addEventListener('click', e => {
      e.preventDefault(); e.stopPropagation();
      if (v.paused || v.ended) { if (v.ended) v.currentTime = 0; v.play().catch(() => { }); } else v.pause();
    });
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
  // La "P" del logo de la página también guiña (primero a los 6 s y luego cada 40 s)
  function guinarLogo() {
    document.querySelectorAll('.logo-p').forEach(m => {
      m.classList.remove('wink');
      void m.getBoundingClientRect();
      m.classList.add('wink');
      setTimeout(() => m.classList.remove('wink'), 900);
    });
  }
  setTimeout(guinarLogo, 6000);
  setInterval(guinarLogo, 40000);
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


/* Navidad: luces + nieve sobre el logo (del 20 de noviembre al 6 de enero; ?navidad=1 la fuerza y ?navidad=0 la apaga) */
(function () {
  const f = location.search + location.hash;
  const d = new Date(), m = d.getMonth() + 1, dia = d.getDate();
  let on = (m === 11 && dia >= 20) || m === 12 || (m === 1 && dia <= 6);
  if (/navidad=1/.test(f)) on = true;
  if (/navidad=0/.test(f)) on = false;
  if (!on) return;
  document.documentElement.classList.add('navidad');
  const COL = ['#ff3b3b', '#3bd15f', '#ffd23b', '#3b9bff', '#ff8a3d'];
  let flakes = [], cv = null, cx = null, anim = 0;
  function luces(lg) {
    let s = lg.querySelector('.xm-lights');
    if (!s) { s = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); s.setAttribute('class', 'xm-lights'); s.setAttribute('aria-hidden', 'true'); lg.insertBefore(s, lg.firstChild); }
    const w = lg.offsetWidth, h = 34, n = Math.max(5, Math.round(w / 30));
    s.setAttribute('viewBox', '0 0 ' + w + ' ' + h); s.style.width = w + 'px'; s.style.height = h + 'px';
    let t = '<path class="xm-wire" d="M0 4 Q ' + w / 2 + ' 26 ' + w + ' 4"/>';
    for (let i = 0; i <= n; i++) {
      const k = i / n, x = k * w, y = (1 - k) * (1 - k) * 4 + 2 * (1 - k) * k * 26 + k * k * 4, c = COL[i % 5];
      t += '<g class="xm-bulb"><line x1="' + x + '" y1="' + y + '" x2="' + x + '" y2="' + (y + 3) + '" stroke="#1f4a2c" stroke-width="2.5"/><ellipse cx="' + x + '" cy="' + (y + 8) + '" rx="3.6" ry="5" fill="' + c + '"/><ellipse cx="' + x + '" cy="' + (y + 8) + '" rx="8" ry="9.5" fill="' + c + '" opacity=".25"/></g>';
    }
    s.innerHTML = t;
  }
  function nieve(lg) {
    if (!cv) { cv = document.createElement('canvas'); cv.className = 'xm-snow'; cv.setAttribute('aria-hidden', 'true'); lg.appendChild(cv); cx = cv.getContext('2d'); }
    cv.style.left = '-14px'; cv.style.top = '-6px';
    cv.width = lg.offsetWidth + 28; cv.height = lg.offsetHeight + 12;
    flakes = Array.from({ length: 22 }, () => ({ x: Math.random() * cv.width, y: Math.random() * cv.height, r: 0.9 + Math.random() * 1.9, v: 0.25 + Math.random() * 0.7, a: Math.random() * 6 }));
    if (!anim && !matchMedia('(prefers-reduced-motion:reduce)').matches) { anim = 1; loop(); }
  }
  function loop() {
    if (!cv || !cv.isConnected) { anim = 0; return; }
    if (!document.hidden) {
      cx.clearRect(0, 0, cv.width, cv.height); cx.fillStyle = 'rgba(255,255,255,.9)';
      for (const p of flakes) { p.y += p.v; p.a += 0.03; p.x += Math.sin(p.a) * 0.35; if (p.y > cv.height) { p.y = -3; p.x = Math.random() * cv.width; } cx.beginPath(); cx.arc(p.x, p.y, p.r, 0, 7); cx.fill(); }
    }
    requestAnimationFrame(loop);
  }
  function montar() {
    const lg = document.querySelector('header .logo') || document.querySelector('.logo');
    if (!lg || !lg.offsetWidth) return;
    luces(lg); nieve(lg);
  }
  window.addEventListener('load', montar);
  window.addEventListener('resize', () => { clearTimeout(montar.t); montar.t = setTimeout(montar, 200); });
  setTimeout(montar, 300);
})();
