package com.mk.daftar.feature.backup.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.backup.BackupPasswordProblem
import com.mk.daftar.core.designsystem.component.DaftarFieldKind
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.cloud.CloudBackupRepository
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudProvider
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.feature.backup.resources.Res
import com.mk.daftar.feature.backup.resources.backup_confirm_label
import com.mk.daftar.feature.backup.resources.backup_confirm_mismatch
import com.mk.daftar.feature.backup.resources.backup_password_hint
import com.mk.daftar.feature.backup.resources.backup_password_invalid
import com.mk.daftar.feature.backup.resources.backup_password_label
import com.mk.daftar.feature.backup.resources.backup_password_too_long
import com.mk.daftar.feature.backup.resources.backup_password_too_short
import com.mk.daftar.feature.backup.resources.backup_password_warning
import com.mk.daftar.feature.backup.resources.backup_submit
import com.mk.daftar.feature.backup.resources.backup_success_done
import com.mk.daftar.feature.backup.resources.backup_title
import com.mk.daftar.feature.backup.resources.cloud_sign_in_apple
import com.mk.daftar.feature.backup.resources.cloud_sign_in_google
import com.mk.daftar.feature.backup.resources.cloud_upload_changed
import com.mk.daftar.feature.backup.resources.cloud_upload_committing
import com.mk.daftar.feature.backup.resources.cloud_upload_failed
import com.mk.daftar.feature.backup.resources.cloud_upload_offline
import com.mk.daftar.feature.backup.resources.cloud_upload_oversize
import com.mk.daftar.feature.backup.resources.cloud_upload_preparing
import com.mk.daftar.feature.backup.resources.cloud_upload_quota
import com.mk.daftar.feature.backup.resources.cloud_upload_retention
import com.mk.daftar.feature.backup.resources.cloud_upload_signin_intro
import com.mk.daftar.feature.backup.resources.cloud_upload_unknown
import com.mk.daftar.feature.backup.resources.cloud_upload_uploaded
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** The cloud upload form. Closing it returns to the cloud screen through the navigator. */
@Composable
fun CloudUploadScreen(modifier: Modifier = Modifier) {
    val viewModel: CloudUploadViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val repository: CloudBackupRepository = koinInject()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            CloudUploadEffect.Close -> navigator.goBack()
        }
    }
    CloudUploadContent(
        state = state,
        onIntent = viewModel::onIntent,
        provider = repository.provider,
        modifier = modifier,
    )
}

