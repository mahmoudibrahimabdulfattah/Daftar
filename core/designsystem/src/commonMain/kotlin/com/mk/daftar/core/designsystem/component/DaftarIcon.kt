package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.mk.daftar.core.designsystem.theme.DaftarSpacing

/**
 * A line icon from [com.mk.daftar.core.designsystem.icon.DaftarIcons], tinted with [tint] (by default the
 * surrounding content colour). Directional icons pass [mirrorInRtl] so they point the reading way.
 * A null [contentDescription] marks the icon as decorative.
 */
@Composable
fun DaftarIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = DaftarSpacing.iconSize,
    mirrorInRtl: Boolean = false,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(size)
            .graphicsLayer(scaleX = if (mirrorInRtl && rtl) -1f else 1f),
    )
}
