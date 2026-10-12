package com.mk.daftar.platform

import com.mk.daftar.core.domain.repository.AppLanguage
import platform.Foundation.NSUserDefaults

/**
 * The app's own `AppleLanguages` default, the value Settings also edits for this app. The shell's language host
 * rebuilds the UI for it, so strings resolve in the new language at once.
 */
actual fun applyAppLanguage(language: AppLanguage) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(language.localeTag), forKey = "AppleLanguages")
}
