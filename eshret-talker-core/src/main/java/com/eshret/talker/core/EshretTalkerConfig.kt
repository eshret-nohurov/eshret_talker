package com.eshret.talker.core

// Этот файл описывает конфигурацию логгера eshret_talker.
// Здесь лежат лимиты буфера и флаги поведения для Logcat и форматирования.

data class EshretTalkerConfig(
    // Это максимальное число записей, которые держим в памяти.
    val maxEntries: Int = 400,
    // Это флаг вывода логов в системный Logcat.
    val logcatEnabled: Boolean = true,
    // Это базовый системный тег для Logcat.
    val logcatTag: String = "eshret_talker",
)

