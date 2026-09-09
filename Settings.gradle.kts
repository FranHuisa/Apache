rootProject.name = "apache"
 
// El proyecto tiene dos módulos independientes que se comunican por HTTP:
// - core: el "cerebro" del agente (Spring Boot en Kotlin). Corre como servicio local.
// - desktop: la interfaz gráfica (Compose Desktop). Es un cliente del core.
include("core", "desktop")