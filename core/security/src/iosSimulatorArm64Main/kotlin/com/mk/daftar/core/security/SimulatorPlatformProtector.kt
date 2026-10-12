package com.mk.daftar.core.security

internal actual fun createPlatformEnvelopeProtector(): PlatformEnvelopeProtector = SimulatorOnlyTestEnvelopeProtector()
