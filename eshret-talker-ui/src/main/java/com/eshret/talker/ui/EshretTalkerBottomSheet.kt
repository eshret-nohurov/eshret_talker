package com.eshret.talker.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.eshret.talker.core.EshretTalker

// Этот файл описывает полноэкранный bottom sheet библиотеки eshret_talker.
// Здесь журнал сразу открывается на весь экран и синхронизирует системные бары с окном приложения.

@Composable
fun EshretTalkerBottomSheet(
    // Это экземпляр логгера, который нужно показать.
    talker: EshretTalker,
    // Это callback закрытия sheet.
    onDismiss: () -> Unit,
) {
    // Это верхний отступ под системную статус-панель для самого контейнера sheet.
    val topSystemBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        SyncBottomSheetSystemBars()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EshretTalkerSheetScrimColor),
        ) {
            EshretTalkerScreen(
                talker = talker,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topSystemBarPadding),
                respectStatusBarInsets = false,
            )
        }
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
            val previousWindowBackground = sheetWindow.decorView.background
            val previousLayoutWidth = sheetWindow.attributes.width
            val previousLayoutHeight = sheetWindow.attributes.height
            val previousNavigationBarContrastEnforced =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    sheetWindow.isNavigationBarContrastEnforced
                } else {
                    null
                }
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
            // Это прозрачный фон самого dialog-окна, чтобы системные панели не подхватывали подложку окна.
            sheetWindow.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            sheetWindow.decorView.setBackgroundColor(AndroidColor.TRANSPARENT)
            sheetWindow.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            // Это включение edge-to-edge у dialog-окна sheet, чтобы контент мог просвечивать под навигационной панелью.
            WindowCompat.setDecorFitsSystemWindows(sheetWindow, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                sheetWindow.isNavigationBarContrastEnforced = false
            }
            // Это перенос текущего режима иконок системных баров из activity.
            sheetInsetsController.isAppearanceLightStatusBars = activityInsetsController.isAppearanceLightStatusBars
            sheetInsetsController.isAppearanceLightNavigationBars = activityInsetsController.isAppearanceLightNavigationBars

            onDispose {
                sheetWindow.statusBarColor = previousStatusBarColor
                sheetWindow.navigationBarColor = previousNavigationBarColor
                sheetWindow.setBackgroundDrawable(
                    (previousWindowBackground as? ColorDrawable) ?: ColorDrawable(AndroidColor.TRANSPARENT),
                )
                sheetWindow.decorView.background = previousWindowBackground
                sheetWindow.setLayout(previousLayoutWidth, previousLayoutHeight)
                WindowCompat.setDecorFitsSystemWindows(sheetWindow, true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    sheetWindow.isNavigationBarContrastEnforced =
                        previousNavigationBarContrastEnforced ?: true
                }
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
