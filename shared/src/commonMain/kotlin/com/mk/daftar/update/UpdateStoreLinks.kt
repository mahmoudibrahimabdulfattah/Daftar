package com.mk.daftar.update

import androidx.compose.ui.platform.UriHandler

/** Where Android opens the store. The market link is tried first; the web link covers devices without the store app. */
internal object UpdateStoreLinks {
    const val ANDROID_STORE_URL: String = "market://details?id=com.mk.daftar"
    const val ANDROID_STORE_FALLBACK_URL: String = "https://play.google.com/store/apps/details?id=com.mk.daftar"
}

/** Opens the store, or the fallback link. Returns false when neither could be opened. */
internal fun openStore(uriHandler: UriHandler, storeUrl: String, fallbackUrl: String?): Boolean {
    if (runCatching { uriHandler.openUri(storeUrl) }.isSuccess) return true
    return fallbackUrl != null && runCatching { uriHandler.openUri(fallbackUrl) }.isSuccess
}
