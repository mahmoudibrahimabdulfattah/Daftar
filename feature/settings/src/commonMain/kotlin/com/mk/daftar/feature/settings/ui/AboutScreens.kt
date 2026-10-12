package com.mk.daftar.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.component.DaftarMark
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.navigation.LeaveRequest
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.feature.settings.AppBuildInfo
import com.mk.daftar.feature.settings.resources.Res
import com.mk.daftar.feature.settings.resources.about_build
import com.mk.daftar.feature.settings.resources.about_font_alexandria
import com.mk.daftar.feature.settings.resources.about_font_rubik
import com.mk.daftar.feature.settings.resources.about_licenses
import com.mk.daftar.feature.settings.resources.about_local
import com.mk.daftar.feature.settings.resources.about_source
import com.mk.daftar.feature.settings.resources.about_source_alexandria
import com.mk.daftar.feature.settings.resources.about_source_rubik
import com.mk.daftar.feature.settings.resources.about_title
import com.mk.daftar.feature.settings.resources.about_version
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The upstream sources of the bundled fonts, as their own license files name them. */
private const val ALEXANDRIA_SOURCE: String = "https://github.com/Gue3bara/Alexandria"
private const val RUBIK_SOURCE: String = "https://github.com/googlefonts/rubik"

/** The version and build the app was built with, the bundled font licenses, and their sources. */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val info: AppBuildInfo = koinInject()
    val uriHandler = LocalUriHandler.current
    AboutContent(
        info = info,
        onBack = { navigator.requestLeave(LeaveRequest.Back) },
        onOpenSource = { url -> uriHandler.openUri(url) },
        modifier = modifier,
    )
}

/** The stateless About page. The version comes from the build, never from a constant in this file. */
@Composable
fun AboutContent(
    info: AppBuildInfo,
    onBack: () -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(title = stringResource(Res.string.about_title), modifier = modifier, onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
        ) {
            DaftarMark(Modifier.size(ABOUT_MARK_SIZE), contentDescription = null)
            Text(
                text = stringResource(Res.string.about_local),
                style = DaftarTheme.typography.supportingBody,
                color = DaftarTheme.colors.muted,
            )
        }
        InfoRow(label = stringResource(Res.string.about_version), value = info.versionName)
        InfoRow(label = stringResource(Res.string.about_build), value = info.buildNumber)

        SectionTitle(stringResource(Res.string.about_licenses))
        LicenseEntry(
            name = stringResource(Res.string.about_font_alexandria),
            sourceLabel = stringResource(Res.string.about_source),
            sourceName = stringResource(Res.string.about_source_alexandria),
            onOpen = { onOpenSource(ALEXANDRIA_SOURCE) },
        )
        LicenseEntry(
            name = stringResource(Res.string.about_font_rubik),
            sourceLabel = stringResource(Res.string.about_source),
            sourceName = stringResource(Res.string.about_source_rubik),
            onOpen = { onOpenSource(RUBIK_SOURCE) },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = DaftarTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = DaftarTheme.typography.settingTitle, color = colors.ink)
        // Figures and version numbers stay left-to-right inside the Arabic layout.
        Text("⁦$value⁩", style = DaftarTheme.typography.fineText, color = colors.ink)
    }
}

@Composable
private fun LicenseEntry(name: String, sourceLabel: String, sourceName: String, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs)) {
        Text(name, style = DaftarTheme.typography.supportingBody, color = DaftarTheme.colors.ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sourceLabel, style = DaftarTheme.typography.fineText, color = DaftarTheme.colors.muted)
            DaftarTextButton(onClick = onOpen) {
                Text(sourceName)
            }
        }
    }
}

private val ABOUT_MARK_SIZE = 56.dp
