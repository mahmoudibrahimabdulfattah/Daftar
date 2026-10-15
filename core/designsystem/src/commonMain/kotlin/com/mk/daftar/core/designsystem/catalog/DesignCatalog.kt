package com.mk.daftar.core.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.component.DaftarAllocationBar
import com.mk.daftar.core.designsystem.component.DaftarAllocationSegment
import com.mk.daftar.core.designsystem.component.DaftarAmount
import com.mk.daftar.core.designsystem.component.DaftarBalanceSummary
import com.mk.daftar.core.designsystem.component.DaftarBankMark
import com.mk.daftar.core.designsystem.component.DaftarBankTile
import com.mk.daftar.core.designsystem.component.DaftarCard
import com.mk.daftar.core.designsystem.component.DaftarConfirmationContent
import com.mk.daftar.core.designsystem.component.DaftarCurrencyBadge
import com.mk.daftar.core.designsystem.component.DaftarDateField
import com.mk.daftar.core.designsystem.component.DaftarEmptyState
import com.mk.daftar.core.designsystem.component.DaftarFieldKind
import com.mk.daftar.core.designsystem.component.DaftarFooterAction
import com.mk.daftar.core.designsystem.component.DaftarIcon
import com.mk.daftar.core.designsystem.component.DaftarIconButton
import com.mk.daftar.core.designsystem.component.DaftarKaratQuote
import com.mk.daftar.core.designsystem.component.DaftarKeypad
import com.mk.daftar.core.designsystem.component.DaftarLedgerRow
import com.mk.daftar.core.designsystem.component.DaftarMark
import com.mk.daftar.core.designsystem.component.DaftarNavigationBar
import com.mk.daftar.core.designsystem.component.DaftarNavigationItem
import com.mk.daftar.core.designsystem.component.DaftarNavigationRail
import com.mk.daftar.core.designsystem.component.DaftarNavigationRailItem
import com.mk.daftar.core.designsystem.component.DaftarNotePanel
import com.mk.daftar.core.designsystem.component.DaftarNoteTone
import com.mk.daftar.core.designsystem.component.DaftarPinField
import com.mk.daftar.core.designsystem.component.DaftarPrivacyCover
import com.mk.daftar.core.designsystem.component.DaftarQuote
import com.mk.daftar.core.designsystem.component.DaftarQuoteStrip
import com.mk.daftar.core.designsystem.component.DaftarQuoteTable
import com.mk.daftar.core.designsystem.component.DaftarResultState
import com.mk.daftar.core.designsystem.component.DaftarResultTone
import com.mk.daftar.core.designsystem.component.DaftarSegmentedControl
import com.mk.daftar.core.designsystem.component.DaftarSelectField
import com.mk.daftar.core.designsystem.component.DaftarSettingRow
import com.mk.daftar.core.designsystem.component.DaftarSettingTrailing
import com.mk.daftar.core.designsystem.component.DaftarSkeleton
import com.mk.daftar.core.designsystem.component.DaftarSkeletonSize
import com.mk.daftar.core.designsystem.component.DaftarStatusTag
import com.mk.daftar.core.designsystem.component.DaftarStatusTone
import com.mk.daftar.core.designsystem.component.DaftarTextArea
import com.mk.daftar.core.designsystem.component.DaftarTextButton
import com.mk.daftar.core.designsystem.component.DaftarTextField
import com.mk.daftar.core.designsystem.component.DaftarTileTone
import com.mk.daftar.core.designsystem.component.DaftarTopBar
import com.mk.daftar.core.designsystem.component.OutlineButton
import com.mk.daftar.core.designsystem.component.PrimaryButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res
import com.mk.daftar.core.designsystem.resources.balance_allocation
import com.mk.daftar.core.designsystem.resources.balance_hidden
import com.mk.daftar.core.designsystem.resources.balance_provenance
import com.mk.daftar.core.designsystem.resources.balance_subtotal
import com.mk.daftar.core.designsystem.resources.balance_total
import com.mk.daftar.core.designsystem.resources.balance_unavailable
import com.mk.daftar.core.designsystem.resources.btn_add_account
import com.mk.daftar.core.designsystem.resources.btn_add_holding
import com.mk.daftar.core.designsystem.resources.btn_back
import com.mk.daftar.core.designsystem.resources.btn_edit
import com.mk.daftar.core.designsystem.resources.btn_save
import com.mk.daftar.core.designsystem.resources.btn_saving
import com.mk.daftar.core.designsystem.resources.btn_show_all
import com.mk.daftar.core.designsystem.resources.btn_states_hint
import com.mk.daftar.core.designsystem.resources.btn_toggle_values
import com.mk.daftar.core.designsystem.resources.btn_why
import com.mk.daftar.core.designsystem.resources.card_body
import com.mk.daftar.core.designsystem.resources.card_time
import com.mk.daftar.core.designsystem.resources.card_title
import com.mk.daftar.core.designsystem.resources.catalog_auth_backoff
import com.mk.daftar.core.designsystem.resources.catalog_auth_busy
import com.mk.daftar.core.designsystem.resources.catalog_auth_corrupt
import com.mk.daftar.core.designsystem.resources.catalog_auth_format
import com.mk.daftar.core.designsystem.resources.catalog_auth_hardware
import com.mk.daftar.core.designsystem.resources.catalog_auth_key
import com.mk.daftar.core.designsystem.resources.catalog_auth_mismatch
import com.mk.daftar.core.designsystem.resources.catalog_auth_paused
import com.mk.daftar.core.designsystem.resources.catalog_auth_recovery
import com.mk.daftar.core.designsystem.resources.catalog_auth_transient
import com.mk.daftar.core.designsystem.resources.catalog_auth_wrong_pin
import com.mk.daftar.core.designsystem.resources.catalog_brand
import com.mk.daftar.core.designsystem.resources.catalog_intro
import com.mk.daftar.core.designsystem.resources.catalog_lang_ar
import com.mk.daftar.core.designsystem.resources.catalog_lang_en
import com.mk.daftar.core.designsystem.resources.catalog_language
import com.mk.daftar.core.designsystem.resources.catalog_mirror_hint
import com.mk.daftar.core.designsystem.resources.catalog_theme
import com.mk.daftar.core.designsystem.resources.catalog_theme_dark
import com.mk.daftar.core.designsystem.resources.catalog_theme_light
import com.mk.daftar.core.designsystem.resources.catalog_theme_system
import com.mk.daftar.core.designsystem.resources.catalog_title
import com.mk.daftar.core.designsystem.resources.confirm_blocked_message
import com.mk.daftar.core.designsystem.resources.confirm_blocked_title
import com.mk.daftar.core.designsystem.resources.confirm_discard
import com.mk.daftar.core.designsystem.resources.confirm_discard_message
import com.mk.daftar.core.designsystem.resources.confirm_discard_title
import com.mk.daftar.core.designsystem.resources.confirm_keep
import com.mk.daftar.core.designsystem.resources.confirm_ok
import com.mk.daftar.core.designsystem.resources.empty_message
import com.mk.daftar.core.designsystem.resources.empty_title
import com.mk.daftar.core.designsystem.resources.field_account_name
import com.mk.daftar.core.designsystem.resources.field_account_placeholder
import com.mk.daftar.core.designsystem.resources.field_balance
import com.mk.daftar.core.designsystem.resources.field_balance_helper
import com.mk.daftar.core.designsystem.resources.field_bank
import com.mk.daftar.core.designsystem.resources.field_bank_name
import com.mk.daftar.core.designsystem.resources.field_bank_name_value
import com.mk.daftar.core.designsystem.resources.field_bank_placeholder
import com.mk.daftar.core.designsystem.resources.field_currency
import com.mk.daftar.core.designsystem.resources.field_date
import com.mk.daftar.core.designsystem.resources.field_date_value
import com.mk.daftar.core.designsystem.resources.field_note
import com.mk.daftar.core.designsystem.resources.field_note_placeholder
import com.mk.daftar.core.designsystem.resources.field_pin
import com.mk.daftar.core.designsystem.resources.field_weight
import com.mk.daftar.core.designsystem.resources.field_weight_helper
import com.mk.daftar.core.designsystem.resources.ledger_bank_misr
import com.mk.daftar.core.designsystem.resources.ledger_bank_misr_sub
import com.mk.daftar.core.designsystem.resources.ledger_cash
import com.mk.daftar.core.designsystem.resources.ledger_cash_sub
import com.mk.daftar.core.designsystem.resources.ledger_gold
import com.mk.daftar.core.designsystem.resources.ledger_gold_sub
import com.mk.daftar.core.designsystem.resources.ledger_incomplete
import com.mk.daftar.core.designsystem.resources.ledger_investments
import com.mk.daftar.core.designsystem.resources.ledger_investments_sub
import com.mk.daftar.core.designsystem.resources.nav_assets
import com.mk.daftar.core.designsystem.resources.nav_home
import com.mk.daftar.core.designsystem.resources.nav_journal
import com.mk.daftar.core.designsystem.resources.nav_settings
import com.mk.daftar.core.designsystem.resources.note_context
import com.mk.daftar.core.designsystem.resources.note_error
import com.mk.daftar.core.designsystem.resources.note_warning
import com.mk.daftar.core.designsystem.resources.note_why
import com.mk.daftar.core.designsystem.resources.pin_backoff
import com.mk.daftar.core.designsystem.resources.pin_backoff_title
import com.mk.daftar.core.designsystem.resources.pin_biometric
import com.mk.daftar.core.designsystem.resources.pin_empty
import com.mk.daftar.core.designsystem.resources.pin_entered
import com.mk.daftar.core.designsystem.resources.pin_erase
import com.mk.daftar.core.designsystem.resources.quote_buy
import com.mk.daftar.core.designsystem.resources.quote_karat
import com.mk.daftar.core.designsystem.resources.quote_provenance
import com.mk.daftar.core.designsystem.resources.quote_provenance_refreshing
import com.mk.daftar.core.designsystem.resources.quote_sell
import com.mk.daftar.core.designsystem.resources.quote_unit
import com.mk.daftar.core.designsystem.resources.result_done
import com.mk.daftar.core.designsystem.resources.result_error_message
import com.mk.daftar.core.designsystem.resources.result_error_title
import com.mk.daftar.core.designsystem.resources.result_retry
import com.mk.daftar.core.designsystem.resources.result_saved_message
import com.mk.daftar.core.designsystem.resources.result_saved_title
import com.mk.daftar.core.designsystem.resources.section_auth_states
import com.mk.daftar.core.designsystem.resources.section_balance
import com.mk.daftar.core.designsystem.resources.section_brand
import com.mk.daftar.core.designsystem.resources.section_buttons
import com.mk.daftar.core.designsystem.resources.section_cards
import com.mk.daftar.core.designsystem.resources.section_confirm
import com.mk.daftar.core.designsystem.resources.section_fields
import com.mk.daftar.core.designsystem.resources.section_identity
import com.mk.daftar.core.designsystem.resources.section_ledger
import com.mk.daftar.core.designsystem.resources.section_navigation
import com.mk.daftar.core.designsystem.resources.section_notes
import com.mk.daftar.core.designsystem.resources.section_pin
import com.mk.daftar.core.designsystem.resources.section_privacy
import com.mk.daftar.core.designsystem.resources.section_quotes
import com.mk.daftar.core.designsystem.resources.section_segments
import com.mk.daftar.core.designsystem.resources.section_settings
import com.mk.daftar.core.designsystem.resources.section_states
import com.mk.daftar.core.designsystem.resources.section_status
import com.mk.daftar.core.designsystem.resources.section_topbar
import com.mk.daftar.core.designsystem.resources.section_type
import com.mk.daftar.core.designsystem.resources.segment_bars
import com.mk.daftar.core.designsystem.resources.segment_cash
import com.mk.daftar.core.designsystem.resources.segment_jewelry
import com.mk.daftar.core.designsystem.resources.segment_wallet
import com.mk.daftar.core.designsystem.resources.setting_biometrics
import com.mk.daftar.core.designsystem.resources.setting_biometrics_sub
import com.mk.daftar.core.designsystem.resources.setting_change
import com.mk.daftar.core.designsystem.resources.setting_lock
import com.mk.daftar.core.designsystem.resources.setting_lock_sub
import com.mk.daftar.core.designsystem.resources.setting_lock_value
import com.mk.daftar.core.designsystem.resources.setting_pin
import com.mk.daftar.core.designsystem.resources.setting_pin_sub
import com.mk.daftar.core.designsystem.resources.setting_theme
import com.mk.daftar.core.designsystem.resources.setting_theme_sub
import com.mk.daftar.core.designsystem.resources.status_cached
import com.mk.daftar.core.designsystem.resources.status_estimate
import com.mk.daftar.core.designsystem.resources.status_live
import com.mk.daftar.core.designsystem.resources.status_manual
import com.mk.daftar.core.designsystem.resources.status_refreshing
import com.mk.daftar.core.designsystem.resources.status_stale
import com.mk.daftar.core.designsystem.resources.switch_hide_values
import com.mk.daftar.core.designsystem.resources.topbar_back_title
import com.mk.daftar.core.designsystem.resources.topbar_long_title
import com.mk.daftar.core.designsystem.resources.type_role_app_bar
import com.mk.daftar.core.designsystem.resources.type_role_body
import com.mk.daftar.core.designsystem.resources.type_role_helper
import com.mk.daftar.core.designsystem.resources.type_role_hero
import com.mk.daftar.core.designsystem.resources.type_role_screen
import com.mk.daftar.core.designsystem.resources.type_role_section
import com.mk.daftar.core.designsystem.resources.type_sample_body
import com.mk.daftar.core.designsystem.resources.type_sample_helper
import com.mk.daftar.core.designsystem.resources.type_sample_screen
import com.mk.daftar.core.designsystem.resources.type_sample_section
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.designsystem.theme.DaftarTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Living reference for the design system: every component and state with demo data, in light or dark theme and
 * Arabic or English layout. The toggles at the top re-theme the catalog itself, so they also test that existing
 * style nodes update in place. Demo figures are illustrative, not live quotes.
 */
