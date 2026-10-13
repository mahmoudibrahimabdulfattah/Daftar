package com.mk.daftar

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mk.daftar.core.designsystem.component.DaftarPrivacyCover
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.refresh.PriceRefreshController
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.PreferencesRead
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.domain.repository.UserPreferencesRepository
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.gate.VaultGate
import com.mk.daftar.lifecycle.InactivityMonitor
import com.mk.daftar.platform.applyAppLanguage
import com.mk.daftar.refresh.ZakatTrackingRefresh
import com.mk.daftar.shared.resources.Res
import com.mk.daftar.shared.resources.app_name
import com.mk.daftar.shell.ShellStateOwner
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The persistent label of debug builds. It is English on purpose: it states the key policy exactly. */
internal const val DEBUG_BANNER_TEXT: String = "Debug build — software Keystore permitted. Demo data only."

/**
 * Root of the app, shared by the Android activity and the iOS view controller. Theme, language and layout direction
 * come from the non-sensitive preferences, read outside the vault gate. [showsDebugBanner] and [developerTools] come
 * from the build variant. [covered] hides the content with the neutral cover while the vault is obscured or locking,
 * and it sits above everything, including the developer screens.
 */
@Composable
fun App(
    modifier: Modifier = Modifier,
    showsDebugBanner: Boolean = false,
    developerTools: Boolean = false,
    covered: Boolean = false,
    onThemeChange: (mode: ThemeMode, dark: Boolean) -> Unit = { _, _ -> },
) {
    val inactivity: InactivityMonitor = koinInject()
    LaunchedEffect(inactivity) { inactivity.start() }
    val refresh: PriceRefreshController = koinInject()
    LaunchedEffect(refresh) { refresh.start() }
    val zakatTracking: ZakatTrackingRefresh = koinInject()
    LaunchedEffect(zakatTracking) { zakatTracking.start() }
    val preferences = rememberUserPreferences()
    val mode = preferences?.themeMode ?: ThemeMode.SYSTEM
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val themeChange by rememberUpdatedState(onThemeChange)
    LaunchedEffect(mode, dark) { themeChange(mode, dark) }

    DaftarTheme(darkTheme = dark, arabic = (preferences?.language ?: AppLanguage.AR) == AppLanguage.AR) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DaftarTheme.colors.paper)
                // Deliberate touches and key presses count toward inactivity. Hover and recomposition never do.
                .pointerInput(inactivity) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed || it.changedToUp() }) inactivity.recordInteraction()
                        }
                    }
                }
                .onPreviewKeyEvent {
                    inactivity.recordInteraction()
                    false
                },
        ) {
            if (preferences != null) {
                PreferredContent(
                    preferences = preferences,
                    showsDebugBanner = showsDebugBanner,
                    developerTools = developerTools,
                    covered = covered,
                )
            }
            if (covered) {
                DaftarPrivacyCover(name = stringResource(Res.string.app_name), modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Everything that depends on the language. The platform language is set before this composition reads any string, and
 * keying on the language rebuilds the frame in the new direction. The vault, its navigation and its state are owned by
 * the activity or controller, so the rebuild never locks or loses them.
 */
@Composable
private fun PreferredContent(
    preferences: UserPreferences,
    showsDebugBanner: Boolean,
    developerTools: Boolean,
    covered: Boolean,
    shell: ShellStateOwner = viewModel(factory = shellOwnerFactory),
) {
    val language = preferences.language
    remember(language) { applyAppLanguage(language) }
    key(language) {
        val direction = if (language == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            val session: VaultSession = koinInject()
            val sessionState by session.state.collectAsStateWithLifecycle()
            // The status-bar inset has one owner: the banner when it shows, otherwise the screens beneath it.
            val topInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            Column(modifier = Modifier.fillMaxSize()) {
                if (showsDebugBanner) DebugBanner(topInsets)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (showsDebugBanner) Modifier.consumeWindowInsets(topInsets) else Modifier),
                ) {
                    VaultGate(
                        state = sessionState,
                        shell = shell,
                        developerTools = developerTools,
                        covered = covered,
                    )
                }
            }
        }
    }
}

/** The non-sensitive preferences, or null until the stored value is read. An unreadable file reads as defaults. */
@Composable
private fun rememberUserPreferences(repository: UserPreferencesRepository = koinInject()): UserPreferences? {
    val preferences by remember(repository) {
        repository.observe().map { read ->
            when (read) {
                is PreferencesRead.Ready -> read.preferences
                is PreferencesRead.Failed -> UserPreferences()
            }
        }
    }.collectAsStateWithLifecycle(initialValue = null)
    return preferences
}

@Composable
private fun DebugBanner(topInsets: WindowInsets) {
    val colors = DaftarTheme.colors
    Text(
        text = DEBUG_BANNER_TEXT,
        style = DaftarTheme.typography.fieldLabel,
        color = colors.warning,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warningSoft)
            .windowInsetsPadding(topInsets)
            .padding(horizontal = DaftarSpacing.md, vertical = DaftarSpacing.xs),
    )
}

/** Builds the shell owner explicitly: the default factory cannot construct view models by reflection on iOS. */
private val shellOwnerFactory = viewModelFactory { initializer { ShellStateOwner() } }
