package com.mk.daftar.core.common

import kotlin.time.Duration
import kotlin.time.TimeSource

/** Elapsed time on a clock that never goes backward. Penalties and inactivity use it, never wall-clock time. */
fun interface MonotonicTime {
    fun now(): Duration
}

/** The process's monotonic clock, measured from the moment it is first read. */
object SystemMonotonicTime : MonotonicTime {
    private val origin = TimeSource.Monotonic.markNow()

    override fun now(): Duration = origin.elapsedNow()
}
