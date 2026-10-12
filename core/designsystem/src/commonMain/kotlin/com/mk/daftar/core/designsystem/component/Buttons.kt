package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/**
 * Filled call to action ("Save changes"). While [loading] the button stays visible with its saving label
 * but ignores taps, so a save cannot be submitted twice.
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    fullWidth: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) = DaftarButton(
    onClick = onClick,
    baseStyle = DaftarStyles.primaryButton,
    contentColor = DaftarTheme.colors.onPrimary,
    style = style,
    enabled = enabled && !loading,
    fullWidth = fullWidth,
    leadingIcon = leadingIcon,
    modifier = modifier,
    content = content,
)

/** Secondary action with a hairline border. */
@Composable
fun OutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    fullWidth: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) = DaftarButton(
    onClick = onClick,
    baseStyle = DaftarStyles.outlineButton,
    contentColor = DaftarTheme.colors.ink,
    style = style,
    enabled = enabled,
    fullWidth = fullWidth,
    leadingIcon = leadingIcon,
    modifier = modifier,
    content = content,
)

/** Inline text action ("Why?", "Show all"). */
@Composable
fun DaftarTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    fullWidth: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) = DaftarButton(
    onClick = onClick,
    baseStyle = DaftarStyles.textButton,
    contentColor = DaftarTheme.colors.primary,
    style = style,
    enabled = enabled,
    fullWidth = fullWidth,
    leadingIcon = leadingIcon,
    modifier = modifier,
    content = content,
)

/** A 48dp icon-only button. [contentDescription] names it for screen readers. */
@Composable
fun DaftarIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
    mirrorInRtl: Boolean = false,
) {
    val colors = DaftarTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    Box(
        modifier = modifier
            .size(DaftarSpacing.touch)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .styleable(styleState, DaftarStyles.iconButton, style)
            .focusRing(styleState.isFocused, DaftarTheme.shapes.round, colors.gold),
        contentAlignment = Alignment.Center,
    ) {
        DaftarIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = colors.ink,
            mirrorInRtl = mirrorInRtl,
        )
    }
}

@Composable
private fun DaftarButton(
    onClick: () -> Unit,
    baseStyle: Style,
    contentColor: Color,
    style: Style,
    enabled: Boolean,
    fullWidth: Boolean,
    leadingIcon: ImageVector?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    val shape = DaftarTheme.shapes.control
    ProvideContent(contentColor, DaftarTheme.typography.buttonLabel) {
        Row(
            modifier = modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .defaultMinSize(minHeight = DaftarSpacing.buttonMinHeight)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .styleable(styleState, baseStyle, style)
                .focusRing(styleState.isFocused, shape, DaftarTheme.colors.gold),
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) DaftarIcon(leadingIcon, contentDescription = null)
            content()
        }
    }
}
