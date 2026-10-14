package com.mk.daftar.core.ui.presentation

import androidx.compose.runtime.Composable
import com.mk.daftar.core.designsystem.resources.Res
import com.mk.daftar.core.designsystem.resources.count_accounts_few
import com.mk.daftar.core.designsystem.resources.count_accounts_many
import com.mk.daftar.core.designsystem.resources.count_accounts_one
import com.mk.daftar.core.designsystem.resources.count_accounts_other
import com.mk.daftar.core.designsystem.resources.count_accounts_two
import com.mk.daftar.core.designsystem.resources.count_accounts_zero
import com.mk.daftar.core.designsystem.resources.count_balances_few
import com.mk.daftar.core.designsystem.resources.count_balances_many
import com.mk.daftar.core.designsystem.resources.count_balances_one
import com.mk.daftar.core.designsystem.resources.count_balances_other
import com.mk.daftar.core.designsystem.resources.count_balances_two
import com.mk.daftar.core.designsystem.resources.count_balances_zero
import com.mk.daftar.core.designsystem.resources.count_banks_accounts_none
import com.mk.daftar.core.designsystem.resources.count_banks_accounts_summary
import com.mk.daftar.core.designsystem.resources.count_banks_few
import com.mk.daftar.core.designsystem.resources.count_banks_many
import com.mk.daftar.core.designsystem.resources.count_banks_one
import com.mk.daftar.core.designsystem.resources.count_banks_other
import com.mk.daftar.core.designsystem.resources.count_banks_two
import com.mk.daftar.core.designsystem.resources.count_banks_zero
import com.mk.daftar.core.designsystem.resources.count_holdings_few
import com.mk.daftar.core.designsystem.resources.count_holdings_many
import com.mk.daftar.core.designsystem.resources.count_holdings_one
import com.mk.daftar.core.designsystem.resources.count_holdings_other
import com.mk.daftar.core.designsystem.resources.count_holdings_two
import com.mk.daftar.core.designsystem.resources.count_holdings_zero
import com.mk.daftar.core.designsystem.resources.count_investment_accounts_few
import com.mk.daftar.core.designsystem.resources.count_investment_accounts_many
import com.mk.daftar.core.designsystem.resources.count_investment_accounts_one
import com.mk.daftar.core.designsystem.resources.count_investment_accounts_other
import com.mk.daftar.core.designsystem.resources.count_investment_accounts_two
import com.mk.daftar.core.designsystem.resources.count_investment_accounts_zero
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The six forms of one counted noun. The forms are in the design system so Home and Assets share them. */
internal class CountForms(
    val zero: StringResource,
    val one: StringResource,
    val two: StringResource,
    val few: StringResource,
    val many: StringResource,
    val other: StringResource,
) {
    fun of(category: CountCategory): StringResource = when (category) {
        CountCategory.ZERO -> zero
        CountCategory.ONE -> one
        CountCategory.TWO -> two
        CountCategory.FEW -> few
        CountCategory.MANY -> many
        CountCategory.OTHER -> other
    }
}

/** The nouns the summaries count. */
enum class CountNoun(internal val forms: CountForms) {
    HOLDINGS(
        CountForms(
            Res.string.count_holdings_zero,
            Res.string.count_holdings_one,
            Res.string.count_holdings_two,
            Res.string.count_holdings_few,
            Res.string.count_holdings_many,
            Res.string.count_holdings_other,
        ),
    ),
    BANKS(
        CountForms(
            Res.string.count_banks_zero,
            Res.string.count_banks_one,
            Res.string.count_banks_two,
            Res.string.count_banks_few,
            Res.string.count_banks_many,
            Res.string.count_banks_other,
        ),
    ),
    ACCOUNTS(
        CountForms(
            Res.string.count_accounts_zero,
            Res.string.count_accounts_one,
            Res.string.count_accounts_two,
            Res.string.count_accounts_few,
            Res.string.count_accounts_many,
            Res.string.count_accounts_other,
        ),
    ),

    /** Broker accounts: the investment list and the Assets row. Investments count accounts, never holdings. */
    INVESTMENT_ACCOUNTS(
        CountForms(
            Res.string.count_investment_accounts_zero,
            Res.string.count_investment_accounts_one,
            Res.string.count_investment_accounts_two,
            Res.string.count_investment_accounts_few,
            Res.string.count_investment_accounts_many,
            Res.string.count_investment_accounts_other,
        ),
    ),
    BALANCES(
        CountForms(
            Res.string.count_balances_zero,
            Res.string.count_balances_one,
            Res.string.count_balances_two,
            Res.string.count_balances_few,
            Res.string.count_balances_many,
            Res.string.count_balances_other,
        ),
    ),
}

/** The count with its noun in the form the count requires. The number is isolated inside the text. */
@Composable
fun countLabel(count: Int, noun: CountNoun): String = stringResource(noun.forms.of(countCategoryOf(count)), count)

/** Banks and accounts together. Both at zero read as one sentence, so the row does not show two empty counts. */
@Composable
fun bankAndAccountLabel(banks: Int, accounts: Int): String = if (banks == 0 && accounts == 0) {
    stringResource(Res.string.count_banks_accounts_none)
} else {
    stringResource(
        Res.string.count_banks_accounts_summary,
        countLabel(banks, CountNoun.BANKS),
        countLabel(accounts, CountNoun.ACCOUNTS),
    )
}
