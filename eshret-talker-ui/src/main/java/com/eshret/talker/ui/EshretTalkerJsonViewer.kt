package com.eshret.talker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

// Этот файл описывает компактный tree-viewer для JSON body внутри журнала.
// Здесь мы показываем объекты и массивы как раскрывающиеся узлы, чтобы большие ответы было легче читать.

@Composable
internal fun EshretTalkerBodyViewer(
    body: String,
    modifier: Modifier = Modifier,
) {
    val jsonNode = parseJsonNodeOrNull(body)
    val verticalScrollState = rememberScrollState()

    if (jsonNode == null) {
        val horizontalScrollState = rememberScrollState()
        SelectionContainer {
            Text(
                text = body,
                color = EshretTalkerTextPrimary,
                style = MaterialTheme.typography.bodySmall,
                softWrap = false,
                modifier = modifier
                    .horizontalScroll(horizontalScrollState)
                    .verticalScroll(verticalScrollState),
            )
        }
    } else {
        val horizontalScrollState = rememberScrollState()
        BoxWithConstraints(
            modifier = modifier,
        ) {
            val availableWidth = maxWidth
            Row(
                modifier = Modifier
                    .horizontalScroll(horizontalScrollState)
                    .verticalScroll(verticalScrollState),
            ) {
                Column(
                    modifier = Modifier.widthIn(min = availableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JsonNodeItem(
                        label = null,
                        node = jsonNode,
                        path = "root",
                        depth = 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonNodeItem(
    label: String?,
    node: JsonNode,
    path: String,
    depth: Int,
) {
    when (node) {
        is JsonNode.Value -> JsonValueRow(
            label = label,
            value = node.value,
            depth = depth,
        )

        is JsonNode.Object -> JsonExpandableNode(
            label = label,
            summary = "{${node.entries.size}}",
            depth = depth,
            path = path,
        ) {
            node.entries.forEach { (childLabel, childNode) ->
                JsonNodeItem(
                    label = childLabel,
                    node = childNode,
                    path = "$path.$childLabel",
                    depth = depth + 1,
                )
            }
        }

        is JsonNode.Array -> JsonExpandableNode(
            label = label,
            summary = "[${node.items.size}]",
            depth = depth,
            path = path,
        ) {
            node.items.forEachIndexed { index, childNode ->
                JsonNodeItem(
                    label = "[$index]",
                    node = childNode,
                    path = "$path[$index]",
                    depth = depth + 1,
                )
            }
        }
    }
}

@Composable
private fun JsonExpandableNode(
    label: String?,
    summary: String,
    depth: Int,
    path: String,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(path) { mutableStateOf(depth == 0) }
    val indent = (depth * 12).dp

    Column(
        modifier = Modifier,
    ) {
        Surface(
            modifier = Modifier
                .padding(start = indent)
                .clickable { expanded = !expanded },
            color = EshretTalkerCardBorderColor.copy(alpha = 0.45f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = EshretTalkerTextSecondary,
                )
                JsonLabel(label = label)
                if (label != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = summary,
                    color = EshretTalkerTextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .padding(start = indent + 12.dp)
                    .background(
                        color = EshretTalkerCardBorderColor.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Column(
                    modifier = Modifier,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun JsonValueRow(
    label: String?,
    value: String,
    depth: Int,
) {
    val indent = (depth * 12).dp

    SelectionContainer {
        Column(
            modifier = Modifier
                .padding(start = indent)
                .background(
                    color = EshretTalkerCardBorderColor.copy(alpha = 0.22f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (label != null) {
                JsonLabel(label = label)
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text(
                text = value,
                color = EshretTalkerTextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun JsonLabel(label: String?) {
    if (label == null) return
    Text(
        text = label,
        color = EshretTalkerTextSecondary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private sealed interface JsonNode {
    data class Object(val entries: List<Pair<String, JsonNode>>) : JsonNode
    data class Array(val items: List<JsonNode>) : JsonNode
    data class Value(val value: String) : JsonNode
}

private fun parseJsonNodeOrNull(raw: String): JsonNode? {
    val trimmed = raw.trim()
    return runCatching {
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed).toJsonNode()
            trimmed.startsWith("[") -> JSONArray(trimmed).toJsonNode()
            else -> null
        }
    }.getOrNull()
}

private fun JSONObject.toJsonNode(): JsonNode.Object {
    val keys = keys().asSequence().toList()
    return JsonNode.Object(
        entries = keys.map { key ->
            key to valueToJsonNode(opt(key))
        },
    )
}

private fun JSONArray.toJsonNode(): JsonNode.Array =
    JsonNode.Array(
        items = List(length()) { index ->
            valueToJsonNode(opt(index))
        },
    )

private fun valueToJsonNode(value: Any?): JsonNode = when (value) {
    null,
    JSONObject.NULL -> JsonNode.Value("null")
    is JSONObject -> value.toJsonNode()
    is JSONArray -> value.toJsonNode()
    is String -> JsonNode.Value("\"$value\"")
    is Number,
    is Boolean -> JsonNode.Value(value.toString())
    else -> JsonNode.Value(value.toString())
}
