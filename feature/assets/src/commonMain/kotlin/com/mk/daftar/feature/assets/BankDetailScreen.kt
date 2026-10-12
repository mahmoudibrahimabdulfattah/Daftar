package com.mk.daftar.feature.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.ui.shell.ShellScreen
import com.mk.daftar.feature.assets.resources.Res
import com.mk.daftar.feature.assets.resources.bank_detail_message
import com.mk.daftar.feature.assets.resources.bank_detail_title
import org.jetbrains.compose.resources.stringResource

/**
 * One bank's detail. Back is offered only when the list is not beside it: in a two-pane window the list stays visible,
 * so the detail has nothing to return to.
 */
@Composable
fun BankDetailScreen(bankId: String, showsBack: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DaftarTheme.colors
    ShellScreen(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = DaftarSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            DaftarTopBar(
                title = stringResource(Res.string.bank_detail_title),
                onBack = if (showsBack) onBack else null,
                backLabel = stringResource(DesignRes.string.btn_back),
            )
            Text(
                text = stringResource(placeholderBankName(bankId)),
                style = DaftarTheme.typography.sectionHeading,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.bank_detail_message),
                style = DaftarTheme.typography.supportingBody,
                color = colors.muted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
