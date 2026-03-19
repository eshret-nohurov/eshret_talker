package com.eshret.talker.okhttp

import com.eshret.talker.core.EshretTalkerLevel
import okhttp3.Request
import okhttp3.Response

// Этот файл описывает настройки сетевого логгера eshret_talker для OkHttp.
// Здесь собраны флаги управления выводом запросов, ответов и ошибок, адаптированные под Request и Response из OkHttp.

data class EshretTalkerOkHttpLoggerSettings(
    // Это общий флаг включения сетевого логгера.
    val enabled: Boolean = true,
    // Это минимальный уровень подробности для сетевых логов.
    val logLevel: EshretTalkerLevel = EshretTalkerLevel.DEBUG,
    // Это флаг показа тела ответа.
    val printResponseData: Boolean = true,
    // Это флаг показа headers ответа.
    val printResponseHeaders: Boolean = false,
    // Это флаг показа response.message.
    val printResponseMessage: Boolean = true,
    // Это флаг показа информации о redirect.
    val printResponseRedirects: Boolean = false,
    // Это флаг показа времени выполнения ответа.
    val printResponseTime: Boolean = false,
    // Это флаг показа тела ошибки.
    val printErrorData: Boolean = true,
    // Это флаг показа headers при ошибке.
    val printErrorHeaders: Boolean = true,
    // Это флаг показа текста ошибки.
    val printErrorMessage: Boolean = true,
    // Это флаг показа тела запроса.
    val printRequestData: Boolean = true,
    // Это флаг показа headers запроса.
    val printRequestHeaders: Boolean = false,
    // Это флаг показа дополнительных сведений по запросу.
    val printRequestExtra: Boolean = false,
    // Это набор headers, которые надо скрывать в логах.
    val hiddenHeaders: Set<String> = emptySet(),
    // Это конвертер тела ответа в строку для кастомного отображения.
    val responseDataConverter: ((Response) -> String?)? = null,
    // Это фильтр исходящих запросов.
    val requestFilter: ((Request) -> Boolean)? = null,
    // Это фильтр входящих ответов.
    val responseFilter: ((Response) -> Boolean)? = null,
    // Это фильтр сетевых ошибок.
    val errorFilter: ((Request, Throwable) -> Boolean)? = null,
    // Это лимит preview размера request body.
    val requestBodyPreviewLimit: Long = 2_048L,
    // Это лимит preview размера response body.
    val responseBodyPreviewLimit: Long = 2_048L,
)
