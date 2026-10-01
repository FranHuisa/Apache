# Bitácora de desarrollo — Apache

## 01/10/2026 — feature/imagenes-horario

### Objetivo

Con Apache ya funcional, añadir dos capacidades nuevas: que Apache pueda enseñar imágenes en el chat (de 1 a 3 según la petición) y una ventana de **Horario** para organizar el día en bloques de tiempo, tanto a mano como pidiéndoselo a Apache.

### Trabajo previo guardado

* Antes de empezar se guardaron en un commit propio (`WIP: empaquetado...`) los cambios locales que no estaban commiteados en `desktop/build.gradle.kts` y `CoreProcessManager.kt` (copia de `java.exe` al runtime del instalador, `console = true` y búsqueda de Java por `JAVA_HOME` → `PATH` → `java.home`).
* A partir de esta sesión se hace **un commit por cada paso** y se sube a GitHub en la rama `feature/imagenes-horario`, para no volver a perder trabajo.

### Imágenes en el chat

* Nueva tool `searchImages` (READ_ONLY) en el Core:
  * Busca en **Wikimedia Commons** (sin API key) y, si no hay resultados o falla, en **Openverse**.
  * Recibe `query` (mejor en inglés) y `count`, que se limita siempre a 1-3.
  * Devuelve las imágenes como líneas `IMAGE|url|título|origen` dentro del resultado de la tool.
* Nueva clase `ChatImage` en `agent`, con el formato de esas líneas y su lectura (`parseFromToolOutput`). Cualquier tool futura puede devolver imágenes con el mismo formato sin tocar el Agent.
* `Agent` recoge las imágenes que aparezcan en los resultados de las tools durante el turno (máximo 3, sin repetir) y las devuelve en `AgentResult.Reply.images`.
* `ChatResponse` incluye ahora `images` (`url`, `title`, `sourceUrl`).
* Instrucción de sistema actualizada: cuándo usar imágenes, 1 para una cosa concreta y 2-3 para varias/comparaciones, y no escribir los enlaces en el texto.
* Desktop:
  * `ChatMessage` y `ChatResponse` llevan la lista de imágenes.
  * `downloadImageBitmap` (`network/ImageApi.kt`) descarga la imagen con OkHttp, la decodifica con Skia y la guarda en una caché en memoria.
  * Nuevo componente `ChatImages`: 1 imagen grande (380×260) o 2-3 miniaturas (230×170), con indicador de carga, aviso si falla y pie con el título. Clic → abre la página de origen en el navegador.
  * `MessageBubble` dibuja las imágenes debajo del texto.

### Horario

* Nueva tool `planDaySchedule` (REVERSIBLE) en el Core: recibe `date` y una lista de `blocks` (`title`, `start`, `end`, `description`) y crea todos los bloques del día en **una sola llamada** (antes habría hecho falta una vuelta a Gemini por cada `createCalendarEvent`).
* Los bloques son eventos normales del calendario con inicio y fin, así que se ven también en la sección Calendario.
* El Agent añade la **fecha y hora actual** a la instrucción de sistema en cada turno, para que Gemini sepa qué es "hoy" o "mañana" sin llamar antes a `getCurrentTime`.
* Instrucción de sistema: para organizar el día, consultar primero `getCalendarEvents` y luego crear todo con `planDaySchedule`, proponiendo horarios razonables en vez de preguntar demasiado.
* Desktop — nueva sección **Horario** en la barra lateral (`ui/schedule`):
  * `ScheduleController`: día seleccionado, bloques, formulario y petición a Apache.
  * Navegación ◀ Hoy ▶ y resumen del día (número de bloques y horas planificadas).
  * Línea de tiempo de 06:00 a 24:00 (se amplía si hay bloques antes), con línea roja de "ahora" en el día actual y desplazamiento automático a la hora actual.
  * Los bloques que se solapan se colocan en columnas.
  * Colores por estado: pendiente, atrasado y completado (mismos colores que Calendario).
  * Clic en una hora vacía → nuevo bloque a esa hora; clic en un bloque → editar, marcar como hecho o eliminar (borrado lógico).
  * **Organizar con Apache**: se escribe qué hay que hacer ese día y Apache reparte el día en bloques. La petición va por la misma conversación del chat, y al terminar el horario se recarga solo.
  * Botón **Ventana aparte ↗**: abre el horario en una ventana propia más pequeña (diseño compacto con pestañas Día / Añadir / Apache) para tenerlo a la vista mientras se usa el resto de Apache.
