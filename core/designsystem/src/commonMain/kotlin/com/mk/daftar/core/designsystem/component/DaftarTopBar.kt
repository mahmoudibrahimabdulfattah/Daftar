package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/**
 * The screen header: the notebook mark on a top-level screen, or a back arrow on a pushed one, then the title
 * and any trailing actions. Long titles wrap instead of shrinking. Pass [backLabel] whenever [onBack] is set.
 */
@Composable
fun DaftarTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DaftarSpacing.topBarMinHeight)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (onBack != null) {
            DaftarIconButton(
                onClick = onBack,
                icon = DaftarIcons.Back,
                contentDescription = backLabel ?: "",
                mirrorInRtl = true,
            )
        } else {
            DaftarMark(Modifier.padding(horizontal = 8.dp).size(32.dp))
        }
        Text(
            text = title,
            style = DaftarTheme.typography.appBarTitle,
            color = DaftarTheme.colors.ink,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}
