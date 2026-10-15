package com.mk.daftar.platform

import com.mk.daftar.core.domain.repository.AppLanguage
import java.util.Locale

/**
 * The JVM default locale, which Compose Resources and the layout direction read on desktop. The shell's language host
 * rebuilds the UI for it, so strings resolve in the new language at once.
 */
actual fun applyAppLanguage(language: AppLanguage) {
    val locale = Locale.forLanguageTag(language.localeTag)
    if (Locale.getDefault().language == locale.language) return
    Locale.setDefault(locale)
}
