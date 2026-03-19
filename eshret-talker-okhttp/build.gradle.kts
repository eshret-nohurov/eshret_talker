// Этот файл описывает Gradle-конфигурацию okhttp-модуля библиотеки eshret_talker.
// Здесь мы подключаем OkHttp и core-модуль для красивого логирования HTTP-запросов и ответов.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.eshret.talker.okhttp"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":eshret-talker-core"))
    implementation(libs.okhttp)
}

