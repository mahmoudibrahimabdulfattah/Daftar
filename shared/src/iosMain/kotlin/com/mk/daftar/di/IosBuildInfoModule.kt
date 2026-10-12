package com.mk.daftar.di

import com.mk.daftar.feature.settings.AppBuildInfo
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle

/** Read from the app bundle's own Info.plist, so the About screen shows what this build really is. */
internal actual val platformBuildInfoModule: Module = module {
    single {
        val info = NSBundle.mainBundle.infoDictionary
        AppBuildInfo(
            versionName = info?.get("CFBundleShortVersionString") as? String ?: "",
            buildNumber = info?.get("CFBundleVersion") as? String ?: "",
        )
    }
}
