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
  ADMIN_EMAILS: ["pelipedesoporte@gmail.com"],            // ej. ["tucorreo@gmail.com"]
  // Dirección pública del sitio, sin barra final (ej. "https://tuusuario.github.io/pelipe" o tu dominio propio).
  // Sirve para sitemap.xml, enlaces canónicos y vistas previas. En GitHub Actions se deduce sola si la dejas vacía.
  SITE_URL: "https://pelipe-cloud.github.io/pelipe",
  // Sitios extra desde donde se puede incrustar el reproductor de una película (solo los que tú pongas aquí).
  // Escribe el dominio, ej. ["miservidor.com", "videos.miescuela.edu"]; sirve cualquier enlace https de ese dominio
  // que permita ser incrustado en un iframe. Se incrusta con permisos reducidos. Publica solo contenido con derechos.
  EMBED_HOSTS: ["https://bysedikamoum.com", "https://filemoon.org/es", "https://filemoon.org/en", "https://n1mwq.org", "https://morencius.com"],
  // Correo para que titulares de derechos pidan retirar un título (se muestra en el pie de página).
  CONTACT_EMAIL: "pelipedesoporte@gmail.com",

  // --- TMDB (opcional) ---
  // Solo sirve para autocompletar título, sinopsis, póster y tráiler al publicar una película.
  // Clave gratis en https://www.themoviedb.org (Configuración > API). Déjala vacía si no la necesitas.
  TMDB_API_KEY: "06e7e727dcf9371ac01ed8da490a5b53",
  LANG: "es-MX"
};
