package com.mk.daftar.feature.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarConfirmationContent
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarSettingRow
import com.mk.daftar.core.designsystem.component.DaftarSettingTrailing
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.cloud.CloudAccountState
import com.mk.daftar.core.domain.cloud.CloudBackupVersion
import com.mk.daftar.core.domain.cloud.CloudDownload
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudProvider
import com.mk.daftar.core.domain.cloud.CloudSignInHandoff
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.pageScroll
import com.mk.daftar.feature.cloud.resources.Res
import com.mk.daftar.feature.cloud.resources.cloud_back
import com.mk.daftar.feature.cloud.resources.cloud_cancel
import com.mk.daftar.feature.cloud.resources.cloud_changed
import com.mk.daftar.feature.cloud.resources.cloud_create
import com.mk.daftar.feature.cloud.resources.cloud_delete_account
import com.mk.daftar.feature.cloud.resources.cloud_delete_account_confirm
import com.mk.daftar.feature.cloud.resources.cloud_delete_version
import com.mk.daftar.feature.cloud.resources.cloud_delete_version_confirm
import com.mk.daftar.feature.cloud.resources.cloud_deleted_partial
import com.mk.daftar.feature.cloud.resources.cloud_deleting
import com.mk.daftar.feature.cloud.resources.cloud_download
import com.mk.daftar.feature.cloud.resources.cloud_downloading
import com.mk.daftar.feature.cloud.resources.cloud_empty_title
import com.mk.daftar.feature.cloud.resources.cloud_failed
import com.mk.daftar.feature.cloud.resources.cloud_finish_deletion
import com.mk.daftar.feature.cloud.resources.cloud_intro
import com.mk.daftar.feature.cloud.resources.cloud_loading
import com.mk.daftar.feature.cloud.resources.cloud_manual_note
import com.mk.daftar.feature.cloud.resources.cloud_offline
import com.mk.daftar.feature.cloud.resources.cloud_quota
import com.mk.daftar.feature.cloud.resources.cloud_reauth
import com.mk.daftar.feature.cloud.resources.cloud_recovery_note
import com.mk.daftar.feature.cloud.resources.cloud_refresh
import com.mk.daftar.feature.cloud.resources.cloud_retention
import com.mk.daftar.feature.cloud.resources.cloud_sign_in_apple
import com.mk.daftar.feature.cloud.resources.cloud_sign_in_google
import com.mk.daftar.feature.cloud.resources.cloud_sign_out
import com.mk.daftar.feature.cloud.resources.cloud_signed_out_note
import com.mk.daftar.feature.cloud.resources.cloud_signing_in
import com.mk.daftar.feature.cloud.resources.cloud_title
import com.mk.daftar.feature.cloud.resources.cloud_unavailable
import com.mk.daftar.feature.cloud.resources.cloud_unknown_outcome
import com.mk.daftar.feature.cloud.resources.cloud_unverified
import com.mk.daftar.feature.cloud.resources.cloud_version_size
import com.mk.daftar.feature.cloud.resources.cloud_version_title
import com.mk.daftar.feature.cloud.resources.desktop_cloud_account
import com.mk.daftar.feature.cloud.resources.desktop_cloud_browser_failed
import com.mk.daftar.feature.cloud.resources.desktop_cloud_browser_intro
import com.mk.daftar.feature.cloud.resources.desktop_cloud_declined
import com.mk.daftar.feature.cloud.resources.desktop_cloud_retry
import com.mk.daftar.feature.cloud.resources.desktop_cloud_timeout
import com.mk.daftar.feature.cloud.resources.desktop_cloud_wait_body
import com.mk.daftar.feature.cloud.resources.desktop_cloud_wait_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The cloud screen. [mode] decides what it offers: upload and deletion from Settings, download to restore from the
 * locked recovery surface. A verified download leaves through [onRestore]; an upload request leaves through [onOpenUpload].
 */
@Composable
fun CloudScreen(
    mode: CloudMode,
    onRestore: (CloudDownload) -> Unit,
    onOpenUpload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CloudViewModel = koinViewModel(parameters = { parametersOf(mode) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The page leaves composition while the upload form is on top, so this runs again when it returns.
    LaunchedEffect(viewModel) { viewModel.onIntent(CloudIntent.Returned) }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            CloudEffect.OpenUpload -> onOpenUpload()
            is CloudEffect.RestoreReady -> onRestore(effect.download)
        }
    }
    CloudContent(state = state, onIntent = viewModel::onIntent, onBack = onBack, modifier = modifier)
}

