package com.eshret.talker.okhttp

import com.eshret.talker.core.EshretTalker
import java.io.IOException
import java.util.Locale
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response

// Этот файл описывает OkHttp-interceptor библиотеки eshret_talker.
// Здесь мы делаем понятные HTTP-логи в стиле talker_dio_logger: запрос, ответ, время, код и безопасные подробности.

class EshretTalkerOkHttpInterceptor(
    // Это экземпляр логгера, куда будут уходить HTTP-события.
    private val talker: EshretTalker,
    // Это лимит размера preview body для удобного чтения.
    private val bodyPreviewLimit: Long = 2_048L,
    // Это список чувствительных header, которые надо маскировать.
    private val redactHeaders: Set<String> = setOf(
        "authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "x-access-token",
    ),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Это исходный запрос из цепочки OkHttp.
        val request = chain.request()
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
        val requestDetails = buildString {
            appendLine("URL: ${request.url}")
            appendLine("Method: $method")
            appendLine("Headers:")
            appendLine(request.headers.redactedDump(redactHeaders))
            request.body?.let { body ->
                readRequestBodyPreview(
                    contentType = body.contentType(),
                    body = body,
                    limit = bodyPreviewLimit,
                )
                    ?.takeIf { it.isNotBlank() }
                    ?.let { preview ->
                        appendLine("Body:")
                        append(preview)
                    }
            }
        }

        // Это лог исходящего запроса.
        talker.httpRequest(
            message = "--> $method $target",
            details = requestDetails,
        )

        return try {
            // Это реальное выполнение HTTP-запроса.
            val response = chain.proceed(request)
            // Это длительность запроса в миллисекундах.
            val tookMs = (System.nanoTime() - startedAtNanos) / 1_000_000

            // Это подробности входящего ответа.
            val responseDetails = buildString {
                appendLine("URL: ${response.request.url}")
                appendLine("Status: ${response.code} ${response.message}")
                appendLine("Duration: ${tookMs} ms")
                appendLine("Headers:")
                appendLine(response.headers.redactedDump(redactHeaders))
                readResponseBodyPreview(
                    response = response,
                    limit = bodyPreviewLimit,
                )
                    ?.takeIf { it.isNotBlank() }
                    ?.let { preview ->
                        appendLine("Body:")
                        append(preview)
                    }
            }

            // Это лог входящего ответа.
            talker.httpResponse(
                message = "<-- ${response.code} $method $target (${tookMs} ms)",
                details = responseDetails,
            )
            response
        } catch (exception: IOException) {
            // Это длительность до сетевой ошибки.
            val tookMs = (System.nanoTime() - startedAtNanos) / 1_000_000

            // Это лог сетевого сбоя.
            talker.error(
                message = "<-- HTTP FAILED $method $target (${tookMs} ms)",
                tag = "HTTP",
                details = exception.message,
                throwable = exception,
            )
            throw exception
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

// Это helper чтения preview request body для текстовых payload.
private fun readRequestBodyPreview(
    contentType: MediaType?,
    body: okhttp3.RequestBody,
    limit: Long,
): String? {
    if (!contentType.isProbablyTextual()) return "(binary body omitted)"

    val buffer = okio.Buffer()
    body.writeTo(buffer)
    return buffer.readUtf8().take(limit.toInt())
}

// Это helper чтения preview response body без разрушения основного потока.
private fun readResponseBodyPreview(
    response: Response,
    limit: Long,
): String? {
    val contentType = response.body?.contentType()
    if (!contentType.isProbablyTextual()) return "(binary body omitted)"
    return response.peekBody(byteCount = limit).string()
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
