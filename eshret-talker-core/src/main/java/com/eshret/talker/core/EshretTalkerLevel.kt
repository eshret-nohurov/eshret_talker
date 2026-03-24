package com.eshret.talker.core

// Этот файл описывает уровни логов библиотеки eshret_talker.
// Здесь мы задаём человекочитаемые названия и тематические смайлики для каждого типа события.

enum class EshretTalkerLevel(
    // Это короткое название уровня для UI и Logcat.
    val title: String,
    // Это смайлик уровня для быстрого визуального распознавания.
    val emoji: String,
) {
    // Это подробный технический уровень.
    VERBOSE(title = "VERBOSE", emoji = "🫧"),

    // Это уровень отладочной информации.
    DEBUG(title = "DEBUG", emoji = "🛠️"),

    // Это обычное информационное сообщение.
    INFO(title = "INFO", emoji = "ℹ️"),

    // Это событие навигации по экранам и маршрутам.
    NAVIGATION(title = "NAVIGATION", emoji = "🧭"),

    // Это успешное завершение действия.
    SUCCESS(title = "SUCCESS", emoji = "✅"),

    // Это предупреждение без критического падения.
    WARNING(title = "WARNING", emoji = "⚠️"),

    // Это ошибка выполнения.
    ERROR(title = "ERROR", emoji = "❌"),

    // Это критическая ошибка.
    CRITICAL(title = "CRITICAL", emoji = "🚨"),

    // Это исходящий HTTP-запрос.
    HTTP_REQUEST(title = "HTTP OUT", emoji = "📤"),

    // Это входящий HTTP-ответ.
    HTTP_RESPONSE(title = "HTTP IN", emoji = "📥"),
}
