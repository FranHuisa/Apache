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

    // Persistencia. Exposed es un ORM ligero 100% Kotlin de JetBrains.
    // Apache 0.1 utiliza MySQL como base de datos principal.
    implementation("org.jetbrains.exposed:exposed-core:0.52.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.52.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.52.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.52.0")

    // Driver JDBC de MySQL
    implementation("com.mysql:mysql-connector-j")

    // Cliente HTTP para hablar con la API de Gemini
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Controlar el volumen maestro de Windows desde Kotlin/Java.
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