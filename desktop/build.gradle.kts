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
compose.desktop {
    application {
        mainClass = "com.apache.MainKt"
    }
}