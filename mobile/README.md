# Apache Móvil

App Android **independiente** de Apache: funciona sin el PC. Gemini se llama
directamente desde el móvil y todos los datos (conversaciones, memoria,
horario, recordatorios) se guardan en una base SQLite del propio teléfono.

Hecha en Kotlin con **Jetpack Compose** (Material 3).

## Abrirla y probarla

1. En Android Studio: **File › Open** y elige la carpeta `mobile` (no la raíz del repo).
2. Espera a que termine la sincronización de Gradle (la primera vez descarga bastante).
3. Conecta el móvil por USB con la **depuración USB** activada
   (Ajustes › Información del teléfono › pulsa 7 veces «Número de compilación» →
   Opciones de desarrollador › Depuración USB).
4. Pulsa ▶ **Run 'app'**.
5. En la app, ve a **Ajustes**, pega tu **API key de Gemini** (la misma que usa
   Apache en el PC, o una nueva en https://aistudio.google.com/apikey) y guarda.

## Qué puede hacer

* Chat con el mismo estilo que Apache de escritorio (respuestas cortas y naturales).
* Voz: botón 🎤 (reconocimiento de voz de Android) y lectura de respuestas en voz alta.
* Imágenes verificadas por Gemini; se guardan en la galería solo si tocas una o dices «guarda la 2».
* Adjuntar fotos, PDF o texto (📎).
* Búsqueda en internet y tiempo.
* Memoria permanente («recuerda que vivo en Madrid»).
* Horario y calendario: bloques del día, «Organizar mi día» y avisos al empezar cada bloque.
* Recordatorios con notificación (aunque la app esté cerrada; se reprograman al reiniciar el móvil).
* Abrir apps del móvil, alarmas y temporizadores en el reloj, y poner canciones (YouTube o Spotify).
* Resumen del día la primera vez que abres la app cada día.

## Qué no hace (es del PC)

Archivos y pantalla del PC, control de la música del PC y Smart Home.

## Versiones

AGP 8.5.2 · Kotlin 1.9.24 · Compose BOM 2024.06.00 (compilador 1.5.14) ·
minSdk 26 (Android 8) · targetSdk 34. Si Android Studio propone actualizar
AGP o Gradle, se puede aceptar, pero entonces Kotlin y el compilador de
Compose también tendrán que subir de versión a la vez.
