package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/** Tone of a [DaftarResultState]: a completed action, or a failed one with a recovery path. */
enum class DaftarResultTone {
    Success,
    Error,
}

/**
 * A screen with no content yet, such as an empty asset category. It offers one way forward through
 * [actionLabel] and [onAction].
 */
@Composable
fun DaftarEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = DaftarIcons.Book,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 40.dp, bottom = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DaftarEmblem(icon = icon, contentDescription = null)
        Spacer(Modifier.height(22.dp))
        Text(
            title,
            style = DaftarTheme.typography.sectionHeading,
            color = DaftarTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DaftarSpacing.xs))
        Text(
            message,
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DaftarSpacing.lg))
        PrimaryButton(onClick = onAction, fullWidth = true, leadingIcon = DaftarIcons.Plus) {
            Text(actionLabel)
        }
    }
}

/**
 * A success or failure message for a whole screen. Failures name what to do next through [primaryLabel]
 * and an optional [secondaryLabel]; the message stays in text so it is read aloud.
 */
@Composable
fun DaftarResultState(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    tone: DaftarResultTone = DaftarResultTone.Success,
    icon: ImageVector = if (tone == DaftarResultTone.Error) DaftarIcons.Alert else DaftarIcons.Check,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val colors = DaftarTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 40.dp, bottom = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DaftarEmblem(
            icon = icon,
            contentDescription = null,
            tint = if (tone == DaftarResultTone.Error) colors.error else colors.primary,
        )
        Spacer(Modifier.height(22.dp))
        Text(title, style = DaftarTheme.typography.sectionHeading, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(DaftarSpacing.xs))
        Text(message, style = DaftarTheme.typography.supportingBody, color = colors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(DaftarSpacing.lg))
        PrimaryButton(onClick = onPrimary, fullWidth = true) { Text(primaryLabel) }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(DaftarSpacing.xs))
            DaftarTextButton(onClick = onSecondary, fullWidth = true) { Text(secondaryLabel) }
        }
    }
}

/** How long a [DaftarSkeleton] line is: a full line, a heavier block or a short line. */
enum class DaftarSkeletonSize {
    Normal,
    Big,
    Short,
}

/**
 * A loading placeholder shaped like a line of text. It is decorative: it is hidden from accessibility, and the
 * screen announces its own loading state. Existing values stay visible while a refresh runs.
 */
@Composable
fun DaftarSkeleton(modifier: Modifier = Modifier, size: DaftarSkeletonSize = DaftarSkeletonSize.Normal) {
    val styleState = remember { MutableStyleState(null) }
    val (height, fraction) = when (size) {
        DaftarSkeletonSize.Normal -> 18.dp to 0.8f
        DaftarSkeletonSize.Big -> 45.dp to 0.95f
        DaftarSkeletonSize.Short -> 18.dp to 0.5f
    }
    Box(
        modifier = modifier
            .fillMaxWidth(fraction)
            .padding(vertical = 16.dp)
            .clearAndSetSemantics { },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .styleable(styleState, DaftarStyles.skeleton),
        )
    }
}

/** A round emblem holding an icon; used by the empty and result states. */
@Composable
internal fun DaftarEmblem(
    icon: ImageVector,
    contentDescription: String?,
    tint: androidx.compose.ui.graphics.Color = DaftarTheme.colors.primary,
) {
    val styleState = remember { MutableStyleState(null) }
    Box(
        modifier = Modifier
            .size(DaftarSpacing.emblemSize)
            .styleable(styleState, DaftarStyles.emblem),
        contentAlignment = Alignment.Center,
    ) {
        DaftarIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = tint,
            size = DaftarSpacing.emblemIconSize,
        )
    }
}
