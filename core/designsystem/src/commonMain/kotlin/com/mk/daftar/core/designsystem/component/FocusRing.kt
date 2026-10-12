package com.mk.daftar.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.mk.daftar.core.designsystem.theme.DaftarSpacing

/**
 * Keyboard focus: a [DaftarSpacing.focusRingWidth] ring in [color], drawn outside the control
 * with a [DaftarSpacing.focusRingGap] gap. It is drawn rather than laid out, so focus never moves content.
 */
internal fun Modifier.focusRing(focused: Boolean, shape: Shape, color: Color): Modifier = if (!focused) {
    this
} else {
    drawWithContent {
        drawContent()
        val width = DaftarSpacing.focusRingWidth.toPx()
        val inset = DaftarSpacing.focusRingGap.toPx() + width / 2f
        val outline = shape.createOutline(
            Size(size.width + inset * 2f, size.height + inset * 2f),
            layoutDirection,
            this,
        )
        translate(left = -inset, top = -inset) {
            drawOutline(outline = outline, color = color, style = Stroke(width = width))
        }
    }
}
