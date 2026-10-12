package com.mk.daftar.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mk.daftar.core.designsystem.catalog.DesignCatalog
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import com.mk.daftar.feature.auth.AuthIntent
import com.mk.daftar.feature.auth.AuthUiState
import com.mk.daftar.feature.auth.AuthViewModel
import com.mk.daftar.feature.auth.ui.AuthContent
import com.mk.daftar.platform.PlatformBackHandler
import com.mk.daftar.shared.resources.Res
import com.mk.daftar.shared.resources.dev_catalog
import com.mk.daftar.shared.resources.dev_heading
import com.mk.daftar.shared.resources.dev_preview
import com.mk.daftar.shared.resources.progress_closing
import com.mk.daftar.shared.resources.progress_opening
import com.mk.daftar.shell.DaftarAppShell
import com.mk.daftar.shell.ShellStateOwner
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The vault gate, outside the shell. Only [VaultSurface.Shell] reaches private navigation, and it exists only for an
 * open generation. When the session leaves Open, the owner releases the generation, so a lock disposes stacks, entry
 * state and drafts; a configuration change keeps them because the session stays Open.
 *
 * The auth view model belongs to the activity, not to a surface, so the Opening branch cannot cancel an attempt in
 * flight. It is asked for a fresh status whenever the vault is locked and whenever the app resumes.
 */
@Composable
fun VaultGate(
    state: VaultSessionState,
    shell: ShellStateOwner,
    developerTools: Boolean,
    covered: Boolean,
    modifier: Modifier = Modifier,
) {
    val auth: AuthViewModel = koinViewModel()
    val visibility: ValuesVisibility = koinInject()
    val authState by auth.state.collectAsStateWithLifecycle()
    val onAuthIntent: (AuthIntent) -> Unit = remember(auth) { { intent -> auth.onIntent(intent) } }
    LaunchedEffect(state) {
        if (state !is VaultSessionState.Open) {
            shell.release()
            // Locking destroys the privacy choice: the next generation starts with values shown.
            visibility.reset()
        }
        if (state == VaultSessionState.Locked || state == VaultSessionState.Unavailable) {
            onAuthIntent(AuthIntent.Recheck)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onAuthIntent(AuthIntent.Recheck) }
    when (val surface = vaultSurfaceFor(state)) {
        VaultSurface.Locked, VaultSurface.Unavailable -> LockedSurface(
            authState = authState,
            onAuthIntent = onAuthIntent,
            developerTools = developerEntriesVisible(state, developerTools),
            covered = covered,
            modifier = modifier,
        )

        is VaultSurface.Progress -> ProgressSurface(closing = surface.closing, modifier = modifier)

        is VaultSurface.Shell -> DaftarAppShell(
            navigator = shell.navigatorFor(surface.generation),
            retention = shell.retention,
            modifier = modifier,
        )
    }
}

/**
 * The locked surface: the auth screens, with the developer entries under them in a debug build. Developer screens
 * replace it while open. Opening one wipes the auth drafts; closing one rechecks the vault status.
 */
@Composable
private fun LockedSurface(
    authState: AuthUiState,
    onAuthIntent: (AuthIntent) -> Unit,
    developerTools: Boolean,
    covered: Boolean,
    modifier: Modifier = Modifier,
) {
    var developer by remember { mutableStateOf<DeveloperDestination?>(null) }
    // The preview's navigation lives in a view model, so a configuration change keeps it; closing it, or covering the
    // app, releases it. Developer screens never survive a cover, as they would not survive backgrounding in any view.
    val preview: ShellStateOwner = viewModel(key = PREVIEW_OWNER_KEY, factory = shellOwnerFactory)
    LaunchedEffect(covered) {
        if (covered) {
            preview.release()
            developer = null
        }
    }
    when (developer) {
        null -> AuthSurface(
            authState = authState,
            onAuthIntent = onAuthIntent,
            showsDeveloper = developerTools,
            onOpen = {
                onAuthIntent(AuthIntent.PrivacyInvalidated)
                developer = it
            },
            modifier = modifier,
        )

        DeveloperDestination.DESIGN_CATALOG -> DeveloperChrome(
            title = stringResource(Res.string.dev_catalog),
            onClose = {
                developer = null
                onAuthIntent(AuthIntent.Recheck)
            },
            modifier = modifier,
        ) {
            DesignCatalog()
        }

        DeveloperDestination.SHELL_PREVIEW -> DeveloperChrome(
            title = stringResource(Res.string.dev_preview),
            onClose = {
                preview.release()
                developer = null
                onAuthIntent(AuthIntent.Recheck)
            },
            modifier = modifier,
        ) {
            ShellPreview(owner = preview)
        }
    }
}

/** The auth screens. The developer entries sit below them and exist only when [showsDeveloper] is true. */
@Composable
private fun AuthSurface(
    authState: AuthUiState,
    onAuthIntent: (AuthIntent) -> Unit,
    showsDeveloper: Boolean,
    onOpen: (DeveloperDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DaftarTheme.colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AuthContent(
            state = authState,
            onIntent = onAuthIntent,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (showsDeveloper) {
            DeveloperEntries(onOpen = onOpen)
        }
    }
}

@Composable
private fun DeveloperEntries(onOpen: (DeveloperDestination) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DaftarSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.dev_heading),
            style = DaftarTheme.typography.fieldLabel,
            color = DaftarTheme.colors.muted,
        )
        DaftarTextButton(onClick = { onOpen(DeveloperDestination.DESIGN_CATALOG) }) {
            Text(stringResource(Res.string.dev_catalog))
        }
        DaftarTextButton(onClick = { onOpen(DeveloperDestination.SHELL_PREVIEW) }) {
            Text(stringResource(Res.string.dev_preview))
        }
    }
}

/** Neutral progress while the vault opens or locks. It shows no private content and animates nothing. */
@Composable
private fun ProgressSurface(closing: Boolean, modifier: Modifier = Modifier) {
    val colors = DaftarTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = colors.primary)
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            text = stringResource(if (closing) Res.string.progress_closing else Res.string.progress_opening),
            style = DaftarTheme.typography.supportingBody,
            color = colors.muted,
        )
    }
}

/**
 * A developer screen with a top bar that closes it. The status bar is owned here and consumed below, so the
 * screen inside never pads for it twice.
 */
@Composable
private fun DeveloperChrome(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val topInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    PlatformBackHandler(enabled = true, onBack = onClose)
    Column(modifier = modifier.fillMaxSize().background(DaftarTheme.colors.paper)) {
        DaftarTopBar(
            title = title,
            onBack = onClose,
            backLabel = stringResource(DesignRes.string.btn_back),
            modifier = Modifier.windowInsetsPadding(topInsets),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .consumeWindowInsets(topInsets),
        ) {
            content()
        }
    }
}

/**
 * The shell preview: its own navigator and retention, separate from any vault generation, with static placeholders. It
 * never opens the vault and never reads a repository.
 */
@Composable
private fun ShellPreview(owner: ShellStateOwner) {
    DaftarAppShell(
        navigator = owner.navigatorFor(PREVIEW_GENERATION),
        retention = owner.retention,
        modifier = Modifier.fillMaxSize(),
    )
}

private val shellOwnerFactory = viewModelFactory { initializer { ShellStateOwner() } }

private const val PREVIEW_OWNER_KEY = "developer-shell-preview"

/** The preview is not a vault generation; it only needs one stable identity for its navigator. */
private const val PREVIEW_GENERATION = 0L
