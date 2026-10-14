package com.mk.daftar.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mk.daftar.core.designsystem.component.DaftarIcon
import com.mk.daftar.core.designsystem.component.DaftarIconButton
import com.mk.daftar.core.designsystem.component.DaftarKeypad
import com.mk.daftar.core.designsystem.component.DaftarMark
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarPinField
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.resources.pin_biometric
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.security.PinEntryBuffer
import com.mk.daftar.feature.auth.AuthBusy
import com.mk.daftar.feature.auth.AuthError
import com.mk.daftar.feature.auth.AuthIntent
import com.mk.daftar.feature.auth.AuthNotice
import com.mk.daftar.feature.auth.AuthPhase
import com.mk.daftar.feature.auth.AuthUiState
import com.mk.daftar.feature.auth.AuthUnavailableReason
import com.mk.daftar.feature.auth.BiometricOffer
import com.mk.daftar.feature.auth.PinMessage
import com.mk.daftar.feature.auth.pinMessageFor
import com.mk.daftar.feature.auth.resources.Res
import com.mk.daftar.feature.auth.resources.auth_biometric_body
import com.mk.daftar.feature.auth.resources.auth_biometric_enable
import com.mk.daftar.feature.auth.resources.auth_biometric_heading
import com.mk.daftar.feature.auth.resources.auth_biometric_later
import com.mk.daftar.feature.auth.resources.auth_biometric_not_enrolled
import com.mk.daftar.feature.auth.resources.auth_biometric_privacy
import com.mk.daftar.feature.auth.resources.auth_biometric_unavailable
import com.mk.daftar.feature.auth.resources.auth_busy_creating
import com.mk.daftar.feature.auth.resources.auth_busy_enrolling
import com.mk.daftar.feature.auth.resources.auth_busy_unlocking
import com.mk.daftar.feature.auth.resources.auth_checking
import com.mk.daftar.feature.auth.resources.auth_confirm_action
import com.mk.daftar.feature.auth.resources.auth_confirm_body
import com.mk.daftar.feature.auth.resources.auth_confirm_heading
import com.mk.daftar.feature.auth.resources.auth_create_action
import com.mk.daftar.feature.auth.resources.auth_create_body
import com.mk.daftar.feature.auth.resources.auth_create_heading
import com.mk.daftar.feature.auth.resources.auth_entered_digits
import com.mk.daftar.feature.auth.resources.auth_erase_digit
import com.mk.daftar.feature.auth.resources.auth_field_label
import com.mk.daftar.feature.auth.resources.auth_field_state
import com.mk.daftar.feature.auth.resources.auth_forgot_pin
import com.mk.daftar.feature.auth.resources.auth_incorrect_pin
import com.mk.daftar.feature.auth.resources.auth_open_failed
import com.mk.daftar.feature.auth.resources.auth_recovery_backup_warning
import com.mk.daftar.feature.auth.resources.auth_recovery_body
import com.mk.daftar.feature.auth.resources.auth_recovery_heading
import com.mk.daftar.feature.auth.resources.auth_restart_creation
import com.mk.daftar.feature.auth.resources.auth_restore_backup
import com.mk.daftar.feature.auth.resources.auth_restore_cloud
import com.mk.daftar.feature.auth.resources.auth_retry
import com.mk.daftar.feature.auth.resources.auth_security_footnote
import com.mk.daftar.feature.auth.resources.auth_step_1
import com.mk.daftar.feature.auth.resources.auth_step_2
import com.mk.daftar.feature.auth.resources.auth_step_3
import com.mk.daftar.feature.auth.resources.auth_try_pin_again
import com.mk.daftar.feature.auth.resources.auth_unavailable_corrupt
import com.mk.daftar.feature.auth.resources.auth_unavailable_format
import com.mk.daftar.feature.auth.resources.auth_unavailable_hardware
import com.mk.daftar.feature.auth.resources.auth_unavailable_heading
import com.mk.daftar.feature.auth.resources.auth_unavailable_key
import com.mk.daftar.feature.auth.resources.auth_unavailable_transient
import com.mk.daftar.feature.auth.resources.auth_unlock_action
import com.mk.daftar.feature.auth.resources.auth_unlock_body
import com.mk.daftar.feature.auth.resources.auth_unlock_heading
import com.mk.daftar.feature.auth.resources.p25_pin_backoff
import com.mk.daftar.feature.auth.resources.p25_pin_biometric_disabled
import com.mk.daftar.feature.auth.resources.p25_pin_biometric_locked
import com.mk.daftar.feature.auth.resources.p25_pin_biometric_unavailable
import com.mk.daftar.feature.auth.resources.p25_pin_duration_seconds
import com.mk.daftar.feature.auth.resources.p25_pin_mismatch
import com.mk.daftar.feature.auth.resources.p25_pin_ready
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The auth screens below the route. Stateless: they draw [state] and report [onIntent], so a screenshot or a preview
 * can render any state with fake data. Create, confirm and unlock share [PinScreen]: one geometry whose lanes are sized
 * for every message of the flow, so a state change never moves the PIN field, the keypad or the primary action.
 * The host owns the system insets; the keypad stays left-to-right inside the right-to-left column.
 */
