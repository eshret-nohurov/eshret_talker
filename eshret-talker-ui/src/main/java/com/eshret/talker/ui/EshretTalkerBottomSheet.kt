package com.eshret.talker.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.eshret.talker.core.EshretTalker

// Этот файл описывает полноэкранный bottom sheet библиотеки eshret_talker.
// Здесь журнал сразу открывается на весь экран и синхронизирует системные бары с окном приложения.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EshretTalkerBottomSheet(
    // Это экземпляр логгера, который нужно показать.
    talker: EshretTalker,
    // Это callback закрытия sheet.
    onDismiss: () -> Unit,
) {
    // Это состояние full-screen bottom sheet без промежуточного partially expanded режима.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize(),
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
        containerColor = EshretTalkerScreenColor,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        scrimColor = EshretTalkerSheetScrimColor,
        dragHandle = {
            EshretTalkerBottomSheetDragHandle()
        },
    ) {
        SyncBottomSheetSystemBars()
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            EshretTalkerScreen(
                talker = talker,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun EshretTalkerBottomSheetDragHandle() {
    // Это короткая ручка в верхней части полноэкранного sheet.
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 4.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(EshretTalkerTextSecondary.copy(alpha = 0.24f))
                .height(4.dp)
                .fillMaxWidth(0.18f),
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun SyncBottomSheetSystemBars() {
    // Это текущее view внутри dialog-окна bottom sheet.
    val view = LocalView.current
    // Это текущий context для поиска activity-окна.
    val context = LocalContext.current
    // Это activity, в котором открыт текущий экран.
    val activity = context.findActivity()
    // Это окно dialog'а, в котором живёт bottom sheet.
    val sheetWindow = (view.parent as? DialogWindowProvider)?.window
    // Это основное окно activity, цвет которого нужно сохранить без изменений.
    val activityWindow = activity?.window

    DisposableEffect(sheetWindow, activityWindow, view) {
        if (sheetWindow == null || activityWindow == null) {
            onDispose { }
        } else {
            // Это сохранение исходных цветов sheet-окна на время показа.
            val previousStatusBarColor = sheetWindow.statusBarColor
            val previousNavigationBarColor = sheetWindow.navigationBarColor
            // Это контроллер системных баров для sheet-окна.
            val sheetInsetsController = WindowCompat.getInsetsController(sheetWindow, view)
            // Это контроллер системных баров основного окна.
            val activityInsetsController = WindowCompat.getInsetsController(activityWindow, activityWindow.decorView)
            // Это сохранение исходных режимов иконок на время показа.
            val previousLightStatusBars = sheetInsetsController.isAppearanceLightStatusBars
            val previousLightNavigationBars = sheetInsetsController.isAppearanceLightNavigationBars

            // Это прозрачный фон верхней системной панели поверх bottom sheet.
            sheetWindow.statusBarColor = Color.Transparent.toArgb()
            // Это прозрачный фон нижней системной панели поверх bottom sheet.
            sheetWindow.navigationBarColor = Color.Transparent.toArgb()
            // Это перенос текущего режима иконок системных баров из activity.
            sheetInsetsController.isAppearanceLightStatusBars = activityInsetsController.isAppearanceLightStatusBars
            sheetInsetsController.isAppearanceLightNavigationBars = activityInsetsController.isAppearanceLightNavigationBars

            onDispose {
                sheetWindow.statusBarColor = previousStatusBarColor
                sheetWindow.navigationBarColor = previousNavigationBarColor
                sheetInsetsController.isAppearanceLightStatusBars = previousLightStatusBars
                sheetInsetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    // Это прямой случай, когда context уже является activity.
    is Activity -> this
    // Это обход обёрток context до настоящей activity.
    is ContextWrapper -> baseContext.findActivity()
    // Это защита для неподходящих context'ов.
    else -> null
}
