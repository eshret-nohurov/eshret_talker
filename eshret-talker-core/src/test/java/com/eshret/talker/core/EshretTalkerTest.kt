package com.eshret.talker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Этот файл проверяет ядро логгера eshret_talker: запись, буфер, часы и главный инвариант —
// сбой логирования (в том числе в sink) не должен ронять вызывающего.
// Logcat в тестах отключён (logcatEnabled = false), чтобы не дёргать android.util.Log.

class EshretTalkerTest {
    // Это базовая конфигурация без Logcat для чистых JVM-тестов.
    private fun config(
        enabled: Boolean = true,
        maxEntries: Int = 400,
    ) = EshretTalkerConfig(
        enabled = enabled,
        maxEntries = maxEntries,
        logcatEnabled = false,
    )

    // Это собирающий sink для проверки веерной рассылки.
    private class RecordingSink : EshretTalkerSink {
        val entries = mutableListOf<EshretTalkerLogEntry>()
        override fun log(entry: EshretTalkerLogEntry) {
            entries += entry
        }
    }

    @Test
    fun `log сохраняет запись с переданными полями`() {
        val talker = EshretTalker(config = config(), clock = { 1_000L })

        talker.info(message = "Открыли Home", tag = "HOME", details = "details")

        val logs = talker.logs.value
        assertEquals(1, logs.size)
        val entry = logs.first()
        assertEquals(EshretTalkerLevel.INFO, entry.level)
        assertEquals("Открыли Home", entry.message)
        assertEquals("HOME", entry.tag)
        assertEquals("details", entry.details)
        assertEquals(1_000L, entry.timestampMillis)
    }

    @Test
    fun `выключенный логгер ничего не пишет`() {
        val talker = EshretTalker(config = config(enabled = false))

        talker.error(message = "boom")

        assertFalse(talker.isEnabled)
        assertTrue(talker.logs.value.isEmpty())
    }

    @Test
    fun `буфер ограничен maxEntries и вытесняет самые старые`() {
        val talker = EshretTalker(config = config(maxEntries = 3))

        repeat(5) { index -> talker.info(message = "msg-$index") }

        val logs = talker.logs.value
        assertEquals(3, logs.size)
        // Остаются последние три сообщения по порядку.
        assertEquals(listOf("msg-2", "msg-3", "msg-4"), logs.map { it.message })
        // id монотонно растут и не переиспользуются после вытеснения.
        assertEquals(listOf(3L, 4L, 5L), logs.map { it.id })
    }

    @Test
    fun `maxEntries=1 держит только последнюю запись`() {
        val talker = EshretTalker(config = config(maxEntries = 1))

        talker.info(message = "first")
        talker.info(message = "second")

        assertEquals(listOf("second"), talker.logs.value.map { it.message })
    }

    @Test
    fun `maxEntries=0 отключает буфер, но sink всё равно получают записи`() {
        val recording = RecordingSink()
        val talker = EshretTalker(
            config = config(maxEntries = 0),
            extraSinks = listOf(recording),
        )

        repeat(3) { index -> talker.info(message = "msg-$index") }

        // Буфер в памяти пуст (как старый takeLast(0)), но рассылка по sink не теряется.
        assertTrue(talker.logs.value.isEmpty())
        assertEquals(3, recording.entries.size)
        assertEquals(listOf("msg-0", "msg-1", "msg-2"), recording.entries.map { it.message })
    }

    @Test
    fun `падающий sink не ломает другие sink и не пробрасывает исключение вызывающему`() {
        val recording = RecordingSink()
        val throwing = EshretTalkerSink { error("sink упал") }
        val talker = EshretTalker(
            config = config(),
            extraSinks = listOf(throwing, recording),
        )

        // Вызов не должен бросить, несмотря на падающий sink.
        talker.info(message = "msg")

        // Исправный sink получил запись.
        assertEquals(1, recording.entries.size)
        assertEquals("msg", recording.entries.first().message)
        // Буфер в памяти тоже не пострадал.
        assertEquals(1, talker.logs.value.size)
    }

    @Test
    fun `clear очищает буфер`() {
        val talker = EshretTalker(config = config())
        talker.info(message = "msg")
        assertEquals(1, talker.logs.value.size)

        talker.clear()

        assertTrue(talker.logs.value.isEmpty())
    }

    @Test
    fun `error фиксирует summary и stack trace из throwable`() {
        val talker = EshretTalker(config = config())
        val throwable = IllegalStateException("нет сети")

        talker.error(message = "Ошибка", throwable = throwable)

        val entry = talker.logs.value.single()
        assertEquals(EshretTalkerLevel.ERROR, entry.level)
        assertNotNull(entry.throwableSummary)
        assertTrue(entry.throwableSummary!!.contains("IllegalStateException"))
        assertTrue(entry.throwableSummary!!.contains("нет сети"))
        assertNotNull(entry.stackTrace)
        assertTrue(entry.stackTrace!!.contains("IllegalStateException"))
    }

    @Test
    fun `лог без throwable не содержит summary и stack trace`() {
        val talker = EshretTalker(config = config())

        talker.warning(message = "просто предупреждение")

        val entry = talker.logs.value.single()
        assertNull(entry.throwableSummary)
        assertNull(entry.stackTrace)
    }

    @Test
    fun `navigation использует уровень и тег NAVIGATION`() {
        val talker = EshretTalker(config = config())

        talker.navigation(message = "Home -> Details")

        val entry = talker.logs.value.single()
        assertEquals(EshretTalkerLevel.NAVIGATION, entry.level)
        assertEquals("NAVIGATION", entry.tag)
    }

    @Test
    fun `handle логирует ошибку с сообщением throwable в details`() {
        val talker = EshretTalker(config = config())
        val throwable = RuntimeException("упало обновление")

        talker.handle(throwable = throwable, message = "Ошибка обновления", tag = "HOME")

        val entry = talker.logs.value.single()
        assertEquals(EshretTalkerLevel.ERROR, entry.level)
        assertEquals("HOME", entry.tag)
        assertEquals("упало обновление", entry.details)
    }
}
