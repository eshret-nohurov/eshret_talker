package com.eshret.talker.core

// Этот файл описывает модель одной записи журнала eshret_talker.
// Здесь хранится всё, что нужно для красивого UI, фильтрации и Logcat-вывода.

data class EshretTalkerLogEntry(
    // Это уникальный id записи для стабильного списка.
    val id: Long,
    // Это время создания записи в миллисекундах.
    val timestampMillis: Long,
    // Это уровень записи.
    val level: EshretTalkerLevel,
    // Это логический тег или модуль события.
    val tag: String,
    // Это основное короткое сообщение записи.
    val message: String,
    // Это дополнительный блок подробностей.
    val details: String? = null,
    // Это краткое описание throwable, если оно есть.
    val throwableSummary: String? = null,
    // Это полный stack trace в виде строки, если он есть.
    val stackTrace: String? = null,
)

