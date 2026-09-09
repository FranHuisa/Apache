// Este build.gradle.kts raíz se deja intencionadamente vacío.
// Cada módulo (core, desktop) define sus propios plugins y dependencias,
// porque son aplicaciones distintas (una es un servicio Spring Boot,
// la otra una app de escritorio Compose) con ciclos de vida distintos.