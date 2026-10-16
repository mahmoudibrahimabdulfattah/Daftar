package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/**
 * Bottom navigation for compact windows. Put four [DaftarNavigationItem]s in [content]; the selected one gets
 * a soft pill behind its icon and a primary, semibold label.
 */
@Composable
fun DaftarNavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = DaftarTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.line, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DaftarSpacing.xs, end = DaftarSpacing.xs, top = 9.dp),
            content = content,
        )
    }
}

/** One destination in a [DaftarNavigationBar]; use it inside the bar's row. */
@Composable
fun RowScope.DaftarNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isSelected = selected }
    val indicatorState = rememberUpdatedStyleState(null) { it.isSelected = selected }
    Column(
        modifier = modifier
            .weight(1f)
            .defaultMinSize(minHeight = DaftarSpacing.navItemMinHeight)
            .actionCursor().selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .focusRing(styleState.isFocused, DaftarTheme.shapes.control, colors.gold)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 30.dp)
                .styleable(indicatorState, DaftarStyles.navIndicator),
            contentAlignment = Alignment.Center,
        ) {
            DaftarIcon(
                icon = icon,
                contentDescription = null,
                tint = if (selected) colors.primary else colors.muted,
            )
        }
        Text(
            text = label,
            style = typography.navLabel.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) colors.primary else colors.muted,
        )
    }
}

/** Side navigation for expanded windows: a 90dp rail with [DaftarNavigationRailItem]s in [content]. */
@Composable
fun DaftarNavigationRail(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = DaftarTheme.colors
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(DaftarSpacing.railWidth)
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        VerticalDivider(color = colors.line, thickness = 1.dp)
    }
}

/** One destination in a [DaftarNavigationRail], labelled under its icon. */
@Composable
fun ColumnScope.DaftarNavigationRailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isSelected = selected }
    Column(
        modifier = modifier
            .width(DaftarSpacing.railItemWidth)
            .defaultMinSize(minHeight = DaftarSpacing.railItemMinHeight)
            .actionCursor().selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .styleable(styleState, DaftarStyles.railItem)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        DaftarIcon(
            icon = icon,
            contentDescription = null,
            tint = if (selected) colors.primary else colors.muted,
        )
        Text(
            text = label,
            style = typography.navLabel,
            color = if (selected) colors.primary else colors.muted,
        )
    }
}
