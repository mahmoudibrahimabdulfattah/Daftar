package com.mk.daftar.feature.journal.di

import com.mk.daftar.core.navigation.JournalRoute
import com.mk.daftar.core.navigation.NoteAddRoute
import com.mk.daftar.core.navigation.NoteEditRoute
import com.mk.daftar.core.navigation.NotesRoute
import com.mk.daftar.core.navigation.ZakatAddRoute
import com.mk.daftar.core.navigation.ZakatEditRoute
import com.mk.daftar.core.navigation.ZakatRoute
import com.mk.daftar.feature.journal.JournalViewModel
import com.mk.daftar.feature.journal.notes.NoteEditorTarget
import com.mk.daftar.feature.journal.notes.NoteEditorViewModel
import com.mk.daftar.feature.journal.notes.NotesListViewModel
import com.mk.daftar.feature.journal.ui.JournalScreen
import com.mk.daftar.feature.journal.ui.NoteEditorScreen
import com.mk.daftar.feature.journal.ui.NotesListScreen
import com.mk.daftar.feature.journal.ui.ZakatEditorScreen
import com.mk.daftar.feature.journal.ui.ZakatListScreen
import com.mk.daftar.feature.journal.zakat.ZakatEditorTarget
import com.mk.daftar.feature.journal.zakat.ZakatEditorViewModel
import com.mk.daftar.feature.journal.zakat.ZakatListViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/**
 * The Journal feature: the overview, the zakat log and the personal notes, with their editors. Their use cases and the
 * clock come from the application graph; nothing here reaches the data layer.
 */
@OptIn(KoinExperimentalAPI::class)
val journalModule: Module = module {
    factory<TimeZone> { TimeZone.currentSystemDefault() }

    viewModelOf(::JournalViewModel)
    viewModelOf(::ZakatListViewModel)
    viewModelOf(::NotesListViewModel)
    viewModel { params ->
        ZakatEditorViewModel(
            target = params.get<ZakatEditorTarget>(),
            observePayment = get(),
            observePayments = get(),
            createPayment = get(),
            updatePayment = get(),
            deletePayment = get(),
            visibility = get(),
            clock = get(),
            timeZone = get(),
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
    navigation<ZakatAddRoute> { ZakatEditorScreen(target = ZakatEditorTarget.New) }
    navigation<ZakatEditRoute> { route -> ZakatEditorScreen(target = ZakatEditorTarget.Existing(route.paymentId)) }
    navigation<NotesRoute> { NotesListScreen() }
    navigation<NoteAddRoute> { NoteEditorScreen(target = NoteEditorTarget.New) }
    navigation<NoteEditRoute> { route -> NoteEditorScreen(target = NoteEditorTarget.Existing(route.noteId)) }
}
