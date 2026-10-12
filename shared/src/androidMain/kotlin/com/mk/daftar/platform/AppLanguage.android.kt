package com.mk.daftar.platform

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mk.daftar.core.domain.repository.AppLanguage

/**
 * Per-app locales. AppCompat stores the choice for the app itself and applies it to the AppCompat activity, which
 * recreates it. The vault generation is held outside the activity, so the recreation keeps it open.
 */
actual fun applyAppLanguage(language: AppLanguage) {
    val tag = language.localeTag
    if (AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-') == tag) return
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}
