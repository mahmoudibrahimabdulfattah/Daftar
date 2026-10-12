Phase 1 should establish Daftar’s complete visual vocabulary and a runnable component catalog. Then follow Nashra’s sequence, inserting encryption before storage and authentication before private features.

The authority is [daftar.html](design/daftar.html). Reuse Nashra’s implementation pattern—not its palette, shapes or weather-specific components.

**Ordered v1 roadmap**

Each row is one branch and one PR. Foundation phases ship through tests and the debug catalog; feature phases ship working vertical slices. Register new modules in `settings.gradle.kts`; `shared` owns DI and navigation wiring.

| Phase | Name / modules touched | Scope | Acceptance check |
|---|---|---|---|
| 1 | **Design system** — `core:designsystem`, `shared` | Tokens, fonts, Styles API, reusable components, localized catalog. | All catalog states render in AR/EN × light/dark on both platforms; details below. |
| 2 | **Models, common utilities, network** — `core:model`, `core:common`, `core:network` | Asset/journal/quote models; exact decimal representation; numeric normalization; clock/connectivity contracts; gold/FX transport and DTO mapping. Resolve provider access and licensing before implementing live adapters. | Fixture tests cover valid, missing, malformed and failed responses; per-karat sell/buy quotes retain source/time; no binary floating-point monetary arithmetic. |
| 3 | **Encryption foundation** — `core:security` | PIN + platform-secret key derivation; platform key storage; encrypted database opening contract; persistent backoff. | Android/iOS tests prove wrong PIN cannot open data, plaintext is absent, relaunch cannot bypass backoff; no automatic wipe. |
| 4 | **Encrypted local data** — `core:database`, `core:datastore`, `core:data`, repository contracts in `core:domain` | Encrypted asset/journal/quote storage; non-sensitive preferences; repository implementations and migrations. Sensitive notes and overrides stay encrypted. | CRUD survives restart; constraints and transactions hold; locked repositories cannot access private records; failed writes preserve existing data. |
| 5 | **Platform services and DI** — `core:common`, `core:network`, `core:security`, `core:data`, `shared`, platform shells | Koin composition, connectivity, lifecycle/session coordination; disable automatic backup/device transfer; recents cover before sensitive rendering. | Both targets resolve dependencies; background closes storage and clears session; previews of the backgrounded app reveal only the neutral cover. |
| 6 | **Valuation and use cases** — `core:domain` | Exact gold/FX valuation, totals, validation, quote selection/freshness, repository use cases. Inject time as input. | Design fixtures reproduce gold `677,812.74`, banks `388,500`, cash `9,900`, total `1,076,212.74`; missing quotes yield incomplete valuation, never zero. |
| 7 | **Adaptive application shell** — `core:ui`, `shared`, minimal `feature:home/assets/journal/settings` entries | Typed Navigation 3 routes; four destinations; bottom navigation/rail; list-detail infrastructure; safe areas and IME handling. | Correct layout at `<600`, `600–839`, `≥840dp`; RTL mirrors panes; top-level positions survive navigation within the session. |
| 8 | **Discreet platform identity** — `androidApp`, `iosApp`, `shared` | Notebook launcher icon, localized name, neutral splash/launch appearance. | Installed apps show «دفتر»/Daftar and the approved notebook mark; no wealth imagery or private launch content. |
| 9 | **PIN onboarding and lock** — `feature:auth`, `core:security`, `core:ui`, `shared` | Create/confirm 6–16 digit PIN, unlock, mismatch, persisted backoff, forgotten-PIN explanation; immediate background lock. | Five wrong attempts trigger 30s, then 60s, 120s, capped at 15m; relaunch preserves delay; lock clears drafts/routes; unlock returns home. |
| 10 | **Optional biometrics** — `feature:auth`, `core:security`, `shared` | Native biometric prompts and authenticated key unwrapping; setup skip; enrollment-change/unavailable fallback. | Real Android/iOS authentication; PIN fallback always works; device credentials do not substitute for the app PIN. |
| 11 | **Home and asset overview** — `feature:home`, `feature:assets`, `core:ui` | Totals, allocation strip, category rows, quote strip, empty/incomplete/loading/offline states; privacy eye. | Uses repositories/domain values; masking immediately removes amounts, weights and sensitive notes from display and accessibility semantics. |
| 12 | **Prices and manual overrides** — `feature:prices`, `core:data`, `core:domain` | Source/time details, sell/buy table, refresh, stale/missing states, manual entry/pinning/removal. | Refresh after unlock and on demand only; failed refresh preserves successful timestamp; pinned manual quote survives refresh; XAU never silently enters totals. |
| 13 | **Gold** — `feature:gold` | List/add/edit/delete; 24K/21K/18K, bars/jewelry, net weight, notes. | Positive weight, ≤2 decimals; correct per-karat sell valuation; no making charges; save failure retains draft; deletion/discard confirmation works. |
| 14 | **Banks and accounts** — `feature:banks` | Bank names; account CRUD in EGP/USD; aggregate EGP value; adaptive bank list/detail. | Account currency is preserved; conversion is display valuation only; bank with accounts cannot be deleted; visible list removes detail back arrow. |
| 15 | **Cash and wallets** — `feature:cash` | Cash/wallet CRUD, EGP/USD, notes. | Zero balance allowed, negative rejected; Thundr is cash balance only; edits update totals correctly. |
| 16 | **Journal** — `feature:journal` | Zakat payment log and free-text notes, forms and empty/error states. | Zakat never deducts assets; amounts in notes never contribute to totals; dates/amounts persist correctly. |
| 17 | **Preferences and PIN change** — `feature:settings`, `core:datastore`, `core:security`, `core:data` | System/light/dark, AR/EN, 30s/1m/5m inactivity timeout, biometric preference, transactional PIN change. | Language/theme apply immediately; background lock is unconditional; current PIN required; failed rekey leaves old PIN/data working. |
| 18 | **Encrypted export** — `core:backup`, `feature:backup`, platform file adapters, `shared` | Versioned authenticated backup, separate confirmed password ≥12 characters, native save picker. | Round-trip fixture decrypts; no PIN/biometric keys exported; fresh salt/nonce; success appears only after completed write; cancellation/failure leaves database intact. |
| 19 | **Restore and forgotten-PIN recovery** — `core:backup`, `feature:backup`, `feature:auth`, `core:security`, `core:data` | Pick/decrypt/validate/review; explicit replacement; new PIN; atomic database replacement. | Wrong password, corruption, newer format, cancellation and interrupted replacement preserve original; restored quote timestamps stay original; biometrics require re-enrollment. |
| 20 | **v1 release verification** — affected modules, platform shells, release docs | Cross-platform accessibility, lifecycle and failure-path review; release configuration, privacy declarations, packaged font licenses. | Complete approved screen/state matrix passes; no private logs/clipboard/notifications/backups; release builds and device smoke tests pass. |

