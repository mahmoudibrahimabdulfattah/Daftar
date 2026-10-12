package com.mk.daftar.platform

import com.mk.daftar.core.domain.repository.AppLanguage

/** The BCP 47 tag the platforms use for [this] language. */
internal val AppLanguage.localeTag: String
    get() = when (this) {
        AppLanguage.AR -> "ar"
        AppLanguage.EN -> "en"
    }

/**
 * Makes [language] the language the platform resolves resource strings in. Compose Resources reads the language from
 * the platform locale, and that locale changes only through these platform calls. Android uses per-app locales, which
 * recreate the activity without losing the vault generation; iOS sets the app's `AppleLanguages`.
 */
expect fun applyAppLanguage(language: AppLanguage)
