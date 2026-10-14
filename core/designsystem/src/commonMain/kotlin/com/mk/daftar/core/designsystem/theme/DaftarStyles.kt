package com.mk.daftar.core.designsystem.theme

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.contentPaddingVertical
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.selected
import androidx.compose.ui.unit.dp

/**
 * Container styles for the design system: shape, fill, border, padding and state changes.
 * Components apply their base style from here and accept a `style` parameter that callers
 * chain on top. Text and icon colours come from the component (see [ProvideContent]), because
 * Material text does not read colours from a style.
 */
object DaftarStyles {
    val primaryButton: Style = Style {
        shape(shapes.control)
        background(colors.primary)
        contentPadding(horizontal = 16.dp, vertical = 11.dp)
        pressed { animate { alpha(PRESSED_ALPHA) } }
        disabled { alpha(DISABLED_ALPHA) }
    }

    val outlineButton: Style = Style {
        shape(shapes.control)
        borderWidth(1.dp)
        borderColor(colors.line)
        contentPadding(horizontal = 16.dp, vertical = 11.dp)
        pressed { animate { alpha(PRESSED_ALPHA) } }
        disabled { alpha(DISABLED_ALPHA) }
    }

    val textButton: Style = Style {
        shape(shapes.control)
        contentPadding(horizontal = 8.dp, vertical = 8.dp)
        pressed { animate { alpha(PRESSED_ALPHA) } }
        disabled { alpha(DISABLED_ALPHA) }
    }

    val iconButton: Style = Style {
        shape(shapes.round)
        pressed { animate { alpha(PRESSED_ALPHA) } }
        disabled { alpha(DISABLED_ALPHA) }
    }

    /** Single-choice segments in one bordered strip; the strip clips its own corners. */
    val segmentedControl: Style = Style {
        shape(shapes.control)
        clip()
        borderWidth(1.dp)
        borderColor(colors.line)
        disabled { alpha(DISABLED_ALPHA) }
    }

    val segment: Style = Style {
        contentPadding(horizontal = 6.dp, vertical = 6.dp)
        selected { animate { background(colors.soft) } }
        pressed { animate { alpha(PRESSED_ALPHA) } }
        disabled { alpha(DISABLED_ALPHA) }
    }

    val field: Style = Style {
        shape(shapes.field)
        background(colors.surface)
        borderWidth(1.dp)
        borderColor(colors.line)
        contentPadding(12.dp)
        disabled { alpha(DISABLED_ALPHA) }
    }

    /** Layered over [field] when the value is invalid: a 2dp error border. */
    val invalidField: Style = Style {
        borderWidth(2.dp)
        borderColor(colors.error)
    }

    val pinField: Style = Style {
        shape(shapes.control)
        background(colors.surface)
        borderWidth(1.dp)
        borderColor(colors.line)
        disabled { alpha(DISABLED_ALPHA) }
    }

    val keypadKey: Style = Style {
        shape(shapes.surface)
        background(colors.surface)
        pressed { animate { alpha(PRESSED_ALPHA) } }
        disabled { alpha(DISABLED_ALPHA) }
    }

    /** Switch track: olive when on, line-coloured when off. */
    val switchTrack: Style = Style {
        shape(shapes.pill)
        background(colors.line)
        contentPadding(4.dp)
        selected { animate { background(colors.primary) } }
        disabled { alpha(DISABLED_ALPHA) }
    }

    /** Pill behind the selected navigation destination. */
    val navIndicator: Style = Style {
        shape(shapes.pill)
        selected { animate { background(colors.soft) } }
    }

    val railItem: Style = Style {
        shape(shapes.control)
        contentPadding(horizontal = 4.dp, vertical = 8.dp)
        selected { animate { background(colors.soft) } }
        pressed { animate { alpha(PRESSED_ALPHA) } }
    }

    val ledgerRow: Style = Style {
        contentPaddingVertical(15.dp)
        pressed { animate { alpha(PRESSED_ALPHA) } }
    }

    val settingRow: Style = Style {
        contentPaddingVertical(12.dp)
        pressed { animate { alpha(PRESSED_ALPHA) } }
    }

    val tileIcon: Style = Style {
        shape(shapes.tile)
        background(colors.soft)
    }

    val goldTileIcon: Style = Style {
        shape(shapes.tile)
        background(colors.goldTile)
    }

    val bankTileIcon: Style = Style {
        shape(shapes.tile)
        background(colors.bankTile)
    }

    val cashTileIcon: Style = Style {
        shape(shapes.tile)
        background(colors.cashTile)
    }

    val investmentTileIcon: Style = Style {
        shape(shapes.tile)
        background(colors.investmentTile)
    }

    val statusTag: Style = Style {
        shape(shapes.tag)
        background(colors.soft)
        contentPadding(horizontal = 8.dp, vertical = 3.dp)
    }

    val warningStatusTag: Style = Style {
        background(colors.warningSoft)
    }

    /** Quiet context note: a "why" explanation or a hint. */
    val contextNote: Style = Style {
        shape(shapes.control)
        background(colors.soft)
        contentPadding(13.dp)
    }

    val warningNote: Style = Style {
        background(colors.warningSoft)
    }

    val errorNote: Style = Style {
        background(colors.errorSoft)
    }

    /** Notebook card: a bordered surface with generous padding. */
    val notebookCard: Style = Style {
        shape(shapes.control)
        background(colors.surface)
        borderWidth(1.dp)
        borderColor(colors.line)
        contentPadding(17.dp)
    }

    val quoteStrip: Style = Style {
        shape(shapes.control)
        background(colors.surface)
        borderWidth(1.dp)
        borderColor(colors.line)
        contentPadding(12.dp)
    }

    val emblem: Style = Style {
        shape(shapes.round)
        background(colors.soft)
    }

    val skeleton: Style = Style {
        shape(shapes.skeleton)
        background(colors.soft)
    }

    /** Pressed alpha is the only touch feedback; layout never changes. */
    private const val PRESSED_ALPHA = 0.95f
    private const val DISABLED_ALPHA = 0.45f
}
