package com.mk.daftar.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.daftar.core.designsystem.component.DaftarSettingRow
import com.mk.daftar.core.designsystem.component.DaftarSettingTrailing
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.navigation.BackupExportRoute
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.ui.shell.PlaceholderScreen
import com.mk.daftar.feature.settings.resources.Res
import com.mk.daftar.feature.settings.resources.settings_backup_action
import com.mk.daftar.feature.settings.resources.settings_backup_heading
import com.mk.daftar.feature.settings.resources.settings_backup_subtitle
import com.mk.daftar.feature.settings.resources.settings_backup_title
import com.mk.daftar.feature.settings.resources.settings_message
import com.mk.daftar.feature.settings.resources.settings_title
import org.jetbrains.compose.resources.stringResource

/**
 * A placeholder with one entry: the encrypted export. Its only other content is the neutral copy. It has no
 * repository and shows no figures.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    PlaceholderScreen(
        title = stringResource(Res.string.settings_title),
        message = stringResource(Res.string.settings_message),
        modifier = modifier,
    ) {
        Spacer(Modifier.height(DaftarSpacing.md))
        Text(
            text = stringResource(Res.string.settings_backup_heading),
            style = DaftarTheme.typography.sectionHeading,
            color = DaftarTheme.colors.ink,
        )
        DaftarSettingRow(
            title = stringResource(Res.string.settings_backup_title),
            subtitle = stringResource(Res.string.settings_backup_subtitle),
            trailing = DaftarSettingTrailing.Link(
                text = stringResource(Res.string.settings_backup_action),
                onClick = { navigator.goTo(BackupExportRoute) },
            ),
        )
    }
}
