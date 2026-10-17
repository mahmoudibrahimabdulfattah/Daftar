package com.mk.daftar.core.ui.shell

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarTheme

@Composable
actual fun PageScrollbar(state: ScrollState, modifier: Modifier) {
    if (state.maxValue == 0) return
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val thickness = if (hovered || dragged) 8.dp else 4.dp
    val colors = DaftarTheme.colors
    // The 8dp gutter is reserved whether or not the thumb is wide, so hovering never moves anything.
    Box(modifier = modifier.width(8.dp), contentAlignment = Alignment.Center) {
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier.fillMaxHeight().width(thickness),
            interactionSource = interaction,
            style = ScrollbarStyle(
                minimalHeight = 32.dp,
                thickness = thickness,
                shape = RoundedCornerShape(4.dp),
                hoverDurationMillis = 150,
                unhoverColor = colors.line,
                hoverColor = colors.muted,
            ),
        )
    }
}
