package com.mk.daftar.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.component.DaftarKeypad
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarPinField
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.resources.pin_empty
import com.mk.daftar.core.designsystem.resources.pin_entered
import com.mk.daftar.core.designsystem.resources.pin_erase
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.navigation.TopLevelDestination
import com.mk.daftar.core.security.PinEntryBuffer
import com.mk.daftar.core.ui.shell.RootReselectScroll
import com.mk.daftar.feature.settings.SecurityActionFailure
import com.mk.daftar.feature.settings.resources.Res
import com.mk.daftar.feature.settings.resources.bio_cancelled
import com.mk.daftar.feature.settings.resources.bio_failed
import com.mk.daftar.feature.settings.resources.bio_invalidated_notice
import com.mk.daftar.feature.settings.resources.bio_locked_out
import com.mk.daftar.feature.settings.resources.bio_storage
import com.mk.daftar.feature.settings.resources.bio_unavailable
import com.mk.daftar.feature.settings.resources.pin_backoff_seconds
import com.mk.daftar.feature.settings.resources.pin_failed_unavailable
import com.mk.daftar.feature.settings.resources.pin_wrong_current
import com.mk.daftar.feature.settings.resources.prefs_language_arabic
import com.mk.daftar.feature.settings.resources.prefs_language_english
import com.mk.daftar.feature.settings.resources.prefs_theme_dark
import com.mk.daftar.feature.settings.resources.prefs_theme_light
import com.mk.daftar.feature.settings.resources.prefs_theme_system
import com.mk.daftar.feature.settings.resources.prefs_timeout_30
import com.mk.daftar.feature.settings.resources.prefs_timeout_300
import com.mk.daftar.feature.settings.resources.prefs_timeout_60
import com.mk.daftar.feature.settings.resources.settings_biometrics_not_enrolled
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A settings page: a top bar, then one scrollable column of at most 640dp. A page with [onBack] shows a back arrow; the
 * top-level Settings page shows the notebook mark instead. The host owns the system insets.
 */
@Composable
internal fun SettingsPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    rootDestination: TopLevelDestination? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    if (rootDestination != null) {
        RootReselectScroll(rootDestination) { scroll.animateScrollTo(0) }
    }
    Column(modifier = modifier.fillMaxSize().background(DaftarTheme.colors.paper)) {
        DaftarTopBar(
            title = title,
            onBack = onBack,
            backLabel = if (onBack != null) stringResource(DesignRes.string.btn_back) else null,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = DaftarSpacing.md, vertical = DaftarSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = MAX_PAGE_WIDTH).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
                content = content,
            )
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = DaftarTheme.typography.sectionHeading,
        color = DaftarTheme.colors.ink,
        modifier = Modifier.fillMaxWidth().padding(top = DaftarSpacing.sm).semantics { heading() },
    )
}

/** An inline note. Errors are announced as they appear; the text always carries the message, never the icon alone. */
@Composable
internal fun SettingsNote(
    message: String,
    modifier: Modifier = Modifier,
    tone: DaftarNoteTone = DaftarNoteTone.Context,
    icon: ImageVector? = null,
) {
    DaftarNotePanel(modifier = modifier.fillMaxWidth(), tone = tone, icon = icon) {
        Text(message, style = DaftarTheme.typography.noteBody)
    }
}

/**
 * The PIN display: a label, then one dot per digit. Its accessibility output carries the label and the count, never the
 * digits themselves.
 */
@Composable
internal fun PinField(label: String, enteredCount: Int, enabled: Boolean, invalid: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
    ) {
        Text(
            text = label,
            style = DaftarTheme.typography.fieldLabel,
            color = DaftarTheme.colors.muted,
            modifier = Modifier.fillMaxWidth(),
        )
        val count = if (enteredCount == 0) {
            stringResource(DesignRes.string.pin_empty)
        } else {
            stringResource(DesignRes.string.pin_entered, enteredCount)
        }
        DaftarPinField(
            enteredCount = enteredCount,
            stateDescription = "$label, $count",
            placeholder = "••••••",
            enabled = enabled,
            invalid = invalid,
        )
    }
}

