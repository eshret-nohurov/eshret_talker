package com.eshret.talker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eshret.talker.core.EshretTalkerSession
import com.eshret.talker.core.EshretTalkerSessionStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Этот файл описывает экран списка сессий eshret_talker.
// Сессии сгруппированы по дням; в строке — время сессии, сколько назад она закончилась и
// число записей. Тап открывает логи сессии; есть удаление одной сессии и удаление всех.

@Composable
fun EshretTalkerSessionsScreen(
    // Это хранилище сессий.
    store: EshretTalkerSessionStore,
    // Это переход к логам выбранной сессии.
    onOpenSession: (String) -> Unit,
    // Это возврат к живому журналу.
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    respectStatusBarInsets: Boolean = false,
) {
    // Это scope для фоновой загрузки и удаления (диск — только вне main thread).
    val scope = rememberCoroutineScope()
    // Это ключ перезагрузки списка после удаления.
    var refreshKey by remember { mutableIntStateOf(0) }
    // Это текущий список сессий, новейшие первыми.
    var sessions by remember { mutableStateOf<List<EshretTalkerSession>>(emptyList()) }
    // Это момент загрузки списка — точка отсчёта для «сколько назад».
    var nowMillis by remember { mutableLongStateOf(0L) }
    // Это флаг первичной загрузки.
    var isLoading by remember { mutableStateOf(true) }
    // Это сессия, для которой запрошено подтверждение удаления.
    var sessionPendingDelete by remember { mutableStateOf<EshretTalkerSession?>(null) }
    // Это флаг подтверждения удаления всех сессий.
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    // Это id активной сессии для пометки «текущая».
    val currentSessionId = remember(store) { store.currentSessionId }
    // Это нижний inset навигационной панели.
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(refreshKey) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            store.listSessions() to System.currentTimeMillis()
        }
        sessions = loaded.first
        nowMillis = loaded.second
        isLoading = false
    }

    // Это сессии, сгруппированные по дню старта. groupBy сохраняет порядок: дни и сессии
    // внутри дня остаются новейшими сверху, потому что список уже отсортирован.
    val groupedByDay = remember(sessions) {
        sessions.groupBy { dayHeaderFormatter.format(Date(it.startedAtMillis)) }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = EshretTalkerScreenColor,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EshretTalkerSubScreenTopBar(
                title = "Сессии",
                subtitle = "Журналы по дням · хранятся 7 дней",
                respectStatusBarInsets = respectStatusBarInsets,
                onBack = onBack,
                actions = {
                    if (sessions.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllConfirm = true }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Удалить все сессии",
                                tint = EshretTalkerTextPrimary,
                            )
                        }
                    }
                },
            )

            when {
                isLoading -> EshretTalkerSessionsPlaceholder(text = "Загрузка сессий…")
                sessions.isEmpty() -> EshretTalkerSessionsPlaceholder(text = "Пока нет сохранённых сессий")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 20.dp + navigationBarBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    groupedByDay.forEach { (day, daySessions) ->
                        item(key = "day_$day") {
                            Text(
                                text = day,
                                color = EshretTalkerTextSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                        items(
                            items = daySessions,
                            key = { session -> session.id },
                        ) { session ->
                            EshretTalkerSessionRow(
                                session = session,
                                isCurrent = session.id == currentSessionId,
                                nowMillis = nowMillis,
                                onClick = { onOpenSession(session.id) },
                                onDelete = { sessionPendingDelete = session },
                            )
                        }
                    }
                }
            }
        }
    }

    sessionPendingDelete?.let { session ->
        EshretTalkerConfirmDialog(
            title = "Удалить сессию?",
            message = "Сессия от ${timeFormatter.format(Date(session.startedAtMillis))} и все её записи будут удалены.",
            confirmLabel = "Удалить",
            onConfirm = {
                sessionPendingDelete = null
                scope.launch {
                    withContext(Dispatchers.IO) { store.deleteSession(session.id) }
                    refreshKey++
                }
            },
            onDismiss = { sessionPendingDelete = null },
        )
    }

    if (showDeleteAllConfirm) {
        EshretTalkerConfirmDialog(
            title = "Удалить все сессии?",
            message = "Будут удалены все сохранённые сессии. Текущая сессия начнётся заново.",
            confirmLabel = "Удалить все",
            onConfirm = {
                showDeleteAllConfirm = false
                scope.launch {
                    withContext(Dispatchers.IO) { store.deleteAllSessions() }
                    refreshKey++
                }
            },
            onDismiss = { showDeleteAllConfirm = false },
        )
    }
}

@Composable
private fun EshretTalkerSessionRow(
    session: EshretTalkerSession,
    isCurrent: Boolean,
    nowMillis: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = EshretTalkerCardBorderColor,
                shape = RoundedCornerShape(16.dp),
            ),
        color = EshretTalkerToolbarColor,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            ) {
                Text(
                    text = "🕒 ${timeFormatter.format(Date(session.startedAtMillis))}" +
                        if (isCurrent) "  • текущая" else "",
                    color = EshretTalkerTextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${humanizeAgo(nowMillis - session.lastActivityAtMillis)} · " +
                        "${session.entryCount} ${recordsWord(session.entryCount)}",
                    color = EshretTalkerTextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Удалить сессию",
                    tint = EshretTalkerTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun EshretTalkerSessionsPlaceholder(text: String) {
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

// Это закреплённый верхний бар для вложенных экранов (сессии и логи сессии) — кнопка назад,
// заголовок, подзаголовок и слот действий справа, в едином стиле с основным журналом.
@Composable
internal fun EshretTalkerSubScreenTopBar(
    title: String,
    subtitle: String?,
    respectStatusBarInsets: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EshretTalkerToolbarColor)
            .then(
                if (respectStatusBarInsets) Modifier.statusBarsPadding() else Modifier,
            )
            .padding(start = 4.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = EshretTalkerTextPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = EshretTalkerTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = EshretTalkerTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            actions()
        }
    }
}

// Это переиспользуемый диалог подтверждения деструктивных действий над сессиями.
@Composable
internal fun EshretTalkerConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EshretTalkerToolbarColor,
        titleContentColor = EshretTalkerTextPrimary,
        textContentColor = EshretTalkerTextSecondary,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, color = EshretTalkerTextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Отмена", color = EshretTalkerTextSecondary)
            }
        },
    )
}

// Это форматтер времени старта сессии (для имени строки и текста подтверждения).
private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

// Это форматтер дня для заголовка группы.
private val dayHeaderFormatter = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

// Это человекочитаемое «сколько назад» от текущего момента до последней активности сессии.
private fun humanizeAgo(deltaMillis: Long): String {
    if (deltaMillis < 60_000) return "только что"
    val minutes = deltaMillis / 60_000
    return when {
        minutes < 60 -> "$minutes мин назад"
        minutes < 60 * 24 -> "${minutes / 60} ч назад"
        else -> "${minutes / (60 * 24)} дн назад"
    }
}

// Это согласование слова «запись» с числом по правилам русского языка.
private fun recordsWord(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "записей"
        mod10 == 1 -> "запись"
        mod10 in 2..4 -> "записи"
        else -> "записей"
    }
}
