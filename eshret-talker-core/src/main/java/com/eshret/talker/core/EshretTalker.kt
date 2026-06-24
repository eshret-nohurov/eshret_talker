package com.eshret.talker.core

import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Этот файл описывает основной логгер библиотеки eshret_talker.
// Здесь собрана вся публичная API логгера, буфер в памяти и отправка записей во внешние sink-приёмники.

class EshretTalker(
    // Это конфигурация поведения логгера.
    private val config: EshretTalkerConfig = EshretTalkerConfig(),
    // Это список внешних sink-приёмников логов.
    extraSinks: List<EshretTalkerSink> = emptyList(),
    // Это источник текущего времени. Вынесен в зависимость, чтобы записи журнала были
    // детерминированными в тестах; в проде используется системное время по умолчанию.
    private val clock: () -> Long = { System.currentTimeMillis() },
    // Это постоянное хранилище сессий (опционально). Если задано — все логи текущего запуска
    // сохраняются на диск целиком, без ограничения maxEntries, и переживают перезапуск.
    private val sessionStore: EshretTalkerSessionStore? = null,
) {
    // Это публичный доступ к хранилищу сессий для UI (список/чтение/удаление сессий).
    val sessions: EshretTalkerSessionStore? get() = sessionStore
    // Это счётчик id для стабильной идентификации записей.
    private val nextId = AtomicLong(1L)

    // Это внутренний буфер логов для UI.
    private val _logs = MutableStateFlow<List<EshretTalkerLogEntry>>(emptyList())

    // Это публичный поток логов для экрана журнала.
    val logs: StateFlow<List<EshretTalkerLogEntry>> = _logs.asStateFlow()

    // Это публичный флаг, включён ли логгер сейчас.
    val isEnabled: Boolean get() = config.enabled

    // Это публичный флаг, включён ли вывод в Logcat.
    val isLogcatEnabled: Boolean get() = config.enabled && config.logcatEnabled

    // Это полный набор sink-приёмников, включая системный Logcat.
    private val sinks: List<EshretTalkerSink> = buildList {
        if (config.enabled && config.logcatEnabled) {
            add(LogcatEshretTalkerSink(baseTag = config.logcatTag))
        }
        addAll(extraSinks)
    }

    // Это очистка буфера логов в памяти.
    fun clear() {
        _logs.value = emptyList()
    }

    // Это общий метод записи произвольного события.
    fun log(
        level: EshretTalkerLevel,
        message: String,
        tag: String = "APP",
        details: String? = null,
        throwable: Throwable? = null,
    ) {
        // Это ранний выход, если логгер глобально отключён.
        if (!config.enabled) return

        // Предохранитель: сбой ЛОГИРОВАНИЯ никогда не должен валить приложение (инцидент
        // 2026-06-12: OOM при сборке лог-строки уронил процесс из сетевого потока). Ловим
        // Throwable, включая OutOfMemoryError, — здесь это осознанно: лучше потерять одну
        // запись журнала, чем процесс. Сбой не логируем сами — логгер о сбое логгера
        // сообщить безопасно не может (это и была петля).
        val entry = runCatching {
            // Это подготовка stack trace в строку.
            val stackTrace = throwable?.toStackTraceString()
            // Это краткая строка ошибки для UI и консоли.
            val throwableSummary = throwable?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}".trim() }

            // Это итоговая запись журнала.
            EshretTalkerLogEntry(
                id = nextId.getAndIncrement(),
                timestampMillis = clock(),
                level = level,
                tag = tag,
                message = message,
                details = details,
                throwableSummary = throwableSummary,
                stackTrace = stackTrace,
            )
        }.getOrElse { return }

        // Это обновление in-memory буфера с ограничением длины.
        runCatching {
            _logs.update { current ->
                appendCapped(current = current, entry = entry, maxEntries = config.maxEntries)
            }
        }

        // Это отправка записи во все внешние sink-приёмники. Каждый sink изолирован:
        // упавший sink (Logcat/телеметрия/любой кастомный) не мешает остальным и не роняет вызывающего.
        sinks.forEach { sink ->
            runCatching { sink.log(entry) }
        }

        // Это сохранение записи в постоянную сессию (если хранилище подключено). В отличие от
        // in-memory буфера выше, сюда уходят ВСЕ записи сессии, без ограничения maxEntries.
        sessionStore?.let { store -> runCatching { store.append(entry) } }
    }

    // Это подробный технический лог.
    fun verbose(message: String, tag: String = "APP", details: String? = null) {
        log(level = EshretTalkerLevel.VERBOSE, message = message, tag = tag, details = details)
    }

    // Это отладочный лог.
    fun debug(message: String, tag: String = "APP", details: String? = null) {
        log(level = EshretTalkerLevel.DEBUG, message = message, tag = tag, details = details)
    }

    // Это информационный лог.
    fun info(message: String, tag: String = "APP", details: String? = null) {
        log(level = EshretTalkerLevel.INFO, message = message, tag = tag, details = details)
    }

    // Это лог навигации между экранами и маршрутами.
    fun navigation(message: String, details: String? = null) {
        log(level = EshretTalkerLevel.NAVIGATION, message = message, tag = "NAVIGATION", details = details)
    }

    // Это лог успешного действия.
    fun success(message: String, tag: String = "APP", details: String? = null) {
        log(level = EshretTalkerLevel.SUCCESS, message = message, tag = tag, details = details)
    }

    // Это предупреждающий лог.
    fun warning(message: String, tag: String = "APP", details: String? = null) {
        log(level = EshretTalkerLevel.WARNING, message = message, tag = tag, details = details)
    }

    // Это лог ошибки.
    fun error(
        message: String,
        tag: String = "APP",
        details: String? = null,
        throwable: Throwable? = null,
    ) {
        log(
            level = EshretTalkerLevel.ERROR,
            message = message,
            tag = tag,
            details = details,
            throwable = throwable,
        )
    }

    // Это лог критической ошибки.
    fun critical(
        message: String,
        tag: String = "APP",
        details: String? = null,
        throwable: Throwable? = null,
    ) {
        log(
            level = EshretTalkerLevel.CRITICAL,
            message = message,
            tag = tag,
            details = details,
            throwable = throwable,
        )
    }

    // Это лог исходящего HTTP-запроса.
    fun httpRequest(message: String, details: String? = null) {
        log(level = EshretTalkerLevel.HTTP_REQUEST, message = message, tag = "HTTP", details = details)
    }

    // Это лог входящего HTTP-ответа.
    fun httpResponse(message: String, details: String? = null) {
        log(level = EshretTalkerLevel.HTTP_RESPONSE, message = message, tag = "HTTP", details = details)
    }

    // Это helper для краткой обработки исключения через единый вызов.
    fun handle(
        throwable: Throwable,
        message: String,
        tag: String = "APP",
    ) {
        error(
            message = message,
            tag = tag,
            details = throwable.message,
            throwable = throwable,
        )
    }
}

