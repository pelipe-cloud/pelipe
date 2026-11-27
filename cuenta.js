// Cuenta de Pelipe: entrar con enlace al correo y vincular la app / TV con un código de 6 dígitos.
// Usa el mismo proyecto de Firebase que auth.js (initializeApp con la misma configuración devuelve la misma app).
const V = '10.14.1';
let M;
async function fb() {
  if (M) return M;
  const [ap, a, f] = await Promise.all([
    import(`https://www.gstatic.com/firebasejs/${V}/firebase-app.js`),
    import(`https://www.gstatic.com/firebasejs/${V}/firebase-auth.js`),
    import(`https://www.gstatic.com/firebasejs/${V}/firebase-firestore.js`)
  ]);
  const app = ap.getApps().length ? ap.getApp() : ap.initializeApp((window.CONFIG || {}).FIREBASE);
  M = { a, f, auth: a.getAuth(app), db: f.getFirestore(app) };
  await M.auth.authStateReady();
  return M;
}
const KEY = 'pelipe:mail';

/* ---------- entrar con enlace al correo (sin contraseña) ---------- */
export async function enviarEnlace(email) {
  const { a, auth } = await fb();
  auth.languageCode = 'es'; // el correo de Firebase sale en español
  const base = location.origin + location.pathname.replace(/[^/]*$/, '');
  await a.sendSignInLinkToEmail(auth, email, { url: base + '#/entrar', handleCodeInApp: true });
  try { localStorage.setItem(KEY, email); } catch { }
}
export const hayEnlace = () => /[?&]oobCode=/.test(location.search) && /mode=signIn/.test(location.search);
export async function terminarEnlace() {
  const { a, auth } = await fb();
  if (!a.isSignInWithEmailLink(auth, location.href)) return null;
  let email = '';
  try { email = localStorage.getItem(KEY) || ''; } catch { }
  if (!email) email = (prompt('Escribe el correo al que llegó el enlace, para confirmar:') || '').trim();
  if (!email) throw new Error('Falta el correo');
  const cu = auth.currentUser;
  let r = null;
  if (cu && cu.isAnonymous) {
    // se conserva la sesión de visitante (sus reacciones) al convertirla en cuenta
    try { r = await a.linkWithCredential(cu, a.EmailAuthProvider.credentialWithLink(email, location.href)); }
    catch (e) { if (e.code !== 'auth/credential-already-in-use' && e.code !== 'auth/email-already-in-use') throw e; }
  }
  if (!r) r = await a.signInWithEmailLink(auth, email, location.href);
  try { localStorage.removeItem(KEY); } catch { }
  history.replaceState(null, '', location.pathname + '#/');
  return r.user;
}

/* ---------- vincular la app / TV ---------- */
// El dispositivo crea vinculos/{código} (15 min de vida). Aquí se reclama con la sesión iniciada.
export async function vincular(codigo) {
  const { f, auth, db } = await fb();
  const u = auth.currentUser;
  if (!u || u.isAnonymous) throw new Error('sesion');
  const code = String(codigo || '').replace(/\D/g, '');
  if (code.length !== 6) throw new Error('codigo');
  const ref = f.doc(db, 'vinculos', code);
  const s = await f.getDoc(ref);
  if (!s.exists()) throw new Error('noexiste');
  const d = s.data();
  if (d.owner) throw new Error('usado');
  if (Date.now() - (d.ts || 0) > 15 * 60 * 1000) throw new Error('expirado');
  // primero se autoriza al dispositivo y luego se le avisa de quién es su cuenta
  await f.setDoc(f.doc(db, 'users', u.uid, 'dispositivos', d.dev), { nombre: d.nombre || 'Dispositivo', tipo: d.tipo || '', ts: Date.now() });
  await f.updateDoc(ref, { owner: u.uid, ownerNombre: u.email || u.displayName || '' });
  return d;
}
export async function dispositivos() {
  const { f, auth, db } = await fb();
  const u = auth.currentUser;
  if (!u || u.isAnonymous) return [];
  const s = await f.getDocs(f.collection(db, 'users', u.uid, 'dispositivos'));
  return s.docs.map(d => ({ id: d.id, ...d.data() })).sort((x, y) => (y.ts || 0) - (x.ts || 0));
}
export async function desvincular(dev) {
  const { f, auth, db } = await fb();
  const u = auth.currentUser;
  if (!u) return;
  await f.deleteDoc(f.doc(db, 'users', u.uid, 'dispositivos', dev));
}
