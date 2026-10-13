package com.mk.daftar.feature.journal.zakat

import com.mk.daftar.core.domain.hijri.HijriCalendar
import com.mk.daftar.core.domain.hijri.RamadanDate
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.zakat.ZakatYearRules
import com.mk.daftar.core.domain.zakat.ZakatYearStatus
import com.mk.daftar.core.model.ZakatYear as DeclaredYear
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.nonReadyPhase
import kotlinx.datetime.LocalDate

/** A tracked year as the screens show it: its key, its civil Ramadan date and its status. Never an amount. */
data class ZakatYearSummary(
    val hijriYear: Int,
    val gregorianYear: Int,
    val status: ZakatYearStatus,
    val date: LocalDate,
    val adjusted: Boolean,
)

internal fun DeclaredYear.summary(today: LocalDate): ZakatYearSummary {
    val date = ZakatYearRules.dateOf(this)
    return ZakatYearSummary(
        hijriYear = hijriYear,
        gregorianYear = date.year,
        status = ZakatYearRules.statusOf(this, today),
        date = date,
        adjusted = userAdjusted,
    )
}

/** Every tracked year, newest first, for the history list. */
internal fun ZakatTrackingState.summariesNewestFirst(today: LocalDate): List<ZakatYearSummary> =
    years.map { it.summary(today) }.sortedByDescending { it.hijriYear }

/** The Hijri years awaiting confirmation, oldest first. Paid years never appear. */
internal fun ZakatTrackingState.dueYears(today: LocalDate): List<Int> = dueSummaries(today).map { it.hijriYear }

/** The years awaiting confirmation, oldest first. Paid years never appear. */
internal fun ZakatTrackingState.dueSummaries(today: LocalDate): List<ZakatYearSummary> =
    summariesNewestFirst(today).filter { it.status == ZakatYearStatus.DUE }.sortedBy { it.hijriYear }

/** The per-year Ramadan adjustments, keyed by year. Empty while tracking has not been read, so the calculated date applies. */
internal fun ZakatTrackingState?.adjustments(): Map<Int, Int> =
    this?.years?.associate { it.hijriYear to it.overrideDays }.orEmpty()

/** The next Ramadan that has not passed, with its adjusted civil date. Null when every supported Ramadan has passed. */
internal fun nextRamadanOf(today: LocalDate, adjustments: Map<Int, Int>): RamadanDate? =
    HijriCalendar.nextRamadan(today, adjustments)

/** Where a review should open: one due year opens its own page; several open the history, oldest due first. */
internal fun reviewDestinationOf(due: List<Int>): ReviewDestination? = when {
    due.isEmpty() -> null
    due.size == 1 -> ReviewDestination.Year(due.single())
    else -> ReviewDestination.History
}

sealed interface ReviewDestination {
    data class Year(val hijriYear: Int) : ReviewDestination

    data object History : ReviewDestination
}

/** Hijri years typed with ASCII or Arabic-Indic digits, surrounding spaces allowed. Null when not a whole number. */
internal fun parseHijriYear(text: String): Int? {
    val ascii = buildString {
        for (ch in text.trim()) {
            append(if (ch in '٠'..'٩') '0' + (ch - '٠') else ch)
        }
    }
    if (ascii.isEmpty() || ascii.any { it !in '0'..'9' }) return null
    return ascii.toIntOrNull()
}

/** The first blocking phase among the reads, in the precedence locked, failed, loading. Null when all are ready. */
internal fun blockingPhaseOf(vararg reads: VaultRead<*>): JournalPhase? {
    val phases = reads.mapNotNull { it.nonReadyPhase() }
    return phases.firstOrNull { it == JournalPhase.Locked }
        ?: phases.firstOrNull { it is JournalPhase.Failed }
        ?: phases.firstOrNull()
}
