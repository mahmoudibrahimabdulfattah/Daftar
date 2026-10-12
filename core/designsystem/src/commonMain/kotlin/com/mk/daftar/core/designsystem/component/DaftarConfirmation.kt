package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/**
 * The body of a confirmation sheet or dialog for deleting, discarding, replacing or a blocked action. The safe
 * action is primary and always present. A destructive action, when given, is outlined rather than red, so the
 * safe choice stays the visual default. Blocked confirmations pass no destructive action.
 */
@Composable
fun DaftarConfirmationContent(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructiveLabel: String? = null,
    onDestructiveClick: (() -> Unit)? = null,
) {
    val typography = DaftarTheme.typography
    val colors = DaftarTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = typography.sectionHeading,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(message, style = typography.supportingBody, color = colors.muted)
        PrimaryButton(onClick = onPrimaryClick, fullWidth = true) { Text(primaryLabel) }
        if (destructiveLabel != null && onDestructiveClick != null) {
            OutlineButton(onClick = onDestructiveClick, fullWidth = true) { Text(destructiveLabel) }
        }
    }
}
