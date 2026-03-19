package com.eshret.talker.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eshret.talker.core.EshretTalker

// Этот файл описывает bottom sheet библиотеки eshret_talker.
// Здесь мы упаковываем основной экран логов в удобный модальный формат.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EshretTalkerBottomSheet(
    // Это экземпляр логгера, который нужно показать.
    talker: EshretTalker,
    // Это callback закрытия sheet.
    onDismiss: () -> Unit,
) {
    // Это модальный нижний лист со всем экраном логов внутри.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(),
        containerColor = EshretTalkerScreenColor,
    ) {
        EshretTalkerScreen(
            talker = talker,
            modifier = Modifier.fillMaxHeight(),
        )
    }
}
