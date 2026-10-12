package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/**
 * The save or confirm area pinned under a form. It is a sibling of the scrolling content, not part of it.
 * The screen that hosts it applies IME and navigation-bar insets to the whole column, so the footer and the
 * focused field move up together without a second inset here.
 */
@Composable
fun DaftarFooterAction(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = DaftarTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.paper)) {
        HorizontalDivider(color = colors.line, thickness = 1.dp)
        Column(
            modifier = Modifier.padding(horizontal = DaftarSpacing.lg, vertical = DaftarSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
            content = content,
        )
    }
}
