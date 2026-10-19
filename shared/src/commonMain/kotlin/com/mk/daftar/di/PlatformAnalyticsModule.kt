package com.mk.daftar.di

import org.koin.core.module.Module

/** The platform's analytics reporter. Only Android reports; the other platforms bind a no-op. */
internal expect val platformAnalyticsModule: Module
