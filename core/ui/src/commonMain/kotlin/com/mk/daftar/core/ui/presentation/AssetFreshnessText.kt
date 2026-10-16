package com.mk.daftar.core.ui.presentation

import androidx.compose.runtime.Composable
import com.mk.daftar.core.domain.refresh.PriceAsset
import com.mk.daftar.core.model.SourceTime
import com.mk.daftar.core.ui.resources.Res
import com.mk.daftar.core.ui.resources.freshness_checked
import com.mk.daftar.core.ui.resources.freshness_date
import com.mk.daftar.core.ui.resources.freshness_date_year
import com.mk.daftar.core.ui.resources.freshness_gold
import com.mk.daftar.core.ui.resources.freshness_manual
import com.mk.daftar.core.ui.resources.freshness_months
import com.mk.daftar.core.ui.resources.freshness_saved_failed
import com.mk.daftar.core.ui.resources.freshness_source_unknown
import com.mk.daftar.core.ui.resources.freshness_stamp
import com.mk.daftar.core.ui.resources.freshness_time_uncertain
import com.mk.daftar.core.ui.resources.freshness_today
import com.mk.daftar.core.ui.resources.freshness_updated
import com.mk.daftar.core.ui.resources.freshness_updated_today
import com.mk.daftar.core.ui.resources.freshness_updated_today_at
import com.mk.daftar.core.ui.resources.freshness_usd
import com.mk.daftar.core.ui.resources.quotes_waiting
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * One asset's freshness line, prefixed with the asset: "Gold: updated today, 14:05", "Dollar: updated 15 Oct, 14:00 ·
 * checked today". The source's time is never replaced by the fetch time; a successful check on a later day is said
 * separately, so an unchanged publication still shows that the refresh worked. Dates are Cairo dates; [now] decides
 * what "today" is.
 */
@Composable
fun assetFreshnessText(asset: PriceAsset, freshness: AssetFreshness?, now: Instant): String {
    val months = stringArrayResource(Res.array.freshness_months)
    val today = now.toLocalDateTime(CAIRO).date
    val body = when (freshness) {
        null -> stringResource(Res.string.quotes_waiting)

        AssetFreshness.TimeUncertain -> stringResource(Res.string.freshness_time_uncertain)

        is AssetFreshness.Manual -> stringResource(
            Res.string.freshness_manual,
            stampText(freshness.enteredAt, months, today),
        )

        is AssetFreshness.Remote -> {
            val checked = freshness.checkedAt.toLocalDateTime(CAIRO).date
            val checkedText = if (checked == today) {
                stringResource(Res.string.freshness_today)
            } else {
                stampText(freshness.checkedAt, months, today)
            }
            val source = freshness.source
            when {
                freshness.failed -> stringResource(
                    Res.string.freshness_saved_failed,
                    when (source) {
                        is SourceTime.Timestamp -> stampText(source.value, months, today)
                        is SourceTime.DateOnly -> source.value.label(months, today)
                        SourceTime.Unknown -> stampText(freshness.checkedAt, months, today)
                    },
                )

                source is SourceTime.Timestamp && source.value.toLocalDateTime(CAIRO).date == today ->
                    stringResource(Res.string.freshness_updated_today_at, timeText(source.value))

                source is SourceTime.DateOnly && source.value == today -> stringResource(
                    Res.string.freshness_updated_today,
                )

                source is SourceTime.Timestamp -> updatedChecked(stampText(source.value, months, today), checkedText)

                source is SourceTime.DateOnly -> updatedChecked(source.value.label(months, today), checkedText)

                else -> stringResource(Res.string.freshness_source_unknown, checkedText)
            }
        }
    }
    return stringResource(
        when (asset) {
            PriceAsset.GOLD -> Res.string.freshness_gold
            PriceAsset.USD -> Res.string.freshness_usd
        },
        body,
    )
}

@Composable
private fun updatedChecked(updated: String, checked: String): String =
    stringResource(Res.string.freshness_updated, updated) + " · " +
        stringResource(Res.string.freshness_checked, checked)

@Composable
private fun stampText(at: Instant, months: List<String>, today: LocalDate): String {
    val date = at.toLocalDateTime(CAIRO).date
    return stringResource(Res.string.freshness_stamp, date.label(months, today), timeText(at))
}

@Composable
private fun LocalDate.label(months: List<String>, today: LocalDate): String {
    val month = months[month.ordinal]
    return if (year == today.year) {
        stringResource(Res.string.freshness_date, day, month)
    } else {
        stringResource(Res.string.freshness_date_year, day, month, year)
    }
}

private fun timeText(at: Instant): String {
    val local = at.toLocalDateTime(CAIRO)
    return "$LTR${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}$POP"
}

private val CAIRO: TimeZone = TimeZone.of("Africa/Cairo")
private const val LTR = "⁦"
private const val POP = "⁩"