@Composable
fun DesignCatalog(modifier: Modifier = Modifier) {
    var themeIndex by rememberSaveable { mutableIntStateOf(0) }
    // Strings follow the device language, so the layout starts in the matching direction.
    var arabic by rememberSaveable { mutableStateOf(Locale.current.language == "ar") }
    val dark = when (themeIndex) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    DaftarTheme(darkTheme = dark, arabic = arabic) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(if (dark) BoardDark else BoardLight)
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DaftarSpacing.md, vertical = DaftarSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.md),
            ) {
                CatalogHeader(
                    themeIndex = themeIndex,
                    onThemeChange = { themeIndex = it },
                    arabic = arabic,
                    onLanguageChange = { arabic = it },
                )
                CatalogSection(Res.string.section_brand) { BrandSection() }
                CatalogSection(Res.string.section_type) { TypeSection() }
                CatalogSection(Res.string.section_buttons) { ButtonSection() }
                CatalogSection(Res.string.section_fields) { FieldSection() }
                CatalogSection(Res.string.section_segments) { SegmentSection() }
                CatalogSection(Res.string.section_settings) { SettingsSection() }
                CatalogSection(Res.string.section_topbar) { TopBarSection() }
                CatalogSection(Res.string.section_navigation) { NavigationSection() }
                CatalogSection(Res.string.section_ledger) { LedgerSection() }
                CatalogSection(Res.string.section_identity) { IdentitySection() }
                CatalogSection(Res.string.section_cards) { CardSection() }
                CatalogSection(Res.string.section_status) { StatusSection() }
                CatalogSection(Res.string.section_notes) { NoteSection() }
                CatalogSection(Res.string.section_balance) { BalanceSection() }
                CatalogSection(Res.string.section_quotes) { QuoteSection() }
                CatalogSection(Res.string.section_pin) { PinSection() }
                CatalogSection(Res.string.section_auth_states) { AuthStatesSection() }
                CatalogSection(Res.string.section_states) { StateSection() }
                CatalogSection(Res.string.section_confirm) { ConfirmSection() }
                CatalogSection(Res.string.section_privacy) { PrivacySection() }
                Spacer(Modifier.height(DaftarSpacing.xl))
            }
        }
    }
}

