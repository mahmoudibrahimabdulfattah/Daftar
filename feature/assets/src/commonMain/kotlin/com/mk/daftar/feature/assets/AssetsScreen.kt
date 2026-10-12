package com.mk.daftar.feature.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.mk.daftar.feature.assets.resources.assets_message
import com.mk.daftar.feature.assets.resources.assets_title
import com.mk.daftar.feature.assets.resources.banks_message
import com.mk.daftar.feature.assets.resources.banks_title
import org.jetbrains.compose.resources.stringResource

/** The assets overview. It holds no balances; its one row opens the bank list. */
@Composable
fun AssetsScreen(onOpenBanks: () -> Unit, modifier: Modifier = Modifier) {
    ShellScreen(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DaftarSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            DaftarTopBar(title = stringResource(Res.string.assets_title))
            Text(
                text = stringResource(Res.string.assets_message),
                style = DaftarTheme.typography.supportingBody,
                color = DaftarTheme.colors.muted,
            )
            DaftarLedgerRow(
                title = stringResource(Res.string.banks_title),
                subtitle = stringResource(Res.string.banks_message),
                icon = DaftarIcons.Gold,
                onClick = onOpenBanks,
            )
        }
    }
}
