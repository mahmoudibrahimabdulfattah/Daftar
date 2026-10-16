package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/** The trailing control of a [DaftarSettingRow]. */
sealed interface DaftarSettingTrailing {
    /** A read-only current value, such as "Light" or "1 minute". */
    data class Value(val text: String) : DaftarSettingTrailing

    /** A value that opens another screen or action, such as "Change". */
    data class Link(val text: String, val onClick: () -> Unit) : DaftarSettingTrailing

    /** A toggle; [label] names it for screen readers. */
    data class Switch(
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val label: String,
        val enabled: Boolean = true,
    ) : DaftarSettingTrailing
}

/**
 * A preference row: title and subtitle, a trailing value, link or switch, and a hairline divider.
 * Only the link and switch are touch targets; the row itself does not take taps.
 */
@Composable
fun DaftarSettingRow(
    title: String,
    subtitle: String,
    trailing: DaftarSettingTrailing,
    modifier: Modifier = Modifier,
    style: Style = Style,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val styleState = rememberUpdatedStyleState(null)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 65.dp)
                .styleable(styleState, DaftarStyles.settingRow, style),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = typography.settingTitle, color = colors.ink)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = typography.rowSubtitle, color = colors.muted)
            }
            when (trailing) {
                is DaftarSettingTrailing.Value -> Text(
                    text = trailing.text,
                    style = typography.fineText,
                    color = colors.ink,
                    modifier = Modifier.weight(1f, fill = false),
                )

                is DaftarSettingTrailing.Link -> SettingLink(trailing.text, trailing.onClick)

                is DaftarSettingTrailing.Switch -> DaftarSwitch(
                    checked = trailing.checked,
                    onCheckedChange = trailing.onCheckedChange,
                    label = trailing.label,
                    enabled = trailing.enabled,
                )
            }
        }
        HorizontalDivider(color = colors.line, thickness = 1.dp)
    }
}

@Composable
private fun SettingLink(text: String, onClick: () -> Unit) {
    val colors = DaftarTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .heightIn(min = DaftarSpacing.touch)
            .actionCursor().clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = DaftarTheme.typography.fineText, color = colors.primary)
    }
}
