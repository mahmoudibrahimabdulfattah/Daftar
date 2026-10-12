package com.mk.daftar.di

import com.mk.daftar.core.security.BiometricPrompter
import com.mk.daftar.platform.AndroidBiometricPrompter
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformBiometricModule: Module = module {
    single<BiometricPrompter> { AndroidBiometricPrompter() }
}
