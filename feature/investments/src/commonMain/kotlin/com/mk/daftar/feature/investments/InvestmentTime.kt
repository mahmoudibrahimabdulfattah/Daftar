package com.mk.daftar.feature.investments

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** The zone every value date is chosen and shown in. Stored instants are UTC; only the display follows Cairo. */
internal val CairoZone: TimeZone = TimeZone.of("Africa/Cairo")

/**
 * The instant of a Cairo local date and time, or null when that time does not exist there (a spring-forward gap) or is
 * ambiguous (a fall-back overlap). An ambiguous time needs an explicit offset choice, which this path does not offer, so
 * it is rejected rather than guessed.
 */
internal fun cairoInstantOf(local: LocalDateTime): Instant? {
    val instant = local.toInstant(CairoZone)
    if (instant.toLocalDateTime(CairoZone) != local) return null
    val earlier = instant - 1.hours
    val later = instant + 1.hours
    val ambiguous = earlier.toLocalDateTime(CairoZone) == local || later.toLocalDateTime(CairoZone) == local
    return instant.takeUnless { ambiguous }
}

/** The Cairo local date and time of [instant], for the picker and the display. */
internal fun cairoLocalOf(instant: Instant): LocalDateTime = instant.toLocalDateTime(CairoZone)
