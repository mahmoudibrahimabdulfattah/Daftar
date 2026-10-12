package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/**
 * On/off switch: a 47 × 28dp track with a 20dp thumb, inside a 48dp touch target. [label] names the switch
 * for screen readers; the thumb itself is decorative.
 */
@Composable
fun DaftarSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
) {
    val colors = DaftarTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) {
        it.isSelected = checked
        it.isEnabled = enabled
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = label }
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 47.dp, height = 28.dp)
                .styleable(styleState, DaftarStyles.switchTrack, style)
                .focusRing(styleState.isFocused, DaftarTheme.shapes.pill, colors.gold),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(if (checked) colors.onPrimary else colors.surface, DaftarTheme.shapes.round),
            )
        }
    }
}