* `fetchCalendarEventsBetween(from, to)` en `CalendarApi` para pedir solo los eventos de un día.
* `ChatController.runTurn` ahora devuelve el texto de la respuesta (o `null` si falla), para poder mostrarlo en Horario.
* Ayuda actualizada con ejemplos de imágenes y de horario.

### Poner una canción concreta

* Nueva tool `playSong` (REVERSIBLE, responde sin volver a Gemini):
  * **YouTube** (por defecto): `YouTubeSongSearch` lee la página de resultados de YouTube (sin API key), coge el primer vídeo y lo abre en el navegador con `autoplay=1`. Si no consigue leer el resultado, abre la búsqueda para que el usuario elija.
  * **Spotify** (solo si se pide): abre `spotify:search:...` en la app. Spotify no deja reproducir una canción concreta sin su API con OAuth, así que el usuario tiene que darle a reproducir.
  * Antes de abrir la canción pausa lo que esté sonando (Windows Media Session), para que no suenen dos cosas a la vez.
  * Las URLs se abren con `rundll32 url.dll,FileProtocolHandler` porque Spring Boot arranca en modo headless (no sirve `java.awt.Desktop`) y así se evita que `cmd` corte la URL en los `&`.
* Al abrirse en el navegador, la canción aparece como sesión multimedia de Windows, así que `musicControl` (pausa, siguiente, volumen) sigue funcionando sobre ella.
* Instrucción de sistema: `playSong` para poner algo concreto, `musicControl` para controlar lo que ya suena.
* Ayuda actualizada con ejemplos («Pon Bohemian Rhapsody de Queen», «Busca la canción Despacito»).

### Memoria permanente y sección Memoria

* La tabla `memory` existía en el esquema desde el 12/09, pero nada la usaba y la sección Memoria del Desktop era solo un texto de "próximamente".
* Core:
  * `UserMemoryRepository` + `UserMemoryService` sobre `memory` (distintos de `MemoryRepository`, que es el historial de cada conversación).
  * Si ya hay un dato con la misma clave (sin distinguir mayúsculas), se actualiza en vez de duplicarse.
  * Categorías: `personal`, `preferencia`, `rutina`, `trabajo`, `salud` y `otro`.
  * Tools `rememberFact` y `forgetFact` (REVERSIBLE). Apache guarda solo lo personal y duradero cuando se lo cuentas o cuando dices «recuerda que...».
  * En cada turno, el Agent añade a la instrucción de sistema lo que recuerda del usuario (los 60 datos más importantes). Si MySQL falla en ese paso, Apache sigue respondiendo sin esos datos.
  * La memoria se guarda en una tabla propia, `user_memory` (columnas `memory_key` / `memory_value` para evitar las palabras reservadas de MySQL), que `DatabaseFactory` crea al arrancar si no existe. Primero se intentó usar la tabla `memory` del esquema inicial, pero en la base de datos real tiene otras columnas (`Unknown column 'memory.key'`), así que se dejó intacta.
  * Nueva API `/api/memory` (listar, crear, editar y borrar datos) y `/api/memory/conversations` (lista de conversaciones con título sacado del primer mensaje, y sus mensajes visibles, sin las llamadas internas a tools).
* Desktop, sección **Memoria**:
  * Pestaña «Lo que sabe de ti»: datos agrupados por categoría, con formulario para añadir, editar u olvidar.
  * Pestaña «Conversaciones»: historial de conversaciones, vista de los mensajes, botón «Retomar en el chat» y «+ Nueva conversación».
  * `ChatController.openConversation` / `startNewConversation` y `SessionStore.clearConversationId`.

### Arreglo: el Core se bloqueaba al lanzarlo desde el Desktop

* `CoreProcessManager` arrancaba el Core sin leer su salida. Con los logs en DEBUG, el pipe se llenaba (en Windows es de pocos KB) y el Core dejaba de responder.
* Ahora la salida se redirige a `~/.apache/core.log`, que además sirve para revisar errores.

### Problemas encontrados

* En el entorno de esta sesión no hay acceso a Maven Central ni a los repositorios de Compose, así que no se ha podido ejecutar Gradle. El código del Core tocado se compiló con `kotlinc` 1.9.24 contra stubs de Spring/OkHttp/Jackson (sin errores) y se probaron aparte la lectura de líneas `IMAGE|` y el reparto de bloques solapados. El Desktop (Compose) se ha revisado a mano: **hay que compilarlo y probarlo en local**.

