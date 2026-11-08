# Pelipe.pm — películas libres para ver en español

Sitio estático (HTML + CSS + JS, sin compilación) para **reproducir películas propias, de dominio público o con licencia libre**. Cada película se ve completa en la propia página, con reacciones tipo Facebook, favoritas y listas. No hay comentarios.

## Qué incluye
- **Inicio:** portada grande que rota sola cada 10 segundos entre las **10 películas publicadas más recientemente** (se pausa al pasar el mouse; tiene flechas y puntos; botón "▶ Ver película" y, si la película tiene, "Ver tráiler"), y solo tres filas: **Top Semanales** (10, por reacciones de los últimos 7 días), **Te recomendamos…** (10 al azar en cada visita) y **10 más votadas** (por reacciones totales). Si la persona dejó algo a medias, arriba aparece además **Continuar viendo**.
- **Películas:** todo el catálogo con búsqueda y filtros (género, año, calidad, licencia, orden), con **carga progresiva** (24 a la vez; el resto aparece al bajar o con "Cargar más"). **Géneros** con conteo.
- **Página de cada película:** reproductor integrado con **subtítulos y audio opcionales**, reacciones (sin registro), **ficha técnica** (año, duración, calidad, calificación, licencia, créditos), favorita, listas, "Más como esta" y **tráiler opcional**.
- **Cuentas con Google:** favoritas y listas guardadas en la nube.
- **Publicar** (solo administrador y editores autorizados): formulario, buscador de dominio público en Internet Archive y autocompletar opcional con TMDB.

## 1. Firebase (obligatorio)
Gratis en el plan Spark.

