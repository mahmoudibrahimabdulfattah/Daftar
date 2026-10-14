package com.mk.daftar.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.UIKit.UIPasteboardOptionLocalOnly

/** Local to this device (no Universal Clipboard) and expiring after a minute. */
@Composable
internal actual fun rememberSensitiveCopy(): (String) -> Unit = remember {
    { text: String ->
        UIPasteboard.generalPasteboard.setItems(
            listOf(mapOf<Any?, Any?>("public.utf8-plain-text" to text)),
            mapOf<Any?, Any?>(
                UIPasteboardOptionLocalOnly to true,
                UIPasteboardOptionExpirationDate to
                    NSDate.dateWithTimeIntervalSinceNow(SENSITIVE_CLIP_SECONDS.toDouble()),
            ),
        )
    }
}
