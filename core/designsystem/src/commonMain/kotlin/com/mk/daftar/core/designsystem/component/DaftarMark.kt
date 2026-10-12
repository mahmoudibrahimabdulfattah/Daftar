package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/**
 * The notebook mark from the approved design: a closed notebook with a spine line and two ruled lines.
 * [frame] tints the cover and [detail] the cut-out lines, which should match the surface behind it.
 * Pass [contentDescription] when the mark stands alone; leave it null when the name sits beside it.
 */
@Composable
fun DaftarMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    frame: Color = DaftarTheme.colors.primary,
    detail: Color = DaftarTheme.colors.paper,
) {
    val vector = remember(frame, detail) { daftarMarkVector(frame, detail) }
    Image(vector, contentDescription = contentDescription, modifier = modifier)
}

/** The notebook mark as a 48-unit vector, for places that need the drawable directly. */
fun daftarMarkVector(frame: Color, detail: Color = frame): ImageVector =
    ImageVector.Builder("DaftarMark", 48.dp, 48.dp, 48f, 48f)
        .addPath(addPathNodes(COVER), fill = SolidColor(frame), pathFillType = PathFillType.NonZero)
        .addPath(addPathNodes(SPINE), stroke = SolidColor(detail), strokeLineWidth = 2f)
        .addPath(
            addPathNodes(RULES),
            stroke = SolidColor(detail),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
        )
        .build()

private const val COVER = "M15 6H33A5 5 0 0 1 38 11V37A5 5 0 0 1 33 42H15A5 5 0 0 1 10 37V11A5 5 0 0 1 15 6Z"
private const val SPINE = "M18 7V41"
private const val RULES = "M24 17H32M24 24H32"