Quote policy: **explicit pinned manual override wins until removed**. Otherwise use live → cached with original timestamp → manual input when unavailable. Adopt the design’s proposed **over 24 hours** stale threshold for v1. An incomplete valuation shows `—` for total plus a clearly labeled available subtotal.

Every PR runs:

```bash
./gradlew spotlessCheck testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug
./gradlew iosSimulatorArm64Test
```

Then launch `com.mk.daftar.debug` on Android; UI phases also run on **iPhone 18 Pro**. Squash-merge, delete both branch copies, pull `master`, then begin the next phase.

**Phase 1: implementation contract**

Create `core:designsystem` with `daftar.kmp.compose`, package `com.mk.daftar.core.designsystem`, and resources namespace `com.mk.daftar.designsystem.resources`.

Use Nashra’s `theme/`, `component/`, `icon/`, `catalog/` organization:

- `DaftarColors`, `DaftarTypography`, `DaftarShapes`, `DaftarSpacing`, `DaftarStyles`.
- Immutable `DaftarTheme` with companion token accessors.
- Dynamic `compositionLocalOf`, with `StyleScope` extensions reading `currentValue`, so existing style nodes react to theme switches.
- Container styles composed through `Modifier.styleable(styleState, baseStyle, style)`.
- `modifier: Modifier = Modifier` first optional parameter; `style: Style = Style` for styled components.
- One remembered `MutableInteractionSource` shared by interaction handling and `rememberUpdatedStyleState`; propagate enabled/selected states.
- `ProvideContent` supplies `LocalContentColor` and `LocalTextStyle`: container styles alone do not color Material text/icons.
- Match Material 3’s complete color scheme to these tokens, including error and surface-container roles; prevent default purple/pink roles leaking into pickers.
- Keep components stateless, with callbacks and presentation values. No repositories, security operations or navigation ownership in this module.

