package com.eshret.talker.core

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Этот файл описывает Logcat-sink библиотеки eshret_talker.
// Здесь мы выводим логи в читаемом многострочном формате, чтобы в консоли было легче ориентироваться.

class LogcatEshretTalkerSink(
    // Это базовый системный тег для Logcat.
    private val baseTag: String,
) : EshretTalkerSink {
    // Это формат времени для каждой записи в консоли.
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun log(entry: EshretTalkerLogEntry) {
        // Это итоговый системный тег с логическим модулем записи.
        val tag = buildString {
            append(baseTag)
            if (entry.tag.isNotBlank()) {
                append(".")
                append(entry.tag)
            }
        }

        // Это красивый многострочный блок для Logcat.
        val message = buildString {
            appendLine("┌────────────────────────────────────────")
            appendLine("${entry.level.emoji} ${entry.level.title}  ${timeFormatter.format(Date(entry.timestampMillis))}")
            if (entry.tag.isNotBlank()) {
                appendLine("🏷️  ${entry.tag}")
            }
            appendLine(entry.message)
            entry.details
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    appendLine("• • •")
                    appendLine(it)
                }
            entry.throwableSummary
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    appendLine("• • •")
                    appendLine(it)
                }
            entry.stackTrace
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    appendLine("• • •")
                    appendLine(it)
                }
            append("└────────────────────────────────────────")
        }

        // Это выбор системного уровня Logcat.
        when (entry.level) {
            EshretTalkerLevel.VERBOSE -> Log.v(tag, message)
            EshretTalkerLevel.DEBUG -> Log.d(tag, message)
            EshretTalkerLevel.INFO -> Log.i(tag, message)
            EshretTalkerLevel.SUCCESS -> Log.i(tag, message)
            EshretTalkerLevel.WARNING -> Log.w(tag, message)
            EshretTalkerLevel.ERROR -> Log.e(tag, message)
            EshretTalkerLevel.CRITICAL -> Log.e(tag, message)
            EshretTalkerLevel.HTTP_REQUEST -> Log.d(tag, message)
            EshretTalkerLevel.HTTP_RESPONSE -> Log.d(tag, message)
        }
    }
}