### Estado actual

* [x] Tool `searchImages` (Wikimedia Commons + Openverse)
* [x] 1-3 imágenes por respuesta en `ChatResponse`
* [x] Imágenes dibujadas en el chat del Desktop
* [x] Tool `planDaySchedule`
* [x] Fecha y hora actual en la instrucción de sistema
* [x] Sección Horario con línea de tiempo diaria
* [x] Crear / editar / completar / eliminar bloques desde Horario
* [x] Organizar el día con Apache desde Horario
* [x] Horario en ventana aparte
* [x] Tool `playSong` (YouTube; Spotify solo abre la búsqueda)
* [x] Memoria permanente (`rememberFact` / `forgetFact`) en el contexto de Gemini
* [x] Sección Memoria con datos editables e historial de conversaciones
* [x] Salida del Core redirigida a `~/.apache/core.log`
* [ ] Compilar y probar en local (`gradlew :desktop:run`)
* [ ] Guardar las imágenes en el historial para mostrarlas al recargar una conversación
* [ ] Arrastrar bloques para moverlos o cambiar su duración
* [ ] Que el usuario pueda adjuntar imágenes en el chat (Gemini acepta imágenes como entrada)
* [ ] Integrar la API de Spotify (OAuth) para poner canciones concretas también en Spotify
* [ ] Botones de confirmar/cancelar en el chat para las acciones RECOVERABLE (hoy el Desktop solo muestra el aviso)

### Decisiones de diseño

* El Desktop descarga las imágenes; el Core solo pasa URLs. Así el Core no mueve binarios y el chat se pinta en cuanto llega el texto.
* Las imágenes viajan como líneas `IMAGE|...` dentro del resultado de la tool, en lugar de un canal aparte, para no cambiar el contrato `Tool.execute(): String`.
* Los bloques del horario reutilizan `calendar_event` (con `endAt`) en vez de crear una tabla nueva: un solo sitio para los eventos y sin migraciones de MySQL.

## 14/09/2026 — fix-0.1: reorganización del Desktop, calendario y Core integrado

### Objetivo

Ordenar el código del Desktop, que se había concentrado en un `App.kt` enorme, terminar la interfaz del calendario y conseguir que el ejecutable arranque el Core por sí solo.

### Trabajo realizado

* `CalendarController` del Core con endpoints para listar (por rango `from`/`to`, por defecto 30 días), crear, editar, completar (`POST /events/{id}/complete`) y cancelar (`POST /events/{id}/cancel`, borrado lógico) eventos.
* Interfaz de **Calendario** en el Desktop: formulario de nuevo evento / edición, lista de próximos eventos con estado (Confirmado, Atrasado, Completado), diálogo de cancelación y archivo de eventos completados.
* El estado "Atrasado" no se guarda en MySQL: se calcula en el Desktop comparando con la hora actual.
* Gran refactor del Desktop (`BIG CHANGES`): `App.kt` pasa de más de 3000 líneas a ser solo el punto de entrada que crea los controllers y elige la pantalla. Nueva estructura:
  * `model/` — DTOs de chat, calendario, notificaciones y voz.
  * `network/` — llamadas HTTP al Core (`ChatApi`, `CalendarApi`, `NotificationApi`, `VoiceApi`, `ApiClient`).
  * `session/SessionStore` — `conversationId` persistido en `~/.apache/session.properties`.
  * `ui/chat`, `ui/calendar`, `ui/voice`, `ui/notifications`, `ui/ayuda`, `ui/memoria`, `ui/components`, `ui/theme` (paleta `ApacheColors`).
  * `util/` — utilidades de calendario y expresión regular de la wake word.
* `CoreProcessManager`: el Desktop arranca el Core automáticamente.
  * Si ya hay un Core escuchando en el puerto 8080 no lanza otro.
  * Busca el jar del Core empaquetado como recurso (`/core/apache-core.jar`) y, en desarrollo, en las rutas de `core/build/libs`.
  * Espera a que Spring Boot responda antes de abrir la interfaz y lo detiene al cerrar la ventana.