// Это добавление записи в буфер с ограничением длины за одну аллокацию.
// Старый вариант `(current + entry).takeLast(maxEntries)` аллоцировал список ДВАЖДЫ на каждый
// лог (сначала склейка, потом takeLast) — на горячем пути (частые HTTP/verbose-логи) это лишний
// мусор и нагрузка на GC. Здесь при заполненном буфере собираем результат одним проходом.
private fun appendCapped(
    current: List<EshretTalkerLogEntry>,
    entry: EshretTalkerLogEntry,
    maxEntries: Int,
): List<EshretTalkerLogEntry> {
    // Это вырожденный случай: буфер отключён, держим только sink-вывод.
    if (maxEntries <= 0) return emptyList()
    // Это быстрый путь, пока буфер не заполнен: одна аллокация на склейку.
    if (current.size < maxEntries) return current + entry
    // Это вытеснение самых старых записей: оставляем последние (maxEntries - 1) и добавляем новую.
    val result = ArrayList<EshretTalkerLogEntry>(maxEntries)
    result.addAll(current.subList(current.size - (maxEntries - 1), current.size))
    result.add(entry)
    return result
}

// Это helper преобразования stack trace в строку.
private fun Throwable.toStackTraceString(): String {
    // Это writer для сборки полного stack trace.
    val writer = StringWriter()
    // Это printer для записи stack trace в строковый буфер.
    val printer = PrintWriter(writer)
    printStackTrace(printer)
    printer.flush()
    return writer.toString()
}