private const val THEME_LIGHT = 1
private const val THEME_DARK = 2

private val BoardLight = Color(0xFFEAE9E0)
private val BoardDark = Color(0xFF121710)

@Composable
private fun CatalogHeader(
    themeIndex: Int,
    onThemeChange: (Int) -> Unit,
    arabic: Boolean,
    onLanguageChange: (Boolean) -> Unit,
) {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
        ) {
            DaftarMark(Modifier.size(44.dp), contentDescription = stringResource(Res.string.catalog_brand))
            Text(
                text = stringResource(Res.string.catalog_title),
                style = typography.screenHeading,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(stringResource(Res.string.catalog_intro), style = typography.supportingBody, color = colors.muted)
        Text(stringResource(Res.string.catalog_theme), style = typography.fieldLabel, color = colors.ink)
        DaftarSegmentedControl(
            options = listOf(
                stringResource(Res.string.catalog_theme_system),
                stringResource(Res.string.catalog_theme_light),
                stringResource(Res.string.catalog_theme_dark),
            ),
            selectedIndex = themeIndex,
            onSelect = onThemeChange,
        )
        Text(stringResource(Res.string.catalog_language), style = typography.fieldLabel, color = colors.ink)
        DaftarSegmentedControl(
            options = listOf(
                stringResource(Res.string.catalog_lang_ar),
                stringResource(Res.string.catalog_lang_en),
            ),
            selectedIndex = if (arabic) 0 else 1,
            onSelect = { onLanguageChange(it == 0) },
        )
    }
}

