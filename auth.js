// Inicio de sesión con Google + guardado en la nube (Firebase Auth y Firestore).
// Si no hay configuración de Firebase, `Cloud.enabled` queda en false y el sitio
// funciona igual, guardando favoritas y listas solo en el navegador.
const V = '10.14.1';
const cfg = (window.CONFIG || {}).FIREBASE;
let enabled = !!(cfg && cfg.apiKey);
let auth, db, fb = {};

// Firebase se carga en segundo plano: la página se pinta sin esperar a que termine (antes bloqueaba todo el sitio).
// `ready` se resuelve en true cuando Firebase quedó listo, o en false si no pudo cargarse.
const ready = (async () => {
  if (!enabled) return false;
  try {
    const [app, a, f] = await Promise.all([
      import(`https://www.gstatic.com/firebasejs/${V}/firebase-app.js`),
      import(`https://www.gstatic.com/firebasejs/${V}/firebase-auth.js`),
      import(`https://www.gstatic.com/firebasejs/${V}/firebase-firestore.js`)
    ]);
    const inst = app.initializeApp(cfg);
    auth = a.getAuth(inst);
    db = f.getFirestore(inst);
    fb = { ...a, ...f };
    return true;
  } catch (e) {
    console.error('Firebase no pudo cargarse:', e);
    enabled = false;
    return false;
  }
})();

const listeners = [];

