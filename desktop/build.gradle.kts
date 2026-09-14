import org.gradle.api.tasks.Copy
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.apache"
version = "0.1.0"

repositories {
    google()
    mavenCentral()
}

dependencies {

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "21"
}

/*
 * Copia el Core ejecutable dentro de los recursos del Desktop.
 *
 * El Core se genera mediante Spring Boot como un JAR ejecutable que
 * contiene todas sus dependencias.
 *
 * De esta forma, el Desktop podrá incluir el Core cuando se empaquete
 * como aplicación/instalador.
 */
tasks.processResources {
    dependsOn(project(":core").tasks.named("bootJar"))

    from(project(":core").tasks.named<Jar>("bootJar")) {
        into("core")
        rename {
            "apache-core.jar"
        }
    }
}

compose.desktop {
    application {

        mainClass = "com.apache.MainKt"

        /*
         * Genera el instalador/ejecutable nativo de Windows
         * (jpackage por debajo).
         *
         * Apache incluye ahora también el Core dentro de sus recursos.
         * El Desktop será el encargado de iniciar el Core automáticamente
         * cuando se ejecute la aplicación.
         */
        nativeDistributions {

            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )

            packageName = "Apache"
            packageVersion = "0.1.0"
            description = "Apache — asistente local de escritorio"
            vendor = "FranHuisa"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                upgradeUuid = "5f2f2b3a-6d1e-4b6a-9b7a-2f1a6c3d9e10"
            }
        }
    }
}