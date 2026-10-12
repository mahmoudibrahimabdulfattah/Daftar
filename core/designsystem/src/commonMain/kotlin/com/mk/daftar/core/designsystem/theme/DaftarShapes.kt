package com.mk.daftar.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Corner radii from the approved design. Component-specific values are preserved, not averaged. */
@Immutable
data class DaftarShapes(
    /** Text fields, text areas and select fields. */
    val field: Shape = RoundedCornerShape(10.dp),
    /** Buttons, segments, notes, notebook cards, quote strip and PIN field. */
    val control: Shape = RoundedCornerShape(12.dp),
    /** Generic surfaces and keypad keys. */
    val surface: Shape = RoundedCornerShape(16.dp),
    /** Status tags. */
    val tag: Shape = RoundedCornerShape(6.dp),
    /** Row icon tiles. */
    val tile: Shape = RoundedCornerShape(11.dp),
    /** Skeleton placeholders. */
    val skeleton: Shape = RoundedCornerShape(4.dp),
    /** Navigation indicator and switch track. */
    val pill: Shape = RoundedCornerShape(20.dp),
    /** Icon buttons, emblems and the switch thumb. */
    val round: Shape = CircleShape,
)