/** The stateless cloud page. Screenshots and tests render it with fake state. */
@Composable
fun CloudContent(
    state: CloudUiState,
    onIntent: (CloudIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(Res.string.cloud_title),
                onBack = if (state.busy == null) onBack else null,
                backLabel = stringResource(DesignRes.string.btn_back),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .pageScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md)
                    .padding(bottom = DaftarSpacing.xl),
            ) {
                Column(
                    modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
                ) {
                    Spacer(Modifier.height(DaftarSpacing.sm))
                    AccountBlock(state)
                    state.problem?.let { ProblemPanel(it, onIntent) }
                    if (state.waitingForBrowser) {
                        BrowserWaiting(onIntent)
                    } else {
                        state.busy?.let { BusyLine(it) }
                        CloudBody(state = state, onIntent = onIntent)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudBody(state: CloudUiState, onIntent: (CloudIntent) -> Unit) {
    when (state.view) {
        CloudView.Unavailable -> DaftarNotePanel(tone = DaftarNoteTone.Context) {
            Text(
                text = stringResource(Res.string.cloud_unavailable),
                style = DaftarTheme.typography.supportingBody,
                color = DaftarTheme.colors.ink,
            )
        }

        CloudView.SignedOut -> SignedOutBody(state, onIntent)

        CloudView.Loading -> LoadingBody()

        CloudView.Empty -> EmptyBody(state, onIntent)

        CloudView.Versions -> VersionsBody(state, onIntent)

        CloudView.DeletionIncomplete -> DeletionIncompleteBody(state, onIntent)

        is CloudView.Problem -> ProblemView(state, onIntent)
    }
}

@Composable
private fun SignedOutBody(state: CloudUiState, onIntent: (CloudIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        IntroText(stringResource(Res.string.cloud_intro))
        if (state.canRestore) {
            IntroText(stringResource(Res.string.cloud_recovery_note))
        }
        if (state.signInHandoff == CloudSignInHandoff.BROWSER) {
            IntroText(stringResource(Res.string.desktop_cloud_browser_intro))
        }
        PrimaryButton(
            onClick = { onIntent(CloudIntent.SignIn) },
            fullWidth = true,
            enabled = state.busy == null,
        ) {
            Text(
                stringResource(
                    if (state.provider == CloudProvider.APPLE) {
                        Res.string.cloud_sign_in_apple
                    } else {
                        Res.string.cloud_sign_in_google
                    },
                ),
            )
        }
    }
}

@Composable
private fun EmptyBody(state: CloudUiState, onIntent: (CloudIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Text(
            text = stringResource(Res.string.cloud_empty_title),
            style = DaftarTheme.typography.sectionHeading,
            color = DaftarTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (state.confirmAccountDelete) AccountConfirmation(onIntent)
        if (state.canUpload) {
            IntroText(stringResource(Res.string.cloud_retention))
            IntroText(stringResource(Res.string.cloud_manual_note))
            PrimaryButton(
                onClick = { onIntent(CloudIntent.OpenUpload) },
                fullWidth = true,
                enabled = state.busy == null,
            ) {
                Text(stringResource(Res.string.cloud_create))
            }
        }
        AccountFooter(state, onIntent)
    }
}

@Composable
private fun VersionsBody(state: CloudUiState, onIntent: (CloudIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        state.pendingVersion?.let { version -> VersionConfirmation(version, onIntent) }
        if (state.confirmAccountDelete) AccountConfirmation(onIntent)
        if (state.canUpload) {
            IntroText(stringResource(Res.string.cloud_retention))
            IntroText(stringResource(Res.string.cloud_manual_note))
        }
        if (state.canRestore) {
            IntroText(stringResource(Res.string.cloud_recovery_note))
        }
        state.versions.forEach { version ->
            VersionRow(version = version, state = state, onIntent = onIntent)
        }
        if (state.canUpload) {
            PrimaryButton(
                onClick = { onIntent(CloudIntent.OpenUpload) },
                fullWidth = true,
                enabled = state.busy == null,
            ) {
                Text(stringResource(Res.string.cloud_create))
            }
        }
        AccountFooter(state, onIntent)
    }
}

@Composable
private fun VersionRow(version: CloudBackupVersion, state: CloudUiState, onIntent: (CloudIntent) -> Unit) {
    val kibibytes = ((version.size + KIBIBYTE - 1) / KIBIBYTE).toString()
    val trailing = if (state.canRestore) {
        DaftarSettingTrailing.Link(
            text = stringResource(Res.string.cloud_download),
            onClick = { onIntent(CloudIntent.Download(version)) },
        )
    } else {
        DaftarSettingTrailing.Link(
            text = stringResource(Res.string.cloud_delete_version),
            onClick = { onIntent(CloudIntent.RequestDeleteVersion(version)) },
        )
    }
    DaftarSettingRow(
        title = stringResource(Res.string.cloud_version_title, version.slot + 1),
        subtitle = stringResource(Res.string.cloud_version_size, kibibytes),
        trailing = trailing,
    )
}

@Composable
private fun VersionConfirmation(version: CloudBackupVersion, onIntent: (CloudIntent) -> Unit) {
    DaftarNotePanel(tone = DaftarNoteTone.Warning) {
        Text(
            text = stringResource(Res.string.cloud_delete_version_confirm),
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.ink,
        )
    }
    DaftarConfirmationContent(
        title = stringResource(Res.string.cloud_version_title, version.slot + 1),
        message = stringResource(Res.string.cloud_delete_version_confirm),
        primaryLabel = stringResource(Res.string.cloud_cancel),
        onPrimaryClick = { onIntent(CloudIntent.DismissConfirmation) },
        destructiveLabel = stringResource(Res.string.cloud_delete_version),
        onDestructiveClick = { onIntent(CloudIntent.ConfirmDeleteVersion) },
    )
}

@Composable
private fun AccountConfirmation(onIntent: (CloudIntent) -> Unit) {
    DaftarConfirmationContent(
        title = stringResource(Res.string.cloud_delete_account),
        message = stringResource(Res.string.cloud_delete_account_confirm),
        primaryLabel = stringResource(Res.string.cloud_cancel),
        onPrimaryClick = { onIntent(CloudIntent.DismissConfirmation) },
        destructiveLabel = stringResource(Res.string.cloud_delete_account),
        onDestructiveClick = { onIntent(CloudIntent.ConfirmDeleteAccount) },
    )
}

@Composable
private fun DeletionIncompleteBody(state: CloudUiState, onIntent: (CloudIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        DaftarNotePanel(tone = DaftarNoteTone.Warning) {
            Text(
                text = stringResource(Res.string.cloud_deleted_partial),
                style = DaftarTheme.typography.supportingBody,
                color = DaftarTheme.colors.ink,
            )
        }
        PrimaryButton(
            onClick = { onIntent(CloudIntent.FinishDeletion) },
            fullWidth = true,
            enabled = state.busy == null,
        ) {
            Text(stringResource(Res.string.cloud_finish_deletion))
        }
        AccountFooter(state, onIntent)
    }
}

@Composable
private fun ProblemView(state: CloudUiState, onIntent: (CloudIntent) -> Unit) {
    OutlineButton(
        onClick = { onIntent(CloudIntent.Refresh) },
        fullWidth = true,
        enabled = state.busy == null,
    ) {
        Text(stringResource(Res.string.cloud_refresh))
    }
}

@Composable
private fun AccountFooter(state: CloudUiState, onIntent: (CloudIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        if (state.view == CloudView.SignedOut || state.view == CloudView.Loading ||
            state.view == CloudView.Unavailable
        ) {
            return
        }
        Spacer(Modifier.height(DaftarSpacing.sm))
        OutlineButton(
            onClick = { onIntent(CloudIntent.SignOut) },
            fullWidth = true,
            enabled = state.busy == null,
        ) {
            Text(stringResource(Res.string.cloud_sign_out))
        }
        IntroText(stringResource(Res.string.cloud_signed_out_note))
        if (state.canUpload && state.accountState == CloudAccountState.ACTIVE) {
            OutlineButton(
                onClick = { onIntent(CloudIntent.RequestDeleteAccount) },
                fullWidth = true,
                enabled = state.busy == null,
            ) {
                Text(stringResource(Res.string.cloud_delete_account))
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = DaftarTheme.colors.primary)
        Spacer(Modifier.height(DaftarSpacing.md))
        Text(
            text = stringResource(Res.string.cloud_loading),
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.muted,
        )
    }
}

@Composable
private fun BusyLine(busy: CloudBusy) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = DaftarTheme.colors.primary)
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            text = stringResource(
                when (busy) {
                    CloudBusy.SIGNING_IN -> Res.string.cloud_signing_in
                    CloudBusy.DOWNLOADING -> Res.string.cloud_downloading
                    CloudBusy.DELETING_VERSION, CloudBusy.DELETING_ACCOUNT -> Res.string.cloud_deleting
                },
            ),
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.muted,
        )
    }
}

/** The signed-in account, shown where the platform reports its email (desktop). Phones never set it. */
@Composable
private fun AccountBlock(state: CloudUiState) {
    val email = state.accountEmail ?: return
    if (state.view == CloudView.SignedOut || state.view == CloudView.Unavailable) return
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xxs)) {
        Text(
            text = stringResource(Res.string.desktop_cloud_account),
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.muted,
        )
        Text(
            text = "\u2066$email\u2069",
            style = DaftarTheme.typography.settingTitle,
            color = DaftarTheme.colors.ink,
        )
    }
}

