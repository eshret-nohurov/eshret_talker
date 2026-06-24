package com.eshret.talker.core

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.json.JSONObject

// Этот файл описывает файловую реализацию хранилища сессий eshret_talker.
// Каждая сессия — один файл формата JSON Lines (одна запись = одна строка JSON) в каталоге
// <rootDirectory>/eshret_talker_sessions. Имя файла кодирует время старта сессии.
//
// Важно по дизайну:
//  - Запись идёт в ФОНОВОМ single-thread executor: лог не должен блокировать вызывающего
//    и тем более его ронять (вся работа с диском обёрнута в runCatching).
//  - Все файловые операции сериализованы одним потоком executor'а — никаких гонок и блокировок.
//  - org.json встроен в Android (нулевая прод-зависимость); newline/кавычки в сообщениях и
//    stack trace он экранирует сам, поэтому одна запись всегда укладывается в одну строку.
//  - Хранилище удерживает ВСЕ записи сессии (лимит maxEntries из EshretTalkerConfig сюда не
//    относится — это только про in-memory буфер UI).

class FileEshretTalkerSessionStore(
    // Это базовый каталог приложения для хранения (обычно context.filesDir).
    rootDirectory: File,
    // Это срок хранения сессий в днях; всё старше удаляется при старте.
    private val retentionDays: Int = 7,
    // Это источник времени; вынесен в зависимость ради детерминированных тестов.
    private val clock: () -> Long = { System.currentTimeMillis() },
) : EshretTalkerSessionStore {
    // Это рабочий каталог хранилища сессий. mkdirs здесь (а не в init) — чтобы pickFreeId ниже
    // уже видел реальный каталог и существующие файлы прошлых запусков.
    private val directory = File(rootDirectory, "eshret_talker_sessions").apply {
        runCatching { mkdirs() }
    }

    // Это единственный фоновый поток для всех операций хранилища.
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "eshret-talker-sessions").apply { isDaemon = true }
    }

    // Это id и файл текущей (активной) сессии — фиксируются один раз при создании хранилища.
    // pickFreeId гарантирует уникальное имя файла: при совпадении времени старта до миллисекунды
    // (перезапуск/второй стор в тот же момент) добавляется суффикс, иначе append-режим подмешал бы
    // записи нового запуска в файл старого и сломал бы уникальность id записей.
    private val currentId = pickFreeId(formatId(clock()))
    private val currentFile = fileFor(currentId)

    // Это кэш числа записей по сессиям, чтобы listSessions не перечитывал каждый файл целиком.
    // Доступ только с потока executor'а (через submit/clearCurrent/delete) — синхронизация не нужна.
    private val countCache = HashMap<String, CountSnapshot>()

    // Это открытый на дозапись writer текущей сессии. Меняется только на потоке executor'а
    // (плюс инициализация ниже), @Volatile — ради видимости между потоками.
    @Volatile
    private var writer: BufferedWriter? = null

    override val currentSessionId: String get() = currentId

    init {
        writer = openWriter(currentFile)
        // Ретеншн в фоне, чтобы не задерживать старт приложения. Текущую сессию он не тронет
        // (она только что началась), удалит лишь сессии старше retentionDays.
        execute { runRetention() }
    }

    override fun append(entry: EshretTalkerLogEntry) {
        // Сериализацию и запись делаем в фоне: на вызывающем потоке — ничего тяжёлого.
        execute {
            // Если writer не открылся при старте (нет места/прав) — пробуем переоткрыть его сейчас,
            // чтобы единичный сбой инициализации не «съел» всю сессию навсегда.
            val target = writer ?: openWriter(currentFile).also { writer = it } ?: return@execute
            val line = entry.toJsonLine() ?: return@execute
            target.write(line)
            target.write("\n")
            target.flush()
        }
    }

    override fun listSessions(): List<EshretTalkerSession> = submit {
        sessionFiles()
            .mapNotNull { file -> file.toSessionMetadata() }
            .sortedByDescending { it.startedAtMillis }
    }.orEmpty()

    override fun readEntries(sessionId: String): List<EshretTalkerLogEntry> = submit {
        val file = fileFor(sessionId)
        if (!file.exists()) {
            emptyList()
        } else {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { parseJsonLine(it) }.toList()
            }
        }
    }.orEmpty()

    override fun deleteSession(sessionId: String) {
        submit {
            if (sessionId == currentId) {
                clearCurrent()
            } else {
                fileFor(sessionId).delete()
                countCache.remove(sessionId)
            }
        }
    }

    override fun deleteAllSessions() {
        submit {
            sessionFiles().forEach { file ->
                if (file.idFromName() != currentId) {
                    file.delete()
                }
            }
            countCache.clear()
            clearCurrent()
        }
    }

    // Это очистка текущей сессии: закрываем writer, удаляем файл и открываем ПУСТОЙ заново
    // (append = false — даже если delete() не сработал, файл усечётся, и старые записи не всплывут),
    // чтобы запись продолжалась в ту же активную сессию (id не меняется).
    private fun clearCurrent() {
        runCatching { writer?.close() }
        currentFile.delete()
        countCache.remove(currentId)
        writer = openWriter(currentFile, append = false)
    }

    // Это удаление сессий старше срока хранения. Сканируем по префиксу имени (а не по успешно
    // разобранному id), чтобы чистить и «битые» файлы сессий: startedAt берём из имени, при сбое
    // разбора — из времени модификации, иначе такие файлы жили бы вечно.
    private fun runRetention() {
        val cutoff = clock() - retentionDays.toLong() * 24L * 60L * 60L * 1000L
        val files = directory.listFiles { file -> file.isFile && file.name.startsWith(FILE_PREFIX) }
            ?: return
        files.forEach { file ->
            if (file.name == currentFile.name) return@forEach
            val startedAt = file.idFromName()?.let { parseStartedAt(it) } ?: file.lastModified()
            if (startedAt < cutoff) {
                file.delete()
                file.idFromName()?.let { countCache.remove(it) }
            }
        }
    }

    // Это сборка метаданных сессии из файла.
    private fun File.toSessionMetadata(): EshretTalkerSession? {
        val id = idFromName() ?: return null
        val startedAt = parseStartedAt(id) ?: lastModified()
        return EshretTalkerSession(
            id = id,
            startedAtMillis = startedAt,
            lastActivityAtMillis = lastModified(),
            entryCount = cachedCount(id),
        )
    }

    // Это выбор свободного имени сессии при совпадении времени старта до миллисекунды.
    private fun pickFreeId(base: String): String {
        if (!fileFor(base).exists()) return base
        var suffix = 2
        while (fileFor("${base}_$suffix").exists()) suffix++
        return "${base}_$suffix"
    }

    // Это число записей с кэшем по (lastModified, length): прошлые сессии считаются один раз,
    // текущая пересчитывается только когда файл реально изменился (после новых append).
    private fun File.cachedCount(id: String): Int {
        val lastModified = lastModified()
        val length = length()
        countCache[id]?.let { snapshot ->
            if (snapshot.lastModified == lastModified && snapshot.length == length) {
                return snapshot.count
            }
        }
        val counted = countNonBlankLines()
        countCache[id] = CountSnapshot(lastModified = lastModified, length = length, count = counted)
        return counted
    }

    // Это все файлы сессий в каталоге.
    private fun sessionFiles(): List<File> =
        directory.listFiles { file -> file.isFile && file.idFromName() != null }?.toList().orEmpty()

    private fun fileFor(id: String): File = File(directory, "$FILE_PREFIX$id$FILE_SUFFIX")

    // Это извлечение id сессии из имени файла session_<id>.jsonl.
    private fun File.idFromName(): String? {
        if (!name.startsWith(FILE_PREFIX) || !name.endsWith(FILE_SUFFIX)) return null
        return name.substring(FILE_PREFIX.length, name.length - FILE_SUFFIX.length).takeIf { it.isNotBlank() }
    }

    // Это подсчёт непустых строк (= числа записей) в файле сессии.
    private fun File.countNonBlankLines(): Int = runCatching {
        var count = 0
        bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { if (it.isNotBlank()) count++ }
        }
        count
    }.getOrDefault(0)

    // Это открытие writer'а текущей сессии (UTF-8), с защитой от сбоев. append = true дозаписывает,
    // append = false усекает файл (используется при очистке сессии).
    private fun openWriter(file: File, append: Boolean = true): BufferedWriter? = runCatching {
        file.parentFile?.mkdirs()
        BufferedWriter(OutputStreamWriter(FileOutputStream(file, append), Charsets.UTF_8))
    }.getOrNull()

    // Это постановка задачи в фон без блокировки и без шанса уронить вызывающего.
    private fun execute(block: () -> Unit) {
        runCatching {
            executor.execute {
                runCatching { block() }
            }
        }
    }

    // Это синхронное выполнение на потоке executor'а с возвратом результата (для list/read/delete).
    // Вызывается из UI на IO-диспетчере, поэтому блокировка здесь безопасна и сериализует доступ к файлам.
    private fun <T> submit(block: () -> T): T? =
        runCatching { executor.submit(Callable { block() }).get() }.getOrNull()

    // Это снимок состояния файла для кэша числа записей.
    private data class CountSnapshot(
        val lastModified: Long,
        val length: Long,
        val count: Int,
    )

    private companion object {
        private const val FILE_PREFIX = "session_"
        private const val FILE_SUFFIX = ".jsonl"
        private const val ID_PATTERN = "yyyy-MM-dd_HH-mm-ss-SSS"
    }

    // Это формат id сессии как времени старта. SimpleDateFormat не потокобезопасен, поэтому
    // создаём свежий экземпляр на каждый вызов (дёшево и без общего изменяемого состояния).
    private fun formatId(millis: Long): String =
        SimpleDateFormat(ID_PATTERN, Locale.US).format(Date(millis))

    private fun parseStartedAt(id: String): Long? =
        runCatching { SimpleDateFormat(ID_PATTERN, Locale.US).parse(id)?.time }.getOrNull()

    // Это сериализация записи в одну строку JSON. org.json экранирует переносы и кавычки сам.
    private fun EshretTalkerLogEntry.toJsonLine(): String? = runCatching {
        JSONObject().apply {
            put("id", id)
            put("ts", timestampMillis)
            put("level", level.name)
            put("tag", tag)
            put("message", message)
            details?.let { put("details", it) }
            throwableSummary?.let { put("throwable", it) }
            stackTrace?.let { put("stack", it) }
        }.toString()
    }.getOrNull()

    // Это разбор одной строки JSON в запись журнала. Битые строки молча пропускаются.
    private fun parseJsonLine(line: String): EshretTalkerLogEntry? {
        if (line.isBlank()) return null
        return runCatching {
            val json = JSONObject(line)
            EshretTalkerLogEntry(
                id = json.getLong("id"),
                timestampMillis = json.getLong("ts"),
                level = EshretTalkerLevel.valueOf(json.getString("level")),
                tag = json.getString("tag"),
                message = json.getString("message"),
                details = json.optStringOrNull("details"),
                throwableSummary = json.optStringOrNull("throwable"),
                stackTrace = json.optStringOrNull("stack"),
            )
        }.getOrNull()
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null
}
