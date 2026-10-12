package com.mk.daftar.feature.journal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.daftar.core.ui.shell.PlaceholderScreen
import com.mk.daftar.feature.journal.resources.Res
import com.mk.daftar.feature.journal.resources.journal_message
import com.mk.daftar.feature.journal.resources.journal_title
import org.jetbrains.compose.resources.stringResource

/** A placeholder: a heading and neutral copy. It has no repository and shows no figures. */
@Composable
fun JournalScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(Res.string.journal_title),
        message = stringResource(Res.string.journal_message),
        modifier = modifier,
    )
}
