package com.mk.daftar.feature.home

/** Trackpad scrolling must never start a pull; the top bar refresh action is used instead. */
internal actual val pullToRefreshAvailable: Boolean = false