Nashra’s checked-in [Styles implementation] and [button implementation] are the concrete templates. Both repositories currently declare Compose Multiplatform `1.12.1` and Material 3 `1.9.0`; prove Styles compilation on both targets before changing dependencies. The HTML’s historical alpha warning is not an instruction to downgrade.

**Exact color tokens**

All colors are opaque.

| Kotlin token ← CSS | Light | Dark |
|---|---|---|
| `paper` ← `--paper` | `#F5F3EB` | `#191E18` |
| `surface` | `#FFFEF8` | `#232A21` |
| `ink` | `#262D24` | `#EFF0E6` |
| `muted` | `#626657` | `#BDC4B3` |
| `primary` | `#45583D` | `#BFD0AD` |
| `onPrimary` ← `--on` | `#FFFEF8` | `#23331E` |
| `soft` | `#E4E8D9` | `#35432E` |
| `line` | `#C8CBBB` | `#515D49` |
| `gold` | `#835B24` | `#E7BF80` |
| `goldSoft` | `#F1E4CC` | `#413524` |
| `error` | `#A1332C` | `#FFB4A9` |
| `errorSoft` | `#FBE3DC` | `#482723` |
| `warning` ← `--warn` | `#78521D` | `#F0CC87` |
| `warningSoft` ← `--warnsoft` | `#F7E9CA` | `#42371F` |

Catalog-only board tokens: `board = #EAE9E0 / #121710`, `device = #41483B / #78816E`. Phone frames, fake status bars and gesture bars are presentation scaffolding, not production components.

Use paper for screen backgrounds; surface for fields/cards/navigation; ink for primary text; muted for supporting text. Brass identifies gold, with the HTML’s gold-colored focus ring as an explicit exception. No gradients or financial performance charts.

**Typography**

Bundle Alexandria and Rubik locally, with actual weight mappings **400/500/600/700**. Alexandria serves Arabic; Rubik serves English **and all numeric runs in either language**. Do not rely on synthesized weights or downloaded fonts.

These are application CSS values, excluding the design-board masthead:

| Role | Size / weight | Line-height |
|---|---|---|
| Screen heading | `27sp / 500` | `1.55em` |
| App-bar title | `18sp / 500` | `1.6em` |
| Section heading | `19sp / 500` | `1.6em` |
| Small heading | `14sp / 700` | `1.8em` |
| Notebook title | `15sp / 700` | `1.8em` |
| Body | `13sp / 400` | `1.8em` |
| Supporting panel/notebook body | `12sp / 400` | `1.8em`; note panels `1.9em` |
| Ledger title | `13sp / 500` | `1.8em` |
| Ledger value / input text | `14sp / 400` | `1.8em` |
| Button / setting title | `12sp / 500` | `1.8em` |
| Field label | `11sp / 500` | `1.8em` |
| Fine text / row subtitle | `11sp / 400` | `1.8em` |
| Helper / tag / timestamp / navigation label | `10sp / 400` | `1.8em` |
| Segment label | `12sp / 400`, selected `600` | `1.8em` |
| Selected bottom-nav label | `10sp / 600` | `1.8em` |
| Quote number | `13sp / 500`, Rubik | `1.8em` |
| Hero amount | `38sp / 500`, Rubik | `1.3em` |
| PIN / keypad numeral | `24sp / 400`, Rubik | Natural font metrics; HTML supplies no explicit line-height |