export const Cloud = {
  get enabled() { return enabled; },
  ready,
  onUser(cb) { listeners.push(cb); return ready.then(ok => ok ? fb.onAuthStateChanged(auth, cb) : undefined); },
  // Si la persona ya reaccionó como visitante (sesión anónima), se vincula su cuenta de Google
  // a esa misma sesión para conservar sus reacciones.
  async signIn() {
    if (!fb.GoogleAuthProvider) await ready; // solo espera si Firebase aún no terminó de cargar
    const prov = new fb.GoogleAuthProvider();
    const cu = auth.currentUser;
    if (cu && cu.isAnonymous) {
      try {
        const r = await fb.linkWithPopup(cu, prov);
        listeners.forEach(f => f(r.user)); // vincular no dispara onAuthStateChanged
        return r;
      } catch (e) {
        if (e.code !== 'auth/credential-already-in-use') throw e;
      }
    }
    return fb.signInWithPopup(auth, prov);
  },
  signOut() { return fb.signOut(auth); },

  // Reacciones: una por persona (visitante anónimo o con Google), en peliculas/{id}/votos/{uid}
  currentUid() { return auth && auth.currentUser ? auth.currentUser.uid : null; },
  async ensureUid() {
    if (auth.currentUser) return auth.currentUser.uid;
    return (await fb.signInAnonymously(auth)).user.uid;
  },
  async getVote(peli, uid) {
    const s = await fb.getDoc(fb.doc(db, 'peliculas', peli, 'votos', uid));
    return s.exists() ? s.data().tipo : null;
  },
  setVote(peli, uid, tipo) { return fb.setDoc(fb.doc(db, 'peliculas', peli, 'votos', uid), { tipo, t: Date.now() }); },
  removeVote(peli, uid) { return fb.deleteDoc(fb.doc(db, 'peliculas', peli, 'votos', uid)); },
  async countVotes(peli, tipos) {
    const col = fb.collection(db, 'peliculas', peli, 'votos');
    const r = await Promise.all(tipos.map(t => fb.getCountFromServer(fb.query(col, fb.where('tipo', '==', t)))));
    return Object.fromEntries(tipos.map((t, i) => [t, r[i].data().count]));
  },

  // Total de reacciones de una película (o solo las posteriores a `since`, en ms) — para los rankings
  async countAll(peli, since) {
    const col = fb.collection(db, 'peliculas', peli, 'votos');
    return (await fb.getCountFromServer(since ? fb.query(col, fb.where('t', '>=', since)) : col)).data().count;
  },

  // Pie del sitio: presencia (visitas recientes) y totales
  beat(uid) { return fb.setDoc(fb.doc(db, 'presencia', uid), { t: fb.serverTimestamp() }); },
  async countOnline(min) {
    const q = fb.query(fb.collection(db, 'presencia'), fb.where('t', '>=', fb.Timestamp.fromMillis(Date.now() - min * 60000)));
    return (await fb.getCountFromServer(q)).data().count;
  },
  async countPeliculas() { return (await fb.getCountFromServer(fb.collection(db, 'peliculas'))).data().count; },
  async countReacciones() { return (await fb.getCountFromServer(fb.collectionGroup(db, 'votos'))).data().count; },

  async load(uid) {
    const s = await fb.getDoc(fb.doc(db, 'users', uid));
    return s.exists() ? s.data() : null;
  },
  save(uid, data) { return fb.setDoc(fb.doc(db, 'users', uid), data); },

  // Catálogo propio (colección "peliculas": lectura pública, escritura solo administradores)
  async listPeliculas() {
    const s = await fb.getDocs(fb.collection(db, 'peliculas'));
    return s.docs.map(d => ({ id: d.id, ...d.data() }));
  },
  async getPelicula(id) {
    const s = await fb.getDoc(fb.doc(db, 'peliculas', id));
    return s.exists() ? { id: s.id, ...s.data() } : null;
  },
  savePelicula(id, d) { return fb.setDoc(fb.doc(db, 'peliculas', id), d); },
  deletePelicula(id) { return fb.deleteDoc(fb.doc(db, 'peliculas', id)); },

  // Reportes de problemas (video, audio, subtítulos): uno por persona y película
  sendReporte(id, d) { return fb.setDoc(fb.doc(db, 'reportes', id), { ...d, t: fb.serverTimestamp() }); },
  async listReportes(email) {
    const col = fb.collection(db, 'reportes');
    const s = await fb.getDocs(email ? fb.query(col, fb.where('por', '==', email)) : col);
    return s.docs.map(d => ({ id: d.id, ...d.data() }));
  },
  deleteReporte(id) { return fb.deleteDoc(fb.doc(db, 'reportes', id)); },

  // Borradores: solo los ve el administrador (y el editor dueño); la película pasa a "peliculas" al publicarse
  async listBorradores(email) {
    const col = fb.collection(db, 'borradores');
    const q = email ? fb.query(col, fb.where('por', '==', email)) : col;
    return (await fb.getDocs(q)).docs.map(d => ({ id: d.id, ...d.data() }));
  },
  async getBorrador(id) {
    const s = await fb.getDoc(fb.doc(db, 'borradores', id));
    return s.exists() ? { id: s.id, ...s.data() } : null;
  },
  saveBorrador(id, d) { return fb.setDoc(fb.doc(db, 'borradores', id), d); },
  deleteBorrador(id) { return fb.deleteDoc(fb.doc(db, 'borradores', id)); },

  // Editores: personas a quienes el administrador da permiso para publicar (colección "editores", id = correo en minúsculas)
  async isEditor(email) {
    const s = await fb.getDoc(fb.doc(db, 'editores', email));
    return s.exists();
  },
  async listEditores() {
    const s = await fb.getDocs(fb.collection(db, 'editores'));
    return s.docs.map(d => ({ id: d.id, ...d.data() }));
  },
  addEditor(email, por) { return fb.setDoc(fb.doc(db, 'editores', email), { email, agregado: Date.now(), por }); },
  removeEditor(email) { return fb.deleteDoc(fb.doc(db, 'editores', email)); }
};

// Cualquier llamada a la nube espera a que Firebase esté listo (las que ya son síncronas se dejan igual)
for (const k of Object.keys(Cloud)) {
  const f = Cloud[k];
  if (typeof f !== 'function' || ['onUser', 'currentUid', 'signIn'].includes(k)) continue;
  Cloud[k] = async (...a) => { await ready; return f.apply(Cloud, a); };
}
