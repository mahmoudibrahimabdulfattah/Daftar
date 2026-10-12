package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/**
 * Single choice among a few equal options, such as EGP / USD or Light / Dark. Segments share one bordered
 * strip with hairline dividers. The selected segment is soft with primary, semibold text.
 */
@Composable
fun DaftarSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
) {
    val colors = DaftarTheme.colors
    val styleState = rememberUpdatedStyleState(null) { it.isEnabled = enabled }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .styleable(styleState, DaftarStyles.segmentedControl, style),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
            }
            DaftarSegment(
                label = option,
                selected = index == selectedIndex,
                enabled = enabled,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun RowScope.DaftarSegment(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) {
        it.isSelected = selected
        it.isEnabled = enabled
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .styleable(styleState, DaftarStyles.segment)
            .focusRing(styleState.isFocused, RectangleShape, colors.gold),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = typography.segmentLabel.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) colors.primary else colors.ink,
        )
    }
}
