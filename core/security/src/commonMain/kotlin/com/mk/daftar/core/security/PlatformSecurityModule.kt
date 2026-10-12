package com.mk.daftar.core.security

import org.koin.core.module.Module

/** Platform key protection and durable stores: Keystore on Android, Secure Enclave and Keychain on iOS. */
internal expect val platformSecurityModule: Module
