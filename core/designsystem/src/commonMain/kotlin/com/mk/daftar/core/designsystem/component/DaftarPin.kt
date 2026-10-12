package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

private const val DOT = "•"

/**
 * The PIN entry display. It shows one dot per digit entered, never the digits themselves: the field's
 * accessibility output is replaced by [stateDescription], such as "Three of six digits entered".
 * [placeholder] shows in muted text while empty. [invalid] draws the error border.
 */
@Composable
fun DaftarPinField(
    enteredCount: Int,
    stateDescription: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    invalid: Boolean = false,
    style: Style = Style,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val styleState = rememberUpdatedStyleState(null) { it.isEnabled = enabled }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DaftarSpacing.pinFieldHeight)
            .clearAndSetSemantics { contentDescription = stateDescription }
            .styleable(
                styleState,
                DaftarStyles.pinField,
                if (invalid) DaftarStyles.invalidField else Style,
                style,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            if (enteredCount == 0) {
                Text(placeholder, style = typography.pinDigit, color = colors.muted)
            } else {
                Text(
                    text = DOT.repeat(enteredCount),
                    style = typography.pinDigit.copy(letterSpacing = 6.sp),
                    color = if (invalid) colors.error else colors.ink,
                )
            }
        }
    }
}

/**
 * The 1–9, 0 keypad, always laid out left-to-right. The bottom-left slot holds a biometric key when
 * [onBiometric] is set, and is blank otherwise; the bottom-right key erases the last digit. Keys are at least
 * 58dp tall. Digits are announced as numbers and nothing else.
 */
@Composable
fun DaftarKeypad(
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    eraseLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    biometricLabel: String = "",
    onBiometric: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
        ) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { digits ->
                Row(horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
                    digits.forEach { digit ->
                        KeypadKey(enabled = enabled, onClick = { onDigit(digit) }) {
                            Text(digit.toString(), style = DaftarTheme.typography.pinDigit)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
                if (onBiometric != null) {
                    KeypadKey(enabled = enabled, onClick = onBiometric) {
                        DaftarIcon(
                            icon = DaftarIcons.Fingerprint,
                            contentDescription = biometricLabel,
                            tint = DaftarTheme.colors.primary,
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f).defaultMinSize(minHeight = DaftarSpacing.keypadKeyMinHeight))
                }
                KeypadKey(enabled = enabled, onClick = { onDigit(0) }) {
                    Text("0", style = DaftarTheme.typography.pinDigit)
                }
                KeypadKey(enabled = enabled, onClick = onErase) {
                    DaftarIcon(
                        icon = DaftarIcons.Backspace,
                        contentDescription = eraseLabel,
                        tint = DaftarTheme.colors.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeypadKey(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = DaftarTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    Box(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = DaftarSpacing.keypadKeyMinHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .styleable(styleState, DaftarStyles.keypadKey)
            .focusRing(styleState.isFocused, DaftarTheme.shapes.surface, colors.gold),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
