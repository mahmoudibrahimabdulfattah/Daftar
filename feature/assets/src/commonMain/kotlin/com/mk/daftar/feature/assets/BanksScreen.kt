package com.mk.daftar.feature.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.feature.assets.resources.Res
import com.mk.daftar.feature.assets.resources.bank_placeholder_note
import com.mk.daftar.feature.assets.resources.banks_message
import com.mk.daftar.feature.assets.resources.banks_title
import org.jetbrains.compose.resources.stringResource

/**
 * The bank list. Placeholder names only: no balances, no repository. [selectedBankId] marks the row whose detail is
 * open, and [onOpenBank] asks the shell to show that detail.
 */
@Composable
fun BanksScreen(selectedBankId: String?, onOpenBank: (String) -> Unit, modifier: Modifier = Modifier) {
    ShellScreen(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = DaftarSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            DaftarTopBar(title = stringResource(Res.string.banks_title))
            Text(
                text = stringResource(Res.string.banks_message),
                style = DaftarTheme.typography.supportingBody,
                color = DaftarTheme.colors.muted,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = DaftarSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
            ) {
                items(PlaceholderBanks, key = { it.id }) { bank ->
                    DaftarLedgerRow(
                        title = stringResource(bank.name),
                        subtitle = stringResource(Res.string.bank_placeholder_note),
                        icon = DaftarIcons.Book,
                        selected = bank.id == selectedBankId,
                        onClick = { onOpenBank(bank.id) },
                    )
                }
            }
        }
    }
}