* `desktop/build.gradle.kts`: `processResources` mete el `bootJar` del Core dentro de los recursos del Desktop, y la ventana inicial pasa a 1400×850.
* Empaquetado (cambios guardados el 01/10 como WIP): tarea `copyJavaExecutable` que copia `java.exe` al runtime del instalador antes de `packageExe`, `console = true` en Windows y búsqueda de Java `JAVA_HOME` → `PATH` → `java.home` en `CoreProcessManager`.

### Problemas encontrados

* El runtime reducido que genera `jpackage` no incluye `java.exe`, así que el Desktop empaquetado no podía lanzar el Core como subproceso.

### Estado actual

* [x] Endpoints de calendario completos
* [x] Interfaz de calendario con archivo de completados
* [x] Desktop dividido por dominios (model / network / ui / util)
* [x] El Desktop arranca y detiene el Core automáticamente
* [x] Core empaquetado dentro del Desktop
* [ ] Comprobar el instalador `.exe` en un equipo limpio
* [ ] Avisar en la interfaz si MySQL no está disponible

## 13/09/2026 — fix/notifications-followup + voz + empaquetado

### Objetivo

Cerrar los pendientes detectados en la sesión anterior sobre notificaciones, añadir un control de silencio para la voz de Apache, y dar el primer paso para distribuir Apache 0.1.0 como ejecutable.

### Trabajo realizado

* Revisado el flujo completo `ReminderScheduler → ReminderService → NotificationService → NotificationRepository → MySQL`: confirmado que ya persiste correctamente cada notificación generada por un recordatorio. No ha hecho falta ningún cambio en esta parte.

* Corregido el bug de notificaciones que "reaparecían" en el Desktop:

  * Identificada la causa: `dismissNotification` hacía un descarte optimista en memoria mientras `markNotificationRead` se confirmaba en segundo plano; el siguiente polling sobrescribía `pendingNotifications` con la respuesta del servidor (todavía "no leída") y la notificación volvía a aparecer.

  * Añadido un conjunto `locallyDismissed` compartido entre el descarte del usuario y el bucle de polling, que oculta la notificación mientras la confirmación está en vuelo y se sincroniza de nuevo con el servidor en cuanto este la reporta como leída.

  * Si `markNotificationRead` falla, se deshace el descarte local para no perder la notificación de vista para siempre.

* Corregido que `fetchUnreadNotifications()` se ejecutaba en el hilo de UI (bloqueante) dentro del polling; ahora usa `withContext(Dispatchers.IO)`, igual que el resto de llamadas de red del Desktop.

* Añadido endpoint `GET /api/notifications/read` en `NotificationController` (más los métodos correspondientes en `NotificationService` y `NotificationRepository`) para consultar el histórico de notificaciones ya leídas, sin borrado físico.

* Añadido un botón de silenciar/activar la voz de Apache en la sección Chat del Desktop:

  * Nuevo estado `isMuted` en `App()`.

  * `speakReply` ahora recibe `muted: Boolean` y no llega a llamar al endpoint de Text-to-Speech si la voz está silenciada (ahorra la llamada a Gemini, no solo la reproducción).

  * El texto de la respuesta se sigue mostrando siempre igual; el silencio solo afecta al audio.

* Añadida configuración de `nativeDistributions` en `desktop/build.gradle.kts` (formatos `Msi` y `Exe` vía `jpackage`) para poder generar un instalador/ejecutable de Windows de la interfaz de escritorio.

### Problemas encontrados

* El empaquetado con `nativeDistributions` solo genera el ejecutable del módulo `desktop`. Apache sigue siendo dos procesos independientes (`core` como servicio Spring Boot en el puerto 8080, más MySQL): el `.exe`/`.msi` no arranca ni empaqueta ninguno de los dos. Por ahora hay que seguir arrancando `core` y MySQL a mano antes de abrir el ejecutable.

### Estado actual

* [x] Persistencia de notificaciones verificada extremo a extremo
* [x] Notificaciones descartadas ya no reaparecen por condición de carrera
* [x] Polling de notificaciones fuera del hilo de UI
* [x] Endpoint de histórico de notificaciones leídas
* [x] Botón de silenciar la voz de Apache
* [x] Configuración de empaquetado nativo (Msi/Exe) para el Desktop
* [ ] Arranque unificado de `core` + MySQL junto con el ejecutable
* [ ] Icono personalizado y firma del instalador
* [ ] Purga o gestión de notificaciones muy antiguas

### Decisiones de diseño

