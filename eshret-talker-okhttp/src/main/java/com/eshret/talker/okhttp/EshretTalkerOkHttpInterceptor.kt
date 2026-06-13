package com.eshret.talker.okhttp

import com.eshret.talker.core.EshretTalker
import com.eshret.talker.core.EshretTalkerLevel
import java.io.IOException
import java.util.Locale
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response

// Этот файл описывает OkHttp-interceptor библиотеки eshret_talker.
// Здесь мы делаем понятные HTTP-логи: запрос, ответ, время, код и безопасные подробности с настраиваемыми флагами.

class EshretTalkerOkHttpInterceptor(
    // Это экземпляр логгера, куда будут уходить HTTP-события.
    private val talker: EshretTalker,
    // Это набор настроек сетевого логгера.
    private val settings: EshretTalkerOkHttpLoggerSettings = EshretTalkerOkHttpLoggerSettings(
        hiddenHeaders = setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-access-token",
        ),
    ),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Это ранний пропуск, если сетевой логгер отключён.
        if (!talker.isEnabled || !settings.enabled) {
            return chain.proceed(chain.request())
        }

        // Это исходный запрос из цепочки OkHttp.
        val request = chain.request()
        // Это проверка, надо ли логировать конкретный запрос.
        val shouldLogRequest = settings.requestFilter?.invoke(request) ?: true
        // Это время начала выполнения запроса.
        val startedAtNanos = System.nanoTime()
        // Это короткое имя метода запроса.
        val method = request.method
        // Это полный путь и query запроса.
        val target = request.url.let { url ->
            buildString {
                append(url.encodedPath)
                if (url.encodedQuery != null) {
                    append("?")
                    append(url.encodedQuery)
                }
            }
        }

        // Это лог исходящего запроса. Предохранитель: сбой сборки деталей или самого логирования
        // (инцидент 2026-06-12: OOM на склейке лог-строки) не должен ломать сетевой вызов.
        if (shouldLogRequest && settings.logLevel.allowsRequestLogging()) {
            runCatching {
                talker.httpRequest(
                    message = "--> $method $target",
                    details = buildRequestDetails(request),
                )
            }
        }

        return try {
            // Это реальное выполнение HTTP-запроса.
            val response = chain.proceed(request)
            // Это проверка, надо ли логировать конкретный ответ.
            val shouldLogResponse = settings.responseFilter?.invoke(response) ?: true
            // Это длительность запроса в миллисекундах.
            val tookMs = (System.nanoTime() - startedAtNanos) / 1_000_000

            // Это лог входящего ответа, РАЗДЕЛЁННЫЙ по исходу:
            //  - успешный (2xx) → httpResponse (обычный лог, гейтится уровнем как запрос);
            //  - неуспешный (4xx/5xx) → error (красный, гейтится как ошибки — виден и на INFO+).
            // Предохранитель: сбой логирования не должен терять уже полученный ответ.
            if (shouldLogResponse) {
                runCatching {
                    val responseDetails = buildResponseDetails(response, tookMs)
                    val message = buildResponseMessage(response = response, method = method, target = target, tookMs = tookMs)
                    if (response.isSuccessful) {
                        if (settings.logLevel.allowsRequestLogging()) {
                            talker.httpResponse(message = message, details = responseDetails)
                        }
                    } else if (settings.logLevel.allowsErrorLogging()) {
                        talker.error(message = message, tag = "HTTP", details = responseDetails)
                    }
                }
            }
            response
        } catch (exception: IOException) {
            // Это проверка, надо ли логировать конкретную ошибку.
            val shouldLogError = settings.errorFilter?.invoke(request, exception) ?: true
            // Это длительность до сетевой ошибки.
            val tookMs = (System.nanoTime() - startedAtNanos) / 1_000_000

            // Это лог сетевого сбоя. Предохранитель: именно на этом пути случился OOM-краш
            // 2026-06-12 (сборка деталей с телом запроса в сетевом потоке) — сбой логирования
            // не должен подменять исходную сетевую ошибку.
            if (shouldLogError && settings.logLevel.allowsErrorLogging()) {
                runCatching {
                    talker.error(
                        message = "<-- HTTP FAILED $method $target (${tookMs} ms)",
                        tag = "HTTP",
                        details = buildErrorDetails(
                            request = request,
                            exception = exception,
                            tookMs = tookMs,
                        ),
                        throwable = exception,
                    )
                }
            }
            throw exception
        }
    }

    // Это сборка подробностей исходящего запроса по текущим флагам.
    private fun buildRequestDetails(request: Request): String = buildString {
        appendLine("URL: ${request.url}")
        appendLine("Method: ${request.method}")
        if (settings.printRequestExtra) {
            appendLine("Extra:")
            appendLine(request.extraDump())
        }
        if (settings.printRequestHeaders) {
            appendLine("Headers:")
            appendLine(request.headers.redactedDump(settings.hiddenHeaders))
        }
        if (settings.printRequestData) {
            request.body?.let { body ->
                readRequestBodyPreview(
                    contentType = body.contentType(),
                    body = body,
                    limit = settings.requestBodyPreviewLimit,
                )
                    ?.takeIf { it.isNotBlank() }
                    ?.let { preview ->
                        appendLine("Body:")
                        append(preview)
                    }
            }
        }
    }

    // Это сборка подробностей входящего ответа по текущим флагам.
    private fun buildResponseDetails(
        response: Response,
        tookMs: Long,
    ): String = buildString {
        appendLine("URL: ${response.request.url}")
        append("Status: ${response.code}")
        if (settings.printResponseMessage) {
            append(" ${response.message}")
        }
        appendLine()
        if (settings.printResponseTime) {
            appendLine("Duration: ${tookMs} ms")
        }
        if (settings.printResponseRedirects) {
            appendLine("Redirect: ${response.isRedirect}")
        }
        if (settings.printResponseHeaders) {
            appendLine("Headers:")
            appendLine(response.headers.redactedDump(settings.hiddenHeaders))
        }
        if (settings.printResponseData) {
            val bodyPreview = runCatching {
                settings.responseDataConverter?.invoke(response)
            }.getOrNull()
                ?: readResponseBodyPreview(
                    response = response,
                    limit = settings.responseBodyPreviewLimit,
                )
            bodyPreview
                ?.takeIf { it.isNotBlank() }
                ?.let { preview ->
                    appendLine("Body:")
                    append(preview)
                }
        }
    }

    // Это сборка подробностей по сетевой ошибке.
    private fun buildErrorDetails(
        request: Request,
        exception: IOException,
        tookMs: Long,
    ): String = buildString {
        appendLine("URL: ${request.url}")
        appendLine("Method: ${request.method}")
        appendLine("Duration: ${tookMs} ms")
        if (settings.printErrorMessage) {
            appendLine("Message: ${exception.message.orEmpty()}")
        }
        if (settings.printErrorHeaders) {
            appendLine("Headers:")
            appendLine(request.headers.redactedDump(settings.hiddenHeaders))
        }
        if (settings.printErrorData) {
            request.body?.let { body ->
                readRequestBodyPreview(
                    contentType = body.contentType(),
                    body = body,
                    limit = settings.requestBodyPreviewLimit,
                )
                    ?.takeIf { it.isNotBlank() }
                    ?.let { preview ->
                        appendLine("Body:")
                        append(preview)
                    }
            }
        }
    }

    // Это сборка короткой строки заголовка для ответа.
    private fun buildResponseMessage(
        response: Response,
        method: String,
        target: String,
        tookMs: Long,
    ): String = buildString {
        append("<-- ${response.code} $method $target")
        if (settings.printResponseTime) {
            append(" (${tookMs} ms)")
        }
        if (settings.printResponseMessage && response.message.isNotBlank()) {
            append(" ${response.message}")
        }
    }
}

