package com.mk.daftar.core.designsystem.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** Sets the colour and text style that `Text` and `Icon` inside [content] pick up. */
@Composable
fun ProvideContent(color: Color, textStyle: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides color,
        LocalTextStyle provides textStyle,
        content = content,
    )
}
