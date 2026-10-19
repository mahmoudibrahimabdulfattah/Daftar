package com.mk.daftar.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mk.daftar.core.designsystem.component.DaftarCard
import com.mk.daftar.core.designsystem.component.DaftarConfirmationContent
import com.mk.daftar.core.designsystem.component.DaftarMark
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.update.UpdateStatus
import com.mk.daftar.shared.resources.Res
import com.mk.daftar.shared.resources.update_action
import com.mk.daftar.shared.resources.update_checking
import com.mk.daftar.shared.resources.update_force_body
import com.mk.daftar.shared.resources.update_force_title
import com.mk.daftar.shared.resources.update_later
import com.mk.daftar.shared.resources.update_retry
import com.mk.daftar.shared.resources.update_retry_failed
import com.mk.daftar.shared.resources.update_soft_body
import com.mk.daftar.shared.resources.update_soft_body_version
import com.mk.daftar.shared.resources.update_soft_title
import com.mk.daftar.shared.resources.update_still_required
import com.mk.daftar.shared.resources.update_store_failed
import org.jetbrains.compose.resources.stringResource

/**
 * The screen shown while a forced update is required. It has no navigation and no dismiss: the only actions are the
 * store and a re-check.
 */
@Composable
internal fun ForceUpdateScreen(
    retry: RetryOutcome,
    storeFailed: Boolean,
    onUpdate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val checking = retry == RetryOutcome.Checking
    val result = when {
        storeFailed -> stringResource(Res.string.update_store_failed)
        retry == RetryOutcome.Failed -> stringResource(Res.string.update_retry_failed)
        retry == RetryOutcome.StillRequired -> stringResource(Res.string.update_still_required)
        else -> null
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DaftarMark(Modifier.size(56.dp))
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(Res.string.update_force_title),
                    style = typography.screenHeading,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.update_force_body),
                    style = typography.supportingBody,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                PrimaryButton(onClick = onUpdate, fullWidth = true) {
                    Text(stringResource(Res.string.update_action))
                }
                Spacer(Modifier.height(12.dp))
                OutlineButton(onClick = onRetry, enabled = !checking, fullWidth = true) {
                    Text(stringResource(if (checking) Res.string.update_checking else Res.string.update_retry))
                }
                if (result != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = result,
                        style = typography.supportingBody,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** A dismissable prompt over the open vault. Later, back and an outside tap all postpone it for seven days. */
@Composable
internal fun SoftUpdateDialog(
    status: UpdateStatus.Soft,
    storeFailed: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    val colors = DaftarTheme.colors
    Dialog(onDismissRequest = onLater) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            DaftarCard(modifier = Modifier.widthIn(max = 360.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DaftarConfirmationContent(
                        title = stringResource(Res.string.update_soft_title),
                        message = if (status.latestName.isBlank()) {
                            stringResource(Res.string.update_soft_body)
                        } else {
                            stringResource(Res.string.update_soft_body_version, status.latestName)
                        },
                        primaryLabel = stringResource(Res.string.update_action),
                        onPrimaryClick = onUpdate,
                    )
                    if (storeFailed) {
                        Text(
                            text = stringResource(Res.string.update_store_failed),
                            style = DaftarTheme.typography.supportingBody,
                            color = colors.muted,
                        )
                    }
                    OutlineButton(onClick = onLater, fullWidth = true) {
                        Text(stringResource(Res.string.update_later))
                    }
                }
            }
        }
    }
}