1. https://console.firebase.google.com → crea un proyecto.
2. **Authentication → Comenzar → Método de acceso**: habilita **Google** y también **Anónimo** (el anónimo permite que los visitantes reaccionen sin registrarse).
3. **Authentication → Configuración → Dominios autorizados**: agrega `tuusuario.github.io` (`localhost` ya viene).
4. **Firestore Database → Crear base de datos**.
5. **Firestore → Reglas**: pega esto (cambia el correo por el tuyo, en minúsculas) y publica:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {

       function verificado() {
         return request.auth != null && request.auth.token.email_verified == true;
       }
       function esAdmin() {
         return verificado() && request.auth.token.email in ['tucorreo@gmail.com'];
       }
       function esEditor() {
         return verificado()
           && exists(/databases/$(database)/documents/editores/$(request.auth.token.email));
       }

       // Cada persona solo accede a sus favoritas y listas
       match /users/{uid} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }

       // Editores: solo el administrador los gestiona; cada uno puede leer su propio registro
       match /editores/{email} {
         allow read: if esAdmin() || (verificado() && request.auth.token.email == email);
         allow write: if esAdmin();
       }

       // Presencia (contador "en línea" del pie): cada visita escribe solo su documento, con la hora del servidor
       match /presencia/{uid} {
         allow read: if true;
         allow create, update: if request.auth != null && request.auth.uid == uid
                               && request.resource.data.keys().hasOnly(['t'])
                               && request.resource.data.t == request.time;
         allow delete: if request.auth != null && request.auth.uid == uid;
       }

       // Permite contar todas las reacciones del sitio (contador del pie)
       match /{path=**}/votos/{uid} {
         allow read: if true;
       }

       // Reportes de problemas: cualquiera (incluso visitante anónimo) envía uno por película;
       // solo el administrador (y el editor dueño de la película) los lee y los cierra
       match /reportes/{id} {
         allow create, update: if request.auth != null
                               && id == request.resource.data.peli + '_' + request.auth.uid
                               && request.resource.data.uid == request.auth.uid
                               && request.resource.data.keys().hasOnly(['peli', 'titulo', 'tipo', 'detalle', 'uid', 'por', 'ua', 'seg', 'fuente', 't'])
                               && request.resource.data.tipo in ['no_carga', 'se_traba', 'sin_audio', 'audio_mal', 'subs_mal', 'imagen', 'otro']
                               && request.resource.data.detalle is string && request.resource.data.detalle.size() <= 500
                               && request.resource.data.t == request.time;
         allow read, delete: if esAdmin() || (esEditor() && resource.data.por == request.auth.token.email);
       }

       // Borradores: solo el administrador (y el editor dueño) los ven y gestionan. No son públicos.
       match /borradores/{id} {
         allow read, delete: if esAdmin() || (esEditor() && resource.data.por == request.auth.token.email);
         allow create: if esAdmin() || (esEditor() && request.resource.data.por == request.auth.token.email);
         allow update: if esAdmin()
                       || (esEditor() && resource.data.por == request.auth.token.email
                           && request.resource.data.por == resource.data.por);
       }

       // Catálogo: todos pueden ver.
       // El administrador gestiona todo; un editor solo crea y gestiona lo suyo.
       match /peliculas/{id} {
         allow read: if true;
         allow create: if esAdmin()
                       || (esEditor() && request.resource.data.por == request.auth.token.email);
         allow update: if esAdmin()
                       || (esEditor() && resource.data.por == request.auth.token.email
                           && request.resource.data.por == resource.data.por);
         allow delete: if esAdmin()
                       || (esEditor() && resource.data.por == request.auth.token.email);

         // Reacciones: cualquiera puede verlas; cada persona (incluso visitante anónimo)
         // solo escribe y borra su propia reacción, y solo con un tipo permitido.
         match /votos/{uid} {
           allow read: if true;
           allow create, update: if request.auth != null && request.auth.uid == uid
                                 && request.resource.data.keys().hasOnly(['tipo', 't'])
                                 && request.resource.data.tipo in ['like', 'love', 'haha', 'wow', 'sad', 'angry'];
           allow delete: if request.auth != null && request.auth.uid == uid;
         }
       }
     }
   }
   ```
   Pruébalas con el simulador de reglas de la consola antes de publicar.
6. **Configuración del proyecto → Tus apps → Web (`</>`)**: copia `apiKey`, `authDomain`, `projectId` y `appId` en `FIREBASE` de `config.js`.
7. En `config.js` pon tu correo en `ADMIN_EMAILS` (el mismo de las reglas) y, si quieres, `CONTACT_EMAIL` para que un titular de derechos pueda pedir el retiro de un título.

> Las claves de Firebase son visibles en el navegador; es normal. La seguridad la dan las reglas. `ADMIN_EMAILS` solo muestra u oculta el panel; quien realmente puede publicar lo definen las reglas.

## 2. Publicar películas
Inicia sesión con tu correo de administrador y entra a **Publicar**. Pega el enlace del video y el sitio detecta cómo reproducirlo:

| Enlace | Dónde alojar | Notas |
|---|---|---|
| YouTube | Tu canal (público o "no listado") | Gratis; el más simple para obras propias. |
| Vimeo | Tu cuenta de Vimeo | Permite habilitar el embebido solo en tu dominio (plan de pago). |
| Internet Archive | archive.org | Gratis, ideal para dominio público y Creative Commons. El panel trae un buscador que rellena el formulario. |
| `.mp4` / `.webm` | Tu propio almacenamiento (Cloudflare R2, Backblaze B2, Bunny Storage…) | Enlace directo `https`. Activa CORS si hace falta. |
| `.m3u8` (HLS) | Bunny Stream, Cloudflare Stream, Mux… | Calidad adaptable; se reproduce con hls.js. |

Campos: título, año, duración, calificación (0–10), géneros (separados por coma; el primero se ve como etiqueta en la tarjeta y con ellos se arman las filas y la página Géneros), **calidad** (4K, 1440p, 1080p, 720p, 480p o 360p; se ve como etiqueta en la tarjeta y sirve de filtro), sinopsis, póster, **imagen de fondo** (opcional, para la portada; si no hay, se usa el póster difuminado), **tráiler** (opcional, YouTube o Vimeo; si lo dejas vacío no aparece el botón) y **licencia obligatoria** con casilla de confirmación de derechos. Para Creative Commons o permiso del titular se exigen créditos o el enlace de la licencia. En películas de Internet Archive, revisa la licencia en su página antes de publicar.

**Autocompletar con TMDB (opcional):** si pones una clave en `TMDB_API_KEY` (gratis en themoviedb.org → Configuración → API), el panel muestra un buscador que rellena título, sinopsis, póster, fondo y tráiler. Tú sigues poniendo el enlace del video y confirmando la licencia. Si no pones clave, esa sección no aparece y el sitio funciona igual.

### Borrador, publicación programada y copia de seguridad
En el formulario, **¿Cuándo publicarla?** ofrece tres opciones:
- **Publicar ahora.**
- **Programar una fecha y hora** (tu hora local): la película queda guardada y aparece sola en el sitio a esa hora, como la más nueva (en el banner y en "Nuevas"). Hasta entonces el público no la ve ni por enlace directo; tú sí, como vista previa. Importante: la película programada vive en la misma colección pública, así que alguien con conocimientos técnicos podría leer sus datos (por ejemplo el enlace del video) consultando Firebase directamente antes de la hora; no la uses para secretos. Quien ya tenía el sitio abierto la verá en un máximo de 5 minutos tras la hora (por la caché del catálogo).
- **Guardar como borrador:** puede quedar incompleto (solo exige el título). Los borradores se guardan en una colección aparte (`borradores`) que las reglas dejan **privada**: solo tú, o el editor dueño, los leen. Se ven con "vista previa" desde la lista. Desde la lista puedes **Publicar ahora** (te pide confirmar los derechos y que estén el video y la licencia), **Editar** (para programarlo o publicarlo) o **Eliminar**. Necesita la regla `borradores` de la sección de Firebase.

Una película publicada también puede volver a borrador (se retira del catálogo; sus reacciones se conservan y regresan si la vuelves a publicar).

**Copia de seguridad:** en Publicar, el botón **⬇ Descargar copia** baja un archivo `.json` con películas (incluidas las programadas), borradores y, para el administrador, el equipo; un editor solo baja lo suyo. **No incluye** reacciones, favoritas y listas de las personas, reportes ni los videos. El panel te dice hace cuántos días hiciste la última. El administrador puede **⬆ Restaurar desde un archivo**: pide confirmación y reemplaza lo que tenga el mismo ID. Conviene descargar una copia después de cada tanda de publicaciones y guardarla fuera del sitio.

### Subtítulos y audio (opcionales)
- **Subtítulos:** en el campo *Subtítulos* escribe una línea por idioma: `Español | subs/mi-pelicula-es.srt`. Acepta `.srt` y `.vtt`; el sitio convierte el `.srt` solo y lo lee en UTF-8 o Latin-1 (acentos correctos). Nadie los ve hasta que la persona los elige en el menú **Subtítulos** bajo el video (se recuerda su elección). Lo más simple: crea una carpeta `subs/` junto a `index.html` en tu repositorio de GitHub y usa esa ruta; si los alojas en otro sitio, debe permitir CORS (`raw.githubusercontent.com` lo permite).
- **Audio:** hay tres caminos. (1) *Otros audios*: una línea por versión, `Inglés original | https://…/pelicula-en.mp4`, que es el video completo con ese audio; el video principal es el audio principal (ponle nombre en *Nombre del audio principal*). Al cambiar, el video sigue en el mismo minuto. (2) Un `.m3u8` (HLS) que ya traiga varias pistas de audio: el menú aparece solo. (3) En Safari, un archivo con varias pistas también las muestra. En Chrome y Firefox un único `.mp4` con varias pistas no permite cambiarlas, por eso se usa el camino 1 o 2.
- Los subtítulos y el selector de audio funcionan en videos propios (.mp4, .webm, .m3u8). En YouTube, Vimeo e Internet Archive se usan los que ofrezca su propio reproductor.
- Los subtítulos son compartidos entre versiones de audio, así que deben coincidir en tiempos con todas.

