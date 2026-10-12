package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/** Default tiles are olive on soft; gold tiles mark gold holdings only. */
enum class DaftarTileTone {
    Default,
    Gold,
}

/**
 * A row in a list of holdings or settings: a tile icon, title and subtitle, then a value. [value] may wrap to a
 * second line at large font sizes. With [onClick] the row is a button, shows a chevron and a focus ring.
 */
@Composable
fun DaftarLedgerRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tone: DaftarTileTone = DaftarTileTone.Default,
    value: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    style: Style = Style,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isSelected = selected }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .styleable(styleState, DaftarStyles.ledgerRow, style)
                .focusRing(styleState.isFocused, DaftarTheme.shapes.control, colors.gold),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) DaftarTileIcon(icon = icon, tone = tone, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = typography.ledgerTitle, color = colors.ink)
                if (subtitle != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, style = typography.rowSubtitle, color = colors.muted)
                }
            }
            if (value != null) {
                Box(
                    modifier = Modifier.weight(1f, fill = false),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    ProvideContent(colors.ink, typography.ledgerValue.copy(textAlign = TextAlign.End)) {
                        value()
                    }
                }
            }
            if (onClick != null) {
                DaftarIcon(
                    icon = DaftarIcons.Chevron,
                    contentDescription = null,
                    tint = colors.muted,
                    mirrorInRtl = true,
                )
            }
        }
        HorizontalDivider(color = colors.line, thickness = 1.dp)
    }
}

/** The 38dp rounded tile that carries a row's icon. Gold tiles are for gold rows only. */
@Composable
fun DaftarTileIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tone: DaftarTileTone = DaftarTileTone.Default,
) {
    val colors = DaftarTheme.colors
    val gold = tone == DaftarTileTone.Gold
    val styleState = rememberUpdatedStyleState(null)
    Box(
        modifier = modifier
            .size(DaftarSpacing.tileSize)
            .styleable(styleState, if (gold) DaftarStyles.goldTileIcon else DaftarStyles.tileIcon),
        contentAlignment = Alignment.Center,
    ) {
        DaftarIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = if (gold) colors.gold else colors.primary,
            size = DaftarSpacing.tileIconSize,
        )
    }
}
