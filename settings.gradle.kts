pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        // Android Gradle Plugin
        id("com.android.application") version "7.4.2" apply false
        // Kotlin Android
        id("org.jetbrains.kotlin.android") version "1.8.21" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyAsapNewversion"
include(":app")