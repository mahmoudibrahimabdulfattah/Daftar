package com.mk.daftar.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsScreenNameTest {

    @Test
    fun topLevelAndFixedRoutesHaveTheirLiteralNames() {
        assertEquals("home", analyticsScreenName(HomeRoute))
        assertEquals("settings", analyticsScreenName(SettingsRoute))
        assertEquals("zakat_calculator", analyticsScreenName(ZakatCalculatorRoute))
        assertEquals("cloud_upload", analyticsScreenName(CloudUploadRoute))
    }

    @Test
    fun routeWithArgumentsReportsOnlyTheFixedName() {
        assertEquals("bank_detail", analyticsScreenName(BankDetailRoute(bankId = "eg.nbe")))
        assertEquals("zakat_add", analyticsScreenName(ZakatAddRoute(prefillAmount = "12345.67", forHijriYear = 1447)))
        assertEquals("zakat_year", analyticsScreenName(ZakatYearRoute(hijriYear = 1447)))
        assertEquals("manual_quote", analyticsScreenName(ManualQuoteRoute(quoteKey = "gold-21-sell")))
        assertEquals("convert_cash", analyticsScreenName(ConvertCashRoute(sourceId = "cash-42")))
    }

    @Test
    fun unknownKeysReportAsOther() {
        val unknown = object : NavKey {}
        assertEquals("other", analyticsScreenName(unknown))
    }
}
