package com.mk.daftar.feature.journal.di

import com.mk.daftar.core.navigation.JournalRoute
import com.mk.daftar.feature.journal.JournalScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/** This feature's Navigation 3 entries, registered for the shell's entry provider. */
@OptIn(KoinExperimentalAPI::class)
val journalModule: Module = module {
    navigation<JournalRoute> { JournalScreen() }
}
