package com.mk.daftar.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The line icons from the approved design (24×24 viewBox, 1.7 stroke, round caps and joins).
 * They are drawn in black and tinted by [com.mk.daftar.core.designsystem.component.DaftarIcon].
 * Directional icons (arrows, chevrons) mirror in RTL; gold and fingerprint do not.
 */
object DaftarIcons {
    val Book: ImageVector by lazy {
        lineIcon(
            "Book",
            "M7 3H17A2 2 0 0 1 19 5V19A2 2 0 0 1 17 21H7A2 2 0 0 1 5 19V5A2 2 0 0 1 7 3Z M9 " +
                "3v18m3-12h4m-4 4h4",
        )
    }

    val Home: ImageVector by lazy {
        lineIcon(
            "Home",
            "m3 10 9-7 9 7v10H3Z M9 20v-7h6v7",
        )
    }

    val Gold: ImageVector by lazy {
        lineIcon(
            "Gold",
            "m6 7-3 11h18L18 7Z M6 7h12M8 11h8",
        )
    }

    /** A solid ingot for gold category tiles: one even-odd path whose lower seam is cut out to the tile. */
    val GoldBar: ImageVector by lazy {
        ImageVector.Builder("GoldBar", 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes("M6 5H18L22 16V19H2V16Z M4.5 15L3.8 17H20.2L19.5 15Z"),
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            )
            .build()
    }

    val Bank: ImageVector by lazy {
        lineIcon(
            "Bank",
            "m3 8 9-5 9 5H3Zm2 3v7m7-7v7m7-7v7M3 21h18",
        )
    }

    val Wallet: ImageVector by lazy {
        lineIcon(
            "Wallet",
            "M5 5H19A2 2 0 0 1 21 7V18A2 2 0 0 1 19 20H5A2 2 0 0 1 3 18V7A2 2 0 0 1 5 5Z M3 8h18m-5 " +
                "5h5v4h-5z",
        )
    }

    val Note: ImageVector by lazy {
        lineIcon(
            "Note",
            "M5 3h14v18H5Zm4 5h6m-6 4h6m-6 4h4",
        )
    }

    val Heart: ImageVector by lazy {
        lineIcon(
            "Heart",
            "M20 5c-3-3-7 0-8 2-1-2-5-5-8-2-5 5 3 11 8 15 5-4 13-10 8-15Z",
        )
    }

    val Settings: ImageVector by lazy {
        lineIcon(
            "Settings",
            "M4 7h16M4 17h16 M6 7A3 3 0 1 0 12 7A3 3 0 1 0 6 7Z M13 17A3 3 0 1 0 19 17A3 3 0 1 0 13 " +
                "17Z",
        )
    }

    val Eye: ImageVector by lazy {
        lineIcon(
            "Eye",
            "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z M9 12A3 3 0 1 0 15 12A3 3 0 1 0 9 12Z",
        )
    }

    val EyeOff: ImageVector by lazy {
        lineIcon(
            "EyeOff",
            "m3 3 18 18M9 5c7-2 13 7 13 7s-2 4-6 6M6 6c-3 2-4 6-4 6s4 7 10 7m-3-9 5 5",
        )
    }

    val Back: ImageVector by lazy {
        lineIcon(
            "Back",
            "m14 5-7 7 7 7",
        )
    }

    val Plus: ImageVector by lazy {
        lineIcon(
            "Plus",
            "M12 4v16M4 12h16",
        )
    }

    val Lock: ImageVector by lazy {
        lineIcon(
            "Lock",
            "M7 10H17A2 2 0 0 1 19 12V19A2 2 0 0 1 17 21H7A2 2 0 0 1 5 19V12A2 2 0 0 1 7 10Z M8 " +
                "10V7a4 4 0 0 1 8 0v3m-4 5v2",
        )
    }

    val Fingerprint: ImageVector by lazy {
        lineIcon(
            "Fingerprint",
            "M4 12a8 8 0 0 1 16 0m-12 6v-6a4 4 0 0 1 8 0v7m-4-7v10M4 16v3m16-3v3",
        )
    }

    val Refresh: ImageVector by lazy {
        lineIcon(
            "Refresh",
            "M20 9a8 8 0 1 0 0 6M20 3v6h-6",
        )
    }

    val Check: ImageVector by lazy {
        lineIcon(
            "Check",
            "m5 12 5 5L20 7",
        )
    }

    val Alert: ImageVector by lazy {
        lineIcon(
            "Alert",
            "m12 3 10 18H2Z M12 9v5m0 3v1",
        )
    }

    val Chevron: ImageVector by lazy {
        lineIcon(
            "Chevron",
            "m9 5 7 7-7 7",
        )
    }

    val Close: ImageVector by lazy {
        lineIcon(
            "Close",
            "m6 6 12 12M6 18 18 6",
        )
    }

    val Backspace: ImageVector by lazy {
        lineIcon(
            "Backspace",
            "m8 5-6 7 6 7h13V5Zm4 4 6 6m-6 0 6-6",
        )
    }

    val Clock: ImageVector by lazy {
        lineIcon(
            "Clock",
            "M3 12A9 9 0 1 0 21 12A9 9 0 1 0 3 12Z M12 7v5l3 2",
        )
    }

    val Export: ImageVector by lazy {
        lineIcon(
            "Export",
            "M4 14v6h16v-6M12 3v12m-5-7 5-5 5 5",
        )
    }

    val Import: ImageVector by lazy {
        lineIcon(
            "Import",
            "M4 14v6h16v-6M12 3v12m-5-5 5 5 5-5",
        )
    }

    val Offline: ImageVector by lazy {
        lineIcon(
            "Offline",
            "m3 3 18 18M3 9c3-3 5-4 9-4s7 1 9 4M6 13c2-2 4-3 7-2m-4 5 3-1 3 1M12 20h.01",
        )
    }

    val Edit: ImageVector by lazy {
        lineIcon(
            "Edit",
            "m4 16 12-12 4 4L8 20H4Zm9-9 4 4",
        )
    }

    val Calendar: ImageVector by lazy {
        lineIcon(
            "Calendar",
            "M5 6H19A2 2 0 0 1 21 8V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V8A2 2 0 0 1 5 6Z M8 3V7M16 " +
                "3V7M3 11H21",
        )
    }
}

private fun lineIcon(name: String, pathData: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(
        pathData = addPathNodes(pathData),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    .build()
