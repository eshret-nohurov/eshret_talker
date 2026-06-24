package com.eshret.talker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.eshret.talker.core.EshretTalkerLevel
import com.eshret.talker.core.EshretTalkerLogEntry
import com.eshret.talker.core.EshretTalkerSessionStore
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Этот файл описывает экран логов одной сессии eshret_talker.
// Записи читаются из хранилища (а не из живого буфера), показываются теми же карточками,
// что и основной журнал, с поиском и фильтрами. Доступны копирование и шаринг ВСЕХ записей.

@Composable
fun EshretTalkerSessionLogScreen(
    // Это хранилище сессий.
    store: EshretTalkerSessionStore,
    // Это id открываемой сессии.
    sessionId: String,
    // Это возврат к списку сессий.
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    respectStatusBarInsets: Boolean = false,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    // Это scope для чтения с диска, копирования и шаринга (всё вне main thread).
    val scope = rememberCoroutineScope()
    // Это все записи сессии в порядке записи (старые → новые).
    var entries by remember { mutableStateOf<List<EshretTalkerLogEntry>>(emptyList()) }
    // Это флаг чтения сессии с диска.
    var isLoading by remember { mutableStateOf(true) }
    // Это текст поиска по логам сессии.
    var query by remember { mutableStateOf("") }
    // Это скрытые уровни логов.
    var hiddenLevels by remember { mutableStateOf(setOf<EshretTalkerLevel>()) }
    // Это список уровней для фильтров.
    val levels = remember { EshretTalkerLevel.entries.toList() }
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(sessionId) {
        isLoading = true
        entries = withContext(Dispatchers.IO) { store.readEntries(sessionId) }
        isLoading = false
    }

    // Это записи для показа: новые сверху, с учётом скрытых уровней и поиска.
    val filteredEntries = remember(entries, query, hiddenLevels) {
        entries.asReversed().filter { entry ->
            val levelMatches = entry.level !in hiddenLevels
            val queryMatches = query.isBlank() ||
                entry.message.contains(query, ignoreCase = true) ||
                entry.tag.contains(query, ignoreCase = true) ||
                entry.details.orEmpty().contains(query, ignoreCase = true)
            levelMatches && queryMatches
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = EshretTalkerScreenColor,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EshretTalkerSubScreenTopBar(
                title = "Сессия",
                subtitle = prettySessionTitle(sessionId),
                respectStatusBarInsets = respectStatusBarInsets,
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            if (entries.isEmpty()) {
                                showTalkerToast(context, "В сессии нет записей")
                            } else {
                                scope.launch {
                                    runCatching {
                                        val shareText = withContext(Dispatchers.Default) { entries.toShareText() }
                                        clipboardManager.setText(AnnotatedString(shareText))
                                    }.onSuccess {
                                        showTalkerToast(context, "Логи сессии скопированы")
                                    }.onFailure {
                                        showTalkerToast(context, "Не удалось скопировать логи")
                                    }
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Скопировать логи сессии",
                            tint = EshretTalkerTextPrimary,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (entries.isEmpty()) {
                                showTalkerToast(context, "В сессии нет записей")
                            } else {
                                scope.launch {
                                    runCatching {
                                        val exportFile = withContext(Dispatchers.IO) {
                                            writeLogsExportFile(context = context, entries = entries)
                                        }
                                        shareLogsAsFile(context = context, exportFile = exportFile)
                                    }.onFailure {
                                        showTalkerToast(context, "Не удалось открыть системный share")
                                    }
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Поделиться логами сессии",
                            tint = EshretTalkerTextPrimary,
                        )
                    }
                },
            )

            EshretTalkerSessionFilters(
                levels = levels,
                query = query,
                hiddenLevels = hiddenLevels,
                onQueryChange = { query = it },
                onLevelClick = { level -> hiddenLevels = hiddenLevels.toggle(level) },
            )

            when {
                isLoading -> EshretTalkerSessionLogPlaceholder(text = "Загрузка логов сессии…")
                entries.isEmpty() -> EshretTalkerSessionLogPlaceholder(text = "В этой сессии нет записей")
                filteredEntries.isEmpty() -> EshretTalkerSessionLogPlaceholder(text = "Под фильтр ничего не подходит")
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 20.dp + navigationBarBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = filteredEntries,
                        key = { entry -> entry.id },
                    ) { entry ->
                        EshretTalkerLogCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun EshretTalkerSessionFilters(
    levels: List<EshretTalkerLevel>,
    query: String,
    hiddenLevels: Set<EshretTalkerLevel>,
    onQueryChange: (String) -> Unit,
    onLevelClick: (EshretTalkerLevel) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EshretTalkerToolbarColor)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = levels,
                key = { level -> level.name },
            ) { level ->
                EshretTalkerCompactLevelChip(
                    level = level,
                    selected = level !in hiddenLevels,
                    onClick = { onLevelClick(level) },
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, end = 8.dp),
            singleLine = true,
            label = { Text(text = "Поиск по логам сессии") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
}

@Composable
private fun EshretTalkerSessionLogPlaceholder(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = EshretTalkerTextSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// Это форматтеры для красивого заголовка сессии из её id (yyyy-MM-dd_HH-mm-ss-SSS).
private val sessionIdParser = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US)
private val sessionTitleFormatter = SimpleDateFormat("d MMMM yyyy, HH:mm:ss", Locale.getDefault())

private fun prettySessionTitle(sessionId: String): String =
    runCatching { sessionIdParser.parse(sessionId)?.let { sessionTitleFormatter.format(it) } }
        .getOrNull()
        ?: sessionId