Hero amount tracking: `−0.035em`. English screen heading: `−0.02em`.

**Native Arabic decision:** follow Nashra’s corrected typography: Arabic tracking `0`, minimum line-height `1.7em`, centered line-height alignment and `Trim.None`. This intentionally overrides the HTML’s tighter Arabic heading metrics to prevent clipped dots and overlapping lines. Numeric Rubik styles retain their own metrics.

Use tabular numerals; format money with grouping and two decimals, weights with up to two decimals. Isolate number **and unit together** with `⁦…⁩`. Input accepts Arabic-Indic digits; normalization belongs in common/domain code.

**Shapes, spacing and dimensions**

The HTML’s prose says surfaces `16dp`, while rendered notebook/quote/note cards explicitly use `12px`. Preserve those component-specific values; use `16dp` only as the generic surface token.

| Token/use | Value |
|---|---|
| Main spacing scale | `4, 8, 12, 16, 20, 24, 32dp` |
| Fields | `10dp` radius |
| Buttons, segments, notes, notebook cards, quote strip, PIN field | `12dp` radius |
| Generic surface, keypad keys | `16dp` radius |
| Tags / tile icons / skeletons | `6 / 11 / 4dp` radius |
| Nav indicator / switch track | `20dp` radius |
| Icon buttons, emblems, switch thumb | Circle |
| Borders/dividers | `1dp`; invalid field border `2dp` |
| Focus ring | `3dp gold`, `3dp` outer gap |
| Content padding | Start/end `20dp`, top `8dp`, bottom `22dp` |
| Top bar | Minimum `66dp`; horizontal `10dp`, vertical `9dp`, gap `6dp` |
| Footer action area | Vertical `12dp`, horizontal `20dp`, top divider |
| Buttons | Minimum `48dp`; padding horizontal `16dp`, vertical `11dp`, content gap `8dp`; text button padding `8dp` |
| Inputs / textarea | Minimum `52dp / 110dp`; padding `12dp` |
| Field spacing | Vertical margin `17dp`; label gap `7dp`; helper gap `5dp` |
| Ledger row | Minimum `64dp`; vertical padding `15dp`; gap `10dp` |
| Setting row | Minimum `65dp`; vertical padding `12dp`; gap `12dp` |
| Notebook / note / quote padding | `17 / 13 / 12dp` |
| Icons | Standard `22dp`; row tile `38dp` with `20dp` icon; note icon `18dp` |
| PIN/keypad | PIN field `54dp`; keys minimum `58dp`; grid gap `8dp`, always LTR |
| Bottom navigation | Item minimum `62dp`; indicator `56 × 30dp`; outer padding top `9dp`, horizontal `8dp` |
| Rail | Width `90dp`; item width `70dp`, minimum height `60dp`, radius `12dp` |

Use minimum heights, not fixed heights for text-bearing controls. Grow and wrap at larger system font sizes; never shrink text to preserve a row.

**Components and required states**

Build reusable presentation components; feature screens assemble them later.

