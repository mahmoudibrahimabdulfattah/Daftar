package com.mk.daftar.core.domain.zakat

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The civil zone every zakat date is measured in, whatever the device's current zone or travel. */
val ZAKAT_TIME_ZONE: TimeZone = TimeZone.of("Africa/Cairo")

/** The civil date in [ZAKAT_TIME_ZONE] at [now]. Due dates compare civil dates, never elapsed hours. */
fun zakatToday(now: Instant): LocalDate = now.toLocalDateTime(ZAKAT_TIME_ZONE).date
