# Bitácora de desarrollo — Apache

## 09/09/2026

### Objetivo
Crear la base del proyecto y conseguir ejecutar la aplicación de escritorio.

### Trabajo realizado
- Creado proyecto Gradle con los módulos `core` y `desktop`.
- Configurado Kotlin y Java 21.
- Configurado Spring Boot en `core`.
- Configurado Compose Desktop en `desktop`.
- Creado `Main.kt`.
- Conseguida la ejecución de la ventana de escritorio.

### Problemas encontrados
- `compileKotlin` no aparecía inicialmente en `desktop`.
- Había imports antiguos `com.apache.core.tools.*`.
- La aplicación no encontraba `com.apache.MainKt`.

### Soluciones
- Corregida la configuración de Gradle.
- Eliminados los imports incorrectos.
- Creado `desktop/src/main/kotlin/com/apache/Main.kt`.

### Estado actual
- [x] Core compila
- [x] Spring Boot arranca
- [x] Desktop compila
- [x] Ventana Compose abre
- [ ] Crear interfaz del asistente
- [ ] Conectar Gemini
- [ ] Sistema de herramientas
- [ ] Memoria
- [ ] Base de datos MySQL
- [ ] Seguridad/permisos