/** A titled group on a paper card, the unit of the catalog. */
@Composable
private fun CatalogSection(title: StringResource, content: @Composable ColumnScope.() -> Unit) {
    val colors = DaftarTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, DaftarTheme.shapes.surface)
            .padding(DaftarSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
    ) {
        Text(
            text = stringResource(title),
            style = DaftarTheme.typography.sectionHeading,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun BrandSection() {
    val colors = DaftarTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.lg),
        ) {
            DaftarMark(Modifier.size(24.dp))
            DaftarMark(Modifier.size(48.dp))
            DaftarMark(Modifier.size(65.dp), frame = colors.gold)
            Box(
                modifier = Modifier.size(88.dp).background(colors.primary, DaftarTheme.shapes.surface),
                contentAlignment = Alignment.Center,
            ) {
                DaftarMark(Modifier.size(55.dp), frame = colors.onPrimary, detail = colors.primary)
            }
        }
        Text(
            stringResource(Res.string.catalog_mirror_hint),
            style = DaftarTheme.typography.caption,
            color = colors.muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaftarIcon(DaftarIcons.Book, contentDescription = null)
            DaftarIcon(DaftarIcons.Home, contentDescription = null)
            DaftarIcon(DaftarIcons.Gold, contentDescription = null, tint = colors.gold)
            DaftarIcon(DaftarIcons.Bank, contentDescription = null)
            DaftarIcon(DaftarIcons.Wallet, contentDescription = null)
            DaftarIcon(DaftarIcons.Note, contentDescription = null)
            DaftarIcon(DaftarIcons.Heart, contentDescription = null)
            DaftarIcon(DaftarIcons.Settings, contentDescription = null)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaftarIcon(DaftarIcons.Eye, contentDescription = null)
            DaftarIcon(DaftarIcons.EyeOff, contentDescription = null)
            DaftarIcon(DaftarIcons.Fingerprint, contentDescription = null)
            DaftarIcon(DaftarIcons.Lock, contentDescription = null)
            DaftarIcon(DaftarIcons.Refresh, contentDescription = null)
            DaftarIcon(DaftarIcons.Alert, contentDescription = null, tint = colors.error)
            DaftarIcon(DaftarIcons.Offline, contentDescription = null)
            DaftarIcon(DaftarIcons.Clock, contentDescription = null)
            DaftarIcon(DaftarIcons.Calendar, contentDescription = null)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaftarIcon(DaftarIcons.Back, contentDescription = null, mirrorInRtl = true)
            DaftarIcon(DaftarIcons.Chevron, contentDescription = null, mirrorInRtl = true)
            DaftarIcon(DaftarIcons.Plus, contentDescription = null)
            DaftarIcon(DaftarIcons.Check, contentDescription = null)
            DaftarIcon(DaftarIcons.Close, contentDescription = null)
            DaftarIcon(DaftarIcons.Backspace, contentDescription = null)
        }
    }
}

@Composable
private fun TypeSection() {
    val colors = DaftarTheme.colors
    val typography = DaftarTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Text(stringResource(Res.string.type_role_screen), style = typography.caption, color = colors.muted)
        Text(stringResource(Res.string.type_sample_screen), style = typography.screenHeading, color = colors.ink)
        Text(stringResource(Res.string.type_role_app_bar), style = typography.caption, color = colors.muted)
        Text(stringResource(Res.string.catalog_brand), style = typography.appBarTitle, color = colors.ink)
        Text(stringResource(Res.string.type_role_section), style = typography.caption, color = colors.muted)
        Text(stringResource(Res.string.type_sample_section), style = typography.sectionHeading, color = colors.ink)
        Text(stringResource(Res.string.type_role_body), style = typography.caption, color = colors.muted)
        Text(stringResource(Res.string.type_sample_body), style = typography.body, color = colors.ink)
        Text(stringResource(Res.string.type_role_hero), style = typography.caption, color = colors.muted)
        DaftarAmount(amount = "1,076,212.74", unit = "EGP")
        Text(stringResource(Res.string.type_role_helper), style = typography.caption, color = colors.muted)
        Text(stringResource(Res.string.type_sample_helper), style = typography.caption, color = colors.muted)
    }
}

