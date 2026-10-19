package com.mk.daftar.diagnostics

/**
 * Reports which screen is shown, by a fixed name from `analyticsScreenName`. Nothing else leaves the app: no values,
 * identifiers, route arguments, notes or PINs. Platforms without an analytics SDK use [NoOpAppAnalytics].
 */
interface AppAnalytics {
    fun screen(name: String)
}

/** Discards every report. Used where no analytics SDK is configured. */
object NoOpAppAnalytics : AppAnalytics {
    override fun screen(name: String) = Unit
}
