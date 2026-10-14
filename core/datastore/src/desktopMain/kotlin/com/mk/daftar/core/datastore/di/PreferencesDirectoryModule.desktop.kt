package com.mk.daftar.core.datastore.di

import com.mk.daftar.core.common.desktop.DesktopAppDirectories
import org.koin.core.module.Module
import org.koin.dsl.module

/** The app's private directory: owner-only, device-only, and excluded from Time Machine on macOS. */
internal actual val preferencesDirectoryModule: Module = module {
    single(PreferencesDirectory) { DesktopAppDirectories.directory("preferences").absolutePath }
}
