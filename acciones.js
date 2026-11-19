/* Botones de la ficha (Favorita, Compartir, Añadir a lista, Ver tráiler) junto al "Me gusta", centrados y con emoji.
   No toca app.js: mueve los botones ya creados (así conservan su función) y les pone el emoji al texto. */
(() => {
  const EMO = {
    share: () => '📤 Compartir',
    addlist: () => '📚 Añadir a lista',
    trailer: () => '🎬 Ver tráiler',
    fav: b => (b.classList.contains('on') ? '❤️ En favoritas' : '🤍 Favorita')
  };

  function listo() {
    const th = document.querySelector('.theater-in');
    if (!th) return;
    // 1) Emoji en el texto (se repite si la página cambia el texto, p. ej. al marcar favorita)
    Object.keys(EMO).forEach(id => {
      const b = document.getElementById(id);
      if (!b) return;
      const t = EMO[id](b);
      if (b.textContent !== t) b.textContent = t;
    });
    // 2) Mover los botones junto al "Me gusta"
    const box = document.getElementById('react');
    const act = th.querySelector('.actions');
    if (!box || !act || box.querySelector('.react-btns')) return;
    const grp = document.createElement('div');
    grp.className = 'react-btns';
    ['fav', 'share', 'addlist', 'trailer'].forEach(id => { const b = document.getElementById(id); if (b) grp.appendChild(b); });
    if (!grp.children.length) return;
    const ra = box.querySelector('.react-act');
    if (ra) ra.after(grp); else box.appendChild(grp);
    if (!act.children.length) act.remove();
  }

  let t;
  new MutationObserver(() => { clearTimeout(t); t = setTimeout(listo, 60); }).observe(document.documentElement, { childList: true, subtree: true, characterData: true });
  listo();
})();