### Compartir, ícono e instalar como app
- **Compartir:** en cada película, el botón **↗ Compartir** abre el menú nativo del celular; en computador, un panel con *Copiar enlace*, WhatsApp, Telegram, Facebook, X y correo.
- **Ícono:** `icon.svg` (pestaña del navegador), `apple-touch-icon.png` (iPhone) y `icon-192.png` / `icon-512.png` (app instalada). Para cambiarlos, reemplaza esos archivos conservando los nombres.
- **Instalar como app:** `manifest.webmanifest` y `sw.js` permiten instalar Pelipe en el celular o el computador. En Chrome/Edge aparece "📲 Instalar app" en el pie; en iPhone el mismo botón explica cómo (Safari → Compartir → Añadir a pantalla de inicio). El service worker siempre intenta primero la red, así que las actualizaciones se ven al instante; sin conexión solo abre la estructura del sitio (las películas necesitan internet). Nunca guarda videos.
- **Vista previa al compartir:** el botón Compartir enlaza a la página estática de la película (`/p/ID/`), así WhatsApp/Facebook muestran su póster, título y sinopsis. Si la película es tan nueva que su página aún no existe, `404.html` abre el reproductor directamente. Para la vista previa del sitio en general, pon tu dirección en `SITE_URL` de `config.js` (el actualizador ajusta `og:image` solo).

