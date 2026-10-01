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
* [ ] Android
* [ ] Docker
* [ ] GitHub / CI
