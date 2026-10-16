package com.mk.daftar.feature.journal.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import com.mk.daftar.core.domain.usecase.ObserveZakatEstimateUseCase
import com.mk.daftar.core.navigation.JournalRoute
import com.mk.daftar.core.navigation.NoteAddRoute
import com.mk.daftar.core.navigation.NoteEditRoute
import com.mk.daftar.core.navigation.NotesRoute
import com.mk.daftar.core.navigation.ZakatAddRoute
import com.mk.daftar.core.navigation.ZakatCalculatorRoute
import com.mk.daftar.core.navigation.ZakatDateRoute
import com.mk.daftar.core.navigation.ZakatEditRoute
import com.mk.daftar.core.navigation.ZakatHistoryRoute
import com.mk.daftar.core.navigation.ZakatRoute
import com.mk.daftar.core.navigation.ZakatYearAddRoute
import com.mk.daftar.core.navigation.ZakatYearRoute
import com.mk.daftar.core.ui.shell.isDesktopPlatform
import com.mk.daftar.feature.journal.JournalViewModel
import com.mk.daftar.feature.journal.notes.NoteEditorTarget
import com.mk.daftar.feature.journal.notes.NoteEditorViewModel
import com.mk.daftar.feature.journal.notes.NotesListViewModel
import com.mk.daftar.feature.journal.ui.JournalScreen
import com.mk.daftar.feature.journal.ui.NoteEditorScreen
import com.mk.daftar.feature.journal.ui.NotesListScreen
import com.mk.daftar.feature.journal.ui.ZakatCalculatorScreen
import com.mk.daftar.feature.journal.ui.ZakatDateScreen
import com.mk.daftar.feature.journal.ui.ZakatEditorScreen
import com.mk.daftar.feature.journal.ui.ZakatHistoryScreen
import com.mk.daftar.feature.journal.ui.ZakatListScreen
import com.mk.daftar.feature.journal.ui.ZakatYearAddScreen
import com.mk.daftar.feature.journal.ui.ZakatYearPlaceholder
import com.mk.daftar.feature.journal.ui.ZakatYearScreen
import com.mk.daftar.feature.journal.zakat.ZakatCalculatorViewModel
import com.mk.daftar.feature.journal.zakat.ZakatDateViewModel
import com.mk.daftar.feature.journal.zakat.ZakatEditorLaunch
import com.mk.daftar.feature.journal.zakat.ZakatEditorTarget
import com.mk.daftar.feature.journal.zakat.ZakatEditorViewModel
import com.mk.daftar.feature.journal.zakat.ZakatHistoryViewModel
import com.mk.daftar.feature.journal.zakat.ZakatListViewModel
import com.mk.daftar.feature.journal.zakat.ZakatYearAddViewModel
import com.mk.daftar.feature.journal.zakat.ZakatYearViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * The Journal feature: the overview, the zakat declarations, calculator and payment log, and the personal notes, with
 * their editors. Their use cases and the clock come from the application graph; nothing here reaches the data layer.
 */
@OptIn(KoinExperimentalAPI::class, ExperimentalMaterial3AdaptiveApi::class)
val journalModule: Module = module {
    factory<TimeZone> { TimeZone.currentSystemDefault() }

    viewModelOf(::JournalViewModel)
    viewModelOf(::ZakatListViewModel)
    viewModel {
        ZakatCalculatorViewModel(
            estimates = { get<ObserveZakatEstimateUseCase>().invoke() },
            observeTracking = get(),
            observeDay = get(),
            visibility = get(),
        )
    }
    viewModelOf(::ZakatHistoryViewModel)
    viewModelOf(::ZakatYearAddViewModel)
    viewModelOf(::NotesListViewModel)
    viewModel { params ->
        ZakatYearViewModel(
            hijriYear = params.get(),
            observeTracking = get(),
            observePayments = get(),
            observeDay = get(),
            setPaid = get(),
            visibility = get(),
        )
    }
    viewModel { params ->
        ZakatDateViewModel(
            hijriYear = params.get(),
            observeTracking = get(),
            setAdjustment = get(),
        )
    }
    viewModel { params ->
        ZakatEditorViewModel(
            target = params.get<ZakatEditorTarget>(),
            observePayment = get(),
            observePayments = get(),
            observeTracking = get(),
            createPayment = get(),
            updatePayment = get(),
            deletePayment = get(),
            visibility = get(),
            clock = get(),
            timeZone = get(),
            launch = params.getOrNull<ZakatEditorLaunch>() ?: ZakatEditorLaunch(),
        )
    }
    viewModel { params ->
        NoteEditorViewModel(
            target = params.get<NoteEditorTarget>(),
            observeNote = get(),
            observeNotes = get(),
            createNote = get(),
            updateNote = get(),
            deleteNote = get(),
            visibility = get(),
        )
    }

    navigation<JournalRoute> { JournalScreen() }
    navigation<ZakatRoute> { ZakatListScreen() }
    navigation<ZakatCalculatorRoute> { ZakatCalculatorScreen() }
    // On desktop the years list and the selected year sit side by side (design phase 28); phones and tablets keep one
    // page at a time. Editors opened from a year stay full pages.
    navigation<ZakatHistoryRoute>(
        metadata = if (isDesktopPlatform) {
            ListDetailSceneStrategy.listPane(detailPlaceholder = {
                ZakatYearPlaceholder()
            })
        } else {
            emptyMap()
        },
    ) {
        ZakatHistoryScreen()
    }
    navigation<ZakatYearRoute>(
        metadata = if (isDesktopPlatform) ListDetailSceneStrategy.detailPane() else emptyMap(),
    ) { route ->
        ZakatYearScreen(hijriYear = route.hijriYear)
    }
    navigation<ZakatYearAddRoute> { ZakatYearAddScreen() }
    navigation<ZakatDateRoute> { route -> ZakatDateScreen(hijriYear = route.hijriYear) }
    navigation<ZakatAddRoute> { route ->
        ZakatEditorScreen(
            target = ZakatEditorTarget.New,
            launch = ZakatEditorLaunch(prefillAmount = route.prefillAmount, initialYear = route.forHijriYear),
        )
    }
    navigation<ZakatEditRoute> { route -> ZakatEditorScreen(target = ZakatEditorTarget.Existing(route.paymentId)) }
    navigation<NotesRoute> { NotesListScreen() }
    navigation<NoteAddRoute> { NoteEditorScreen(target = NoteEditorTarget.New) }
    navigation<NoteEditRoute> { route -> NoteEditorScreen(target = NoteEditorTarget.Existing(route.noteId)) }
}
