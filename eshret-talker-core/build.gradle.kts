import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Этот файл описывает Gradle-конфигурацию core-модуля библиотеки eshret_talker.
// Здесь мы подключаем базовые Android/Kotlin зависимости для ядра логгера.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.eshret.talker.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // coroutines-core идёт через api: тип StateFlow присутствует в публичной сигнатуре
    // EshretTalker.logs, поэтому потребители core должны получать его транзитивно.
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // org.json встроен в Android (прод-зависимость не нужна), но JVM-тестам хранилища сессий
    // нужна явная реализация на classpath.
    testImplementation(libs.json)
}
