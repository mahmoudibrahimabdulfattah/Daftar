package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/**
 * A notebook card: a bordered surface for a note or a record. [title], [body] copy, a [time] stamp and
 * [actions] appear in that order; the content grows with what it holds.
 */
@Composable
fun DaftarCard(
    modifier: Modifier = Modifier,
    style: Style = Style,
    title: String? = null,
    time: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val styleState = remember { MutableStyleState(null) }
    ProvideContent(DaftarTheme.colors.ink, DaftarTheme.typography.supportingBody) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .styleable(styleState, DaftarStyles.notebookCard, style),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = DaftarTheme.typography.notebookTitle,
                    modifier = Modifier.semantics { heading() },
                )
            }
            content()
            if (time != null) {
                Text(time, style = DaftarTheme.typography.caption, color = DaftarTheme.colors.muted)
            }
            if (actions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    content = actions,
                )
            }
        }
    }
}
