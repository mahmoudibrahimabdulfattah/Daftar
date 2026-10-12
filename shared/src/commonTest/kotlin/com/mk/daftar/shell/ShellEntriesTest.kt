package com.mk.daftar.shell

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.mk.daftar.core.navigation.AccountAddRoute
import com.mk.daftar.core.navigation.AccountEditRoute
import com.mk.daftar.core.navigation.AssetsRoute
import com.mk.daftar.core.navigation.BankAddRoute
import com.mk.daftar.core.navigation.BankDetailRoute
import com.mk.daftar.core.navigation.BankEditRoute
import com.mk.daftar.core.navigation.BanksRoute
import com.mk.daftar.core.navigation.GoldAddRoute
import com.mk.daftar.core.navigation.GoldEditRoute
import com.mk.daftar.core.navigation.GoldRoute
import com.mk.daftar.core.navigation.HomeRoute
import com.mk.daftar.core.navigation.JournalRoute
import com.mk.daftar.core.navigation.ManualQuoteRoute
import com.mk.daftar.core.navigation.NoteAddRoute
import com.mk.daftar.core.navigation.NoteEditRoute
import com.mk.daftar.core.navigation.NotesRoute
import com.mk.daftar.core.navigation.PricesRoute
import com.mk.daftar.core.navigation.SettingsRoute
import com.mk.daftar.core.navigation.ZakatAddRoute
import com.mk.daftar.core.navigation.ZakatEditRoute
import com.mk.daftar.core.navigation.ZakatRoute
import com.mk.daftar.feature.assets.di.assetsModule
import com.mk.daftar.feature.banks.di.banksModule
import com.mk.daftar.feature.gold.di.goldModule
import com.mk.daftar.feature.home.di.homeModule
import com.mk.daftar.feature.journal.di.journalModule
import com.mk.daftar.feature.prices.di.pricesModule
import com.mk.daftar.feature.settings.di.settingsModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication

/**
 * Every route the shell can show resolves to an entry in the Koin graph, and the bank pair carries the list-detail
 * metadata that lets the scene strategy place them. Only the feature modules load: they have no platform dependencies.
 */
@OptIn(KoinExperimentalAPI::class, ExperimentalMaterial3AdaptiveApi::class)
class ShellEntriesTest {

    private val koin = koinApplication {
        modules(homeModule, assetsModule, journalModule, settingsModule, pricesModule)
        modules(homeModule, assetsModule, goldModule, journalModule, settingsModule)

        modules(homeModule, assetsModule, banksModule, journalModule, settingsModule, pricesModule)
    }.koin

    /**
     * The same provider the shell builds: every `navigation<Route>` installer the graph holds, in one entry provider.
     * Entries are created without composing them, so no screen runs.
     */
    private val provider: (Any) -> NavEntry<Any> = run {
        val installers = koin.getAll<Function1<EntryProviderScope<Any>, Unit>>()
        entryProvider<Any> {
            installers.forEach { install -> install(this) }
        }
    }

    @Test
    fun everyRouteResolvesItsEntry() {
        val routes = listOf<NavKey>(
            HomeRoute,
            AssetsRoute,
            JournalRoute,
            SettingsRoute,
            BanksRoute,
            GoldRoute,
            GoldAddRoute,
            GoldEditRoute("sample-holding"),
            BankDetailRoute("sample-a"),
            BankAddRoute,
            BankEditRoute("sample-a"),
            AccountAddRoute("sample-a"),
            AccountEditRoute("sample-account"),
            ZakatRoute,
            ZakatAddRoute,
            ZakatEditRoute("sample-payment"),
            NotesRoute,
            NoteAddRoute,
            NoteEditRoute("sample-note"),
            PricesRoute,
            ManualQuoteRoute(null),
            ManualQuoteRoute("gold-21-sell"),
        )
        routes.forEach { route ->
            assertNotNull(provider(route), "$route resolves to an entry")
        }
    }

    @Test
    fun theBankListCarriesTheListPaneMetadata() {
        assertEquals(
            ListDetailSceneStrategy.listPane(detailPlaceholder = {}).keys,
            provider(BanksRoute).metadata.keys,
        )
    }

    @Test
    fun theBankDetailCarriesTheDetailPaneMetadata() {
        assertEquals(ListDetailSceneStrategy.detailPane().keys, provider(BankDetailRoute("sample-a")).metadata.keys)
    }

    @Test
    fun theTopLevelScreensCarryNoSceneMetadata() {
        listOf<NavKey>(HomeRoute, AssetsRoute, JournalRoute, SettingsRoute).forEach { route ->
            assertTrue(provider(route).metadata.isEmpty(), "$route is an ordinary single-pane entry")
        }
    }
}
