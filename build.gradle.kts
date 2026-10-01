// Build raíz: solo declara las versiones de los plugins (con `apply false`).
//
// Cada módulo (core, desktop) aplica los plugins que necesita, pero SIN
// versión: si cada módulo declara su propia versión, Gradle carga el plugin
// de Kotlin dos veces ("The Kotlin Gradle plugin was loaded multiple times"),
// lo que puede corromper la caché de compilación incremental.
plugins {
    kotlin("jvm") version "1.9.24" apply false
    kotlin("plugin.spring") version "1.9.24" apply false
    id("org.springframework.boot") version "3.3.2" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
}
