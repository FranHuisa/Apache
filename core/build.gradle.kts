plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.5"
}

group = "com.apache"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Web: expone la API REST que consume la app de escritorio
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // Dependencia para controlar la reproducción multimedia en Windows (Spotify, reproductores locales, etc.)
    implementation("org.endlesssource.mediainterface:all:3.0.0")
    // Persistencia local. Exposed es un ORM ligero 100% Kotlin de JetBrains.
    // Usamos SQLite ahora (fase prototipo); migrar a MySQL más adelante
    // solo implica cambiar el driver JDBC y la URL de conexión en
    // DatabaseFactory.kt, el resto del código (tablas, queries) no cambia.
    implementation("org.jetbrains.exposed:exposed-core:0.52.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.52.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.52.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.52.0")
    implementation("com.mysql:mysql-connector-j")
    // Para cuando migréis a MySQL, añadir en su lugar:
    // implementation("mysql:mysql-connector-java:8.0.33")

    // Cliente HTTP para hablar con la API de Gemini
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // controlar el volumen maestro de Windows desde Kotlin/Java.
    implementation("com.github.bjoernpetersen:volctl:3.0.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "21"
}

tasks.withType<Test> {
    useJUnitPlatform()
}