package com.mk.daftar.core.account.di

import org.koin.core.module.Module

/** The platform's sign-in gateway: Google through Credential Manager on Android, Sign in with Apple on iOS. */
expect val accountModule: Module
