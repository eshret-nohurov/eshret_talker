package com.eshret.talker.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.eshret.talker.core.EshretTalkerLogEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Это общий формат времени для экспортируемого журнала.
private val exportLogTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

// Это формат даты и времени для имени экспортируемого файла.
private val exportFileNameFormatter = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

// Это сборка полного текстового представления всех логов для копирования и шаринга.
internal fun List<EshretTalkerLogEntry>.toShareText(): String = buildString {
    this@toShareText.forEachIndexed { index, entry ->
        append(entry.toShareBlock())
        if (index != this@toShareText.lastIndex) {
            appendLine()
            appendLine()
        }
    }
}

// Это создание и запуск системного share-intent с текстовым файлом логов.
internal fun shareLogsAsFile(
    context: Context,
    logsText: String,
) {
    val exportDirectory = File(context.cacheDir, "eshret_talker_exports").apply {
        mkdirs()
    }
    val exportFile = File(exportDirectory, buildExportFileName())
    exportFile.writeText(logsText)

    val authority = "${context.packageName}.eshret_talker.fileprovider"
    val fileUri = FileProvider.getUriForFile(context, authority, exportFile)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Логи eshret_talker")
        putExtra(Intent.EXTRA_STREAM, fileUri)
        clipData = ClipData.newRawUri("Логи eshret_talker", fileUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooserIntent = Intent.createChooser(shareIntent, "Поделиться лог-файлом").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooserIntent)
}

// Это построение текста одной записи журнала для экспорта.
private fun EshretTalkerLogEntry.toShareBlock(): String = buildString {
    appendLine("id: $id")
    appendLine("time: ${exportLogTimeFormatter.format(Date(timestampMillis))}")
    appendLine("level: ${level.title}")
    if (tag.isNotBlank()) {
        appendLine("tag: $tag")
    }
    appendLine("message: $message")
    details
        ?.takeIf { it.isNotBlank() }
        ?.let {
            appendLine()
            appendLine("details:")
            appendLine(it)
        }
    throwableSummary
        ?.takeIf { it.isNotBlank() }
        ?.let {
            appendLine()
            appendLine("throwable:")
            appendLine(it)
        }
    stackTrace
        ?.takeIf { it.isNotBlank() }
        ?.let {
            appendLine()
            appendLine("stackTrace:")
            append(it)
        }
}

// Это генерация имени лог-файла в формате eshret_talker_дата_время_миллисекунды.txt.
private fun buildExportFileName(nowMillis: Long = System.currentTimeMillis()): String =
    "eshret_talker_${exportFileNameFormatter.format(Date(nowMillis))}.txt"