* Las notificaciones leídas se conservan en MySQL (no se borran físicamente); mantener el histórico permite futuras consultas tipo "qué recordatorios se dispararon esta semana" y conserva el sentido de `readAt`.

* El silencio de voz es solo del lado del Desktop y no se persiste entre sesiones (vuelve a activarse al reiniciar la app); si hace falta recordarlo, es un cambio pequeño (guardarlo en `~/.apache/session.properties`, igual que `conversationId`).

### Siguiente fase — hacia Apache 0.2.0

* Unificar el arranque: hacer que el ejecutable del Desktop lance automáticamente `core` (por ejemplo, empaquetando el jar de Spring Boot como recurso y arrancándolo como subproceso), o documentar y automatizar con un `.bat`/servicio de Windows mientras tanto.
* Comprobar que MySQL esté disponible antes de arrancar `core` y mostrar un error claro en el Desktop si no lo está, en vez de que el polling falle en silencio.
* Añadir un icono y metadatos propios al instalador (`iconFile`, firma de código si se va a distribuir fuera del propio equipo).
* Persistir la preferencia de silencio de voz entre sesiones.
* Job de purga o paginación para el histórico de notificaciones leídas, antes de que crezca sin límite.
* Corregir los problemas de codificación UTF-8 detectados en la sesión anterior (`recordarÃ©`, etc.) en la consola/cliente.
* Revisar y ampliar la cobertura de pruebas del flujo de recordatorios y notificaciones (hoy validado solo manualmente).

## 13/09/2026 — feature/calendar-tasks-reminders (calendario y tareas)

### Trabajo realizado

* Capa de datos de calendario sobre MySQL: `CalendarRepository` (calendario por defecto del usuario, eventos, completar y cancelar con borrado lógico) y `CalendarService` con las validaciones (título obligatorio, fin no anterior al inicio).
* Tools de calendario: `createCalendarEvent`, `getCalendarEvents`, `updateCalendarEvent` y `deleteCalendarEvent` (esta última pide confirmación al ser RECOVERABLE).
* Tareas: `TaskRepository` + `TaskService` y tools `createTask`, `listTasks`, `completeTask`, `updateTask` y `deleteTask`.
* Tools de recordatorios y notificaciones: `createReminder`, `listReminders`, `cancelReminder` y `listNotifications`.
* `ToolDateParsing`: lectura tolerante de fechas (`yyyy-MM-ddTHH:mm` o `yyyy-MM-dd`) compartida por las tools de calendario, tareas y recordatorios.
* `ApacheDefaults.DEFAULT_USER_ID` como único sitio del usuario por defecto de Apache 0.1.

## 13/09/2026 — feature/reminders-notifications

### Recordatorios y notificaciones

### Objetivo

Implementar y comprobar el sistema de recordatorios de Apache 0.1, conectando Gemini con las herramientas del Core, MySQL, el scheduler y las notificaciones nativas de Windows.

### Trabajo realizado

* Completada la implementación del sistema de recordatorios sobre MySQL.

* Creado `ReminderRepository` para gestionar los recordatorios mediante Exposed.

* Creado `ReminderService` para separar la lógica de negocio del acceso a datos.

* Implementados los estados de los recordatorios:

  * `pending`
  * `triggered`
  * `cancelled`

* Implementado `ReminderScheduler` mediante `@Scheduled`, realizando comprobaciones periódicas de recordatorios pendientes.

* Creado `NotificationRepository` para acceder a la tabla `notification`.

* Creado `NotificationService` para generar y gestionar notificaciones.

* Creado `NotificationController` con endpoints para:

  * Consultar notificaciones.
  * Consultar únicamente las no leídas.
  * Marcar una notificación como leída.
  * Marcar todas las notificaciones como leídas.

* Añadida la herramienta `createReminder` al `ToolRegistry`.

* Confirmado mediante los logs del Core que `createReminder` está correctamente registrada.

* Implementada la comunicación del Desktop con el Core para consultar notificaciones pendientes.

* Implementado el polling periódico de notificaciones en el cliente Desktop.

* Implementado `WindowsNotificationManager` para mostrar notificaciones nativas de Windows mediante AWT.

### Incidencias solucionadas

* El envío de peticiones mediante `curl.exe` desde PowerShell provocaba errores de JSON por problemas de escapado de comillas.

* Se sustituyó la prueba por `Invoke-RestMethod` utilizando JSON generado con `ConvertTo-Json`.

