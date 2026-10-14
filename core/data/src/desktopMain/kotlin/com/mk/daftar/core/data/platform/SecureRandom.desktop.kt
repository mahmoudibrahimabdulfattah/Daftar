package com.mk.daftar.core.data.ids

import java.security.SecureRandom

private val random = SecureRandom()

internal actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }
