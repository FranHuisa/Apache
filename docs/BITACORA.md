# Bitácora de desarrollo — Apache

## 10/09/2026 — feature/get-weather

### Objetivo

Añadir dos tools nuevas: consulta del tiempo (`getWeather`) y control multimedia
(`musicControl`).

### Trabajo realizado

* Creada `GetWeatherTool` (READ_ONLY): consulta el tiempo actual y previsión de
  hasta 7 días para cualquier localización, usando la API pública de
  Open-Meteo (geocoding + forecast, sin necesidad de API key).
* Creada `MusicControlTool` (REVERSIBLE): play/pausa, siguiente/anterior,
  subir/bajar/fijar volumen y consultar qué se está reproduciendo.
* Creada la abstracción `MusicSource` (`core/tools/music`) para que
  `MusicControlTool` no dependa de ningún reproductor concreto. Añadida
  `NoOpMusicSource` como fallback mientras no haya ninguna integración real,
  y `MusicSourceManager` para elegir la fuente activa entre todas las
  registradas en Spring.

### Pendiente

* Integrar una `MusicSource` real (Spotify, reproductor local, YouTube Music).
* Decidir si `getWeather` necesita cachear resultados para evitar pedir el
  mismo tiempo repetidamente en una misma conversación.

## 09/09/2026

### Objetivo

Crear la base del proyecto y conseguir ejecutar la aplicación de escritorio.

### Trabajo realizado

* Creado proyecto Gradle con los módulos `core` y `desktop`.
* Configurado Kotlin y Java 21.
* Configurado Spring Boot en `core`.
* Configurado Compose Desktop en `desktop`.
* Creado `Main.kt`.
* Conseguida la ejecución de la ventana de escritorio.

### Problemas encontrados

* `compileKotlin` no aparecía inicialmente en `desktop`.
* Había imports antiguos `com.apache.core.tools.*`.
* La aplicación no encontraba `com.apache.MainKt`.
* Faltaba el dispatcher principal para Compose Desktop.

### Soluciones

* Corregida la configuración de Gradle.
* Eliminados los imports incorrectos.
* Creado `desktop/src/main/kotlin/com/apache/Main.kt`.
* Añadida la dependencia `kotlinx-coroutines-swing`.
* Corregida la configuración de ejecución de Compose Desktop.

### Estado actual

* [x] Core compila
* [x] Spring Boot arranca
* [x] Desktop compila
* [x] Ventana Compose abre
* [x] Interfaz inicial del asistente
* [x] Desktop conectado con Core mediante HTTP
* [x] Gemini conectado
* [x] Conversación con Gemini
* [x] Function calling
* [x] Herramienta de hora
* [x] Herramienta de información del sistema
* [x] Herramientas registradas mediante `ToolRegistry`
* [x] Sistema inicial de permisos y niveles de riesgo
* [x] Sistema inicial de confirmaciones
* [x] Memoria de conversaciones
* [x] Base de datos MySQL configurada
* [ ] Mejorar gestión de confirmaciones desde la interfaz
* [ ] Completar y probar todas las herramientas
* [ ] Mejorar memoria y contexto
* [ ] Archivos
* [ ] Seguridad avanzada
* [ ] Voz
* [ ] Smart Home