// Это helper безопасного дампа headers с маскированием чувствительных значений.
private fun Headers.redactedDump(redactHeaders: Set<String>): String = buildString {
    if (size == 0) {
        append("(no headers)")
    } else {
        for (index in 0 until size) {
            val name = name(index)
            val value = if (name.lowercase(Locale.getDefault()) in redactHeaders) {
                "***"
            } else {
                value(index)
            }
            append(name)
            append(": ")
            append(value)
            if (index != size - 1) {
                appendLine()
            }
        }
    }
}

// Это helper краткой выгрузки дополнительных сведений по запросу.
private fun Request.extraDump(): String = buildString {
    appendLine("HTTPS: ${url.isHttps}")
    body?.let { body ->
        appendLine("Content-Type: ${body.contentType()}")
        append("Content-Length: ${runCatching { body.contentLength() }.getOrDefault(-1L)}")
    } ?: append("Content-Length: 0")
}

// Это helper чтения preview request body для текстовых payload.
// Лимит здесь обязателен: без обрезки мегабайтные тела раздувают журнал, а вместе с ним —
// экспорт/шаринг логов и любые sink-приёмники (инцидент: OOM на склейке журнала с телами по 1.5 МБ).
private fun readRequestBodyPreview(
    contentType: MediaType?,
    body: okhttp3.RequestBody,
    limit: Long,
): String? {
    if (!contentType.isProbablyTextual()) return "(binary body omitted)"

    // Защита от нештатных настроек: лимит ниже 1 или выше Int.MAX_VALUE ломал бы чтение/String().
    val safeLimit = limit.coerceIn(1L, Int.MAX_VALUE.toLong() - 16)
    val buffer = okio.Buffer()
    body.writeTo(buffer)
    if (buffer.size <= safeLimit) return buffer.readUtf8()
    val totalBytes = buffer.size
    val preview = buffer.readUtf8(safeLimit)
    return "$preview\n…(body truncated: shown $safeLimit of $totalBytes bytes)"
}

