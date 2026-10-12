package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/** What a [DaftarNotePanel] is for. Each tone has its own fill and text colour. */
enum class DaftarNoteTone {
    /** A quiet explanation or a "why". */
    Context,

    /** A caution the user should read before acting. */
    Warning,

    /** A failure. Its text is announced as soon as it appears. */
    Error,
}

/**
 * An inline note: explanation, caution or error. Put the message in [content] as text so it is read aloud and
 * never relies on colour or the optional [icon] alone. [action] is an optional text button under the message.
 */
@Composable
fun DaftarNotePanel(
    modifier: Modifier = Modifier,
    tone: DaftarNoteTone = DaftarNoteTone.Context,
    icon: ImageVector? = null,
    style: Style = Style,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DaftarTheme.colors
    val styleState = remember { MutableStyleState(null) }
    val (toneStyle, contentColor) = when (tone) {
        DaftarNoteTone.Context -> Style to colors.primary
        DaftarNoteTone.Warning -> DaftarStyles.warningNote to colors.warning
        DaftarNoteTone.Error -> DaftarStyles.errorNote to colors.error
    }
    val announce = tone == DaftarNoteTone.Error
    ProvideContent(contentColor, DaftarTheme.typography.noteBody) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .styleable(styleState, DaftarStyles.contextNote, toneStyle, style)
                .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Assertive } else Modifier),
        ) {
            if (icon != null) {
                DaftarIcon(
                    icon = icon,
                    contentDescription = null,
                    size = DaftarSpacing.smallIconSize,
                    modifier = Modifier.padding(end = 5.dp, top = 2.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                content()
                if (action != null) action()
            }
        }
    }
}
