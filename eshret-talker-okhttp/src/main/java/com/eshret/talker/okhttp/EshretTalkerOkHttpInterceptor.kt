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

        // Это подробности исходящего запроса.
        val requestDetails = request.takeIf { shouldLogRequest }?.let {
            buildRequestDetails(it)
        }

        // Это лог исходящего запроса.
        if (shouldLogRequest && settings.logLevel.allowsRequestLogging()) {
            talker.httpRequest(
                message = "--> $method $target",
                details = requestDetails,
            )
        }

        return try {
            // Это реальное выполнение HTTP-запроса.
            val response = chain.proceed(request)
            // Это проверка, надо ли логировать конкретный ответ.
            val shouldLogResponse = settings.responseFilter?.invoke(response) ?: true
            // Это длительность запроса в миллисекундах.
            val tookMs = (System.nanoTime() - startedAtNanos) / 1_000_000

            // Это подробности входящего ответа.
            val responseDetails = response.takeIf { shouldLogResponse }?.let {
                buildResponseDetails(it, tookMs)
            }

            // Это лог входящего ответа.
            if (shouldLogResponse && settings.logLevel.allowsRequestLogging()) {
                talker.httpResponse(
                    message = buildResponseMessage(response = response, method = method, target = target, tookMs = tookMs),
                    details = responseDetails,
                )
            }
            response
        } catch (exception: IOException) {
            // Это проверка, надо ли логировать конкретную ошибку.
            val shouldLogError = settings.errorFilter?.invoke(request, exception) ?: true
            // Это длительность до сетевой ошибки.
            val tookMs = (System.nanoTime() - startedAtNanos) / 1_000_000

            // Это лог сетевого сбоя.
            if (shouldLogError && settings.logLevel.allowsErrorLogging()) {
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
private fun readRequestBodyPreview(
    contentType: MediaType?,
    body: okhttp3.RequestBody,
    limit: Long,
): String? {
    if (!contentType.isProbablyTextual()) return "(binary body omitted)"

    val buffer = okio.Buffer()
    body.writeTo(buffer)
    return buffer.readUtf8()
}

// Это helper чтения preview response body без разрушения основного потока.
private fun readResponseBodyPreview(
    response: Response,
    limit: Long,
): String? {
    val contentType = response.body?.contentType()
    if (!contentType.isProbablyTextual()) return "(binary body omitted)"
    return response.peekBody(byteCount = Long.MAX_VALUE).string()
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
