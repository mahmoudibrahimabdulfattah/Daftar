package com.mk.daftar.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.daftar.core.ui.shell.PlaceholderScreen
import com.mk.daftar.feature.settings.resources.Res
import com.mk.daftar.feature.settings.resources.settings_message
import com.mk.daftar.feature.settings.resources.settings_title
import org.jetbrains.compose.resources.stringResource

/** A placeholder: a heading and neutral copy. It has no repository and shows no figures. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(Res.string.settings_title),
        message = stringResource(Res.string.settings_message),
        modifier = modifier,
    )
}