@Composable
fun AuthContent(
    state: AuthUiState,
    onIntent: (AuthIntent) -> Unit,
    modifier: Modifier = Modifier,
    cloudRestoreOffered: Boolean = false,
) {
    val colors = DaftarTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // The automatic biometric request starts only after this surface is drawn on a resumed host. Recomposition,
    // rotation and focus changes of the OS prompt do not repeat it: the view model clears the pending request.
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    val latestIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(state.biometricAutoPending, resumed) {
        if (state.biometricAutoPending && resumed) latestIntent(AuthIntent.AutomaticBiometric)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val intent = event.toIntent()
                if (intent != null) onIntent(intent)
                intent != null
            },
    ) {
        if (state.phase.usesPinLayout) {
            PinScreen(state = state, onIntent = onIntent, modifier = Modifier.weight(1f))
        } else {
            when (state.phase) {
                AuthPhase.Biometric -> AuthTopBar(
                    title = stringResource(Res.string.auth_biometric_heading),
                    onBack = { onIntent(AuthIntent.Back) },
                )

                AuthPhase.Recovery -> AuthTopBar(
                    title = stringResource(Res.string.auth_forgot_pin),
                    onBack = { onIntent(AuthIntent.LeaveRecovery) },
                )

                else -> Unit
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md, vertical = DaftarSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
                ) {
                    AuthPhaseContent(state = state, onIntent = onIntent, cloudRestoreOffered = cloudRestoreOffered)
                }
            }
        }
    }
}

@Composable
private fun AuthPhaseContent(state: AuthUiState, onIntent: (AuthIntent) -> Unit, cloudRestoreOffered: Boolean) {
    when (val phase = state.phase) {
        AuthPhase.Checking -> CheckingBody()

        AuthPhase.Biometric -> BiometricBody(state, onIntent)

        AuthPhase.Recovery -> RecoveryBody(state, onIntent, cloudRestoreOffered)

        is AuthPhase.Unavailable -> UnavailableBody(phase.reason, state, onIntent)

        // Create, confirm and unlock are drawn by PinScreen.
        AuthPhase.Create, AuthPhase.Confirm, AuthPhase.Unlock -> Unit
    }
}

@Composable
private fun AuthTopBar(title: String, onBack: () -> Unit) {
    DaftarTopBar(
        title = title,
        onBack = onBack,
        backLabel = stringResource(DesignRes.string.btn_back),
    )
}

/** One column per phase, so each phase body emits a single composable root. */
@Composable
private fun PhaseLayout(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        content = content,
    )
}

@Composable
private fun CheckingBody() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
    ) {
        Spacer(Modifier.height(DaftarSpacing.xxl))
        CircularProgressIndicator(color = DaftarTheme.colors.primary)
        Text(
            text = stringResource(Res.string.auth_checking),
            style = DaftarTheme.typography.supportingBody,
            color = DaftarTheme.colors.muted,
        )
    }
}

