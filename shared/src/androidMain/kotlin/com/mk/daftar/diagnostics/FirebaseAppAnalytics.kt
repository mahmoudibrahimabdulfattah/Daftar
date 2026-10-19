package com.mk.daftar.diagnostics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Logs one `screen_view` per visible screen, named by a fixed literal. The SDK is used only when Firebase is configured
 * in this build, so a build without google-services.json logs nothing.
 */
internal class FirebaseAppAnalytics(private val context: Context) : AppAnalytics {
    private val analytics: FirebaseAnalytics? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalytics.getInstance(context)
    }

    override fun screen(name: String) {
        analytics?.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, name)
            },
        )
    }
}
