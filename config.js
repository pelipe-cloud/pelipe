// Configuración de Pelipe.pm.
window.CONFIG = {
  // --- Nube: películas, cuentas, listas y reacciones (obligatorio) ---
  // Crea un proyecto en Firebase y pega aquí su configuración web (pasos en el README).
  FIREBASE: {
    apiKey: "AIzaSyDkqPEXOkNBAI4xV5M8jdgQoPHP7IAxo6c",
    authDomain: "pelipe-m.firebaseapp.com",
    projectId: "pelipe-m",
    appId: "1:103433714506:web:caf84b60de84481369dfb3"
  },

  // --- Publicación de películas ---
  // Administradores: gestionan todo el catálogo y deciden quién más puede publicar (panel "Publicar → Equipo").
  // Deben coincidir con el correo de las reglas de Firestore (ver README).
  ADMIN_EMAILS: ["pelipedesoporte@gmail.com"],            // ej. ["pelipedesoporte@gmail.com"]
  // Dirección pública del sitio, sin barra final (ej. "https://pelipepelipe-cloud.github.io/pelipe" o tu dominio propio).
  // Sirve para sitemap.xml, enlaces canónicos y vistas previas. En GitHub Actions se deduce sola si la dejas vacía.
  SITE_URL: "https://pelipe-cloud.github.io/pelipe",
  // Correo para que titulares de derechos pidan retirar un título (se muestra en el pie de página).
  CONTACT_EMAIL: "pelipedesoporte@gmail.com",

  // --- TMDB (opcional) ---
  // Solo sirve para autocompletar título, sinopsis, póster y tráiler al publicar una película.
  // Clave gratis en https://www.themoviedb.org (Configuración > API). Déjala vacía si no la necesitas.
  TMDB_API_KEY: "",
  LANG: "es-MX"
};