/** The stateless upload page. Screenshots render it with fake state. */
@Composable
fun CloudUploadContent(
    state: CloudUploadUiState,
    onIntent: (CloudUploadIntent) -> Unit,
    provider: CloudProvider,
    modifier: Modifier = Modifier,
) {
    val busy = state.operation == CloudUploadOperation.Preparing || state.operation == CloudUploadOperation.Committing
    ShellScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaftarTopBar(
                title = stringResource(Res.string.backup_title),
                onBack = if (busy) null else ({ onIntent(CloudUploadIntent.Back) }),
                backLabel = stringResource(DesignRes.string.btn_back),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md)
                    .padding(bottom = DaftarSpacing.xl),
            ) {
                Column(
                    modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
                ) {
                    when (val operation = state.operation) {
                        CloudUploadOperation.Idle -> when (state.step) {
                            CloudUploadStep.SIGN_IN -> SignInBody(state, provider, onIntent)
                            CloudUploadStep.PASSWORD -> PasswordBody(state, onIntent)
                        }

                        CloudUploadOperation.Preparing -> BusyBody(stringResource(Res.string.cloud_upload_preparing))

                        CloudUploadOperation.Committing -> BusyBody(stringResource(Res.string.cloud_upload_committing))

                        is CloudUploadOperation.Complete -> DaftarResultState(
                            title = stringResource(Res.string.cloud_upload_uploaded),
                            message = stringResource(Res.string.cloud_upload_retention),
                            primaryLabel = stringResource(Res.string.backup_success_done),
                            onPrimary = { onIntent(CloudUploadIntent.Acknowledge) },
                        )

                        is CloudUploadOperation.Failed -> DaftarResultState(
                            title = stringResource(Res.string.cloud_upload_failed),
                            message = stringResource(failureMessage(operation.error)),
                            primaryLabel = stringResource(Res.string.backup_success_done),
                            onPrimary = { onIntent(CloudUploadIntent.Acknowledge) },
                            tone = DaftarResultTone.Error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignInBody(state: CloudUploadUiState, provider: CloudProvider, onIntent: (CloudUploadIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            text = stringResource(Res.string.cloud_upload_signin_intro),
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.muted,
        )
        state.signInProblem?.let { error ->
            DaftarNotePanel(tone = DaftarNoteTone.Error) {
                Text(
                    text = stringResource(failureMessage(error)),
                    style = DaftarTheme.typography.supportingBody,
                    color = DaftarTheme.colors.ink,
                )
            }
        }
        PrimaryButton(onClick = { onIntent(CloudUploadIntent.SignIn) }, fullWidth = true, enabled = !state.signingIn) {
            Text(
                stringResource(
                    if (provider ==
                        CloudProvider.APPLE
                    ) {
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
private fun PasswordBody(state: CloudUploadUiState, onIntent: (CloudUploadIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        val colors = DaftarTheme.colors
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            text = stringResource(Res.string.cloud_upload_retention),
            style = DaftarTheme.typography.supportingBody,
            color = colors.ink,
        )
        DaftarTextField(
            value = state.password,
            onValueChange = { onIntent(CloudUploadIntent.PasswordChanged(it)) },
            label = stringResource(Res.string.backup_password_label),
            kind = DaftarFieldKind.Password,
            helper = passwordHelper(state),
            invalid = state.showPasswordError,
        )
        DaftarTextField(
            value = state.confirm,
            onValueChange = { onIntent(CloudUploadIntent.ConfirmChanged(it)) },
            label = stringResource(Res.string.backup_confirm_label),
            kind = DaftarFieldKind.Password,
            helper = if (state.confirmMismatch) stringResource(Res.string.backup_confirm_mismatch) else null,
            invalid = state.confirmMismatch,
        )
        DaftarNotePanel(tone = DaftarNoteTone.Warning) {
            Text(
                text = stringResource(Res.string.backup_password_warning),
                style = DaftarTheme.typography.supportingBody,
                color = colors.ink,
            )
        }
        PrimaryButton(onClick = { onIntent(CloudUploadIntent.Submit) }, fullWidth = true) {
            Text(stringResource(Res.string.backup_submit))
        }
    }
}

@Composable
private fun passwordHelper(state: CloudUploadUiState): String = when {
    state.showPasswordError -> when (state.passwordProblem) {
        BackupPasswordProblem.TOO_SHORT -> stringResource(Res.string.backup_password_too_short)
        BackupPasswordProblem.TOO_LONG -> stringResource(Res.string.backup_password_too_long)
        BackupPasswordProblem.INVALID_CHARACTERS -> stringResource(Res.string.backup_password_invalid)
        null -> stringResource(Res.string.backup_password_hint)
    }

    else -> stringResource(Res.string.backup_password_hint)
}

@Composable
private fun BusyBody(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = DaftarTheme.colors.primary)
        Spacer(Modifier.height(DaftarSpacing.lg))
        Text(
            text = message,
            style = DaftarTheme.typography.sectionHeading,
            color = DaftarTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
    }
}

private fun failureMessage(error: CloudError): org.jetbrains.compose.resources.StringResource = when (error) {
    CloudError.OVERSIZE -> Res.string.cloud_upload_oversize
    CloudError.QUOTA_EXCEEDED -> Res.string.cloud_upload_quota
    CloudError.OFFLINE -> Res.string.cloud_upload_offline
    CloudError.CHANGED -> Res.string.cloud_upload_changed
    CloudError.UNKNOWN_OUTCOME -> Res.string.cloud_upload_unknown
    else -> Res.string.cloud_upload_failed
}

private val CONTENT_MAX_WIDTH = 560.dp
