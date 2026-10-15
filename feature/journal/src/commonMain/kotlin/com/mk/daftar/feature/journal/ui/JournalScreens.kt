package com.mk.daftar.feature.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarSkeletonSize
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.navigation.LocalNavigator
import com.mk.daftar.core.navigation.TopLevelDestination
import com.mk.daftar.core.ui.mvi.CollectEffects
import com.mk.daftar.core.ui.shell.DesktopWidths
import com.mk.daftar.core.ui.shell.RootReselectScroll
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.core.ui.shell.desktopPage
import com.mk.daftar.feature.journal.JournalEffect
import com.mk.daftar.feature.journal.JournalIntent
import com.mk.daftar.feature.journal.JournalPhase
import com.mk.daftar.feature.journal.JournalUiState
import com.mk.daftar.feature.journal.JournalViewModel
import com.mk.daftar.feature.journal.resources.Res
import com.mk.daftar.feature.journal.resources.journal_heading
import com.mk.daftar.feature.journal.resources.journal_ledger
import com.mk.daftar.feature.journal.resources.journal_load_failed_message
import com.mk.daftar.feature.journal.resources.journal_load_failed_title
import com.mk.daftar.feature.journal.resources.journal_notes
import com.mk.daftar.feature.journal.resources.journal_notes_count
import com.mk.daftar.feature.journal.resources.journal_title
import com.mk.daftar.feature.journal.resources.journal_zakat_calculator
import com.mk.daftar.feature.journal.resources.journal_zakat_calculator_help
import com.mk.daftar.feature.journal.resources.journal_zakat_review
import com.mk.daftar.feature.journal.resources.zakat_due_count
import com.mk.daftar.feature.journal.resources.zakat_reminder
import com.mk.daftar.feature.journal.resources.zakat_year_label
import com.mk.daftar.feature.journal.zakat.ZakatYearSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The Journal tab: the overview that links the zakat log and the notes. It holds no record of its own. */
@Composable
fun JournalScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val viewModel: JournalViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        if (effect is JournalEffect.Navigate) navigator.goTo(effect.route)
    }
    JournalContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/** The overview as it is drawn. Screenshot tests render it with fixed states. */
@Composable
fun JournalContent(state: JournalUiState, onIntent: (JournalIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    val scroll = rememberScrollState()
    RootReselectScroll(TopLevelDestination.JOURNAL) { scroll.animateScrollTo(0) }
    ShellScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .desktopPage(DesktopWidths.Page)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = DaftarSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            DaftarTopBar(
                title = stringResource(Res.string.journal_title),
                actions = { VisibilityToggle(hidden = state.hidden) { onIntent(JournalIntent.ToggleHidden) } },
            )
            Text(
                text = stringResource(Res.string.journal_heading),
                style = typography.screenHeading,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            when (state.phase) {
                JournalPhase.Loading -> DaftarSkeleton(size = DaftarSkeletonSize.Big)

                JournalPhase.Locked -> Unit

                is JournalPhase.Failed -> DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
                    Text(stringResource(Res.string.journal_load_failed_title))
                    Text(stringResource(Res.string.journal_load_failed_message))
                }

                JournalPhase.Ready, JournalPhase.Empty, JournalPhase.Missing -> {
                    if (state.dueYears.isNotEmpty()) {
                        DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Clock) {
                            Text(stringResource(Res.string.zakat_reminder))
                        }
                        DaftarLedgerRow(
                            title = stringResource(Res.string.journal_zakat_review),
                            subtitle = reviewSubtitle(state.dueYears),
                            icon = DaftarIcons.Heart,
                            onClick = { onIntent(JournalIntent.OpenReview) },
                        )
                    }
                    DaftarLedgerRow(
                        title = stringResource(Res.string.journal_zakat_calculator),
                        subtitle = stringResource(Res.string.journal_zakat_calculator_help),
                        icon = DaftarIcons.Heart,
                        onClick = { onIntent(JournalIntent.OpenCalculator) },
                    )
                    DaftarLedgerRow(
                        title = stringResource(Res.string.journal_notes),
                        subtitle = pluralStringResource(
                            Res.plurals.journal_notes_count,
                            state.noteCount,
                            state.noteCount,
                        ),
                        icon = DaftarIcons.Note,
                        onClick = { onIntent(JournalIntent.OpenNotes) },
                    )
                    DaftarNotePanel(modifier = Modifier.padding(top = DaftarSpacing.xs)) {
                        Text(stringResource(Res.string.journal_ledger))
                    }
                }
            }
        }
    }
}

/**
 * The review row's subtitle: the year's label when one year awaits confirmation, its count when several do. The count
 * is never shown with an amount, and no English suffix is concatenated.
 */
@Composable
private fun reviewSubtitle(due: List<ZakatYearSummary>): String = if (due.size == 1) {
    val year = due.single()
    stringResource(Res.string.zakat_year_label, year.hijriYear, year.gregorianYear)
} else {
    pluralStringResource(Res.plurals.zakat_due_count, due.size, due.size)
}