* Las peticiones con caracteres especiales presentaban problemas de codificación UTF-8 al enviarlas desde PowerShell.

* Se solucionó la prueba de envío utilizando explícitamente bytes UTF-8:

```text
PowerShell
↓
JSON
↓
UTF-8
↓
/api/chat
```

* También se detectó que la consola muestra algunos caracteres como `recordarÃ©`, aunque esto no impide el funcionamiento del sistema.

### Prueba completa realizada

Se probó mediante lenguaje natural:

```text
"Recuérdame dentro de 2 minutos que tengo que probar Apache"
```

El flujo completo funcionó correctamente:

```text
Usuario
↓
ChatController
↓
Agent
↓
Gemini
↓
createReminder
↓
ReminderService
↓
ReminderRepository
↓
MySQL
↓
ReminderScheduler
↓
NotificationService
↓
WindowsNotificationManager
↓
Notificación de Windows
```

El recordatorio se creó correctamente en MySQL con:

```text
title      = Probar Apache
trigger_at = 2026-09-13 22:37:00
status     = pending
```

Una vez alcanzada la hora programada, el estado cambió correctamente:

```text
pending → triggered
```

También se confirmó que Windows mostró correctamente la notificación del recordatorio.

### Estado actual

* [x] `ReminderRepository`
* [x] `ReminderService`
* [x] `ReminderScheduler`
* [x] `createReminder`
* [x] `NotificationRepository`
* [x] `NotificationService`
* [x] `NotificationController`
* [x] Consulta de notificaciones desde Desktop
* [x] Polling de notificaciones
* [x] Notificación nativa de Windows
* [x] Creación de recordatorio mediante lenguaje natural
* [x] Persistencia del recordatorio en MySQL
* [x] Cambio automático `pending → triggered`
* [x] Flujo completo de recordatorio probado

### Pendiente / Problemas detectados

* Comprobar y asegurar que cada recordatorio disparado genera correctamente su registro persistente en `notification`.

* Revisar el comportamiento de las notificaciones ya generadas y su estado `read`.

* Evitar que las notificaciones ya gestionadas vuelvan a aparecer innecesariamente durante el polling.

* Añadir una gestión completa de notificaciones desde Apache:

  * Ver notificaciones pendientes.
  * Marcar una notificación como leída.
  * Marcar todas como leídas.
  * Gestionar o descartar notificaciones antiguas.
  * Mantener sincronizado el estado entre MySQL, Core y Desktop.

* Corregir posteriormente los problemas de visualización UTF-8 (`recordarÃ©`, `¿`, `é`, etc.) en las respuestas mostradas por el cliente o la consola.

### Decisiones de diseño

* Los recordatorios permanecen persistidos en MySQL.

* El `ReminderScheduler` es responsable únicamente de detectar recordatorios vencidos y delegar su procesamiento en `ReminderService`.

* `ReminderService` es responsable de convertir un recordatorio vencido en una notificación.

* El Desktop no ejecuta directamente la lógica de recordatorios; únicamente consulta las notificaciones expuestas por el Core.

* Las notificaciones se gestionan mediante estado (`read`) para separar la generación del aviso de su lectura por parte del usuario.

### Siguiente fase

Completar el sistema de **gestión de notificaciones**, asegurando la persistencia, lectura, descarte y sincronización entre **MySQL → Core → Desktop → Windows** antes de continuar con nuevas funcionalidades de Apache 0.1.

## 12/09/2026 — Memoria y conversaciones en MySQL

### Trabajo realizado

* `MemoryRepository` + `MemoryService` sobre MySQL: las conversaciones y sus mensajes (usuario, modelo, llamadas a funciones y respuestas de funciones) se guardan en base de datos en lugar de en memoria.
* El `Agent` usa `MemoryService` para recuperar el historial completo de cada turno.
* `ChatRequest` / `ChatResponse` separados en `api/dto`, y nuevo `ConfirmRequest` para las confirmaciones.
* Unificado `conversationId` como `Long` en Memory, Agent, API y Desktop.
* El Desktop guarda el `conversationId` para continuar la conversación tras reiniciar.

## 12/09/2026 — feature/database

### Configuración de base de datos MySQL

### Objetivo

Migrar la persistencia de Apache desde SQLite a MySQL y dejar preparada la arquitectura de acceso a datos para las funciones de Apache 0.1.

### Trabajo realizado