@Composable
private fun ButtonSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        val typography = DaftarTheme.typography
        PrimaryButton(onClick = {}, fullWidth = true, leadingIcon = DaftarIcons.Plus) {
            Text(stringResource(Res.string.btn_add_holding), style = typography.buttonLabel)
        }
        PrimaryButton(onClick = {}, fullWidth = true) { Text(stringResource(Res.string.btn_save)) }
        PrimaryButton(onClick = {}, enabled = false, fullWidth = true) { Text(stringResource(Res.string.btn_save)) }
        PrimaryButton(onClick = {}, loading = true, fullWidth = true) { Text(stringResource(Res.string.btn_saving)) }
        OutlineButton(onClick = {}, fullWidth = true, leadingIcon = DaftarIcons.Plus) {
            Text(stringResource(Res.string.btn_add_account))
        }
        OutlineButton(onClick = {
        }, enabled = false, fullWidth = true) { Text(stringResource(Res.string.btn_add_account)) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaftarTextButton(onClick = {}) { Text(stringResource(Res.string.btn_why)) }
            DaftarTextButton(onClick = {}) { Text(stringResource(Res.string.btn_show_all)) }
            DaftarTextButton(onClick = {}, enabled = false) { Text(stringResource(Res.string.btn_edit)) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaftarIconButton(
                onClick = {},
                icon = DaftarIcons.Back,
                contentDescription = stringResource(Res.string.btn_back),
                mirrorInRtl = true,
            )
            DaftarIconButton(
                onClick = {},
                icon = DaftarIcons.Eye,
                contentDescription = stringResource(Res.string.btn_toggle_values),
            )
            DaftarIconButton(
                onClick = {},
                icon = DaftarIcons.Edit,
                contentDescription = stringResource(Res.string.btn_edit),
                enabled = false,
            )
        }
        Text(stringResource(Res.string.btn_states_hint), style = typography.caption, color = DaftarTheme.colors.muted)
    }
}

@Composable
private fun FieldSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        var name by rememberSaveable { mutableStateOf("") }
        var balance by rememberSaveable { mutableStateOf("2400.00") }
        var weight by rememberSaveable { mutableStateOf("80") }
        var pin by rememberSaveable { mutableStateOf("123456") }
        var note by rememberSaveable { mutableStateOf("") }
        DaftarTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(Res.string.field_account_name),
            placeholder = stringResource(Res.string.field_account_placeholder),
        )
        DaftarTextField(
            value = stringResource(Res.string.field_bank_name_value),
            onValueChange = {},
            label = stringResource(Res.string.field_bank_name),
            readOnly = true,
        )
        DaftarTextField(
            value = balance,
            onValueChange = { balance = it },
            label = stringResource(Res.string.field_balance),
            kind = DaftarFieldKind.Decimal,
            enabled = true,
        )
        DaftarTextField(
            value = weight,
            onValueChange = { weight = it },
            label = stringResource(Res.string.field_weight),
            kind = DaftarFieldKind.Decimal,
            helper = stringResource(Res.string.field_weight_helper),
        )
        DaftarTextField(
            value = "",
            onValueChange = {},
            label = stringResource(Res.string.field_balance),
            kind = DaftarFieldKind.Decimal,
            invalid = true,
            helper = stringResource(Res.string.field_balance_helper),
        )
        DaftarTextField(
            value = balance,
            onValueChange = {},
            label = stringResource(Res.string.field_balance),
            kind = DaftarFieldKind.Decimal,
            enabled = false,
        )
        DaftarTextField(
            value = pin,
            onValueChange = { pin = it },
            label = stringResource(Res.string.field_pin),
            kind = DaftarFieldKind.Password,
        )
        DaftarTextArea(
            value = note,
            onValueChange = { note = it },
            label = stringResource(Res.string.field_note),
            placeholder = stringResource(Res.string.field_note_placeholder),
        )
        DaftarSelectField(
            label = stringResource(Res.string.field_bank),
            selectedText = stringResource(Res.string.field_bank_name_value),
            onClick = {},
        )
        DaftarSelectField(
            label = stringResource(Res.string.field_bank),
            selectedText = null,
            placeholder = stringResource(Res.string.field_bank_placeholder),
            onClick = {},
        )
        DaftarSelectField(
            label = stringResource(Res.string.field_currency),
            selectedText = "EGP",
            onClick = {},
            expanded = true,
        )
        DaftarDateField(
            label = stringResource(Res.string.field_date),
            selectedText = stringResource(Res.string.field_date_value),
            onClick = {},
        )
    }
}

@Composable
private fun SegmentSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        var selected by rememberSaveable { mutableIntStateOf(0) }
        DaftarSegmentedControl(
            options = listOf(stringResource(Res.string.segment_cash), stringResource(Res.string.segment_wallet)),
            selectedIndex = selected,
            onSelect = { selected = it },
        )
        DaftarSegmentedControl(
            options = listOf(stringResource(Res.string.segment_bars), stringResource(Res.string.segment_jewelry)),
            selectedIndex = 1,
            onSelect = {},
        )
        DaftarSegmentedControl(
            options = listOf("EGP", "USD"),
            selectedIndex = 0,
            onSelect = {},
            enabled = false,
        )
    }
}

@Composable
private fun SettingsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        var biometrics by rememberSaveable { mutableStateOf(true) }
        var hideValues by rememberSaveable { mutableStateOf(false) }
        DaftarSettingRow(
            title = stringResource(Res.string.setting_pin),
            subtitle = stringResource(Res.string.setting_pin_sub),
            trailing = DaftarSettingTrailing.Link(stringResource(Res.string.setting_change), onClick = {}),
        )
        DaftarSettingRow(
            title = stringResource(Res.string.setting_biometrics),
            subtitle = stringResource(Res.string.setting_biometrics_sub),
            trailing = DaftarSettingTrailing.Switch(
                checked = biometrics,
                onCheckedChange = { biometrics = it },
                label = stringResource(Res.string.setting_biometrics),
            ),
        )
        DaftarSettingRow(
            title = stringResource(Res.string.setting_lock),
            subtitle = stringResource(Res.string.setting_lock_sub),
            trailing = DaftarSettingTrailing.Value(stringResource(Res.string.setting_lock_value)),
        )
        DaftarSettingRow(
            title = stringResource(Res.string.setting_theme),
            subtitle = stringResource(Res.string.setting_theme_sub),
            trailing = DaftarSettingTrailing.Value(stringResource(Res.string.catalog_theme_system)),
        )
        DaftarSettingRow(
            title = stringResource(Res.string.switch_hide_values),
            subtitle = stringResource(Res.string.btn_toggle_values),
            trailing = DaftarSettingTrailing.Switch(
                checked = hideValues,
                onCheckedChange = { hideValues = it },
                label = stringResource(Res.string.switch_hide_values),
            ),
        )
        DaftarSettingRow(
            title = stringResource(Res.string.setting_biometrics),
            subtitle = stringResource(Res.string.setting_biometrics_sub),
            trailing = DaftarSettingTrailing.Switch(
                checked = false,
                onCheckedChange = {},
                label = stringResource(Res.string.setting_biometrics),
                enabled = false,
            ),
        )
    }
}