// Это helper чтения preview response body без разрушения основного потока (peek + лимит).
private fun readResponseBodyPreview(
    response: Response,
    limit: Long,
): String? {
    val contentType = response.body?.contentType()
    if (!contentType.isProbablyTextual()) return "(binary body omitted)"
    // Защита от нештатных настроек: лимит ниже 1 или выше Int.MAX_VALUE ломал бы чтение/String().
    val safeLimit = limit.coerceIn(1L, Int.MAX_VALUE.toLong() - 16)
    // Читаем safeLimit + 1 байт: лишний байт нужен только чтобы понять, что тело длиннее лимита.
    val peekedBytes = response.peekBody(byteCount = safeLimit + 1).bytes()
    if (peekedBytes.size <= safeLimit) return String(peekedBytes, Charsets.UTF_8)
    val preview = String(peekedBytes, 0, safeLimit.toInt(), Charsets.UTF_8)
    return "$preview\n…(body truncated: shown $safeLimit bytes)"
}

// Это helper проверки, можно ли безопасно показать body как текст.
private fun MediaType?.isProbablyTextual(): Boolean {
    val typeValue = this?.type.orEmpty()
    val subtypeValue = this?.subtype.orEmpty()
    return typeValue == "text" ||
        subtypeValue.contains("json", ignoreCase = true) ||
        subtypeValue.contains("xml", ignoreCase = true) ||
        subtypeValue.contains("html", ignoreCase = true) ||
        subtypeValue.contains("x-www-form-urlencoded", ignoreCase = true)
}

// Это helper проверки, можно ли при текущем уровне логировать обычные HTTP-события.
private fun EshretTalkerLevel.allowsRequestLogging(): Boolean = when (this) {
    EshretTalkerLevel.VERBOSE,
    EshretTalkerLevel.DEBUG -> true

    EshretTalkerLevel.INFO,
    EshretTalkerLevel.NAVIGATION,
    EshretTalkerLevel.SUCCESS,
    EshretTalkerLevel.WARNING,
    EshretTalkerLevel.ERROR,
    EshretTalkerLevel.CRITICAL,
    EshretTalkerLevel.HTTP_REQUEST,
    EshretTalkerLevel.HTTP_RESPONSE -> false
}

// Это helper проверки, можно ли при текущем уровне логировать HTTP-ошибки.
private fun EshretTalkerLevel.allowsErrorLogging(): Boolean = when (this) {
    EshretTalkerLevel.VERBOSE,
    EshretTalkerLevel.DEBUG,
    EshretTalkerLevel.INFO,
    EshretTalkerLevel.NAVIGATION,
    EshretTalkerLevel.SUCCESS,
    EshretTalkerLevel.WARNING,
    EshretTalkerLevel.ERROR,
    EshretTalkerLevel.CRITICAL -> true

    EshretTalkerLevel.HTTP_REQUEST,
    EshretTalkerLevel.HTTP_RESPONSE -> true
}
