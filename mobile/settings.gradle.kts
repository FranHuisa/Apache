// Apache Móvil: app Android independiente (no necesita el PC ni el Core).
// Es un proyecto Gradle aparte: ábrelo en Android Studio con File > Open > carpeta "mobile".
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ApacheMobile"
include(":app")