@Composable
private fun BiometricBody(state: AuthUiState, onIntent: (AuthIntent) -> Unit) {
    PhaseLayout {
        val colors = DaftarTheme.colors
        StepLabel(stringResource(Res.string.auth_step_3))
        Box(
            modifier = Modifier
                .size(DaftarSpacing.emblemSize)
                .background(colors.soft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DaftarIcon(
                icon = DaftarIcons.Fingerprint,
                contentDescription = null,
                tint = colors.primary,
                size = DaftarSpacing.emblemIconSize,
            )
        }
        Text(
            text = stringResource(Res.string.auth_biometric_heading),
            style = DaftarTheme.typography.screenHeading,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.auth_biometric_body),
            style = DaftarTheme.typography.supportingBody,
            color = colors.muted,
            textAlign = TextAlign.Center,
        )
        when (state.biometricOffer) {
            BiometricOffer.Checking, BiometricOffer.Available -> Unit

            BiometricOffer.NotEnrolled -> DaftarNotePanel(
                modifier = Modifier.fillMaxWidth(),
                tone = DaftarNoteTone.Context,
            ) {
                Text(stringResource(Res.string.auth_biometric_not_enrolled), style = DaftarTheme.typography.noteBody)
            }

            BiometricOffer.Unavailable -> DaftarNotePanel(
                modifier = Modifier.fillMaxWidth(),
                tone = DaftarNoteTone.Context,
            ) {
                Text(stringResource(Res.string.auth_biometric_unavailable), style = DaftarTheme.typography.noteBody)
            }
        }
        PrimaryButton(
            onClick = { onIntent(AuthIntent.EnableBiometrics) },
            enabled = state.biometricOffer == BiometricOffer.Available && state.busy == null,
            loading = state.busy == AuthBusy.Enrolling,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.auth_biometric_enable))
        }
        when (state.busy) {
            AuthBusy.Creating -> BusyCaption(stringResource(Res.string.auth_busy_creating))
            AuthBusy.Enrolling -> BusyCaption(stringResource(Res.string.auth_busy_enrolling))
            else -> Unit
        }
        DaftarTextButton(
            onClick = { onIntent(AuthIntent.SkipBiometrics) },
            enabled = state.busy == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.auth_biometric_later))
        }
        Text(
            text = stringResource(Res.string.auth_biometric_privacy),
            style = DaftarTheme.typography.fineText,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RecoveryBody(state: AuthUiState, onIntent: (AuthIntent) -> Unit, cloudRestoreOffered: Boolean) {
    PhaseLayout {
        val colors = DaftarTheme.colors
        val typography = DaftarTheme.typography
        Text(
            text = stringResource(Res.string.auth_recovery_heading),
            style = typography.screenHeading,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.auth_recovery_body),
            style = typography.supportingBody,
            color = colors.muted,
            textAlign = TextAlign.Center,
        )
        DaftarNotePanel(modifier = Modifier.fillMaxWidth(), tone = DaftarNoteTone.Warning) {
            Text(stringResource(Res.string.auth_recovery_backup_warning), style = typography.noteBody)
        }
        OutlineButton(
            onClick = { onIntent(AuthIntent.LeaveRecovery) },
            enabled = state.busy == null,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.auth_try_pin_again))
        }
        DaftarTextButton(
            onClick = { onIntent(AuthIntent.OpenRestore) },
            enabled = state.busy == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.auth_restore_backup))
        }
        if (cloudRestoreOffered) {
            DaftarTextButton(
                onClick = { onIntent(AuthIntent.OpenCloudRestore) },
                enabled = state.busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.auth_restore_cloud))
            }
        }
    }
}

@Composable
private fun UnavailableBody(reason: AuthUnavailableReason, state: AuthUiState, onIntent: (AuthIntent) -> Unit) {
    PhaseLayout {
        val colors = DaftarTheme.colors
        val typography = DaftarTheme.typography
        DaftarMark(Modifier.size(DaftarSpacing.emblemSize), contentDescription = null)
        Text(
            text = stringResource(Res.string.auth_unavailable_heading),
            style = typography.screenHeading,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            text = stringResource(reason.bodyResource()),
            style = typography.supportingBody,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // Retry is offered only where a later attempt can succeed. A lost key or unsupported hardware never is.
        if (reason == AuthUnavailableReason.Transient || reason == AuthUnavailableReason.DeviceKeyUnavailable) {
            OutlineButton(
                onClick = { onIntent(AuthIntent.Recheck) },
                enabled = state.busy == null,
                fullWidth = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.auth_retry))
            }
        }
    }
}