@Composable
private fun TopBarSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        DaftarTopBar(title = stringResource(Res.string.catalog_brand))
        DaftarTopBar(
            title = stringResource(Res.string.topbar_back_title),
            onBack = {},
            backLabel = stringResource(Res.string.btn_back),
            actions = {
                DaftarIconButton(
                    onClick = {},
                    icon = DaftarIcons.Edit,
                    contentDescription = stringResource(Res.string.btn_edit),
                )
            },
        )
        DaftarTopBar(
            title = stringResource(Res.string.topbar_long_title),
            onBack = {},
            backLabel = stringResource(Res.string.btn_back),
        )
        DaftarFooterAction {
            PrimaryButton(onClick = {}, fullWidth = true) { Text(stringResource(Res.string.btn_save)) }
        }
    }
}

@Composable
private fun NavigationSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        var selected by rememberSaveable { mutableIntStateOf(1) }
        val labels = listOf(
            stringResource(Res.string.nav_home),
            stringResource(Res.string.nav_assets),
            stringResource(Res.string.nav_journal),
            stringResource(Res.string.nav_settings),
        )
        val icons = listOf(DaftarIcons.Home, DaftarIcons.Gold, DaftarIcons.Note, DaftarIcons.Settings)
        DaftarNavigationBar {
            labels.forEachIndexed { index, label ->
                DaftarNavigationItem(
                    label = label,
                    icon = icons[index],
                    selected = index == selected,
                    onClick = { selected = index },
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            DaftarNavigationRail {
                labels.forEachIndexed { index, label ->
                    DaftarNavigationRailItem(
                        label = label,
                        icon = icons[index],
                        selected = index == selected,
                        onClick = { selected = index },
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        val typography = DaftarTheme.typography
        DaftarLedgerRow(
            title = stringResource(Res.string.ledger_gold),
            subtitle = stringResource(Res.string.ledger_gold_sub),
            icon = DaftarIcons.GoldBar,
            tone = DaftarTileTone.Gold,
            value = { DaftarAmount(amount = "677,812.74", unit = "EGP", style = typography.ledgerValue) },
        )
        DaftarLedgerRow(
            title = stringResource(Res.string.ledger_bank_misr),
            subtitle = stringResource(Res.string.ledger_bank_misr_sub),
            icon = DaftarIcons.Bank,
            tone = DaftarTileTone.Bank,
            value = { DaftarAmount(amount = "163,500.00", unit = "EGP", style = typography.ledgerValue) },
            onClick = {},
        )
        DaftarLedgerRow(
            title = stringResource(Res.string.ledger_cash),
            subtitle = stringResource(Res.string.ledger_cash_sub),
            icon = DaftarIcons.Wallet,
            tone = DaftarTileTone.Cash,
            value = { DaftarAmount(amount = "9,900.00", unit = "EGP", style = typography.ledgerValue) },
            onClick = {},
            selected = true,
        )
        DaftarLedgerRow(
            title = stringResource(Res.string.ledger_investments),
            subtitle = stringResource(Res.string.ledger_investments_sub),
            icon = DaftarIcons.Investments,
            tone = DaftarTileTone.Investments,
            value = { DaftarAmount(amount = "157,000.00", unit = "EGP", style = typography.ledgerValue) },
            onClick = {},
        )
        DaftarLedgerRow(
            title = stringResource(Res.string.ledger_gold),
            subtitle = stringResource(Res.string.ledger_gold_sub),
            icon = DaftarIcons.GoldBar,
            tone = DaftarTileTone.Gold,
            value = {
                Text(
                    text = stringResource(Res.string.ledger_incomplete),
                    style = typography.ledgerValue,
                    color = DaftarTheme.colors.warning,
                    fontWeight = FontWeight.Medium,
                )
            },
            onClick = {},
        )
    }
}

/** Bank identity tiles (monogram on brand colour, neutral outline) and typographic currency badges. */
@Composable
private fun IdentitySection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        val typography = DaftarTheme.typography
        Row(
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaftarBankTile(mark = DaftarBankMark.Monogram("NBE", Color(0xFF006341)))
            DaftarBankTile(mark = DaftarBankMark.Monogram("CIB", Color(0xFF003B71)))
            DaftarBankTile(mark = DaftarBankMark.Monogram("ALEX", Color(0xFFA64000)))
            DaftarBankTile(mark = DaftarBankMark.Neutral)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The official Thndr mark sits on its near-black tile; the monogram is the offline fallback.
            DaftarBankTile(
                mark = DaftarBankMark.Official(
                    assetKey = "broker_eg_thndr",
                    brandBacking = Color(0xFF141414),
                    fallback = DaftarBankMark.Monogram("TH", Color(0xFFFFE147), Color(0xFF262D24)),
                ),
            )
            DaftarBankTile(mark = DaftarBankMark.Monogram("TH", Color(0xFFFFE147), Color(0xFF262D24)))
            DaftarBankTile(mark = DaftarBankMark.Neutral)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaftarCurrencyBadge(glyph = "$")
            DaftarCurrencyBadge(glyph = "E£")
            DaftarCurrencyBadge(glyph = "ج.م", arabicGlyph = true)
        }
        DaftarLedgerRow(
            title = stringResource(Res.string.ledger_bank_misr),
            subtitle = stringResource(Res.string.ledger_bank_misr_sub),
            leading = { DaftarBankTile(mark = DaftarBankMark.Monogram("BM", Color(0xFF9D1535))) },
            value = { DaftarAmount(amount = "163,500.00", unit = "EGP", style = typography.ledgerValue) },
        )
    }
}

@Composable
private fun CardSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        DaftarCard(
            title = stringResource(Res.string.card_title),
            time = stringResource(Res.string.card_time),
            actions = {
                DaftarTextButton(onClick = {}) { Text(stringResource(Res.string.btn_edit)) }
            },
        ) {
            Text(stringResource(Res.string.card_body), style = DaftarTheme.typography.supportingBody)
        }
    }
}

