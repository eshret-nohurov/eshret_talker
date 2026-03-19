// Этот файл описывает настройки Gradle для отдельного репозитория библиотеки eshret_talker.
// Здесь мы подключаем version catalog, репозитории и модули core, ui и okhttp.

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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "eshret_talker"

include(":eshret-talker-core")
include(":eshret-talker-ui")
include(":eshret-talker-okhttp")

