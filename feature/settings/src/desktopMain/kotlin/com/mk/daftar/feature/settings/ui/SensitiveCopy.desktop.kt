package com.mk.daftar.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.Timer
import kotlin.concurrent.schedule

/**
 * The desktop clipboard has no sensitive flag, so the clip is cleared after a minute when it is still the one this
 * screen copied. Clipboard history tools may still record it; the screen tells the owner to write the key down.
 */
@Composable
internal actual fun rememberSensitiveCopy(): (String) -> Unit = remember {
    { text: String ->
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
        Timer(true).schedule(SENSITIVE_CLIP_SECONDS * 1000) {
            val current = runCatching { clipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull()
            if (current == text) clipboard.setContents(StringSelection(""), null)
        }
    }
}
