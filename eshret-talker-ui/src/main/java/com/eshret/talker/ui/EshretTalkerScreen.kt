package com.eshret.talker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import com.eshret.talker.core.EshretTalker
import com.eshret.talker.core.EshretTalkerLevel
import com.eshret.talker.core.EshretTalkerLogEntry
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
    // Это скрытые уровни, которые пользователь не хочет видеть в списке.
    var hiddenLevels by remember { mutableStateOf(setOf<EshretTalkerLevel>()) }
    // Это локальный флаг показа action-sheet с действиями над журналом.
    var showActionsSheet by remember { mutableStateOf(false) }
    // Это направление списка: `true` показывает новые записи сверху, `false` — старые сверху.
    var newestFirst by remember { mutableStateOf(true) }
    // Это флаг, который просит после переворота списка прокрутить журнал в самый верх.
    var scrollToTopAfterReorder by remember { mutableStateOf(false) }
    // Это manager системного буфера обмена.
    val clipboardManager = LocalClipboardManager.current
    // Это context для toast и share-intent.
    val context = LocalContext.current
    val density = LocalDensity.current
    // Это нижний inset навигационной панели, чтобы последние элементы не прятались под системным баром.
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Это список уровней для горизонтального скролла.
    val levels = remember { EshretTalkerLevel.entries.toList() }
    // Это полный текст всего журнала для копирования и шаринга.
    val shareText = remember(entries) { entries.toShareText() }
    // Это состояние списка логов.
    val listState = rememberLazyListState()
    // Это текущая измеренная высота сворачиваемой панели фильтров и поиска.
    var collapsiblePanelHeightPx by remember { mutableFloatStateOf(0f) }
    // Это текущее смещение сворачиваемой панели: `0` видно полностью, отрицательное значение прячет её вверх.
    var collapsiblePanelOffsetPx by remember { mutableFloatStateOf(0f) }
    // Это последнее направление пользовательского скролла: вниз прячет панель, вверх показывает.
    var lastScrollDirection by remember { mutableFloatStateOf(0f) }
    // Это nested scroll-контроллер панели: он обновляет её видимость параллельно со скроллом списка.
    val headerAndOverscrollConnection = remember(collapsiblePanelHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                if (collapsiblePanelHeightPx <= 0f || available.y == 0f) {
                    return Offset.Zero
                }

                lastScrollDirection = available.y
                collapsiblePanelOffsetPx = (collapsiblePanelOffsetPx + available.y)
                    .coerceIn(-collapsiblePanelHeightPx, 0f)
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress, collapsiblePanelHeightPx, lastScrollDirection) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        if (collapsiblePanelHeightPx <= 0f) return@LaunchedEffect
        if (collapsiblePanelOffsetPx == 0f || collapsiblePanelOffsetPx == -collapsiblePanelHeightPx) {
            return@LaunchedEffect
        }

        val visibleFraction = ((collapsiblePanelHeightPx + collapsiblePanelOffsetPx) / collapsiblePanelHeightPx)
            .coerceIn(0f, 1f)
        val hiddenFraction = 1f - visibleFraction

        val targetOffset = when {
            lastScrollDirection < 0f -> {
                if (hiddenFraction >= 0.2f) -collapsiblePanelHeightPx else 0f
            }
            lastScrollDirection > 0f -> {
                if (visibleFraction >= 0.2f) 0f else -collapsiblePanelHeightPx
            }
            else -> {
                if (visibleFraction >= 0.5f) 0f else -collapsiblePanelHeightPx
            }
        }

        if (targetOffset == collapsiblePanelOffsetPx) return@LaunchedEffect

        val startOffset = collapsiblePanelOffsetPx
        animate(
            initialValue = startOffset,
            targetValue = targetOffset,
            animationSpec = tween(
                durationMillis = 180,
                easing = FastOutSlowInEasing,
            ),
        ) { value, _ ->
            collapsiblePanelOffsetPx = value
        }
    }

    // Это итоговый список логов с учётом скрытых уровней, тегов, поиска и текущего порядка.
    val filteredEntries = remember(entries, query, hiddenLevels, newestFirst) {
        val baseEntries = if (newestFirst) entries.asReversed() else entries
        baseEntries.filter { entry ->
            val levelMatches = entry.level !in hiddenLevels
            val queryMatches = query.isBlank() ||
                entry.message.contains(query, ignoreCase = true) ||
                entry.tag.contains(query, ignoreCase = true) ||
                entry.details.orEmpty().contains(query, ignoreCase = true)
            levelMatches && queryMatches
        }
    }

    LaunchedEffect(newestFirst, scrollToTopAfterReorder) {
        if (!scrollToTopAfterReorder) return@LaunchedEffect
        listState.scrollToItem(index = 0, scrollOffset = 0)
        scrollToTopAfterReorder = false
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = EshretTalkerScreenColor,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EshretTalkerTopBar(
                respectStatusBarInsets = respectStatusBarInsets,
                newestFirst = newestFirst,
                onActionsClick = { showActionsSheet = true },
                onReverseOrderClick = {
                    newestFirst = !newestFirst
                    scrollToTopAfterReorder = true
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds(),
            ) {
                val panelHeightDp = with(density) { collapsiblePanelHeightPx.toDp() }

                if (filteredEntries.isEmpty()) {
                    // Это пустое состояние, если логов пока нет или фильтр ничего не нашёл.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = navigationBarBottomPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Пока нет логов для показа",
                            color = EshretTalkerTextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    // Это основной список логов, который уходит вверх вместе со скрытием панели фильтров.
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(headerAndOverscrollConnection)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        state = listState,
                        contentPadding = PaddingValues(
                            top = panelHeightDp + 8.dp,
                            bottom = 20.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = filteredEntries,
                            key = { entry -> entry.id },
                        ) { entry ->
                            EshretTalkerLogCard(entry = entry)
                        }
                        item(key = "navigation_bar_spacer") {
                            Spacer(
                                modifier = Modifier.height(navigationBarBottomPadding),
                            )
                        }
                    }
                }

                EshretTalkerCollapsibleControls(
                    levels = levels,
                    query = query,
                    hiddenLevels = hiddenLevels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = collapsiblePanelOffsetPx.roundToInt()) },
                    onQueryChange = { query = it },
                    onLevelClick = { level ->
                        hiddenLevels = hiddenLevels.toggle(level)
                    },
                    onHeightMeasured = { measuredHeight ->
                        collapsiblePanelHeightPx = measuredHeight.toFloat()
                        collapsiblePanelOffsetPx = collapsiblePanelOffsetPx
                            .coerceIn(-collapsiblePanelHeightPx, 0f)
                    },
                )
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
                }
            )
        }
    }
}