| Component | Required variants/states |
|---|---|
| `DaftarMark`, `DaftarIcon` | Exact notebook SVG mark from HTML; theme-aware tint; decorative/labeled; directional arrows mirror, gold/fingerprint do not. |
| `PrimaryButton`, `OutlineButton`, `DaftarTextButton`, `DaftarIconButton` | Enabled, pressed, focused, disabled; saving label disables repeat submission; optional leading icon/full-width. Primary `primary/onPrimary`; outline transparent/line/ink; text primary. |
| `DaftarTextField` | Empty, placeholder, populated, focused, disabled, read-only, invalid with textual helper; text/decimal/password modes; labels remain visible. |
| `DaftarTextArea`, `DaftarSelectField`, `DaftarDateField` | Same field shell; multiline growth; selected/unselected; expanded picker; invalid. Use native/platform-appropriate picker behavior later. |
| `DaftarSegmentedControl` | Single selection, unselected/selected, pressed/focused/disabled; selected soft/primary/600; internal dividers and equal-width items. |
| `DaftarSwitch`, `DaftarSettingRow` | On/off, enabled/disabled/focused; switch `47 × 28dp`, thumb `20dp`, padding `4dp`; enclosing target ≥48dp. Value, link and switch trailing variants. |
| `DaftarTopBar`, `DaftarFooterAction` | Brand or back; optional trailing actions; long title; keyboard-safe footer contract. |
| `DaftarNavigationBar`, `DaftarNavigationRail` | Selected/unselected, focused/pressed; four slot-driven destinations. Bottom selected pill soft, label primary; unselected muted. |
| `DaftarLedgerRow`, `DaftarTileIcon` | Static/clickable, default/gold tone; subtitle/value/chevron slots; wrapped value; selected/focused where interactive. |
| `DaftarCard` | Surface/line notebook container; optional title/body/time/actions; content grows. |
| `DaftarStatusTag` | Live/cached: soft/primary. Manual/estimate/stale: warningSoft/warning. Refreshing: labeled icon; meaning never depends on color alone. |
| `DaftarNotePanel` | Context soft/primary, warning warningSoft/warning, error errorSoft/error; optional icon and action; accessible error text. |
| `DaftarAmount`, `DaftarBalanceSummary`, `DaftarAllocationBar` | Visible/masked/unavailable; currency and provenance slots; incomplete subtotal; allocation hidden when masked and never calculated from demo percentages. |
| `DaftarQuoteStrip`, `DaftarQuoteTable` | Per-quote available/missing and provenance; refresh-in-progress preserves values; sell column gold/500, buy column ordinary ink. |
| `DaftarPinField`, `DaftarKeypad` | Empty/partial/complete/error/disabled/backoff; 6–16 digits; erase; optional biometric slot. Secret values excluded from accessibility output. No authentication logic. |
| `DaftarEmptyState`, `DaftarResultState`, `DaftarSkeleton` | Empty with CTA; success/error with recovery actions; skeleton normal/big/short. Existing balances remain during quote refresh. |
| `DaftarConfirmationContent` | Delete/discard/replace/blocked variants; primary safe action and outlined destructive action, matching HTML. No invented red destructive button. |
| `DaftarPrivacyCover` | Neutral notebook mark/name only; immediate display. Actual lifecycle/recents enforcement belongs to platform/security phases. |

Global disabled opacity is **0.45**, matching HTML, rather than Nashra’s `0.5`. HTML specifies hover brightness `0.95` but no touch-pressed treatment; **Phase 1 decision:** use a subtle `0.95` pressed alpha through Styles, without layout changes. Preserve visible keyboard focus. Navigation transitions are `180ms`; reduced motion removes transitions; masking and locking are immediate.

**Phase 1 acceptance**

The catalog must exercise every component/state with demo data, all four language/theme combinations, RTL arrows and LTR numeric runs. Verify normal and 200% font scale, wrapped ledger values, focus order, screen-reader names/state/error announcements, and touch targets.

Compare representative catalog compositions against the HTML: app bar, balance/ledger/quote strip, form, PIN pad, settings row and bottom navigation. Confirm runtime theme switching updates existing Styles nodes. Run the shared PR checks and both required simulators.

Phase 1 ends with that working catalog and documented token/component contracts; no valuation, persistence or authentication behavior belongs in this PR.