package com.mk.daftar

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.mk.daftar.core.designsystem.catalog.DesignCatalog
import com.mk.daftar.core.designsystem.component.DaftarPrivacyCover
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.shared.resources.Res
import com.mk.daftar.shared.resources.app_name
import org.jetbrains.compose.resources.stringResource

/** The persistent label of debug builds. It is English on purpose: it states the key policy exactly. */
internal const val DEBUG_BANNER_TEXT: String = "Debug build — software Keystore permitted. Demo data only."

/**
 * Root of the app, shared by the Android activity and the iOS view controller. [showsDebugBanner] comes from the
 * build variant. [covered] hides the content with the neutral cover while the vault is obscured or locking.
 */
@Composable
fun App(modifier: Modifier = Modifier, showsDebugBanner: Boolean = false, covered: Boolean = false) {
    DaftarTheme {
        // The status-bar inset has one owner: the banner when it shows, otherwise the catalog's safe-drawing padding.
        val topInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showsDebugBanner) DebugBanner(topInsets)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (showsDebugBanner) Modifier.consumeWindowInsets(topInsets) else Modifier),
                ) {
                    DesignCatalog()
                }
            }
            if (covered) {
                DaftarPrivacyCover(name = stringResource(Res.string.app_name), modifier = Modifier.fillMaxSize())
            }
        }
    }
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
