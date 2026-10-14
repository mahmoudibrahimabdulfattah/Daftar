package com.mk.daftar.feature.settings.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The clip is flagged sensitive, so Android hides it from the copy preview and keyboard suggestions, and it is cleared
 * after a minute when it is still the one this screen copied.
 */
@Composable
internal actual fun rememberSensitiveCopy(): (String) -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        { text: String ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("", text)
            clip.description.extras = PersistableBundle().apply {
                putBoolean(sensitiveExtra(), true)
            }
            clipboard.setPrimaryClip(clip)
            Handler(Looper.getMainLooper()).postDelayed({
                val current = clipboard.primaryClip
                val stillOurs =
                    current != null && current.itemCount > 0 && current.getItemAt(0).text?.toString() == text
                if (stillOurs) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }, SENSITIVE_CLIP_SECONDS * 1000)
        }
    }
}

private fun sensitiveExtra(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ClipDescription.EXTRA_IS_SENSITIVE
} else {
    "android.content.extra.IS_SENSITIVE"
}
