package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/** What a single-line field accepts. Decimal and password fields keep numbers left-to-right. */
enum class DaftarFieldKind {
    Text,
    Decimal,
    Password,

    /** A visible code such as a recovery key: LTR, capitals, no suggestions or learning by the keyboard. */
    Code,
}

/**
 * Single-line text input with a visible label. [helper] carries the validation message when [invalid]
 * is true, so the error is text as well as colour. Decimal input is LTR; Arabic-Indic digits are
 * normalised by the domain layer, not here.
 */
@Composable
fun DaftarTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    style: Style = Style,
    kind: DaftarFieldKind = DaftarFieldKind.Text,
    placeholder: String? = null,
    helper: String? = null,
    invalid: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val typography = DaftarTheme.typography
    val colors = DaftarTheme.colors
    val keyboardType = when (kind) {
        DaftarFieldKind.Text -> KeyboardType.Text
        DaftarFieldKind.Decimal -> KeyboardType.Decimal
        DaftarFieldKind.Password -> KeyboardType.Password
        DaftarFieldKind.Code -> KeyboardType.Password
    }
    // Amounts read left to right in every language, and a name may be typed in either script, but every value sits at
    // the field's start: an Arabic layout never leaves a gap before English text or a number.
    val textStyle = typography.ledgerValue.copy(
        color = colors.ink,
        textDirection = if (kind == DaftarFieldKind.Decimal || kind == DaftarFieldKind.Code) {
            TextDirection.Ltr
        } else {
            TextDirection.Content
        },
        textAlign = layoutStartAlign(),
    )
    DaftarFieldShell(
        label = label,
        modifier = modifier,
        style = style,
        helper = helper,
        invalid = invalid,
        enabled = enabled,
        interactionSource = interactionSource,
        minHeight = DaftarSpacing.fieldMinHeight,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = if (kind == DaftarFieldKind.Code) {
                KeyboardOptions(
                    keyboardType = keyboardType,
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                )
            } else {
                KeyboardOptions(keyboardType = keyboardType)
            },
            visualTransformation = when (kind) {
                DaftarFieldKind.Password -> PasswordVisualTransformation()
                DaftarFieldKind.Decimal -> DigitGroupingTransformation
                DaftarFieldKind.Text, DaftarFieldKind.Code -> VisualTransformation.None
            },
            interactionSource = interactionSource,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = typography.ledgerValue.copy(textAlign = layoutStartAlign()),
                            color = colors.muted,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** Multi-line note field. It starts at four lines and grows with its content. */
@Composable
fun DaftarTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    style: Style = Style,
    placeholder: String? = null,
    helper: String? = null,
    invalid: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val typography = DaftarTheme.typography
    val colors = DaftarTheme.colors
    DaftarFieldShell(
        label = label,
        modifier = modifier,
        style = style,
        helper = helper,
        invalid = invalid,
        enabled = enabled,
        interactionSource = interactionSource,
        minHeight = DaftarSpacing.textAreaMinHeight,
        contentAlignment = Alignment.TopStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            minLines = 4,
            maxLines = Int.MAX_VALUE,
            textStyle = typography.ledgerValue.copy(color = colors.ink, textAlign = layoutStartAlign()),
            cursorBrush = SolidColor(colors.primary),
            interactionSource = interactionSource,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = typography.ledgerValue.copy(textAlign = layoutStartAlign()),
                            color = colors.muted,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/**
 * A field that opens a picker. [selectedText] null shows [placeholder] as an unselected value.
 * [expanded] turns the chevron upward while the platform picker or option list is open.
 */
@Composable
fun DaftarSelectField(
    label: String,
    selectedText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    placeholder: String = "",
    helper: String? = null,
    invalid: Boolean = false,
    enabled: Boolean = true,
    expanded: Boolean = false,
) = DaftarPickerField(
    label = label,
    text = selectedText,
    placeholder = placeholder,
    icon = DaftarIcons.Chevron,
    onClick = onClick,
    modifier = modifier,
    style = style,
    helper = helper,
    invalid = invalid,
    enabled = enabled,
    rotation = if (expanded) -90f else 90f,
)

/** A date field. It shares the picker shell; the platform date picker opens on tap. */
@Composable
fun DaftarDateField(
    label: String,
    selectedText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    placeholder: String = "",
    helper: String? = null,
    invalid: Boolean = false,
    enabled: Boolean = true,
) = DaftarPickerField(
    label = label,
    text = selectedText,
    placeholder = placeholder,
    icon = DaftarIcons.Calendar,
    onClick = onClick,
    modifier = modifier,
    style = style,
    helper = helper,
    invalid = invalid,
    enabled = enabled,
    rotation = 0f,
)

@Composable
private fun DaftarPickerField(
    label: String,
    text: String?,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    rotation: Float,
    helper: String?,
    invalid: Boolean,
    enabled: Boolean,
    style: Style,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val typography = DaftarTheme.typography
    val colors = DaftarTheme.colors
    DaftarFieldShell(
        label = label,
        modifier = modifier,
        style = style,
        helper = helper,
        invalid = invalid,
        enabled = enabled,
        interactionSource = interactionSource,
        minHeight = DaftarSpacing.fieldMinHeight,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
        ) {
            Text(
                text = text ?: placeholder,
                style = typography.ledgerValue,
                color = if (text == null) colors.muted else colors.ink,
                modifier = Modifier.weight(1f),
            )
            DaftarIcon(
                icon = icon,
                contentDescription = null,
                tint = colors.muted,
                size = DaftarSpacing.smallIconSize,
                modifier = Modifier.graphicsLayer(rotationZ = rotation),
            )
        }
    }
}

/**
 * Shared shell for every field: label, bordered surface with its focus ring, and helper text. Invalid
 * fields thicken their border and announce the helper as an error.
 */
@Composable
internal fun DaftarFieldShell(
    label: String,
    style: Style,
    helper: String?,
    invalid: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    minHeight: Dp,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val shapes = DaftarTheme.shapes
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    val helperText = helper
    Column(modifier = modifier.fillMaxWidth().padding(top = DaftarSpacing.fieldTop)) {
        Text(label, style = typography.fieldLabel, color = colors.ink)
        Spacer(Modifier.height(DaftarSpacing.fieldLabelGap))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .styleable(styleState, DaftarStyles.field, if (invalid) DaftarStyles.invalidField else Style, style)
                .focusRing(styleState.isFocused, shapes.field, colors.gold),
            contentAlignment = contentAlignment,
            content = content,
        )
        if (helperText != null) {
            Spacer(Modifier.height(DaftarSpacing.fieldHelperGap))
            Text(
                text = helperText,
                style = typography.caption,
                color = if (invalid) colors.error else colors.muted,
                modifier = Modifier.semantics { if (invalid) error(helperText) },
            )
        }
    }
}

/** The start edge of the layout as an absolute alignment, so text direction alone never moves a field's value. */
@Composable
private fun layoutStartAlign(): TextAlign =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
