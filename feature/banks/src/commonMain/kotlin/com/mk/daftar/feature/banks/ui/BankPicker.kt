package com.mk.daftar.feature.banks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.mk.daftar.core.designsystem.component.DaftarBankTile
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import com.mk.daftar.core.domain.bank.BankDirectory
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.model.bank.BankDirectoryData
import com.mk.daftar.core.model.bank.BankInstitution
import com.mk.daftar.feature.banks.BankEditorIntent
import com.mk.daftar.feature.banks.BankPickerState
import com.mk.daftar.feature.banks.resources.Res
import com.mk.daftar.feature.banks.resources.bank_picker_empty
import com.mk.daftar.feature.banks.resources.bank_picker_other
import com.mk.daftar.feature.banks.resources.bank_picker_privacy
import com.mk.daftar.feature.banks.resources.bank_picker_search
import com.mk.daftar.feature.banks.resources.bank_picker_title
import org.jetbrains.compose.resources.stringResource

/**
 * The offline bank directory: a search field, the matching catalog banks sorted by their name in the app's language,
 * and Other bank, which is always offered outside the results. Nothing is fetched and nothing is saved here.
 */
@Composable
internal fun BankPickerContent(
    picker: BankPickerState,
    onIntent: (BankEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = appLanguage()
    val results = remember(picker.query, language) {
        BankDirectory.search(picker.query, BankDirectoryData.institutions, BankDirectoryData.countries, language)
    }
    Column(modifier = modifier.fillMaxSize()) {
        DaftarTopBar(
            title = stringResource(Res.string.bank_picker_title),
            onBack = { onIntent(BankEditorIntent.ClosePicker) },
            backLabel = stringResource(DesignRes.string.btn_back),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DaftarSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            DaftarTextField(
                value = picker.query,
                onValueChange = { onIntent(BankEditorIntent.PickerQueryChanged(it)) },
                label = stringResource(Res.string.bank_picker_search),
            )
            if (results.isEmpty()) {
                DaftarNotePanel(tone = DaftarNoteTone.Context) {
                    Text(stringResource(Res.string.bank_picker_empty))
                }
            }
            results.forEach { institution ->
                val name = BankDirectory.localizedName(institution, language)
                DaftarLedgerRow(
                    title = name,
                    subtitle = otherName(institution, language),
                    leading = { DaftarBankTile(mark = bankMarkFor(institution.id.value)) },
                    onClick = { onIntent(BankEditorIntent.PickInstitution(institution.id.value, name)) },
                )
            }
            OutlineButton(onClick = { onIntent(BankEditorIntent.PickOther) }, fullWidth = true) {
                Text(stringResource(Res.string.bank_picker_other))
            }
            DaftarNotePanel(tone = DaftarNoteTone.Context) {
                Text(
                    text = stringResource(Res.string.bank_picker_privacy),
                    style = DaftarTheme.typography.fineText,
                )
            }
        }
    }
}

/** The name in the other language, so a bank is recognisable whichever name the user remembers. */
private fun otherName(institution: BankInstitution, language: AppLanguage): String = when (language) {
    AppLanguage.AR -> institution.nameEn
    AppLanguage.EN -> institution.nameAr
}

/** The app's language as the layout shows it: Arabic lays out right to left. */
@Composable
internal fun appLanguage(): AppLanguage =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) AppLanguage.AR else AppLanguage.EN
