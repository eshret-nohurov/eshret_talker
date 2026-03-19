package com.eshret.talker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
// Здесь мы показываем цветные карточки логов, поиск, фильтрацию и удобное чтение событий в стиле talker_flutter.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EshretTalkerScreen(
    // Это экземпляр логгера, чьи записи нужно показать.
    talker: EshretTalker,
    // Это внешний модификатор экрана.
    modifier: Modifier = Modifier,
) {
    // Это текущий список записей из логгера.
    val entries by talker.logs.collectAsState()
    // Это текст поиска по журналу.
    var query by remember { mutableStateOf("") }
    // Это выбранный фильтр по уровню.
    var selectedLevel by remember { mutableStateOf<EshretTalkerLevel?>(null) }

    // Это отфильтрованный и развернутый в обратном порядке список логов.
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
            // Это верхняя панель экрана журнала.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EshretTalkerToolbarColor)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "eshret_talker",
                    color = EshretTalkerTextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Живой журнал действий, ошибок и HTTP-событий",
                    color = EshretTalkerTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(text = "Поиск по логам")
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { talker.clear() }) {
                        Text(text = "Очистить")
                    }
                    Button(onClick = { selectedLevel = null }) {
                        Text(text = "Все")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EshretTalkerLevel.entries.forEach { level ->
                        AssistChip(
                            onClick = {
                                selectedLevel = if (selectedLevel == level) null else level
                            },
                            label = {
                                Text(text = "${level.emoji} ${level.title}")
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selectedLevel == level) {
                                    level.uiStyle().container
                                } else {
                                    EshretTalkerToolbarColor
                                },
                                labelColor = if (selectedLevel == level) {
                                    level.uiStyle().accent
                                } else {
                                    EshretTalkerTextPrimary
                                },
                            ),
                        )
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                // Это пустое состояние, если логов пока нет или фильтр ничего не нашёл.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Пока нет логов для показа",
                        color = EshretTalkerTextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                // Это список логов с разделением карточками.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
private fun EshretTalkerLogCard(
    entry: EshretTalkerLogEntry,
) {
    // Это стиль уровня для цвета карточки.
    val style = entry.level.uiStyle()
    // Это форматтер времени записи.
    val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    // Это локальный флаг разворота подробностей записи.
    var expanded by remember(entry.id) { mutableStateOf(false) }

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
                    ) {
                        Text(
                            text = "${entry.level.emoji} ${entry.level.title}",
                            color = style.accent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = formatter.format(Date(entry.timestampMillis)),
                            color = EshretTalkerTextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
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
