package com.eshret.talker.core

// Этот файл описывает контракт постоянного хранилища сессий eshret_talker.
// В отличие от in-memory буфера логгера (ограниченного maxEntries), хранилище удерживает
// ВСЕ записи сессии на диске — хоть десять тысяч — и переживает перезапуск приложения.

interface EshretTalkerSessionStore {
    // Это id текущей (активной) сессии, в которую идёт запись прямо сейчас.
    val currentSessionId: String

    // Это добавление одной записи в текущую сессию. Вызывается на каждый лог и НЕ должен
    // блокировать вызывающего и тем более его ронять — реализация пишет в фоне и глушит сбои.
    fun append(entry: EshretTalkerLogEntry)

    // Это список метаданных всех сохранённых сессий, новейшие — первыми.
    fun listSessions(): List<EshretTalkerSession>

    // Это чтение всех записей конкретной сессии по её id (для экрана логов сессии).
    fun readEntries(sessionId: String): List<EshretTalkerLogEntry>

    // Это удаление одной сессии. Если удаляют текущую — она очищается, но остаётся активной.
    fun deleteSession(sessionId: String)

    // Это удаление всех сессий (текущая очищается, но остаётся активной).
    fun deleteAllSessions()
}
