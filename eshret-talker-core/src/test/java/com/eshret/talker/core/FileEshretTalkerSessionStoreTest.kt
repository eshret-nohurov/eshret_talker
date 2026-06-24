package com.eshret.talker.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Этот файл проверяет файловое хранилище сессий: round-trip всех полей записи (включая
// «злой» текст с переносами и кавычками), метаданные списка сессий, ретеншн по сроку,
// удаление одной/всех сессий и то, что сохраняется ВСЁ сверх in-memory лимита логгера.
//
// Все операции list/read/delete синхронны и идут через тот же single-thread executor, что и
// append, поэтому к моменту чтения все предшествующие записи гарантированно записаны (FIFO).

class FileEshretTalkerSessionStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun store(
        clockMillis: Long = 1_700_000_000_000L,
        retentionDays: Int = 7,
    ) = FileEshretTalkerSessionStore(
        rootDirectory = tempFolder.root,
        retentionDays = retentionDays,
        clock = { clockMillis },
    )

    private fun entry(
        id: Long,
        level: EshretTalkerLevel = EshretTalkerLevel.INFO,
        tag: String = "APP",
        message: String = "msg",
        details: String? = null,
        throwableSummary: String? = null,
        stackTrace: String? = null,
    ) = EshretTalkerLogEntry(
        id = id,
        timestampMillis = 1_700_000_000_000L + id,
        level = level,
        tag = tag,
        message = message,
        details = details,
        throwableSummary = throwableSummary,
        stackTrace = stackTrace,
    )

    @Test
    fun `записи сессии round-trip без потерь, включая переносы и кавычки`() {
        val store = store()
        val tricky = entry(
            id = 7,
            level = EshretTalkerLevel.ERROR,
            tag = "HTTP",
            message = "строка1\nстрока2 \"в кавычках\" и \\обратный слеш",
            details = "url: https://x?a=1&b=2\nbody: {\"k\":\"v\"}",
            throwableSummary = "IllegalStateException: упало",
            stackTrace = "at A.kt:1\nat B.kt:2\n",
        )

        store.append(tricky)

        val read = store.readEntries(store.currentSessionId)
        // data class equality проверяет, что ВСЕ поля восстановились байт-в-байт.
        assertEquals(listOf(tricky), read)
    }

    @Test
    fun `пустые поля сохраняются как null`() {
        val store = store()

        store.append(entry(id = 1, details = null, throwableSummary = null, stackTrace = null))

        val read = store.readEntries(store.currentSessionId).single()
        assertNull(read.details)
        assertNull(read.throwableSummary)
        assertNull(read.stackTrace)
    }

    @Test
    fun `listSessions возвращает текущую сессию с числом записей`() {
        val store = store()
        repeat(3) { index -> store.append(entry(id = index.toLong())) }

        val sessions = store.listSessions()

        val current = sessions.single { it.id == store.currentSessionId }
        assertEquals(3, current.entryCount)
    }

    @Test
    fun `ретеншн удаляет сессии старше срока хранения`() {
        // Готовим «старую» сессию (2000 год) вручную в каталоге хранилища.
        val sessionsDir = File(tempFolder.root, "eshret_talker_sessions").apply { mkdirs() }
        val oldId = "2000-01-01_10-00-00-000"
        File(sessionsDir, "session_$oldId.jsonl").writeText("{\"id\":1}\n")

        // now = ноябрь 2023 — старая сессия заведомо старше 7 дней.
        val store = store(clockMillis = 1_700_000_000_000L, retentionDays = 7)

        // listSessions идёт через тот же executor после ретеншна (FIFO) — он уже отработал.
        val ids = store.listSessions().map { it.id }
        assertFalse(ids.contains(oldId))
        // Текущая сессия жива.
        assertTrue(ids.contains(store.currentSessionId))
    }

    @Test
    fun `deleteSession удаляет конкретную прошлую сессию`() {
        // Две сессии в одном каталоге: разные clock → разные id.
        val past = store(clockMillis = 1_700_000_000_000L)
        past.append(entry(id = 1))
        val current = store(clockMillis = 1_700_000_500_000L)

        assertTrue(current.listSessions().map { it.id }.contains(past.currentSessionId))

        current.deleteSession(past.currentSessionId)

        assertFalse(current.listSessions().map { it.id }.contains(past.currentSessionId))
    }

    @Test
    fun `deleteAllSessions удаляет прошлые и очищает текущую`() {
        val past = store(clockMillis = 1_700_000_000_000L)
        past.append(entry(id = 1))
        val current = store(clockMillis = 1_700_000_500_000L)
        current.append(entry(id = 2))
        current.append(entry(id = 3))

        current.deleteAllSessions()

        val sessions = current.listSessions()
        // Прошлой сессии нет.
        assertFalse(sessions.map { it.id }.contains(past.currentSessionId))
        // Текущая осталась, но пуста.
        val cur = sessions.single { it.id == current.currentSessionId }
        assertEquals(0, cur.entryCount)
        assertTrue(current.readEntries(current.currentSessionId).isEmpty())
    }

    @Test
    fun `удаление текущей сессии очищает её, но запись продолжается`() {
        val store = store()
        store.append(entry(id = 1))
        store.append(entry(id = 2))

        store.deleteSession(store.currentSessionId)
        assertTrue(store.readEntries(store.currentSessionId).isEmpty())

        store.append(entry(id = 3))
        val read = store.readEntries(store.currentSessionId)
        assertEquals(listOf(3L), read.map { it.id })
    }

    @Test
    fun `EshretTalker сохраняет в сессию все записи сверх maxEntries`() {
        val sessionStore = store()
        val talker = EshretTalker(
            config = EshretTalkerConfig(maxEntries = 2, logcatEnabled = false),
            sessionStore = sessionStore,
        )

        repeat(5) { index -> talker.info(message = "msg-$index") }

        // In-memory буфер ограничен maxEntries.
        assertEquals(2, talker.logs.value.size)
        // А на диск ушли все пять записей сессии.
        val persisted = sessionStore.readEntries(sessionStore.currentSessionId)
        assertEquals(5, persisted.size)
        assertEquals(
            listOf("msg-0", "msg-1", "msg-2", "msg-3", "msg-4"),
            persisted.map { it.message },
        )
    }

    @Test
    fun `два стора с одинаковым временем старта не смешивают логи`() {
        val a = store(clockMillis = 1_700_000_000_000L)
        a.append(entry(id = 1, message = "from-a"))
        // Тот же миллисекунд старта — pickFreeId обязан дать другой файл, иначе append подмешает.
        val b = store(clockMillis = 1_700_000_000_000L)
        b.append(entry(id = 1, message = "from-b"))

        assertNotEquals(a.currentSessionId, b.currentSessionId)
        assertEquals(listOf("from-a"), a.readEntries(a.currentSessionId).map { it.message })
        assertEquals(listOf("from-b"), b.readEntries(b.currentSessionId).map { it.message })
    }

    @Test
    fun `ретеншн чистит файлы сессий с битым именем`() {
        val sessionsDir = File(tempFolder.root, "eshret_talker_sessions").apply { mkdirs() }
        // Пустой id → idFromName == null: старый код такой файл не удалял бы никогда.
        val broken = File(sessionsDir, "session_.jsonl")
        broken.writeText("{}\n")
        broken.setLastModified(1_000_000_000_000L)

        val store = store(clockMillis = 1_700_000_000_000L, retentionDays = 7)
        store.listSessions() // форсит ретеншн (FIFO на том же executor)

        assertFalse(broken.exists())
    }

    @Test
    fun `выключенный логгер ничего не пишет в сессию`() {
        val sessionStore = store()
        val talker = EshretTalker(
            config = EshretTalkerConfig(enabled = false, logcatEnabled = false),
            sessionStore = sessionStore,
        )

        talker.error(message = "boom")

        assertTrue(sessionStore.readEntries(sessionStore.currentSessionId).isEmpty())
    }
}
