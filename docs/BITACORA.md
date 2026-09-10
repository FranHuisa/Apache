# Bitácora de desarrollo — Apache

## 10/09/2026 — feature/get-weather

### Objetivo

Añadir nuevas tools relacionadas con información del sistema y control del
ordenador, además de completar el control multimedia.

### Trabajo realizado

* Creada `GetWeatherTool` (READ_ONLY): consulta el tiempo actual y previsión de
  hasta 7 días para cualquier localización, usando la API pública de Open-Meteo
  (geocoding + forecast, sin necesidad de API key).
* Creada `MusicControlTool` (REVERSIBLE): play/pausa, siguiente/anterior,
  subir/bajar/fijar volumen y consultar qué se está reproduciendo.
* Creada la abstracción `MusicSource` (`core/tools/music`) para que
  `MusicControlTool` no dependa de ningún reproductor concreto.
* Añadida `NoOpMusicSource` como fallback mientras no haya ninguna integración
  real, y `MusicSourceManager` para elegir la fuente activa entre todas las
  registradas en Spring.
* Integrada una `WindowsMusicSource` real mediante Windows Media Session para
  controlar reproductores compatibles.
* Añadido control del volumen del sistema mediante `volctl`.
* Solucionado el funcionamiento de la acción `previous`, realizando una
  segunda pulsación cuando la primera no cambia de canción.
* Completado y probado el control multimedia básico.

### Control de aplicaciones

* Adaptada la antigua `OpenApplicationTool` a una nueva arquitectura basada en
  `ApplicationSource`.
* Creada la abstracción `ApplicationSource`.
* Creada `ApplicationSourceManager` para seleccionar automáticamente la fuente
  de aplicaciones disponible.
* Creada `WindowsApplicationSource` para controlar aplicaciones en Windows.
* Añadidas las acciones `open`, `close` y `status`.
* Implementada la apertura de aplicaciones mediante PowerShell y
  `Get-StartApps`.
* Implementada la resolución de procesos mediante `Get-Process` para cerrar
  aplicaciones y consultar su estado.
* Probado correctamente el lanzamiento de aplicaciones como Calculadora,
  Discord y Predecessor.
* Añadidos alias para algunos nombres de aplicaciones cuando el nombre usado
  por el usuario no coincide con el nombre utilizado por Windows.
* Comprobado el funcionamiento con Visual Studio Code utilizando su proceso
  `Code`.

### Múltiples llamadas a herramientas

* Modificado `GeminiResult` para permitir varias llamadas a herramientas en una
  misma respuesta de Gemini.
* Modificado `GeminiClient` para procesar todas las `functionCall` presentes en
  la respuesta, en lugar de únicamente la primera.
* Modificado `Agent` para ejecutar todas las herramientas solicitadas cuando
  Gemini devuelve varias llamadas.
* Conservado el sistema existente de permisos, niveles de riesgo,
  confirmaciones, memoria y `requiresGeminiResponse`.
* Añadida una instrucción al contexto de Gemini para que, cuando el usuario
  solicite varias acciones, realice todas las acciones necesarias.
* Probado correctamente un caso como:
  `Abre la calculadora y Discord`.
* Solucionado un problema de Kotlin relacionado con el smart cast de una
  variable mutable en `GeminiClient`.

### Problemas encontrados

* PowerShell interpretaba incorrectamente determinados comandos multilínea
  cuando se enviaban directamente mediante `-Command`.
* La apertura de aplicaciones no siempre coincidía con el nombre escrito por
  el usuario debido a la localización y a los nombres internos de Windows.
* El proceso interno de una aplicación no siempre coincide con su nombre
  visible.
* `status` no es completamente fiable para todas las aplicaciones y requiere
  conocer o resolver correctamente su nombre de proceso.
* La gestión de múltiples `functionCall` necesitó modificar tanto el modelo de
  resultados como `GeminiClient` y `Agent`.

### Soluciones

* Cambiado el envío de scripts de PowerShell a `-EncodedCommand`, codificando
  los comandos como UTF-16LE + Base64.
* Utilizado `Get-StartApps` para localizar aplicaciones instaladas en Windows.
* Utilizado `shell:AppsFolder` + AppID para abrir aplicaciones correctamente.
* Añadida resolución de nombres y procesos para aplicaciones conocidas.
* Añadido soporte para procesar varias llamadas a herramientas en una misma
  respuesta de Gemini.

### Pendiente / Mejoras futuras

* Mejorar la detección de `status` para aplicaciones con procesos o nombres
  internos diferentes.
* Crear algún sistema de ayuda para que Apache pueda identificar los nombres
  correctos de las aplicaciones y procesos sin tener que conocerlos
  manualmente.
* Revisar y probar más a fondo el sistema de múltiples `functionCall`; por
  ahora funciona correctamente, pero necesita algo más de revisión antes de
  considerarlo definitivo.
* Integrar una `MusicSource` adicional (Spotify, reproductor local,
  YouTube Music).
* Decidir si `getWeather` necesita cachear resultados para evitar pedir el
  mismo tiempo repetidamente en una misma conversación.

### Estado actual

* [x] Consulta del tiempo
* [x] Control multimedia básico
* [x] Control del volumen del sistema
* [x] Abrir aplicaciones
* [x] Cerrar aplicaciones
* [x] Consultar estado de aplicaciones
* [x] Múltiples llamadas a herramientas en una misma petición
* [x] Probado abrir varias aplicaciones con una sola petición
* [x] Arquitectura `MusicSource`
* [x] Arquitectura `ApplicationSource`
* [x] Integración real con Windows para música
* [x] Integración real con Windows para aplicaciones

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
