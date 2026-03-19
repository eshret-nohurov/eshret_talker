// Этот файл описывает корневую Gradle-конфигурацию библиотеки eshret_talker.
// Здесь мы задаём общие group/version для всех модулей библиотеки.

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    group = "com.eshret.talker"
    version = "0.1.0-local"
}
