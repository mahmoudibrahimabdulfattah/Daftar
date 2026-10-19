package com.mk.daftar.update

import com.mk.daftar.core.domain.update.InstalledBuildProvider

/** The build number the platform reports for this installed app. */
internal class AppInstalledBuildProvider(override val buildNumber: String) : InstalledBuildProvider