private fun AuthUnavailableReason.bodyResource() = when (this) {
    AuthUnavailableReason.UnsupportedHardware -> Res.string.auth_unavailable_hardware
    AuthUnavailableReason.DeviceKeyUnavailable -> Res.string.auth_unavailable_key
    AuthUnavailableReason.CorruptState -> Res.string.auth_unavailable_corrupt
    AuthUnavailableReason.UnsupportedFormat -> Res.string.auth_unavailable_format
    AuthUnavailableReason.Transient -> Res.string.auth_unavailable_transient
}

@Composable
private fun StepLabel(text: String) {
    Text(
        text = text,
        style = DaftarTheme.typography.fieldLabel,
        color = DaftarTheme.colors.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The PIN screen for create, confirm and unlock. A fixed top-bar lane, then one scroll container for everything below
 * it. The intro sits at the top and the input block at the bottom, with the space between them left empty, so the
 * PIN field, keypad and primary action keep the same place in every state of the flow.
 */
@Composable
private fun PinScreen(state: AuthUiState, onIntent: (AuthIntent) -> Unit, modifier: Modifier = Modifier) {
    // No top-bar lane: confirm's Back floats over the intro's start corner, so every phase keeps the same geometry and
    // the whole flow fits a phone screen without scrolling.
    Box(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val padding = DaftarSpacing.md
            val contentWidth = minOf(MAX_CONTENT_WIDTH, maxWidth - padding * 2)
            val lanes = rememberPinLanes(contentWidth)
            val minimumBody = (maxHeight - padding * 2).coerceAtLeast(0.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = padding, vertical = padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BottomAnchoredBody(
                    minHeight = minimumBody,
                    top = { PinIntro(state = state, lanes = lanes) },
                    bottom = { PinInputBlock(state = state, lanes = lanes, onIntent = onIntent) },
                    modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth(),
                )
            }
        }
        if (state.phase == AuthPhase.Confirm) {
            DaftarIconButton(
                onClick = { onIntent(AuthIntent.Back) },
                icon = DaftarIcons.Back,
                contentDescription = stringResource(DesignRes.string.btn_back),
                mirrorInRtl = true,
                modifier = Modifier.align(Alignment.TopStart).padding(DaftarSpacing.xs),
            )
        }
    }
}

/**
 * Places [top] at the start and [bottom] at the end of a body at least [minHeight] tall. Both keep their own height, so
 * the space between them is the flexible region. A shorter body is its natural height and scrolls with its parent.
 */
@Composable
private fun BottomAnchoredBody(
    minHeight: Dp,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(content = {
        top()
        bottom()
    }, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val topPlaceable = measurables[0].measure(Constraints(minWidth = width, maxWidth = width))
        val bottomPlaceable = measurables[1].measure(Constraints(minWidth = width, maxWidth = width))
        val height = maxOf(minHeight.roundToPx(), topPlaceable.height + bottomPlaceable.height)
        layout(width, height) {
            topPlaceable.place(0, 0)
            bottomPlaceable.place(0, height - bottomPlaceable.height)
        }
    }
}

/** The lane heights of the PIN flow, measured for every copy variant at the current width, locale and font scale. */
private data class PinLanes(val step: Dp, val heading: Dp, val message: Dp, val footer: Dp)

@Composable
private fun rememberPinLanes(width: Dp): PinLanes {
    val typography = DaftarTheme.typography
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val steps = listOf(stringResource(Res.string.auth_step_1), stringResource(Res.string.auth_step_2))
    val headings = listOf(
        stringResource(Res.string.auth_create_heading),
        stringResource(Res.string.auth_confirm_heading),
        stringResource(Res.string.auth_unlock_heading),
    )
    // The longest countdown the penalty schedule can show, and a few-form plural, so every backoff variant fits.
    val longest =
        pluralStringResource(Res.plurals.p25_pin_duration_seconds, LONGEST_BACKOFF_SECONDS, LONGEST_BACKOFF_SECONDS)
    val fewForm = pluralStringResource(Res.plurals.p25_pin_duration_seconds, FEW_FORM_SECONDS, FEW_FORM_SECONDS)
    val messages = listOf(
        stringResource(Res.string.auth_create_body),
        stringResource(Res.string.auth_confirm_body),
        stringResource(Res.string.auth_unlock_body),
        stringResource(Res.string.p25_pin_mismatch),
        stringResource(Res.string.auth_incorrect_pin),
        stringResource(Res.string.auth_open_failed),
        stringResource(Res.string.auth_busy_creating),
        stringResource(Res.string.auth_busy_unlocking),
        stringResource(Res.string.p25_pin_biometric_unavailable),
        stringResource(Res.string.p25_pin_biometric_locked),
        stringResource(Res.string.p25_pin_biometric_disabled),
        stringResource(Res.string.p25_pin_backoff, longest),
        stringResource(Res.string.p25_pin_backoff, fewForm),
    )
    val footers = listOf(stringResource(Res.string.auth_security_footnote))
    return remember(steps, headings, messages, footers, width, density, typography) {
        with(density) {
            val widthPx = width.roundToPx()
            val messageWidthPx = (width - MESSAGE_PADDING * 2 - MESSAGE_ICON_ALLOWANCE).roundToPx().coerceAtLeast(1)
            fun heightOf(texts: List<String>, style: TextStyle, maxWidthPx: Int): Dp = texts.maxOf { text ->
                measurer.measure(
                    text = text,
                    style = style,
                    constraints = Constraints(maxWidth = maxWidthPx),
                ).size.height.toDp()
            }
            PinLanes(
                step = heightOf(steps, typography.fieldLabel, widthPx).coerceAtLeast(STEP_LANE_MIN),
                heading = heightOf(headings, typography.screenHeading, widthPx).coerceAtLeast(HEADING_LANE_MIN),
                message = (
                    maxOf(
                        heightOf(messages, typography.noteBody, messageWidthPx),
                        heightOf(messages, typography.supportingBody, messageWidthPx),
                    ) + MESSAGE_PADDING * 2
                    ).coerceAtLeast(MESSAGE_LANE_MIN),
                footer = (heightOf(footers, typography.fineText, widthPx) + DaftarSpacing.xs)
                    .coerceAtLeast(FOOTER_LANE_MIN),
            )
        }
    }
}

@Composable
private fun PinIntro(state: AuthUiState, lanes: PinLanes) {
    val heading = when (state.phase) {
        AuthPhase.Create -> stringResource(Res.string.auth_create_heading)
        AuthPhase.Confirm -> stringResource(Res.string.auth_confirm_heading)
        else -> stringResource(Res.string.auth_unlock_heading)
    }
    // Unlock reserves the step line without speaking it.
    val step = when (state.phase) {
        AuthPhase.Create -> stringResource(Res.string.auth_step_1)
        AuthPhase.Confirm -> stringResource(Res.string.auth_step_2)
        else -> null
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
    ) {
        DaftarMark(Modifier.size(PIN_MARK_SIZE), contentDescription = null)
        Box(
            modifier = Modifier.fillMaxWidth().height(lanes.step),
            contentAlignment = Alignment.Center,
        ) {
            if (step != null) {
                Text(
                    text = step,
                    style = DaftarTheme.typography.fieldLabel,
                    color = DaftarTheme.colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(lanes.heading),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = heading,
                style = DaftarTheme.typography.screenHeading,
                color = DaftarTheme.colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
        }
    }
}

@Composable
private fun PinInputBlock(state: AuthUiState, lanes: PinLanes, onIntent: (AuthIntent) -> Unit) {
    val label = when (state.phase) {
        AuthPhase.Create -> stringResource(Res.string.auth_create_action)
        AuthPhase.Confirm -> stringResource(Res.string.auth_confirm_action)
        else -> stringResource(Res.string.auth_unlock_action)
    }
    val invalid = state.error == AuthError.PinMismatch ||
        (state.error == AuthError.IncorrectPin && state.retrySeconds == null)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PinInput(state = state, invalid = invalid)
        Spacer(Modifier.height(DaftarSpacing.xs))
        PinMessageLane(state = state, height = lanes.message)
        Spacer(Modifier.height(DaftarSpacing.sm))
        PinKeypad(state = state, onIntent = onIntent, modifier = Modifier.heightIn(min = PIN_KEYPAD_MIN_HEIGHT))
        Spacer(Modifier.height(DaftarSpacing.sm))
        PrimaryAction(state = state, label = label, onIntent = onIntent)
        Spacer(Modifier.height(DaftarSpacing.xs))
        PinFooterLane(state = state, height = lanes.footer, onIntent = onIntent)
    }
}

/** The PIN display: a label, then the dots. Its semantics carry the label and the count, never the digits. */
@Composable
private fun PinInput(state: AuthUiState, invalid: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
    ) {
        val label = stringResource(Res.string.auth_field_label)
        val count = pluralStringResource(Res.plurals.auth_entered_digits, state.enteredCount, state.enteredCount)
        Text(
            text = label,
            style = DaftarTheme.typography.fieldLabel,
            color = DaftarTheme.colors.muted,
            modifier = Modifier.fillMaxWidth(),
        )
        DaftarPinField(
            enteredCount = state.enteredCount,
            stateDescription = stringResource(Res.string.auth_field_state, label, count),
            placeholder = "••••••",
            enabled = state.inputEnabled,
            invalid = invalid,
        )
    }
}

/**
 * The one message of the PIN flow. It sits in a lane of fixed height, so an error, a countdown or a notice never moves
 * the controls below it. A backoff is polite only while it first appears; the countdown then stays silent.
 */
@Composable
private fun PinMessageLane(state: AuthUiState, height: Dp) {
    val message = pinMessageFor(state)
    val inBackoff = message is PinMessage.Backoff
    val announcingBackoff = remember(inBackoff) { mutableStateOf(inBackoff) }
    LaunchedEffect(inBackoff) {
        if (inBackoff) {
            delay(BACKOFF_ANNOUNCE_MS)
            announcingBackoff.value = false
        }
    }
    val live = message is PinMessage.Notice || (inBackoff && announcingBackoff.value)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when (message) {
            null -> Unit

            is PinMessage.Backoff -> ErrorNote(
                icon = DaftarIcons.Clock,
                message = pinMessageText(message),
            )

            is PinMessage.Failure -> ErrorNote(
                icon = DaftarIcons.Alert,
                message = pinMessageText(message),
            )

            is PinMessage.Notice -> DaftarNotePanel(modifier = Modifier.fillMaxWidth(), tone = DaftarNoteTone.Context) {
                Text(pinMessageText(message), style = DaftarTheme.typography.noteBody)
            }

            is PinMessage.Busy -> Text(
                text = pinMessageText(message),
                style = DaftarTheme.typography.supportingBody,
                color = DaftarTheme.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            is PinMessage.Explanation -> Text(
                text = pinMessageText(message),
                style = DaftarTheme.typography.supportingBody,
                color = DaftarTheme.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BackoffReadyAnnouncement(inBackoff = inBackoff)
    }
}

@Composable
private fun pinMessageText(message: PinMessage): String = when (message) {
    is PinMessage.Backoff -> stringResource(
        Res.string.p25_pin_backoff,
        pluralStringResource(Res.plurals.p25_pin_duration_seconds, message.seconds, message.seconds),
    )

    is PinMessage.Failure -> stringResource(message.error.messageResource())

    is PinMessage.Busy -> stringResource(
        if (message.busy == AuthBusy.Creating) Res.string.auth_busy_creating else Res.string.auth_busy_unlocking,
    )

    is PinMessage.Notice -> stringResource(message.notice.messageResource())

    is PinMessage.Explanation -> stringResource(
        when (message.phase) {
            AuthPhase.Create -> Res.string.auth_create_body
            AuthPhase.Confirm -> Res.string.auth_confirm_body
            else -> Res.string.auth_unlock_body
        },
    )
}

/**
 * Announces the end of a backoff once. It is a zero-size live node that exists for a moment after the penalty ends, so
 * the countdown itself never speaks.
 */
@Composable
private fun BackoffReadyAnnouncement(inBackoff: Boolean) {
    val ready = stringResource(Res.string.p25_pin_ready)
    var sawBackoff by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    LaunchedEffect(inBackoff) {
        if (inBackoff) {
            sawBackoff = true
            completed = false
        } else if (sawBackoff) {
            sawBackoff = false
            completed = true
            delay(READY_ANNOUNCE_MS)
            completed = false
        }
    }
    if (completed) {
        Box(
            Modifier.size(1.dp).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = ready
            },
        )
    }
}

@Composable
private fun PinKeypad(state: AuthUiState, onIntent: (AuthIntent) -> Unit, modifier: Modifier = Modifier) {
    // The biometric key exists only on the lock screen, and only when the vault says biometrics are ready.
    val biometric = state.biometricReady && state.phase == AuthPhase.Unlock
    DaftarKeypad(
        onDigit = { digit -> onIntent(AuthIntent.Digit('0'.code + digit)) },
        onErase = { onIntent(AuthIntent.Erase) },
        eraseLabel = stringResource(Res.string.auth_erase_digit),
        enabled = state.inputEnabled,
        modifier = modifier.fillMaxWidth(),
        biometricLabel = if (biometric) stringResource(DesignRes.string.pin_biometric) else "",
        onBiometric = if (biometric) ({ onIntent(AuthIntent.BiometricUnlock) }) else null,
    )
}

@Composable
private fun PrimaryAction(state: AuthUiState, label: String, onIntent: (AuthIntent) -> Unit) {
    PrimaryButton(
        onClick = { onIntent(AuthIntent.Submit) },
        enabled = state.submitEnabled,
        loading = state.busy != null,
        fullWidth = true,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

/** The footer of the PIN flow: the security footnote, the restart action, or the recovery action. */
@Composable
private fun PinFooterLane(state: AuthUiState, height: Dp, onIntent: (AuthIntent) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.Center,
    ) {
        when (state.phase) {
            AuthPhase.Create -> Footnote(stringResource(Res.string.auth_security_footnote))

            AuthPhase.Confirm -> DaftarTextButton(
                onClick = { onIntent(AuthIntent.RestartCreation) },
                enabled = state.busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.auth_restart_creation))
            }

            AuthPhase.Unlock -> DaftarTextButton(
                onClick = { onIntent(AuthIntent.ShowRecovery) },
                enabled = state.busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.auth_forgot_pin))
            }

            else -> Unit
        }
    }
}