* Cambiada la configuración de Apache para utilizar MySQL como base de datos principal.
* Añadido el driver JDBC de MySQL.
* Configurada la conexión mediante `application.yml`:

  * Base de datos `apache`.
  * MySQL en `localhost:3306`.
  * Usuario `root`.
* Creada `DatabaseFactory` como punto único de conexión de Apache con la base de datos.
* Definido el esquema inicial de Apache 0.1 con las entidades necesarias para:

  * Usuarios y configuración.
  * Memoria y contexto.
  * Conversaciones y mensajes.
  * Calendarios y eventos.
  * Tareas.
  * Recordatorios y notificaciones.
  * Ejecuciones de herramientas.
  * Permisos.
* Añadidas las primeras tablas Exposed para trabajar con MySQL.
* Creado `UserRepository` para acceder a los usuarios.
* Creado `UserService` para separar la lógica de negocio del acceso a datos.
* Creado `UserController` para exponer la información del usuario mediante la API REST.
* Configurado el usuario inicial `default`.

### Prueba realizada

Se comprobó correctamente la comunicación completa:

```text
HTTP
↓
Controller
↓
Service
↓
Repository
↓
Exposed
↓
MySQL
```

Petición utilizada:

```text
GET http://localhost:8080/api/users/default
```

Respuesta obtenida correctamente:

```json
{
  "id": 1,
  "name": "default",
  "displayName": "Fran",
  "active": true,
  "createdAt": "2026-09-11T22:04:26",
  "updatedAt": "2026-09-11T22:04:26"
}
```

### Incidencia solucionada

* La tabla de usuarios estaba creada inicialmente como `user`, mientras que Apache esperaba `users`.
* Se corrigió el nombre de la tabla mediante MySQL:

```sql
RENAME TABLE user TO users;
```

* Después del cambio, el endpoint volvió a funcionar correctamente.

### Decisiones de diseño

* MySQL será la base de datos principal de Apache 0.1.
* Exposed será la capa utilizada para acceder a la base de datos desde Kotlin.
* Apache no creará automáticamente las tablas desde `DatabaseFactory`.
* La IA no accederá directamente a la base de datos.
* Se mantiene la arquitectura:

```text
User
↓
AI
↓
Tool
↓
Service
↓
Repository
↓
Database
```

* La separación entre cliente, Core y base de datos se mantiene para poder añadir posteriormente el cliente móvil.

### Pendiente / Mejoras futuras

* Completar los mappings Exposed del resto de tablas.
* Implementar la memoria/contexto de Apache.
* Implementar conversaciones y mensajes.
* Implementar calendario, eventos y tareas.
* Añadir posteriormente recordatorios y notificaciones.

### Estado actual

* [x] MySQL configurado
* [x] Driver JDBC de MySQL
* [x] Conexión con MySQL funcionando
* [x] Esquema inicial de Apache 0.1
* [x] Usuario `default`
* [x] Exposed configurado
* [x] `UserRepository`
* [x] `UserService`
* [x] `UserController`
* [x] Comunicación API → MySQL comprobada
* [x] Problema de nombre de tabla solucionado

### Siguiente fase

Pasar a la implementación de **Memory / Context**, manteniendo la misma arquitectura de persistencia y trabajando sobre la base de datos MySQL ya configurada.

## 11/09/2026 — feature/voice

### Ajustes posteriores de escucha y ayuda

* Reducido de 2 a 1 segundo el silencio necesario para finalizar una captura
  de voz, para que la orden se procese con menos espera.
* El modo escucha ya no se desactiva tras recibir una orden: continúa activo
  después de la respuesta de Apache.
* Añadidas las órdenes locales de voz `Apache, apaga` y `Apache, corto` para
  desactivar el modo escucha, además del botón ya existente.
* Eliminada la sección independiente `Herramientas`; sus capacidades se han
  incorporado a una guía de Ayuda más completa, con ejemplos, funcionamiento
  del control por voz y desplazamiento vertical.

### Objetivo

Completar el control por voz de Apache: modo escucha activable, wake word
local "Apache", captura del comando, procesamiento mediante el Agent
existente y respuesta hablada (TTS).

### Trabajo previo (ya realizado en la rama antes de continuar)

* Captura de audio con `MicrophoneRecorder` (PCM 16 kHz/16-bit/mono).
* Detección de voz mediante RMS y parada automática tras 3 segundos de
  silencio, además de parada manual.
