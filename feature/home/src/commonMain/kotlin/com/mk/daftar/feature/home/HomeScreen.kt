package com.mk.daftar.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.daftar.core.ui.shell.PlaceholderScreen
import com.mk.daftar.feature.home.resources.Res
import com.mk.daftar.feature.home.resources.home_message
import com.mk.daftar.feature.home.resources.home_title
import org.jetbrains.compose.resources.stringResource

/** A placeholder: a heading and neutral copy. It has no repository and shows no figures. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(Res.string.home_title),
        message = stringResource(Res.string.home_message),
        modifier = modifier,
    )
}