/** The browser has the sign-in: an indeterminate indicator, what to do there, and a way to stop waiting. */
@Composable
private fun BrowserWaiting(onIntent: (CloudIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = DaftarTheme.colors.primary)
        }
        Text(
            text = stringResource(Res.string.desktop_cloud_wait_title),
            style = DaftarTheme.typography.sectionHeading,
            color = DaftarTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        IntroText(stringResource(Res.string.desktop_cloud_wait_body))
        OutlineButton(onClick = { onIntent(CloudIntent.CancelSignIn) }, fullWidth = true) {
            Text(stringResource(Res.string.cloud_cancel))
        }
    }
}

@Composable
private fun ProblemPanel(error: CloudError, onIntent: (CloudIntent) -> Unit) {
    when (error) {
        // Browser hand-off outcomes are notes above the signed-out content, which keeps its own sign-in action.
        CloudError.TIMED_OUT, CloudError.DECLINED -> {
            DaftarNotePanel(tone = DaftarNoteTone.Context) {
                Text(
                    text = stringResource(
                        if (error == CloudError.TIMED_OUT) {
                            Res.string.desktop_cloud_timeout
                        } else {
                            Res.string.desktop_cloud_declined
                        },
                    ),
                    style = DaftarTheme.typography.supportingBody,
                    color = DaftarTheme.colors.ink,
                )
            }
            return
        }

        CloudError.BROWSER_UNAVAILABLE -> {
            DaftarNotePanel(tone = DaftarNoteTone.Error) {
                Text(
                    text = stringResource(Res.string.desktop_cloud_browser_failed),
                    style = DaftarTheme.typography.supportingBody,
                    color = DaftarTheme.colors.ink,
                )
            }
            PrimaryButton(onClick = { onIntent(CloudIntent.SignIn) }, fullWidth = true) {
                Text(stringResource(Res.string.desktop_cloud_retry))
            }
            OutlineButton(onClick = { onIntent(CloudIntent.DismissProblem) }, fullWidth = true) {
                Text(stringResource(Res.string.cloud_cancel))
            }
            return
        }

        else -> Unit
    }
    val message = problemMessage(error) ?: return
    DaftarNotePanel(tone = DaftarNoteTone.Error) {
        Text(
            text = stringResource(message),
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.ink,
        )
    }
    OutlineButton(onClick = { onIntent(CloudIntent.DismissProblem) }, fullWidth = true) {
        Text(stringResource(Res.string.cloud_back))
    }
}

