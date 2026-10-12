package com.mk.daftar.core.designsystem.theme

import androidx.compose.ui.unit.dp

/** Spacing and sizing tokens from the approved design. */
object DaftarSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Screen content padding: start and end [lg], top [xs], bottom 22dp. */
    val contentTop = 8.dp
    val contentBottom = 22.dp

    /** Minimum touch target for every interactive control. */
    val touch = 48.dp

    /** Minimum height of a button. */
    val buttonMinHeight = 48.dp

    /** Minimum height of a text input; text areas use [textAreaMinHeight]. */
    val fieldMinHeight = 52.dp
    val textAreaMinHeight = 110.dp

    /** Vertical margin above each field, label-to-field gap and helper gap. */
    val fieldTop = 17.dp
    val fieldLabelGap = 7.dp
    val fieldHelperGap = 5.dp

    val topBarMinHeight = 66.dp
    val iconSize = 22.dp
    val smallIconSize = 18.dp
    val tileSize = 38.dp
    val tileIconSize = 20.dp
    val emblemSize = 94.dp
    val emblemIconSize = 45.dp

    /** Pinned PIN field and keypad sizes. */
    val pinFieldHeight = 54.dp
    val keypadKeyMinHeight = 58.dp

    /** Focus ring: a 3dp gold ring drawn 3dp outside the control. */
    val focusRingWidth = 3.dp
    val focusRingGap = 3.dp

    val navItemMinHeight = 62.dp
    val railWidth = 90.dp
    val railItemWidth = 70.dp
    val railItemMinHeight = 60.dp
}