@Composable
private fun StatusSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaftarStatusTag(label = stringResource(Res.string.status_live))
            DaftarStatusTag(label = stringResource(Res.string.status_cached))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(DaftarSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaftarStatusTag(label = stringResource(Res.string.status_manual), tone = DaftarStatusTone.Warning)
            DaftarStatusTag(label = stringResource(Res.string.status_estimate), tone = DaftarStatusTone.Warning)
            DaftarStatusTag(label = stringResource(Res.string.status_stale), tone = DaftarStatusTone.Warning)
        }
        DaftarStatusTag(
            label = stringResource(Res.string.status_refreshing),
            icon = DaftarIcons.Refresh,
        )
    }
}

@Composable
private fun NoteSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        DaftarNotePanel {
            Text(stringResource(Res.string.note_context), style = DaftarTheme.typography.noteBody)
        }
        DaftarNotePanel(
            tone = DaftarNoteTone.Warning,
            icon = DaftarIcons.Clock,
            action = { DaftarTextButton(onClick = {}) { Text(stringResource(Res.string.btn_why)) } },
        ) {
            Text(stringResource(Res.string.note_warning), style = DaftarTheme.typography.noteBody)
        }
        DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.note_error), style = DaftarTheme.typography.noteBody)
        }
        DaftarNotePanel(icon = DaftarIcons.Eye) {
            Text(stringResource(Res.string.note_why), style = DaftarTheme.typography.noteBody)
        }
    }
}

@Composable
private fun BalanceSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        var hidden by rememberSaveable { mutableStateOf(false) }
        val colors = DaftarTheme.colors
        val allocation = listOf(
            DaftarAllocationSegment(677812.74f, colors.gold),
            DaftarAllocationSegment(388500f, colors.primary),
            DaftarAllocationSegment(9900f, colors.cashAccent),
            DaftarAllocationSegment(50000f, colors.investmentAccent),
        )
        DaftarBalanceSummary(
            label = stringResource(Res.string.balance_total),
            amount = "1,076,212.74",
            unit = "EGP",
            masked = hidden,
            maskedDescription = stringResource(Res.string.balance_hidden),
            unavailableDescription = stringResource(Res.string.balance_unavailable),
            provenance = {
                Text(
                    stringResource(Res.string.balance_provenance),
                    style = DaftarTheme.typography.fineText,
                    color = colors.muted,
                )
            },
            allocation = {
                DaftarAllocationBar(
                    segments = allocation,
                    summary = stringResource(Res.string.balance_allocation),
                )
            },
        )
        DaftarSettingRow(
            title = stringResource(Res.string.switch_hide_values),
            subtitle = stringResource(Res.string.btn_toggle_values),
            trailing = DaftarSettingTrailing.Switch(
                checked = hidden,
                onCheckedChange = { hidden = it },
                label = stringResource(Res.string.switch_hide_values),
            ),
        )
        DaftarBalanceSummary(
            label = stringResource(Res.string.balance_total),
            amount = null,
            unit = "EGP",
            unavailableDescription = stringResource(Res.string.balance_unavailable),
            provenance = {
                Text(
                    stringResource(Res.string.balance_provenance),
                    style = DaftarTheme.typography.fineText,
                    color = colors.muted,
                )
            },
            footer = {
                DaftarNotePanel(tone = DaftarNoteTone.Warning, icon = DaftarIcons.Alert) {
                    Text(stringResource(Res.string.balance_subtotal), style = DaftarTheme.typography.noteBody)
                }
            },
        )
    }
}

@Composable
private fun QuoteSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        val colors = DaftarTheme.colors
        DaftarQuoteStrip(
            quotes = listOf(
                DaftarQuote("24K", "7,069"),
                DaftarQuote("21K", "6,185"),
                DaftarQuote("18K", null),
                DaftarQuote("USD", "48.50"),
            ),
            provenance = stringResource(Res.string.quote_provenance),
            status = { DaftarStatusTag(label = stringResource(Res.string.status_cached)) },
        )
        DaftarQuoteStrip(
            quotes = listOf(
                DaftarQuote("24K", "7,069"),
                DaftarQuote("21K", "6,185"),
                DaftarQuote("18K", "5,301"),
                DaftarQuote("USD", "48.50"),
            ),
            provenance = stringResource(Res.string.quote_provenance_refreshing),
            status = {
                DaftarStatusTag(
                    label = stringResource(Res.string.status_refreshing),
                    icon = DaftarIcons.Refresh,
                )
            },
        )
        Text(stringResource(Res.string.quote_unit), style = DaftarTheme.typography.caption, color = colors.muted)
        DaftarQuoteTable(
            rows = listOf(
                DaftarKaratQuote("24K", "7,069", "7,012"),
                DaftarKaratQuote("21K", "6,185", "6,135"),
                DaftarKaratQuote("18K", null, "5,259"),
            ),
            karatLabel = stringResource(Res.string.quote_karat),
            sellLabel = stringResource(Res.string.quote_sell),
            buyLabel = stringResource(Res.string.quote_buy),
        )
    }
}