@Composable
private fun EshretTalkerTopBar(
    respectStatusBarInsets: Boolean,
    newestFirst: Boolean,
    onActionsClick: () -> Unit,
    onReverseOrderClick: () -> Unit,
) {
    // Это закреплённый app bar журнала с заголовком и кнопкой действий.
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
            IconButton(onClick = onReverseOrderClick) {
                Icon(
                    imageVector = Icons.Filled.SwapVert,
                    contentDescription = if (newestFirst) {
                        "Показывать старые сверху"
                    } else {
                        "Показывать новые сверху"
                    },
                    tint = EshretTalkerTextPrimary,
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
    }
}

@Composable
private fun EshretTalkerCollapsibleControls(
    levels: List<EshretTalkerLevel>,
    query: String,
    hiddenLevels: Set<EshretTalkerLevel>,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onLevelClick: (EshretTalkerLevel) -> Unit,
    onHeightMeasured: (Int) -> Unit,
) {
    // Эта панель живёт поверх списка и просто сдвигается вверх под app bar.
    Column(
        modifier = modifier
            .background(EshretTalkerToolbarColor)
            .onSizeChanged { size ->
                onHeightMeasured(size.height)
            }
            .padding(start = 16.dp, end = 8.dp, bottom = 12.dp),
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Типы",
            color = EshretTalkerTextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(modifier = Modifier.height(6.dp))

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
    // Это локальный флаг показа полноэкранного body-диалога.
    var showFullBodyDialog by remember(entry.id) { mutableStateOf(false) }
    // Это manager системного буфера обмена для копирования одной записи.
    val clipboardManager = LocalClipboardManager.current
    // Это context для короткого toast после копирования.
    val context = LocalContext.current
    // Это полный body HTTP-лога, если он есть в details.
    val httpBody = remember(entry.details) { entry.details.orEmpty().extractHttpBodySection() }
    // Это блок метаданных details без большого body, чтобы карточка не раздувалась.
    val detailsWithoutBody = remember(entry.details) { entry.details.orEmpty().removeHttpBodySection() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = EshretTalkerCardBorderColor,
                shape = RoundedCornerShape(18.dp),
            ),
        color = style.container,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { expanded = !expanded },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
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
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${entry.level.emoji} ${entry.level.title}",
                                    color = style.accent,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (entry.tag.isNotBlank()) {
                                    Text(
                                        text = "🏷️ ${entry.tag}",
                                        color = EshretTalkerTextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(
                                    text = formatter.format(Date(entry.timestampMillis)),
                                    color = EshretTalkerTextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
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
                    detailsWithoutBody
                        ?.takeIf { it.isNotBlank() }
                        ?.let { details ->
                            EshretTalkerDetailsBlock(title = "Подробности", value = details)
                        }
                    httpBody
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            FilledTonalButton(
                                onClick = { showFullBodyDialog = true },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text(text = "Открыть body")
                            }
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
            Spacer(modifier = Modifier.width(10.dp))
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(entry.toShareBlockText()))
                    showTalkerToast(context, "Лог скопирован")
                },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Скопировать лог",
                    tint = EshretTalkerTextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    if (showFullBodyDialog && !httpBody.isNullOrBlank()) {
        EshretTalkerLargeTextDialog(
            title = when (entry.level) {
                EshretTalkerLevel.HTTP_RESPONSE -> "Полный body ответа"
                EshretTalkerLevel.HTTP_REQUEST -> "Полный body запроса"
                else -> "Полные подробности"
            },
            value = httpBody,
            onDismiss = { showFullBodyDialog = false },
        )
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

@Composable
private fun EshretTalkerLargeTextDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = EshretTalkerToolbarColor,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        color = EshretTalkerTextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Закрыть",
                            tint = EshretTalkerTextPrimary,
                        )
                    }
                }
                Text(
                    text = "Вложенные массивы и объекты можно раскрывать и сворачивать.",
                    color = EshretTalkerTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                EshretTalkerBodyViewer(
                    body = value,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

private fun String.extractHttpBodySection(): String? {
    val marker = "Body:\n"
    val bodyStartIndex = indexOf(marker)
    if (bodyStartIndex == -1) return null
    return substring(bodyStartIndex + marker.length)
        .trim()
        .takeIf { it.isNotBlank() }
}

private fun String.removeHttpBodySection(): String? {
    val marker = "Body:\n"
    val bodyStartIndex = indexOf(marker)
    if (bodyStartIndex == -1) return takeIf { it.isNotBlank() }
    return substring(0, bodyStartIndex)
        .trimEnd()
        .takeIf { it.isNotBlank() }
}

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item


private fun showTalkerToast(
    context: android.content.Context,
    message: String,
) {
    // Это короткий системный toast для подтверждения действий с журналом.
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
