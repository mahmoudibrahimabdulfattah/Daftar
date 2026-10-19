package com.mk.daftar.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import com.mk.daftar.App
import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.desktop.DesktopAppDirectories
import com.mk.daftar.core.data.session.VaultAuthenticator
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.BankAccountInput
import com.mk.daftar.core.domain.repository.BankInput
import com.mk.daftar.core.domain.repository.CashBalanceInput
import com.mk.daftar.core.domain.repository.GoldHoldingInput
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.InvestmentInput
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.repository.PersonalNoteInput
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.usecase.CompleteFirstRunGuideUseCase
import com.mk.daftar.core.domain.usecase.CreateBankAccountUseCase
import com.mk.daftar.core.domain.usecase.CreateBankUseCase
import com.mk.daftar.core.domain.usecase.CreateCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.CreateGoldHoldingUseCase
import com.mk.daftar.core.domain.usecase.CreateInvestmentUseCase
import com.mk.daftar.core.domain.usecase.CreatePersonalNoteUseCase
import com.mk.daftar.core.domain.usecase.SetInactivityTimeoutUseCase
import com.mk.daftar.core.domain.usecase.SetLanguageUseCase
import com.mk.daftar.core.domain.usecase.SetManualQuoteUseCase
import com.mk.daftar.core.domain.usecase.SetThemeModeUseCase
import com.mk.daftar.core.domain.usecase.UpdateBankAccountUseCase
import com.mk.daftar.core.domain.usecase.UpdateBankUseCase
import com.mk.daftar.core.domain.usecase.UpdateCashBalanceUseCase
import com.mk.daftar.core.domain.usecase.UpdateInvestmentUseCase
import com.mk.daftar.core.domain.usecase.UpdatePersonalNoteUseCase
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.network.FxFetch
import com.mk.daftar.core.network.FxFetchFailure
import com.mk.daftar.core.network.FxNetworkDataSource
import com.mk.daftar.core.network.GoldFetch
import com.mk.daftar.core.network.GoldNetworkDataSource
import com.mk.daftar.core.security.DesktopSecurityConfiguration
import com.mk.daftar.core.security.PinNormalizer
import com.mk.daftar.core.security.desktopSecurityConfigurationModule
import com.mk.daftar.di.DesktopBuildConfiguration
import com.mk.daftar.di.daftarModules
import com.mk.daftar.di.desktopBuildConfigurationModule
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Store listing screenshots: the real app at a 360 x 800 dp phone window at 3x density (1080 x 2400 px), in Arabic and
 * English, with a demo dataset seeded through the app's own use cases. Nothing here touches the network: gold and FX
 * sources fail at once, and the sell prices are manual quotes. Run by name:
 * `./gradlew :desktopApp:jvmTest --tests "*StoreScreens*"`. Output: `build/store-screens/{ar,en}/NN-name.png`.
 */
@OptIn(ExperimentalTestApi::class)
class StoreScreens {
    private val out = File(System.getProperty("daftar.storeScreens.dir")).apply { mkdirs() }

    @Test
    fun arabicAndEnglish() {
        start()
        try {
            val demo = seed()
            for (arabic in listOf(true, false)) {
                val labels = Labels(arabic)
                demo.localize(arabic)
                val dir = File(out, if (arabic) "ar" else "en").apply { mkdirs() }
                for (scene in scenes(labels)) {
                    setPreferences(arabic, scene.dark)
                    phone {
                        Thread.sleep(1000)
                        waitForIdle()
                        scene.steps(this)
                        // Move the pointer off the window so the last tapped row is not drawn as hovered.
                        onRoot().performMouseInput { moveTo(Offset(-10f, -10f)) }
                        waitForIdle()
                        Thread.sleep(700)
                        waitForIdle()
                        val image = onRoot().captureToImage().toAwtImage()
                        ImageIO.write(image, "png", File(dir, "${scene.name}.png"))
                    }
                }
            }
        } finally {
            stopKoin()
        }
    }

    private fun start() {
        if (GlobalContext.getOrNull() != null) stopKoin()
        DesktopAppDirectories.configureAt(Files.createTempDirectory("daftar-store-screens").toFile())
        startKoin {
            modules(
                daftarModules +
                    listOf(
                        desktopSecurityConfigurationModule(DesktopSecurityConfiguration.AllowSoftwareForDebug),
                        desktopBuildConfigurationModule(DesktopBuildConfiguration("1.0.0", "1", debug = true)),
                        // Overrides go last so they win over the production definitions.
                        module {
                            single<Clock> { DemoClock }
                            single<GoldNetworkDataSource> { OfflineGoldSource }
                            single<FxNetworkDataSource> { OfflineFxSource }
                        },
                    ),
            )
        }
    }

