# Roadmap de Apache

## Fase 1 — Base

* [x] Proyecto Gradle
* [x] Core
* [x] Desktop
* [x] Compose Desktop
* [x] Interfaz principal
* [x] Comunicación Desktop ↔ Core
* [x] El Desktop arranca el Core automáticamente (Core empaquetado dentro)
* [x] Instalador nativo (Msi/Exe)

## Fase 2 — IA

* [x] Gemini
* [x] Conversación
* [x] Contexto mediante historial
* [x] Function calling
* [x] Llamadas múltiples a herramientas en una misma respuesta
* [x] Fecha y hora actual en el contexto
* [x] Imágenes en las respuestas (1-3 por respuesta)
* [x] Búsqueda en internet (Google Search vía Gemini)
* [x] Adjuntar imágenes y archivos en el chat
* [x] «¿Qué hay en mi pantalla?» (captura + Gemini)
* [ ] Optimizar uso de tokens y peticiones (resumir conversaciones largas)

## Fase 3 — Herramientas

* [x] Hora
* [x] Información del sistema
* [x] Registro de herramientas
* [x] Niveles de riesgo
* [x] Confirmaciones (con botones Sí / No en el chat)
* [x] Permisos
* [x] Abrir, cerrar y consultar aplicaciones
* [x] Archivos: buscar, ver recientes y abrir
* [x] Tiempo y previsión (Open-Meteo)
* [x] Control multimedia real (Windows Media Session + volumen del sistema)
* [x] Poner una canción concreta (YouTube; Spotify con su API si se configura)
* [x] Calendario, tareas y recordatorios
* [x] Horario diario con avisos al empezar cada bloque
* [x] Resumen del día al abrir Apache
* [ ] Archivos: mover, renombrar y organizar (con confirmación)

## Fase 4 — Memoria

* [x] MySQL
* [x] Persistencia de conversaciones
* [x] Memoria a corto plazo
* [x] Memoria persistente estructurada (`user_memory`)
* [x] Preferencias del usuario (categorías en la memoria)
* [x] Historial de conversaciones consultable y retomable
* [ ] Recuperación inteligente de recuerdos (solo los relevantes para cada pregunta)

## Fase 5 — Voz

* [x] Speech-to-text
* [x] Text-to-speech
* [x] Activación por voz (modo escucha + wake word local "Apache")
* [x] Silencio de voz recordado entre sesiones
* [ ] Confirmaciones de acciones de riesgo por voz
* [ ] Reducir falsos positivos de la wake word con ruido de fondo

## Fase 6 — Smart Home

* [x] MQTT (cliente y tool `smartHome`, desactivado por defecto)
* [x] Dispositivos configurables en `application.yml`
* [ ] ESP32 (firmware de ejemplo)
* [ ] Estado de los dispositivos en la interfaz
* [ ] Automatizaciones

## Fase 7 — Seguridad y expansión

* [x] Confirmaciones para acciones sensibles
* [ ] Sistema avanzado de permisos
* [ ] Auditoría de acciones
* [x] Android: Apache Móvil 0.1.0, app independiente en `mobile/` (Kotlin + Jetpack Compose)
* [ ] Sincronización móvil ↔ escritorio
* [ ] Docker
* [ ] GitHub / CI

## Fase 8 — Asistente de verdad (móvil primero, luego PC)

Objetivo: que Apache no solo responda, sino que se adelante, haga varias cosas
con una frase, se pueda hablar con él sin tocar la pantalla y recuerde tu día.
Orden de trabajo: de lo más sencillo a lo más complejo.

### 8.1 Modo conversación manos libres
* [x] Móvil: botón «Conversación» que encadena escuchar → responder en voz alta → volver a escuchar
* [x] Móvil: se corta solo con «para», «gracias, ya está» o tras unos segundos de silencio
* [x] Móvil: pantalla completa con el orbe animado mientras dura
* [ ] PC: lo mismo con el modo escucha que ya existe

### 8.2 Rutinas por voz
* [ ] Móvil: crear rutinas hablando («cuando diga *me voy a dormir*, pon alarma a las 7 y dime qué tengo mañana»)
* [ ] Móvil: decir la frase ejecuta todos los pasos de la rutina
* [ ] Móvil: ver, editar y borrar rutinas (en Memoria)
* [ ] PC: las mismas rutinas

### 8.3 Diario automático
* [ ] Móvil: por la noche, aviso «¿qué tal el día?» (hora configurable)
* [ ] Móvil: guarda una entrada por día con lo que cuentes, tu ánimo y lo que hiciste (agenda y listas)
* [ ] Móvil: preguntas sobre el pasado: «¿qué hice el finde pasado?», «¿cuándo fui al médico?»
* [ ] Móvil: ver el diario (en Memoria)
* [ ] PC: diario y preguntas

### 8.4 Apache proactivo
* [ ] Móvil: comprobación cada ~2 h en segundo plano que cruza tiempo, agenda, listas y alarmas
* [ ] Móvil: avisos tipo «va a llover a las 17:00 y tienes gimnasio a las 18:00», «mañana empiezas a las 8 y no tienes alarma», «llevas días sin tocar la compra»
* [ ] Móvil: sin repetir el mismo aviso, horas de silencio (noche) y opción de desactivarlo en Ajustes
* [ ] PC: avisos proactivos mientras Apache está abierto

### Ideas para más adelante
* [ ] Cámara «¿qué es esto?» (plantas, menús, etiquetas)
* [ ] Gastos con foto del ticket
* [ ] Hábitos con rachas
* [ ] Widget en la pantalla de inicio