@Composable
private fun AuthStatesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.catalog_auth_mismatch))
        }
        DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.catalog_auth_wrong_pin))
        }
        DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Clock) {
            Text(stringResource(Res.string.catalog_auth_backoff))
        }
        Text(
            text = stringResource(Res.string.catalog_auth_paused),
            style = DaftarTheme.typography.fineText,
            color = DaftarTheme.colors.muted,
        )
        DaftarNotePanel(tone = DaftarNoteTone.Warning) {
            Text(stringResource(Res.string.catalog_auth_recovery))
        }
        DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.catalog_auth_hardware))
        }
        DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.catalog_auth_key))
        }
        DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.catalog_auth_corrupt))
        }
        DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.catalog_auth_format))
        }
        DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Alert) {
            Text(stringResource(Res.string.catalog_auth_transient))
        }
        PrimaryButton(onClick = {}, loading = true, fullWidth = true) {
            Text(stringResource(Res.string.catalog_auth_busy))
        }
    }
}

@Composable
private fun PinSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        val colors = DaftarTheme.colors
        DaftarPinField(
            enteredCount = 0,
            stateDescription = stringResource(Res.string.pin_empty),
            placeholder = PIN_PLACEHOLDER,
        )
        DaftarPinField(
            enteredCount = 3,
            stateDescription = stringResource(Res.string.pin_entered, 3),
        )
        DaftarPinField(
            enteredCount = 6,
            stateDescription = stringResource(Res.string.pin_entered, 6),
        )
        DaftarPinField(
            enteredCount = 6,
            stateDescription = stringResource(Res.string.pin_entered, 6),
            invalid = true,
        )
        DaftarPinField(
            enteredCount = 4,
            stateDescription = stringResource(Res.string.pin_entered, 4),
            enabled = false,
        )
        var entered by rememberSaveable { mutableIntStateOf(0) }
        DaftarPinField(
            enteredCount = entered,
            stateDescription = stringResource(Res.string.pin_entered, entered),
            placeholder = PIN_PLACEHOLDER,
        )
        DaftarKeypad(
            onDigit = { if (entered < MAX_PIN) entered++ },
            onErase = { if (entered > 0) entered-- },
            eraseLabel = stringResource(Res.string.pin_erase),
            biometricLabel = stringResource(Res.string.pin_biometric),
            onBiometric = {},
        )
        DaftarKeypad(
            onDigit = {},
            onErase = {},
            eraseLabel = stringResource(Res.string.pin_erase),
            enabled = false,
        )
        // The keypad in dark theme beside the current one, enabled and disabled, so digit contrast is checked in both.
        DaftarTheme(darkTheme = true, arabic = LocalLayoutDirection.current == LayoutDirection.Rtl) {
            Column(
                modifier = Modifier.background(BoardDark).padding(DaftarSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm),
            ) {
                DaftarKeypad(
                    onDigit = {},
                    onErase = {},
                    eraseLabel = stringResource(Res.string.pin_erase),
                    biometricLabel = stringResource(Res.string.pin_biometric),
                    onBiometric = {},
                )
                DaftarKeypad(
                    onDigit = {},
                    onErase = {},
                    eraseLabel = stringResource(Res.string.pin_erase),
                    enabled = false,
                )
            }
        }
        DaftarNotePanel(tone = DaftarNoteTone.Error, icon = DaftarIcons.Clock) {
            Text(
                stringResource(Res.string.pin_backoff_title),
                style = DaftarTheme.typography.smallHeading,
                color = colors.error,
            )
            Text(stringResource(Res.string.pin_backoff), style = DaftarTheme.typography.noteBody)
        }
    }
}

private const val PIN_PLACEHOLDER = "••••••"
private const val MAX_PIN = 16

@Composable
private fun StateSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        DaftarEmptyState(
            title = stringResource(Res.string.empty_title),
            message = stringResource(Res.string.empty_message),
            actionLabel = stringResource(Res.string.btn_add_holding),
            onAction = {},
            icon = DaftarIcons.Gold,
        )
        DaftarResultState(
            title = stringResource(Res.string.result_saved_title),
            message = stringResource(Res.string.result_saved_message),
            primaryLabel = stringResource(Res.string.result_done),
            onPrimary = {},
        )
        DaftarResultState(
            title = stringResource(Res.string.result_error_title),
            message = stringResource(Res.string.result_error_message),
            primaryLabel = stringResource(Res.string.result_retry),
            onPrimary = {},
            tone = DaftarResultTone.Error,
            secondaryLabel = stringResource(Res.string.btn_back),
            onSecondary = {},
        )
        DaftarSkeleton(size = DaftarSkeletonSize.Big)
        DaftarSkeleton()
        DaftarSkeleton(size = DaftarSkeletonSize.Short)
    }
}

@Composable
private fun ConfirmSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        DaftarConfirmationContent(
            title = stringResource(Res.string.confirm_discard_title),
            message = stringResource(Res.string.confirm_discard_message),
            primaryLabel = stringResource(Res.string.confirm_keep),
            onPrimaryClick = {},
            destructiveLabel = stringResource(Res.string.confirm_discard),
            onDestructiveClick = {},
        )
        DaftarConfirmationContent(
            title = stringResource(Res.string.confirm_blocked_title),
            message = stringResource(Res.string.confirm_blocked_message),
            primaryLabel = stringResource(Res.string.confirm_ok),
            onPrimaryClick = {},
        )
    }
}

@Composable
private fun PrivacySection() {
    Column(verticalArrangement = Arrangement.spacedBy(DaftarSpacing.sm)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(DaftarTheme.colors.line, DaftarTheme.shapes.surface),
        ) {
            DaftarPrivacyCover(
                name = stringResource(Res.string.catalog_brand),
                modifier = Modifier.background(DaftarTheme.colors.paper, DaftarTheme.shapes.surface),
            )
        }
    }
}
