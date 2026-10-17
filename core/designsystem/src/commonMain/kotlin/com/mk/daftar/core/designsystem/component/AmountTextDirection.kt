package com.mk.daftar.core.designsystem.component

import androidx.compose.ui.text.style.TextDirection

/**
 * The paragraph direction of an amount, whose whole text is one LTR isolate. Unspecified follows the content; the Skia
 * text engine (desktop and iOS) measures an isolated LTR run inside an RTL paragraph too wide, so auto-size shrinks the
 * figure there unless the paragraph itself is LTR.
 */
internal expect val amountTextDirection: TextDirection
