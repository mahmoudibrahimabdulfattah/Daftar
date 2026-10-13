package com.mk.daftar.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Daftar's colour roles, named after the approved design tokens. All colours are opaque. */
@Immutable
data class DaftarColors(
    /** Screen background. */
    val paper: Color,
    /** Fields, cards, navigation and sheets. */
    val surface: Color,
    /** Primary text. */
    val ink: Color,
    /** Supporting text. */
    val muted: Color,
    /** Olive: primary actions, live status, selected navigation. */
    val primary: Color,
    /** Content drawn on [primary]. */
    val onPrimary: Color,
    /** Quiet fill for selected items, icon tiles and context notes. */
    val soft: Color,
    /** Hairlines, dividers and outlines. */
    val line: Color,
    /** Brass, reserved for gold only. */
    val gold: Color,
    /** Fill behind gold icons and gold rows. */
    val goldSoft: Color,
    /** Category tiles: strong opaque fills with a paired glyph colour, for gold, banks and cash. */
    val goldTile: Color,
    val onGoldTile: Color,
    val bankTile: Color,
    val onBankTile: Color,
    val cashTile: Color,
    val onCashTile: Color,
    val error: Color,
    val errorSoft: Color,
    /** Cached, manual and estimate status, and warning notes. */
    val warning: Color,
    val warningSoft: Color,
    val isDark: Boolean,
)

internal val LightColors = DaftarColors(
    paper = Color(0xFFF5F3EB),
    surface = Color(0xFFFFFEF8),
    ink = Color(0xFF262D24),
    muted = Color(0xFF626657),
    primary = Color(0xFF45583D),
    onPrimary = Color(0xFFFFFEF8),
    soft = Color(0xFFE4E8D9),
    line = Color(0xFFC8CBBB),
    gold = Color(0xFF835B24),
    goldSoft = Color(0xFFF1E4CC),
    goldTile = Color(0xFF59401F),
    onGoldTile = Color(0xFFF3CE88),
    bankTile = Color(0xFF45583D),
    onBankTile = Color(0xFFFFFEF8),
    cashTile = Color(0xFFCCD8BE),
    onCashTile = Color(0xFF2C4225),
    error = Color(0xFFA1332C),
    errorSoft = Color(0xFFFBE3DC),
    warning = Color(0xFF78521D),
    warningSoft = Color(0xFFF7E9CA),
    isDark = false,
)

internal val DarkColors = DaftarColors(
    paper = Color(0xFF191E18),
    surface = Color(0xFF232A21),
    ink = Color(0xFFEFF0E6),
    muted = Color(0xFFBDC4B3),
    primary = Color(0xFFBFD0AD),
    onPrimary = Color(0xFF23331E),
    soft = Color(0xFF35432E),
    line = Color(0xFF515D49),
    gold = Color(0xFFE7BF80),
    goldSoft = Color(0xFF413524),
    goldTile = Color(0xFF6B4C24),
    onGoldTile = Color(0xFFFFE0A3),
    bankTile = Color(0xFF526747),
    onBankTile = Color(0xFFFFFEF8),
    cashTile = Color(0xFF97AD80),
    onCashTile = Color(0xFF1E3018),
    error = Color(0xFFFFB4A9),
    errorSoft = Color(0xFF482723),
    warning = Color(0xFFF0CC87),
    warningSoft = Color(0xFF42371F),
    isDark = true,
)
