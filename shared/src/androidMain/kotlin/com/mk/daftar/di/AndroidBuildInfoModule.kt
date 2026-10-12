package com.mk.daftar.di

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import com.mk.daftar.feature.settings.AppBuildInfo
import org.koin.core.module.Module
import org.koin.dsl.module

/** Read from the installed package, so the About screen shows what this build really is. */
internal actual val platformBuildInfoModule: Module = module {
    single { androidBuildInfo(get<Context>()) }
}

@Suppress("DEPRECATION")
private fun androidBuildInfo(context: Context): AppBuildInfo {
    val info: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    return AppBuildInfo(versionName = info.versionName.orEmpty(), buildNumber = build.toString())
}