* Transcripción mediante `SpeechToTextClient` mediante Gemini, expuesta en
  `/api/voice/transcribe`.
* Botón de micrófono (push-to-talk) integrado en la pantalla de chat, con la
  transcripción reutilizando el mismo `Agent` que el chat de texto.

### Trabajo realizado ahora

* Añadido el botón "Escucha activada / desactivada" en la pantalla de chat,
  independiente del botón de micrófono manual.
* Añadido el bucle de escucha pasiva en `App.kt` (`startListenLoop` /
  `startCommandCapture`): reutiliza `MicrophoneRecorder` para grabar cada
  posible frase, la transcribe y comprueba localmente si contiene la palabra
  "Apache" (wake word) mediante una expresión regular, sin enviar audio
  continuamente ni depender de un servicio de wake word dedicado.
* Si el comando se dice en la misma frase ("Apache, abre Discord") se procesa
  directamente; si solo se dice "Apache", se graba el comando a continuación.
* Creado `TextToSpeechClient` en el Core (mismo estilo que
  `SpeechToTextClient`, usando la API de Gemini para generar el audio) y el
  endpoint `POST /api/voice/speak`.
* `AudioPlayer` ahora acepta la frecuencia de muestreo del audio a reproducir
  (el micrófono graba a 16 kHz, pero el TTS de Gemini genera el audio a otra
  frecuencia configurable).
* Añadida `runVoiceTurn`, una función que envía el texto al Core, muestra la
  respuesta y la reproduce por voz antes de continuar, para no reactivar el
  micrófono mientras Apache está hablando.
* Si el modo escucha sigue activo tras responder, Apache vuelve a escuchar
  automáticamente.
* Añadida configuración de TTS (modelo, voz, frecuencia de muestreo) en
  `application.yml`, sin tocar la configuración del modelo de texto/STT ya
  existente.

### Decisiones de diseño

* La wake word se detecta localmente en el código de Desktop (comparando el
  texto transcrito), sin usar un motor de wake word offline dedicado (tipo
  Porcupine/Vosk), para no añadir dependencias nuevas ni modelos adicionales.
* La síntesis de voz solo se activa en las interacciones que vienen de voz
  (botón de micrófono y modo escucha); los mensajes escritos por teclado
  siguen respondiéndose únicamente en texto, como hasta ahora.

### Pendiente / Mejoras futuras

* Confirmar acciones de riesgo (`needsConfirmation`) también por voz; de
  momento se siguen resolviendo por texto/UI.
* Afinar el umbral de RMS y el manejo de falsos positivos de la wake word en
  entornos con ruido de fondo.
* Cachear o evitar transcripciones redundantes cuando el modo escucha lleva
  mucho tiempo activo sin que nadie hable.

### Estado actual

* [x] Modo escucha activable/desactivable
* [x] Wake word local "Apache"
* [x] Captura del comando tras detectar la wake word
* [x] Parada automática tras silencio (ya existente)
* [x] Transcripción → Agent → herramientas (ya existente, reutilizado)
* [x] Text-to-Speech de la respuesta
* [x] Bucle de escucha continua mientras el modo escucha esté activo

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

### Mejora de la interfaz

* Añadida navegación lateral entre las secciones `Chat`, `Memoria`,
  `Herramientas` y `Ayuda`.
* Creada una sección `Ayuda` con información sobre las capacidades actuales
  de Apache y ejemplos de comandos que puede utilizar el usuario.
* Mantenido el diseño visual existente de la aplicación, realizando únicamente
  cambios complementarios en la interfaz.
* Mejorada la ventana principal para que se abra centrada en la pantalla.
* Establecido un tamaño inicial de 1000×700 para mejorar la visualización de
  las diferentes secciones.
* Mantenida la posibilidad de redimensionar la ventana.

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
* Añadir desplazamiento vertical en la sección `Ayuda` para poder consultar
  todo su contenido independientemente del tamaño de la ventana.
* Completar progresivamente las secciones `Memoria` y `Herramientas` de la
  interfaz cuando tengan contenido relevante que mostrar.
* Continuar mejorando la interfaz sin alterar el diseño visual actual antes
  de comenzar con la implementación del control por voz.
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
* [x] Navegación básica de la interfaz
* [x] Sección de ayuda
* [x] Ventana centrada y con tamaño inicial mejorado
* [x] Ventana redimensionable

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
