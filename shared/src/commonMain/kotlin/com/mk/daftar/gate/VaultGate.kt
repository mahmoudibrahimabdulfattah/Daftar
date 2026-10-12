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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mk.daftar.core.designsystem.catalog.DesignCatalog
import com.mk.daftar.core.designsystem.component.DaftarMark
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.platform.PlatformBackHandler
import com.mk.daftar.shared.resources.Res
import com.mk.daftar.shared.resources.app_name
import com.mk.daftar.shared.resources.dev_catalog
import com.mk.daftar.shared.resources.dev_heading
import com.mk.daftar.shared.resources.dev_preview
import com.mk.daftar.shared.resources.locked_body
import com.mk.daftar.shared.resources.locked_title
import com.mk.daftar.shared.resources.progress_closing
import com.mk.daftar.shared.resources.progress_opening
import com.mk.daftar.shared.resources.unavailable_body
import com.mk.daftar.shared.resources.unavailable_title
import com.mk.daftar.shell.DaftarAppShell
import com.mk.daftar.shell.ShellStateOwner
import org.jetbrains.compose.resources.stringResource

/**
 * The vault gate, outside the shell. Only [VaultSurface.Shell] reaches private navigation, and it exists only for an
 * open generation. When the session leaves Open, the owner releases the generation, so a lock disposes stacks, entry
 * state and drafts; a configuration change keeps them because the session stays Open.
 */
@Composable
fun VaultGate(
    state: VaultSessionState,
    shell: ShellStateOwner,
    developerTools: Boolean,
    covered: Boolean,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state) {
        if (state !is VaultSessionState.Open) shell.release()
    }
    when (val surface = vaultSurfaceFor(state)) {
        VaultSurface.Locked -> LockedSurface(
            developerTools = developerEntriesVisible(state, developerTools),
            covered = covered,
            modifier = modifier,
        )

        is VaultSurface.Progress -> ProgressSurface(closing = surface.closing, modifier = modifier)

        VaultSurface.Unavailable -> UnavailableSurface(modifier = modifier)

        is VaultSurface.Shell -> DaftarAppShell(
            navigator = shell.navigatorFor(surface.generation),
            retention = shell.retention,
            modifier = modifier,
        )
    }
}

@Composable
private fun LockedSurface(developerTools: Boolean, covered: Boolean, modifier: Modifier = Modifier) {
    var developer by remember { mutableStateOf<DeveloperDestination?>(null) }
    // The preview's navigation lives in a view model, so a configuration change keeps it; closing it, or covering the
    // app, releases it. Developer screens never survive a cover, as they would not survive backgrounding in any view.
    val preview: ShellStateOwner = viewModel(key = PREVIEW_OWNER_KEY)
    LaunchedEffect(covered) {
        if (covered) {
            preview.release()
            developer = null
        }
    }
    when (developer) {
        null -> LockedContent(
            showsDeveloper = developerTools,
            onOpen = { developer = it },
            modifier = modifier,
        )

        DeveloperDestination.DESIGN_CATALOG -> DeveloperChrome(
            title = stringResource(Res.string.dev_catalog),
            onClose = { developer = null },
            modifier = modifier,
        ) {
            DesignCatalog()
        }

        DeveloperDestination.SHELL_PREVIEW -> DeveloperChrome(
            title = stringResource(Res.string.dev_preview),
            onClose = {
                preview.release()
                developer = null
            },
            modifier = modifier,
        ) {
            ShellPreview(owner = preview)
        }
    }
}

/**
 * The locked surface: the notebook mark, the name, and a brief line. It offers no unlock, creates no vault and holds no
 * figures. The developer entries are present only when [showsDeveloper] is true, which a release build never sets.
 */
@Composable
private fun LockedContent(
    showsDeveloper: Boolean,
    onOpen: (DeveloperDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = DaftarSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DaftarMark(Modifier.size(65.dp))
        Spacer(Modifier.height(DaftarSpacing.sm))
        Text(
            text = stringResource(Res.string.app_name),
            style = typography.sectionHeading,
            color = colors.ink,
        )
        Spacer(Modifier.height(DaftarSpacing.md))
        Text(
            text = stringResource(Res.string.locked_title),
            style = typography.screenHeading,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(DaftarSpacing.xs))
        Text(
            text = stringResource(Res.string.locked_body),
            style = typography.supportingBody,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showsDeveloper) {
            Spacer(Modifier.height(DaftarSpacing.xl))
            Text(
                text = stringResource(Res.string.dev_heading),
                style = typography.fieldLabel,
                color = colors.muted,
            )
            DaftarTextButton(onClick = { onOpen(DeveloperDestination.DESIGN_CATALOG) }) {
                Text(stringResource(Res.string.dev_catalog))
            }
            DaftarTextButton(onClick = { onOpen(DeveloperDestination.SHELL_PREVIEW) }) {
                Text(stringResource(Res.string.dev_preview))
            }
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

@Composable
private fun UnavailableSurface(modifier: Modifier = Modifier) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = DaftarSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DaftarMark(Modifier.size(65.dp))
        Spacer(Modifier.height(DaftarSpacing.md))
        Text(
            text = stringResource(Res.string.unavailable_title),
            style = typography.screenHeading,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DaftarSpacing.xs))
        Text(
            text = stringResource(Res.string.unavailable_body),
            style = typography.supportingBody,
            color = colors.muted,
            textAlign = TextAlign.Center,
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

private const val PREVIEW_OWNER_KEY = "developer-shell-preview"

/** The preview is not a vault generation; it only needs one stable identity for its navigator. */
private const val PREVIEW_GENERATION = 0L