### Reportar un problema
Junto a las reacciones hay un botón **⚑ Reportar un problema**. La persona elige el tipo (no carga, se traba, sin audio, audio incorrecto, subtítulos mal, mala imagen, otro) y puede escribir detalles. Con el reporte se guardan el navegador, el minuto del video y el tipo de reproductor, y no hace falta cuenta (usa la sesión anónima). Hay un reporte por persona y película; si lo vuelve a enviar, reemplaza el anterior. En **Publicar** aparece el panel **Reportes de problemas** (el administrador ve todos; un editor, solo los de sus películas) con el botón **Marcar como resuelto**, que lo elimina. Necesita la regla `reportes` de la sección de Firebase.

### Pie: contadores y "en línea"
Muestra películas publicadas, reacciones totales y personas en línea, a la derecha junto a "Hecho con amor". **En línea** significa visitas con el sitio abierto y visible en los últimos 6 minutos: cada visita avisa cada 4 minutos (una escritura pequeña en Firestore) y se cuentan los avisos recientes. Es una cifra aproximada. Para esto cada visitante recibe una sesión anónima invisible (la misma que se usa para reaccionar). Necesita las dos reglas nuevas `presencia` y `{path=**}/votos` de la sección de Firebase; si faltan, el contador muestra "—". Con muchas visitas simultáneas conviene vigilar la cuota de escrituras del plan gratis (20 000 al día).

### Rankings del inicio
Se calculan contando las reacciones de cada película (todos los tipos suman igual) y se guardan 30 minutos en el navegador de cada visitante. Cada cálculo hace 2 consultas de conteo por película (hasta las 100 más recientes), lo que cuesta pocas lecturas de Firestore; está pensado para catálogos de hasta unos cientos de títulos. Mientras no haya reacciones, Top Semanales muestra las más nuevas y la fila "10 más votadas" no aparece.

### Continuar viendo
En videos propios (.mp4, .webm, .m3u8) se guarda el minuto por persona (en su cuenta de Google o, sin sesión, en su navegador) y la película se retoma ahí. Se borra al terminarla. En YouTube, Vimeo e Internet Archive no se puede medir el avance.

### Ficha técnica: ¿de dónde sale?
Duración y calificación se copian **una sola vez** al publicar (con el autocompletar de TMDB si lo activaste, o escritas por ti) y quedan en el registro de tu película. El sitio público nunca consulta TMDB, así que los visitantes no dependen de él. Si usas TMDB, deja la atribución del pie de página.

