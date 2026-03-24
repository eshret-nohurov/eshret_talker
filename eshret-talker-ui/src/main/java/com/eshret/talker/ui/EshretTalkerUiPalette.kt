package com.eshret.talker.ui

import androidx.compose.ui.graphics.Color
import com.eshret.talker.core.EshretTalkerLevel

// Этот файл описывает цветовую палитру UI-библиотеки eshret_talker.
// Здесь мы задаём отдельные цвета карточек, акцентных линий и бейджей для каждого уровня лога.

internal data class EshretTalkerLevelStyle(
    // Это главный цвет уровня.
    val accent: Color,
    // Это цвет мягкой подложки карточки.
    val container: Color,
)

internal fun EshretTalkerLevel.uiStyle(): EshretTalkerLevelStyle = when (this) {
    EshretTalkerLevel.VERBOSE -> EshretTalkerLevelStyle(
        accent = Color(0xFF90CAF9),
        container = Color(0xFF102335),
    )
    EshretTalkerLevel.DEBUG -> EshretTalkerLevelStyle(
        accent = Color(0xFFB39DDB),
        container = Color(0xFF231A33),
    )
    EshretTalkerLevel.INFO -> EshretTalkerLevelStyle(
        accent = Color(0xFF64B5F6),
        container = Color(0xFF10293D),
    )
    EshretTalkerLevel.NAVIGATION -> EshretTalkerLevelStyle(
        accent = Color(0xFFFFD54F),
        container = Color(0xFF33280E),
    )
    EshretTalkerLevel.SUCCESS -> EshretTalkerLevelStyle(
        accent = Color(0xFF66BB6A),
        container = Color(0xFF162E19),
    )
    EshretTalkerLevel.WARNING -> EshretTalkerLevelStyle(
        accent = Color(0xFFFFB74D),
        container = Color(0xFF36250F),
    )
    EshretTalkerLevel.ERROR -> EshretTalkerLevelStyle(
        accent = Color(0xFFEF5350),
        container = Color(0xFF361616),
    )
    EshretTalkerLevel.CRITICAL -> EshretTalkerLevelStyle(
        accent = Color(0xFFFF5252),
        container = Color(0xFF430F0F),
    )
    EshretTalkerLevel.HTTP_REQUEST -> EshretTalkerLevelStyle(
        accent = Color(0xFF26C6DA),
        container = Color(0xFF0F3034),
    )
    EshretTalkerLevel.HTTP_RESPONSE -> EshretTalkerLevelStyle(
        accent = Color(0xFF26A69A),
        container = Color(0xFF11312D),
    )
}

internal val EshretTalkerScreenColor = Color(0xFF0E0E12)
internal val EshretTalkerCardBorderColor = Color(0xFF24242D)
internal val EshretTalkerTextPrimary = Color(0xFFF5F5F7)
internal val EshretTalkerTextSecondary = Color(0xFFAAAAAF)
internal val EshretTalkerToolbarColor = Color(0xFF15151C)
internal val EshretTalkerSheetScrimColor = Color(0xFF000000).copy(alpha = 0.72f)
