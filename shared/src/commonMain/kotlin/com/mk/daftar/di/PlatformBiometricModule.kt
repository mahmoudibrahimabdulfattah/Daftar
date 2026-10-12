package com.mk.daftar.di

import org.koin.core.module.Module

/** The platform biometric prompt adapter: BiometricPrompt on Android, the Secure Enclave's LocalAuthentication on iOS. */
internal expect val platformBiometricModule: Module