    private fun phone(block: DesktopComposeUiTest.() -> Unit) {
        val test = phoneTest()
        test.runTest {
            setContent { App(showsDebugBanner = false, developerTools = false, desktopLayout = false) }
            waitForIdle()
            test.block()
        }
    }

    /**
     * A 1080 x 2400 px surface at density 3 is the 360 x 800 dp phone window. The public `runDesktopComposeUiTest` fixes the
     * density at 1 and does not take one, so this builds the same desktop test through its private constructor, the one
     * the public runner calls with density 1. Only the density and surface differ.
     */
    private fun phoneTest(): DesktopComposeUiTest {
        val constructor = DesktopComposeUiTest::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            CoroutineContext::class.java,
            CoroutineContext::class.java,
            Long::class.javaPrimitiveType,
            Density::class.java,
            Boolean::class.javaPrimitiveType,
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            1080,
            2400,
            EmptyCoroutineContext,
            EmptyCoroutineContext,
            // The timeout is a Duration in its raw encoding: 10 minutes in milliseconds is (600_000 shl 1) + 1.
            1_200_001L,
            Density(3f),
            false,
        ) as DesktopComposeUiTest
    }

    private fun setPreferences(arabic: Boolean, dark: Boolean) = runBlocking {
        GlobalContext.get().get<SetLanguageUseCase>().invoke(if (arabic) AppLanguage.AR else AppLanguage.EN).required()
        GlobalContext.get().get<SetThemeModeUseCase>().invoke(if (dark) ThemeMode.DARK else ThemeMode.LIGHT).required()
    }

    /** Creates the vault and the demo records once. Names are Arabic until [Demo.localize] switches them. */
    private fun seed(): Demo = runBlocking {
        val koin = GlobalContext.get()
        val pin = PinNormalizer.normalize("246810").required("pin")
        koin.get<VaultAuthenticator>().create(pin).required("create vault")
        koin.get<CompleteFirstRunGuideUseCase>().invoke().required()
        koin.get<SetInactivityTimeoutUseCase>().invoke(InactivityTimeout.SECONDS_300).required()

        val setManual = koin.get<SetManualQuoteUseCase>()
        setManual(
            ManualQuoteInput(QuoteKey.Gold(GoldKarat.K24, MerchantSide.SELLS_TO_CUSTOMER), dec("7000"), null),
            null,
        ).required()
        setManual(
            ManualQuoteInput(QuoteKey.Gold(GoldKarat.K21, MerchantSide.SELLS_TO_CUSTOMER), dec("6125"), null),
            null,
        ).required()
        setManual(
            ManualQuoteInput(QuoteKey.Gold(GoldKarat.K18, MerchantSide.SELLS_TO_CUSTOMER), dec("5250"), null),
            null,
        ).required()
        setManual(ManualQuoteInput(QuoteKey.UsdEgp, dec("50"), null), null).required()

        val bank = koin.get<CreateBankUseCase>().invoke(BankInput("بنك توضيحي")).required("bank")
        val savings = koin.get<CreateBankAccountUseCase>().invoke(
            BankAccountInput(bank.value.id, "ادخار", Money(dec("50000"), Currency.EGP), null),
        ).required("savings")
        val usd = koin.get<CreateBankAccountUseCase>().invoke(
            BankAccountInput(bank.value.id, "دولار", Money(dec("1000"), Currency.USD), null),
        ).required("usd")
        val cash = koin.get<CreateCashBalanceUseCase>().invoke(
            CashBalanceInput("نقدي", CashKind.CASH, Money(dec("3000"), Currency.EGP), null),
        ).required("cash")
        val wallet = koin.get<CreateCashBalanceUseCase>().invoke(
            CashBalanceInput("المحفظة", CashKind.WALLET, Money(dec("2000"), Currency.EGP), null),
        ).required("wallet")
        val investment = koin.get<CreateInvestmentUseCase>().invoke(
            InvestmentInput(null, "استثمار توضيحي", dec("25000"), null, DemoClock.now()),
        ).required("investment")

        val gold = koin.get<CreateGoldHoldingUseCase>()
        gold(GoldHoldingInput(GoldKarat.K24, GoldForm.BAR, GoldWeight(dec("10")), null)).required("gold24")
        gold(GoldHoldingInput(GoldKarat.K21, GoldForm.BAR, GoldWeight(dec("8")), null)).required("gold21")
        gold(GoldHoldingInput(GoldKarat.K18, GoldForm.JEWELRY, GoldWeight(dec("4")), null)).required("gold18")

        val note = koin.get<CreatePersonalNoteUseCase>().invoke(
            PersonalNoteInput("فاتورة الذهب", "الفاتورة في ملف المستندات."),
        ).required("note")

        Demo(bank, savings, usd, cash, wallet, investment, note)
    }

    /** The seeded records, held with their latest revisions so they can be renamed for the other locale. */
    private class Demo(
        var bank: Stored<Bank>,
        var savings: Stored<BankAccount>,
        var usd: Stored<BankAccount>,
        var cash: Stored<CashBalance>,
        var wallet: Stored<CashBalance>,
        var investment: Stored<InvestmentAccount>,
        var note: Stored<PersonalNote>,
    ) {
        fun localize(arabic: Boolean) = runBlocking {
            val koin = GlobalContext.get()
            val names = if (arabic) ARABIC else ENGLISH
            bank =
                koin.get<UpdateBankUseCase>()(
                    bank.value.id,
                    bank.revision,
                    BankInput(names.bank),
                ).required("rename bank")
            savings = koin.get<UpdateBankAccountUseCase>()(
                savings.value.id,
                savings.revision,
                BankAccountInput(bank.value.id, names.savings, Money(dec("50000"), Currency.EGP), null),
            ).required("rename savings")
            usd = koin.get<UpdateBankAccountUseCase>()(
                usd.value.id,
                usd.revision,
                BankAccountInput(bank.value.id, names.usd, Money(dec("1000"), Currency.USD), null),
            ).required("rename usd")
            cash = koin.get<UpdateCashBalanceUseCase>()(
                cash.value.id,
                cash.revision,
                CashBalanceInput(names.cash, CashKind.CASH, Money(dec("3000"), Currency.EGP), null),
            ).required("rename cash")
            wallet = koin.get<UpdateCashBalanceUseCase>()(
                wallet.value.id,
                wallet.revision,
                CashBalanceInput(names.wallet, CashKind.WALLET, Money(dec("2000"), Currency.EGP), null),
            ).required("rename wallet")
            investment = koin.get<UpdateInvestmentUseCase>()(
                investment.value.id,
                investment.revision,
                InvestmentInput(null, names.investment, dec("25000"), null, DemoClock.now()),
            ).required("rename investment")
            note = koin.get<UpdatePersonalNoteUseCase>()(
                note.value.id,
                note.revision,
                PersonalNoteInput(names.noteTitle, names.noteBody),
            ).required("rename note")
        }

        private class Names(
            val bank: String,
            val savings: String,
            val usd: String,
            val cash: String,
            val wallet: String,
            val investment: String,
            val noteTitle: String,
            val noteBody: String,
        )

        companion object {
            private val ARABIC = Names(
                bank = "بنك توضيحي",
                savings = "ادخار",
                usd = "دولار",
                cash = "نقدي",
                wallet = "المحفظة",
                investment = "استثمار توضيحي",
                noteTitle = "فاتورة الذهب",
                noteBody = "الفاتورة في ملف المستندات.",
            )
            private val ENGLISH = Names(
                bank = "Demo bank",
                savings = "Savings",
                usd = "USD",
                cash = "Cash",
                wallet = "Wallet",
                investment = "Demo investment",
                noteTitle = "Gold receipt",
                noteBody = "The receipt is in the documents folder.",
            )
        }
    }

    /** Visible labels, taken from the app's own string resources for each locale. */
    private class Labels(val arabic: Boolean) {
        fun pick(ar: String, en: String) = if (arabic) ar else en
        val assets = pick("الأصول", "Assets")
        val journal = pick("السجل", "Journal")
        val settings = pick("الإعدادات", "Settings")
        val gold = pick("الذهب", "Gold")
        val banks = pick("البنوك", "Banks")
        val investments = pick("الاستثمارات", "Investments")
        val quotes = pick("الأسعار", "Quotes")
        val quoteCard = pick("أسعار الذهب والدولار", "Gold and dollar prices")
        val zakatCalculator = pick("حاسبة الزكاة", "Zakat calculator")
        val notes = pick("الملاحظات", "Notes")
        val security = pick("الحماية", "Security")
        val bank = pick("بنك توضيحي", "Demo bank")
        val investment = pick("استثمار توضيحي", "Demo investment")
    }

    private class Scene(val name: String, val dark: Boolean, val steps: DesktopComposeUiTest.() -> Unit)

    private fun scenes(l: Labels): List<Scene> = listOf(
        Scene("01-home", dark = false) {},
        Scene("02-gold", dark = false) {
            tap(l.assets)
            tap(l.gold)
        },
        Scene("03-bank-detail", dark = false) {
            tap(l.assets)
            tap(l.banks)
            tap(l.bank)
        },
        Scene("04-investment-detail", dark = false) {
            tap(l.assets)
            tap(l.investments)
            tap(l.investment)
        },
        Scene("05-prices", dark = false) {
            // The whole quotes card on Home opens Prices; it sits below the fold and is labelled by its description.
            swipeToDescription(l.quoteCard)
            onAllNodes(
                hasContentDescription(l.quoteCard, substring = true) and hasClickAction(),
                useUnmergedTree = false,
            )[0]
                .performClick()
            waitForIdle()
            Thread.sleep(500)
            waitForIdle()
        },
        Scene("06-zakat-calculator", dark = false) {
            tap(l.journal)
            tap(l.zakatCalculator)
        },
        Scene("07-notes", dark = true) {
            tap(l.journal)
            tap(l.notes)
        },
        Scene("08-settings", dark = true) {
            tap(l.settings)
            scrollTo(l.security, clickable = false)
        },
    )

    private fun DesktopComposeUiTest.tap(text: String) {
        key(text).performClick()
        waitForIdle()
        Thread.sleep(500)
        waitForIdle()
    }

    /**
     * Drags the page up until the clickable node described by [description] is laid out above the nav bar. The surface
     * is 2400 px tall and the bottom bar starts near 2180 px.
     */
    private fun DesktopComposeUiTest.swipeToDescription(description: String) {
        repeat(20) {
            val bounds = onAllNodes(
                hasContentDescription(description, substring = true) and hasClickAction(),
                useUnmergedTree = false,
            )
                .fetchSemanticsNodes()
                .firstOrNull()
                ?.boundsInRoot
            if (bounds != null && bounds.height > 0f) return
            onRoot().performTouchInput {
                val x = width / 2f
                swipe(Offset(x, height * 0.8f), Offset(x, height * 0.2f), 400L)
            }
            waitForIdle()
            Thread.sleep(300)
            waitForIdle()
        }
    }

    /** Swipes the page up until a clickable node with [text] exists. The home page has no scrollable semantics node. */
    private fun DesktopComposeUiTest.scrollTo(text: String, clickable: Boolean = true) {
        repeat(12) {
            val matcher = if (clickable) hasText(text) and hasClickAction() else hasText(text)
            if (onAllNodes(matcher, useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
            onRoot().performTouchInput { swipeUp(durationMillis = 300) }
            waitForIdle()
        }
    }

    private fun DesktopComposeUiTest.key(text: String): SemanticsNodeInteraction =
        onAllNodes(hasText(text) and hasClickAction(), useUnmergedTree = false)[0]

    private companion object {
        fun dec(text: String): Decimal = Decimal.parseCanonical(text)

        fun <D, E> Outcome<D, E>.required(what: String = "use case"): D = when (this) {
            is Outcome.Success -> data
            is Outcome.Failure -> error("$what failed: $error")
        }

        /** The demo day: 19 Oct 2026, 10:00 in Africa/Cairo (UTC+3 while Egypt observes summer time). */
        object DemoClock : Clock {
            private val instant = Instant.parse("2026-10-19T07:00:00Z")
            override fun now(): Instant = instant
        }

        /** Gold never comes from the network in the store harness; the manual quotes carry the sell prices. */
        object OfflineGoldSource : GoldNetworkDataSource {
            override suspend fun fetch(): Outcome<GoldFetch, DataError> = Outcome.Failure(DataError.Transport)
        }

        object OfflineFxSource : FxNetworkDataSource {
            override suspend fun fetch(): Outcome<FxFetch, FxFetchFailure> =
                Outcome.Failure(FxFetchFailure(emptyList()))
        }
    }
}