/** The message for a failure, or null when the failure is not shown (a cancelled sign-in). */
private fun problemMessage(error: CloudError): org.jetbrains.compose.resources.StringResource? = when (error) {
    CloudError.CANCELLED -> null
    CloudError.OFFLINE -> Res.string.cloud_offline
    CloudError.QUOTA_EXCEEDED -> Res.string.cloud_quota
    CloudError.CHANGED -> Res.string.cloud_changed
    CloudError.UNKNOWN_OUTCOME -> Res.string.cloud_unknown_outcome
    CloudError.UNVERIFIED -> Res.string.cloud_unverified
    CloudError.REAUTH_REQUIRED -> Res.string.cloud_reauth
    CloudError.UNAVAILABLE -> Res.string.cloud_unavailable
    CloudError.NOT_SIGNED_IN, CloudError.OVERSIZE, CloudError.DELETING, CloudError.FAILED -> Res.string.cloud_failed
    CloudError.TIMED_OUT -> Res.string.desktop_cloud_timeout
    CloudError.BROWSER_UNAVAILABLE -> Res.string.desktop_cloud_browser_failed
    CloudError.DECLINED -> Res.string.desktop_cloud_declined
}

@Composable
private fun IntroText(text: String) {
    Text(text = text, style = DaftarTheme.typography.supportingBody, color = DaftarTheme.colors.muted)
}

private const val KIBIBYTE = 1024
private val CONTENT_MAX_WIDTH = 560.dp
