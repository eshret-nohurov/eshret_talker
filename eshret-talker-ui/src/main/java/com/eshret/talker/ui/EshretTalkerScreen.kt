package com.eshret.talker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eshret.talker.core.EshretTalker
import com.eshret.talker.core.EshretTalkerLevel
import com.eshret.talker.core.EshretTalkerLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Этот файл описывает основной Compose-экран журнала библиотеки eshret_talker.
// Здесь мы показываем компактный toolbar, горизонтальные фильтры, поиск и действия для экспорта логов.

@Composable
fun EshretTalkerScreen(
    // Это экземпляр логгера, чьи записи нужно показать.
    talker: EshretTalker,
    // Это внешний модификатор экрана.
    modifier: Modifier = Modifier,
    // Это флаг учёта верхней системной панели для полноэкранного sheet.
    respectStatusBarInsets: Boolean = false,
) {
    // Это текущий список записей из логгера.
    val entries by talker.logs.collectAsState()
    // Это текст поиска по журналу.
    var query by remember { mutableStateOf("") }
    // Это выбранный фильтр по уровню.
    var selectedLevel by remember { mutableStateOf<EshretTalkerLevel?>(null) }
    // Это локальный флаг показа action-sheet с действиями над журналом.
    var showActionsSheet by remember { mutableStateOf(false) }
    // Это manager системного буфера обмена.
    val clipboardManager = LocalClipboardManager.current
    // Это context для toast и share-intent.
    val context = LocalContext.current
    // Это список уровней для горизонтального скролла.
    val levels = remember { EshretTalkerLevel.entries.toList() }
    // Это полный текст всего журнала для копирования и шаринга.
    val shareText = remember(entries) { entries.toShareText() }

    // Это отфильтрованный и развёрнутый в обратном порядке список логов.
    val filteredEntries = remember(entries, query, selectedLevel) {
        entries.asReversed().filter { entry ->
            val levelMatches = selectedLevel == null || entry.level == selectedLevel
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
            EshretTalkerToolbar(
                levels = levels,
                query = query,
                selectedLevel = selectedLevel,
                respectStatusBarInsets = respectStatusBarInsets,
                onQueryChange = { query = it },
                onLevelClick = { level ->
                    selectedLevel = if (selectedLevel == level) null else level
                },
                onActionsClick = { showActionsSheet = true },
            )

            if (filteredEntries.isEmpty()) {
                // Это пустое состояние, если логов пока нет или фильтр ничего не нашёл.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Пока нет логов для показа",
                        color = EshretTalkerTextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                // Это основной список логов с увеличенной полезной высотой.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
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

        if (showActionsSheet) {
            EshretTalkerActionsBottomSheet(
                hasLogs = entries.isNotEmpty(),
                onDismiss = { showActionsSheet = false },
                onClearHistory = {
                    talker.clear()
                    showActionsSheet = false
                    showTalkerToast(context, "История очищена")
                },
                onCopyAllLogs = {
                    if (entries.isEmpty()) {
                        showTalkerToast(context, "Журнал пуст")
                    } else {
                        clipboardManager.setText(AnnotatedString(shareText))
                        showActionsSheet = false
                        showTalkerToast(context, "Все логи скопированы")
                    }
                },
                onShareLogFile = {
                    if (entries.isEmpty()) {
                        showTalkerToast(context, "Журнал пуст")
                    } else {
                        runCatching {
                            shareLogsAsFile(
                                context = context,
                                logsText = shareText,
                            )
                        }.onSuccess {
                            showActionsSheet = false
                        }.onFailure {
                            showTalkerToast(context, "Не удалось открыть системный share")
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun EshretTalkerToolbar(
    levels: List<EshretTalkerLevel>,
    query: String,
    selectedLevel: EshretTalkerLevel?,
    respectStatusBarInsets: Boolean,
    onQueryChange: (String) -> Unit,
    onLevelClick: (EshretTalkerLevel) -> Unit,
    onActionsClick: () -> Unit,
) {
    // Это компактный toolbar журнала с заголовком, фильтрами и поиском.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EshretTalkerToolbarColor)
            .then(
                if (respectStatusBarInsets) {
                    Modifier.statusBarsPadding()
                } else {
                    Modifier
                },
            )
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "eshret_talker",
                    color = EshretTalkerTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Живой журнал действий, ошибок и HTTP-событий",
                    color = EshretTalkerTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onActionsClick) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Открыть действия",
                    tint = EshretTalkerTextPrimary,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                    selected = selectedLevel == level,
                    onClick = {
                        onLevelClick(level)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            singleLine = true,
            label = {
                Text(text = "Поиск по логам")
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
}

@Composable
private fun EshretTalkerCompactLevelChip(
    level: EshretTalkerLevel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Это компактный горизонтальный фильтр по уровню лога.
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = "${level.emoji} ${level.title}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        },
        modifier = Modifier.height(34.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = EshretTalkerCardBorderColor,
            selectedBorderColor = level.uiStyle().accent,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = EshretTalkerToolbarColor,
            labelColor = EshretTalkerTextPrimary,
            selectedContainerColor = level.uiStyle().container,
            selectedLabelColor = level.uiStyle().accent,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EshretTalkerActionsBottomSheet(
    hasLogs: Boolean,
    onDismiss: () -> Unit,
    onClearHistory: () -> Unit,
    onCopyAllLogs: () -> Unit,
    onShareLogFile: () -> Unit,
) {
    // Это нижний лист с действиями над журналом.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = EshretTalkerToolbarColor,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Действия с журналом",
                color = EshretTalkerTextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Очистка, копирование и экспорт полного списка логов.",
                color = EshretTalkerTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )

            FilledTonalButton(
                onClick = onClearHistory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Очистить историю")
            }

            FilledTonalButton(
                onClick = onCopyAllLogs,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasLogs,
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Скопировать все логи")
            }

            FilledTonalButton(
                onClick = onShareLogFile,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasLogs,
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Поделиться лог-файлом")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EshretTalkerLogCard(
    entry: EshretTalkerLogEntry,
) {
    // Это стиль уровня для цвета карточки.
    val style = entry.level.uiStyle()
    // Это форматтер времени записи.
    val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    // Это локальный флаг разворота подробностей записи.
    var expanded by remember(entry.id) { mutableStateOf(false) }
    // Это manager системного буфера обмена для копирования одной записи.
    val clipboardManager = LocalClipboardManager.current
    // Это context для короткого toast после копирования.
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = EshretTalkerCardBorderColor,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable { expanded = !expanded },
        color = style.container,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 6.dp, height = 54.dp)
                        .background(style.accent, RoundedCornerShape(999.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${entry.level.emoji} ${entry.level.title}",
                            color = style.accent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = formatter.format(Date(entry.timestampMillis)),
                                color = EshretTalkerTextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(entry.toShareBlockText()))
                                    showTalkerToast(context, "Лог скопирован")
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Скопировать лог",
                                    tint = EshretTalkerTextSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    if (entry.tag.isNotBlank()) {
                        Text(
                            text = "🏷️ ${entry.tag}",
                            color = EshretTalkerTextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        text = entry.message,
                        color = EshretTalkerTextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            if (!entry.details.isNullOrBlank() || !entry.throwableSummary.isNullOrBlank() || !entry.stackTrace.isNullOrBlank()) {
                Text(
                    text = if (expanded) "Скрыть детали" else "Показать детали",
                    color = style.accent,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (expanded) {
                entry.details
                    ?.takeIf { it.isNotBlank() }
                    ?.let { details ->
                        EshretTalkerDetailsBlock(title = "Подробности", value = details)
                    }
                entry.throwableSummary
                    ?.takeIf { it.isNotBlank() }
                    ?.let { summary ->
                        EshretTalkerDetailsBlock(title = "Ошибка", value = summary)
                    }
                entry.stackTrace
                    ?.takeIf { it.isNotBlank() }
                    ?.let { trace ->
                        EshretTalkerDetailsBlock(title = "Stack trace", value = trace)
                    }
            }
        }
    }
}

@Composable
private fun EshretTalkerDetailsBlock(
    title: String,
    value: String,
) {
    // Это отдельный текстовый блок подробностей внутри карточки лога.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(
                color = EshretTalkerToolbarColor,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
    ) {
        Text(
            text = title,
            color = EshretTalkerTextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = EshretTalkerTextPrimary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
            overflow = TextOverflow.Clip,
        )
    }
}

private fun showTalkerToast(
    context: android.content.Context,
    message: String,
) {
    // Это короткий системный toast для подтверждения действий с журналом.
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