@Composable
private fun BusyCaption(text: String) {
    Text(
        text = text,
        style = DaftarTheme.typography.supportingBody,
        color = DaftarTheme.colors.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text = text,
        style = DaftarTheme.typography.fineText,
        color = DaftarTheme.colors.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorNote(icon: ImageVector, message: String) {
    DaftarNotePanel(modifier = Modifier.fillMaxWidth(), tone = DaftarNoteTone.Error, icon = icon) {
        Text(message, style = DaftarTheme.typography.noteBody)
    }
}

private fun AuthError.messageResource(): StringResource = when (this) {
    AuthError.PinMismatch -> Res.string.p25_pin_mismatch
    AuthError.IncorrectPin -> Res.string.auth_incorrect_pin
    AuthError.OpenFailed -> Res.string.auth_open_failed
}

private fun AuthNotice.messageResource(): StringResource = when (this) {
    AuthNotice.BiometricsUnavailable -> Res.string.p25_pin_biometric_unavailable
    AuthNotice.BiometricsLockedOut -> Res.string.p25_pin_biometric_locked
    AuthNotice.BiometricsNotEnabled -> Res.string.p25_pin_biometric_disabled
}

/** The phases drawn by [PinScreen]. The others keep their own layout. */
private val AuthPhase.usesPinLayout: Boolean
    get() = this == AuthPhase.Create || this == AuthPhase.Confirm || this == AuthPhase.Unlock

/** Hardware keys: digits in any script, Backspace to erase, Enter to submit. Key-down only. */
private fun KeyEvent.toIntent(): AuthIntent? {
    if (type != KeyEventType.KeyDown) return null
    return when (key) {
        Key.Backspace, Key.Delete -> AuthIntent.Erase

        Key.Enter, Key.NumPadEnter -> AuthIntent.Submit

        else -> {
            val codePoint = utf16CodePoint
            if (PinEntryBuffer.digitValue(codePoint) >= 0) {
                AuthIntent.Digit(codePoint)
            } else {
                null
            }
        }
    }
}

private val MAX_CONTENT_WIDTH = 400.dp
private val PIN_MARK_SIZE = 44.dp
private val PIN_KEYPAD_MIN_HEIGHT = 256.dp
private val STEP_LANE_MIN = 20.dp
private val HEADING_LANE_MIN = 40.dp
private val MESSAGE_LANE_MIN = 64.dp
private val MESSAGE_PADDING = 12.dp
private val MESSAGE_ICON_ALLOWANCE = 32.dp
private val FOOTER_LANE_MIN = 48.dp
private const val LONGEST_BACKOFF_SECONDS = 900
private const val FEW_FORM_SECONDS = 3
private const val BACKOFF_ANNOUNCE_MS = 1_000L
private const val READY_ANNOUNCE_MS = 2_000L
