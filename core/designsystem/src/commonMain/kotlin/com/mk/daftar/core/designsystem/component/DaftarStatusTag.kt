package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarStyles
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/** [Positive] is live or cached data; [Warning] is manual, estimated or stale data. */
enum class DaftarStatusTone {
    Positive,
    Warning,
}

/**
 * A small label for the source or state of a value, such as "Live", "Cached", "Manual" or "Refreshing".
 * The label always carries the meaning; the tone and [icon] only reinforce it.
 */
@Composable
fun DaftarStatusTag(
    label: String,
    modifier: Modifier = Modifier,
    tone: DaftarStatusTone = DaftarStatusTone.Positive,
    icon: ImageVector? = null,
    style: Style = Style,
) {
    val colors = DaftarTheme.colors
    val styleState = remember { MutableStyleState(null) }
    val warning = tone == DaftarStatusTone.Warning
    val contentColor = if (warning) colors.warning else colors.primary
    ProvideContent(contentColor, DaftarTheme.typography.caption) {
        Row(
            modifier = modifier.styleable(
                styleState,
                DaftarStyles.statusTag,
                if (warning) DaftarStyles.warningStatusTag else Style,
                style,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) DaftarIcon(icon = icon, contentDescription = null, size = 12.dp)
            Text(label)
        }
    }
}