## 3. Quién puede publicar
| Rol | Cómo se obtiene | Qué puede hacer |
|---|---|---|
| **Administrador** | Tu correo en `ADMIN_EMAILS` y en las reglas | Publicar, editar y eliminar cualquier película; dar y quitar acceso a editores. |
| **Editor** | Tú lo agregas en **Publicar → Equipo con permiso para publicar** con su correo de Google | Publicar, y editar o eliminar solo sus propias películas. No ve la sección Equipo. |
| **Cualquier otra persona** | — | Ver y reproducir, reaccionar, guardar favoritas y listas. El menú "Publicar" no le aparece y las reglas rechazan cualquier intento de escribir. |

Al quitarle el acceso a un editor pierde el permiso de inmediato; sus películas ya publicadas se conservan (puedes eliminarlas tú).

## 4. Reacciones
Me gusta, Me encanta, Me divierte, Me asombra, Me entristece y Me enoja. En computador la barra se abre al pasar el mouse sobre el botón; en el móvil, manteniéndolo presionado. Tocar de nuevo la reacción elegida la quita.

- **Sin registro:** al reaccionar por primera vez, Firebase crea una sesión anónima invisible y guarda una reacción por persona. Si luego inicia sesión con Google, su reacción se conserva.
- **Límite real:** sin cuenta no se puede impedir al 100 % que alguien repita votos (por ejemplo, borrando los datos del navegador). Sirven como muestra de ánimo, no como votación oficial.

## 5. Probar y publicar el sitio
- Local: `python3 -m http.server 8000` y abre http://localhost:8000 (no abre con doble clic porque usa módulos).
- GitHub Pages: sube la carpeta a un repositorio y activa Pages (Settings → Pages → rama `main`, carpeta `/root`).
- El catálogo se guarda 5 minutos en el navegador de cada visitante. Quien puede publicar siempre ve los datos en vivo.

### Actualizar el sitio (catálogo estático, SEO y sitemap)
Para que el sitio sea rápido y no gaste lecturas de Firestore, el público lee **`catalogo.json`**, un archivo estático. El mismo paso genera una página por película (`p/ID/index.html`, con título, sinopsis, datos estructurados y vista previa para compartir), `sitemap.xml` y `robots.txt`, que Google usa para encontrar cada película. Todo lo hace `build.mjs`, sin instalar nada (Node 18+).

- **Automático (recomendado):** el archivo `.github/workflows/actualizar-sitio.yml` lo ejecuta cada hora en GitHub y guarda los cambios. Para actualizar ya: pestaña **Actions → Actualizar sitio → Run workflow**. Si el repositorio es privado o Actions está desactivado, usa la opción manual.
- **Manual:** `node build.mjs` en la carpeta del sitio y sube los archivos que cambien (`catalogo.json`, `p/`, `sitemap.xml`, `index.html`).
- Pon tu dirección en `SITE_URL` (`config.js`); en GitHub Actions se deduce sola. Sin ella no se crean sitemap ni enlaces canónicos.
- Después de publicar el sitio, registra `https://TU-DIRECCION/sitemap.xml` en Google Search Console para que empiece a indexar.
- Si `catalogo.json` no existe o está vacío, la app lee Firestore directamente (funciona igual, solo gasta lecturas).
- Una película nueva aparece al público en la siguiente actualización (hasta 1 h con el modo automático). Las programadas se activan en esa misma cadencia. Para borrar una película del todo, elimínala en el panel y actualiza el sitio.
- La carpeta `p/` es 100 % generada: no pongas nada tuyo ahí.
- Imágenes: para que el inicio cargue ligero usa pósters en WebP de unos 300–500 px de ancho y fondos de 200 KB o menos; evita los originales gigantes de TMDB.
- Fuente del logo: Bebas Neue desde Google Fonts (ya enlazada en `index.html`; para cambiarla edita ese enlace y `.logo` en `styles.css`).

## Atribución
Si usas el autocompletar de TMDB, el pie de página muestra la atribución requerida. Dominio público: Internet Archive.
