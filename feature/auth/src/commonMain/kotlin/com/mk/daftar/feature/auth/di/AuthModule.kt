package com.mk.daftar.feature.auth.di

import com.mk.daftar.feature.auth.AuthDraftRegistry
import com.mk.daftar.feature.auth.AuthViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The auth screens. The gateway, the clock and the privacy registry are provided by the application graph. */
val authModule: Module = module {
    single { AuthDraftRegistry() }
    viewModelOf(::AuthViewModel)
}