/** The keypad. Digits leave as 0 to 9; the caller maps them to code points. Left-to-right, even in Arabic. */
@Composable
internal fun PinKeypad(enabled: Boolean, onDigit: (Int) -> Unit, onErase: () -> Unit) {
    DaftarKeypad(
        onDigit = onDigit,
        onErase = onErase,
        eraseLabel = stringResource(DesignRes.string.pin_erase),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        biometricLabel = "",
        onBiometric = null,
    )
}

/** Hardware keys for a PIN step: digits in any script, Backspace to erase, Enter to submit. Key-down only. */
@Composable
internal fun Modifier.pinHardwareKeys(onDigit: (Int) -> Unit, onErase: () -> Unit, onSubmit: () -> Unit): Modifier {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return this
        .focusRequester(focus)
        .focusable()
        .onPreviewKeyEvent { event ->
            val handled = when (val action = event.toPinAction()) {
                null -> false

                PinKeyAction.Erase -> {
                    onErase()
                    true
                }

                PinKeyAction.Submit -> {
                    onSubmit()
                    true
                }

                is PinKeyAction.Digit -> {
                    onDigit(action.codePoint)
                    true
                }
            }
            handled
        }
}

private sealed interface PinKeyAction {
    data class Digit(val codePoint: Int) : PinKeyAction

    data object Erase : PinKeyAction

    data object Submit : PinKeyAction
}

private fun KeyEvent.toPinAction(): PinKeyAction? {
    if (type != KeyEventType.KeyDown) return null
    return when (key) {
        Key.Backspace, Key.Delete -> PinKeyAction.Erase
        Key.Enter, Key.NumPadEnter -> PinKeyAction.Submit
        else -> utf16CodePoint.takeIf { PinEntryBuffer.digitValue(it) >= 0 }?.let { PinKeyAction.Digit(it) }
    }
}

/** The text for a failed PIN check, when it has one. Null means nothing is shown beside the field. */
@Composable
internal fun pinFailureMessage(failure: SecurityActionFailure?): String? = when (failure) {
    SecurityActionFailure.WrongPin -> stringResource(Res.string.pin_wrong_current)
    SecurityActionFailure.Failed -> stringResource(Res.string.pin_failed_unavailable)
    else -> null
}

/** The explanation for a biometric result that the settings row should show. Cancellation keeps PIN access and says so. */
@Composable
internal fun biometricFailureMessage(failure: SecurityActionFailure?): String? = when (failure) {
    null -> null
    SecurityActionFailure.Cancelled -> stringResource(Res.string.bio_cancelled)
    SecurityActionFailure.BiometricLockedOut -> stringResource(Res.string.bio_locked_out)
    SecurityActionFailure.BiometricNotEnrolled -> stringResource(Res.string.settings_biometrics_not_enrolled)
    SecurityActionFailure.BiometricUnavailable -> stringResource(Res.string.bio_unavailable)
    SecurityActionFailure.BiometricInvalidated -> stringResource(Res.string.bio_invalidated_notice)
    SecurityActionFailure.StorageFailure -> stringResource(Res.string.bio_storage)
    SecurityActionFailure.SessionEnded -> null
    SecurityActionFailure.Failed -> stringResource(Res.string.bio_failed)
    is SecurityActionFailure.Throttled -> null
    SecurityActionFailure.WrongPin -> null
}

/** The seconds of a PIN penalty, as a sentence. */
@Composable
internal fun backoffMessage(seconds: Int): String =
    pluralStringResource(Res.plurals.pin_backoff_seconds, seconds, seconds)

@Composable
internal fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> Res.string.prefs_theme_system
        ThemeMode.LIGHT -> Res.string.prefs_theme_light
        ThemeMode.DARK -> Res.string.prefs_theme_dark
    },
)

@Composable
internal fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.AR -> Res.string.prefs_language_arabic
        AppLanguage.EN -> Res.string.prefs_language_english
    },
)

@Composable
internal fun timeoutLabel(timeout: InactivityTimeout): String = stringResource(
    when (timeout) {
        InactivityTimeout.SECONDS_30 -> Res.string.prefs_timeout_30
        InactivityTimeout.SECONDS_60 -> Res.string.prefs_timeout_60
        InactivityTimeout.SECONDS_300 -> Res.string.prefs_timeout_300
    },
)

/** The width of a settings column on wide windows. */
private val MAX_PAGE_WIDTH = 640.dp
