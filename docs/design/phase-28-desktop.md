# Phase 28 — Daftar on macOS and Windows

Design decision, 14 October 2026. Implementation handoff only; this phase changes no application code. The `p28-` frames in [daftar.html](daftar.html#p28-desktop) are the wide-layout reference. Earlier phone frames remain authoritative for compact layouts. All displayed records are illustrative.

## Preserve the current product

Keep Alexandria, Rubik numeric runs, current colors, identity tiles, official marks, spacing, rows, buttons and Phase 25 PIN geometry. Carry forward Phase 24 Investments and Phase 27 price provenance/status, plus current recovery-key behavior. No charts, transaction tables, sidebar tree, new destinations or desktop-only financial features.

Hard implementation boundary: leave the current Android/iOS layout path unchanged, including phone landscape. Apply these new arrangements only when **desktop platform AND client window width ≥600dp**. Width, never monitor resolution or OS identity alone, selects the arrangement. Existing mobile 600/840 behavior remains intact. Below 600dp use the original compact layout, with no changed tokens, paddings, typography, component order or bottom-bar behavior. Desktop input and security adapters are separate from layout. This extra platform guard guarantees that a landscape phone cannot accidentally receive a new wide rule.

## Window and width contract

- Default client area: **1200 × 800dp**, excluding native title bar and borders. Minimum client area: **720 × 600dp**. Use OS logical units at display scaling, not physical pixels. Initially center in the current monitor's usable work area; shrink the default to fit. The desktop launch environment must accommodate the minimum at its current scale.
- Native decorated, resizable window; keep native close/minimize/maximize controls and OS shortcuts. No custom title-bar controls. Title is **دفتر** in Arabic and **Daftar** in English, including while locked. Never put route, record name or amounts in the OS title.
- Remember normal-window size, position, monitor and maximized state in local non-sensitive preferences. Never restore minimized/hidden. Clamp restored bounds to a connected monitor's work area; recenter if the saved monitor is gone. Open locked regardless of geometry restoration. One vault window; no detachable panes.
- `W` is client width before subtracting the rail. Existing `RailBreakpoint=600dp`, `SimultaneousPanesWidth=840dp` remain. Do not measure the remaining pane as the window width.

| Window width | Shell and layout |
| --- | --- |
| <600dp, Compact | Original phone composition, untouched. Defensive desktop fallback only; below desktop minimum. |
| 600–839dp, Medium | Existing 90dp rail at logical start; one capped page. Desktop minimum falls here. |
| 840–1199dp, Expanded | Same rail; eligible Nav3 pairs may appear side by side. Home/Assets remain one column. |
| ≥1200dp, Large | Same rail; Home supporting column and two-column Assets. No third column at any width. |

For new desktop layouts use 24dp outer horizontal gutters, 24dp top/bottom page padding below the existing 66dp top bar, 24dp column gap. Width caps below describe the usable inner content box, excluding gutters and rail. Center that box in the remaining space, not the full window. Use `min(cap, availableWidth)`; never force a minimum child width that creates horizontal scrolling. Root top bars retain their existing actions. All nested screens have a visible 48dp back target, including in a detail pane; the arrow points to logical start.

List-detail group cap: **1200dp**, after the outer gutters. At 840dp: 702dp group = 280dp list + 24dp gap + 398dp detail. Through Expanded use 280dp list; at Large use 320dp list and give the remainder to detail. Detail body capped at 640dp, editors at 560dp, centered within their pane. A 1dp line at the list's logical end sits inside its width. List is on the right in Arabic and left in English. Each pane scrolls independently; top bars/footer actions do not scroll with records. No drag splitter.

## Screen decisions

All entries below apply only inside the new desktop ≥600dp branch. A single-page cap remains in force at every wider class unless a pair/grid is explicitly specified. Form fields always stay in one column.

| Routes | Medium / unpaired cap | Expanded (≥840dp) | Large (≥1200dp) |
| --- | --- | --- | --- |
| Home | 720dp | Same single column | 1120dp group: 24dp gap, main takes remainder, supporting column 360dp |
| Assets | 720dp, four category rows | Same | 1120dp, two equal columns, 24dp gap; row-major Gold, Banks, Cash, Investments |
| Gold | 720dp, balance then holdings then Add | Same; no new detail route | Same |
| Gold Add / Edit | 560dp | Same, full page | Same |
| Cash | 720dp, balance then records then Add | Same; no detail pane | Same |
| Cash Add / Edit | 560dp | Same, full page | Same |
| Banks / BankDetail | 720dp | Existing Nav3 list-detail pair; 1200dp group | Same, 320dp list |
| Bank Add / Edit, Account Add / Edit | 560dp | Full-page editor; return to selected BankDetail | Same |
| Investments / Detail | 720dp | Existing Nav3 pair; 1200dp group | Same, 320dp list |
| Investments Add / Edit / ConvertCash | 560dp | Existing Nav3 scene in detail pane; body 560dp max | Same |
| Prices / PriceDetails | 640dp | Same; preserve Phase 27 board and provenance, no supporting pane | Same |
| ManualQuote | 640dp (existing cap) | Same | Same |
| Journal | 720dp | Same: reminder, calculator, Notes and current journal entries in original order | Same |
| Notes | 720dp list | Same; no preview pane or invented read-only route | Same |
| Notes Add / Edit | 560dp | Same, full page | Same |
| Zakat landing | 720dp | Same, current-year summary/actions | Same |
| Zakat History / Year | 720dp | Nav3 years list + selected year, 1200dp group | Same, 320dp list |
| Zakat YearAdd / Add / Edit / Date | 560dp | Full-page editors; return to originating year | Same |
| Zakat Calculator | 640dp | Same; keep estimate, assumptions and record-payment action together | Same |
| Settings / Preferences / About | 640dp | Same; no settings sidebar | Same |
| ChangePin / RecoveryKey | 560dp page; PIN step uses auth width | Same; recovery text/key never becomes a second pane | Same |
| BackupExport / Restore | 560dp (existing cap) | Same, native file dialog for destination/source | Same |
| Cloud / CloudUpload | Absent | Absent | Absent |
| Setup / Confirm / Unlock / Forgot PIN / recovery PIN reset | PIN column 360dp; explanatory/recovery forms 560dp | Same | Same |

Gold and Cash rows already lead directly to editing; Notes is also an edit flow. Keep that interaction rather than adding selection and a new detail destination. The existing rows and notebook cards should not be expanded into tables.

### Home composition

At ≥1200dp put greeting, total, valuation completeness, allocation and four category rows in the main column, in their existing order. At logical end put the **unchanged Phase 27 quote card**, then the existing zakat reminder. Show guide content only under the existing first-run conditions, in the main column; do not display a fictional zero total when prices are missing. No duplicate price table or invented recent activity. Keep eye and refresh in the existing top bar. Use one page scroll for both columns, aligned at their top edges; the supporting column is not sticky. At <1200dp preserve the current single-column content order. Use existing empty, pending, manual, stale and partial-value states without changing their meaning.

### Scene and resize behavior

Use Nav3 `ListDetailSceneStrategy` for the existing Banks/Investments pairs and the new History→Year pair. Do not use a second independent navigation controller. Entering a list without a selection shows its placeholder; never automatically open the first financial record. Selecting a row opens its detail, marks the row with the existing soft fill, and moves keyboard focus to the detail heading. Empty lists show their existing Add action and an empty detail placeholder. Loading/error stays within the affected pane, with the existing retry action.

Resize preserves entry identity, selection, draft, validation and scroll positions. Collapsing below 840dp shows the active detail/editor with Back to its list; expanding restores both panes without duplicating entries. Back from a selected detail clears selection and shows the placeholder. Back from an editor returns to its originating detail. Existing discard confirmation applies to Back, Esc, rail navigation and window close when a draft is dirty. Explicit lock, minimize/hide and inactivity lock immediately and clear sensitive drafts without waiting for that confirmation. At 200% text, if the actual pane content cannot fit, fall back to a single active pane even above 840dp; `LocalSimultaneousPanes` is eligibility, not proof that a pair is displayed. Determine Back visibility from actual scene state, not that boolean alone.

## PIN and desktop input

Keep the on-screen keypad at every desktop width: it is familiar, supports pointer/touch and preserves Phase 25's stable layout. Center a **360dp** column in the client area without a rail. Use the Phase 25 698dp minimum inner stack, 16dp surrounding padding and the existing reserved top-bar space. The input/message/keypad/submit/footer block remains bottom anchored; error, busy, biometric unavailable and countdown messages use the same reserved lane. At minimum height the entire stack scrolls vertically; never shrink the 58dp keys or pin the footer over content. At larger heights anchor to the bottom of the available stack; never enlarge keypad keys to fill width.

- Accept ASCII, Arabic-Indic and Eastern Arabic-Indic digits from top row or numpad; normalize before validation. Preserve current 6–16 digit policy. No spaces, signs or decimal characters; no paste, copy, cut, drag or input history for PINs.
- Enter invokes the same enabled submit action as the button; never auto-submit at six digits. Ignore key repeat for Enter and all input during an in-flight request/backoff. Backspace removes one digit; holding it may repeat deletion. Do not intercept OS shortcuts or keys belonging to another field/dialog.
- On entering a PIN step focus its secure input. Tab/Shift+Tab follows input, keypad in numeric order, submit, then secondary action. Enter/Space on a focused keypad key activates that key, not the form. The bottom row remains biometric/blank, zero, erase; never mirror its LTR order.
- Clicking a keypad key keeps the PIN input ready for typing. After error return focus to input; clear rejected digits using existing behavior. No forced focus on every recomposition. When the system biometric dialog owns focus, the app does not capture input; after cancellation restore PIN focus without another automatic prompt.
- Touch ID appears only when usable and enabled. Preserve one automatic attempt per lock episode and all cancel/backoff/recovery exclusions. No unsupported biometric onboarding step. PIN is always available. Windows without a verified Hello adapter ships PIN-only, with no disabled promotional tile.

## Pointer, keyboard and privacy

Keep 48dp minimum interactive targets, 52dp fields, current density and typography. No compact desktop density mode. Hover must not reveal hidden values or actions unavailable by keyboard/touch.

- Interactive unfilled rows, rail items, icon/outline/text buttons: existing `soft` background on hover; selected row retains soft fill after pointer leaves. Filled primary buttons/keypad: 0.95 opacity on hover. Pressed: existing 0.95 opacity, disabled: existing 0.45; no stacked opacity reductions, lift or resizing. Use the existing Styles API, isolated to desktop pointer state. Do not recolor bank marks.
- Hand cursor on enabled actions; arrow on noninteractive text and disabled actions; I-beam only in editable fields. No custom cursors.
- Retain the implemented **3dp gold focus ring, 3dp outside the control** (the established focus-token exception to gold's category usage). Reserve clipping clearance. Focus never changes layout. Keyboard order: rail, top-bar actions, main/list, detail/supporting region, footer. Within a region follow visual reading order. Escape closes the foremost popup first, then runs Back with the existing dirty-draft guard; at a root or lock screen it does nothing. It never quits/unlocks.
- **Cmd+L on macOS / Ctrl+L on Windows** locks immediately from any unlocked app route, including an editor. Also expose the existing lock action in Settings with the localized shortcut as its trailing hint. No shortcut toast containing data. No other new app shortcuts; standard text editing and OS window shortcuts remain.
- No text selection on displayed balances, weights, notes, PINs or record labels. Editable fields allow in-field selection/caret operations, but sensitive fields disable copy/cut/drag and selection context menus; clipboard must never receive balances or notes. PIN also disables paste. Do not wrap pages in a general selection container. Existing recovery-key Copy is the sole sensitive copy exception, on explicit tap, device-only/sensitive where supported and cleared after 60 seconds. Public help/license/source text may be selected/copied. Existing protection against sensitive accessibility values remains; control labels and focus cues stay accessible.
- Vertical scrollbars per scrollable pane/page at its logical end. Reserve an 8dp gutter, 4dp thumb at rest and 8dp on hover/drag, using `line` / `muted`; show while overflowing, no layout shift. Wheel/trackpad scrolls the hovered pane. Page Up/Down/Home/End scroll the focused region unless an editor owns those keys. No horizontal page scrolling. Rail itself may scroll on a short/high-text-scale window.
- No record context menus, drag-and-drop, multi-select or double-click requirement in v1. Single click activates a row. Existing visible edit/delete controls remain. Native public text editing menus and file dialogs retain normal OS behavior.

## Platform omissions and exact copy

Hide Google sign-in, cloud backups, cloud slots/upload and Google account management, including deep-link entry points. Hide Android fingerprint/system-prompt illustrations, Android storage permissions, Android splash/status/gesture bars, iOS home indicator, Face ID-only instructions, mobile share sheets and recents-cover demonstrations. Export/restore use native Save/Open dialogs and `.dftar`; cancel returns without a success message. Do not claim OS backup/sync. Keep local recovery, encrypted export and restore.

Screen capture exclusion, hardware-backed storage and inactivity/minimize/hide locking are release gates. Cover content before hiding/minimizing; resume into the lock surface. Desktop has no mobile recents design. A cover is not a substitute for capture exclusion. If a platform cannot meet an engineering gate, it is not silently represented as supported by this design.

These are desktop resources only. Reuse unchanged strings elsewhere. Interpolate product/system names as isolated LTR runs in Arabic; don't translate `Touch ID`, `Windows Hello` or key names.

| Purpose | Arabic | English |
| --- | --- | --- |
| Window title | دفتر | Daftar |
| PIN setup instruction | اختر من ⁦6⁩ إلى ⁦16⁩ رقمًا، مختلفة عن رمز جهازك. | Choose 6–16 digits, different from your device PIN. |
| macOS enable action / setting | تفعيل ⁦Touch ID⁩ | Enable Touch ID |
| macOS unlock action | فتح باستخدام ⁦Touch ID⁩ | Unlock with Touch ID |
| macOS onboarding explanation | استخدم ⁦Touch ID⁩ لفتح دفتر. يمكنك دائمًا استخدام رمز دفتر. | Use Touch ID to open Daftar. You can always use your Daftar PIN. |
| macOS unavailable after enrollment | ⁦Touch ID⁩ غير متاح الآن. استخدم رمز دفتر. | Touch ID is unavailable right now. Use your Daftar PIN. |
| Generic supported device-unlock setting | تفعيل فتح القفل بالجهاز | Enable device unlock |
| Generic supported device-unlock action | فتح باستخدام الجهاز | Unlock with device unlock |
| Generic supported device-unlock explanation | استخدم وسيلة التحقق على جهازك لفتح دفتر. يمكنك دائمًا استخدام رمز دفتر. | Use your device’s verification method to open Daftar. You can always use your Daftar PIN. |
| Generic unavailable after enrollment | فتح القفل بالجهاز غير متاح الآن. استخدم رمز دفتر. | Device unlock is unavailable right now. Use your Daftar PIN. |
| Lock action / tooltip | قفل دفتر | Lock Daftar |
| macOS lock shortcut hint | ⁦⌘L⁩ | ⌘L |
| Windows lock shortcut hint | ⁦Ctrl+L⁩ | Ctrl+L |
| Back tooltip / accessible label | رجوع | Back |
| Empty Banks detail | اختر بنكًا لعرض حساباته. | Select a bank to view its accounts. |
| Empty Investments detail | اختر حساب استثمار لعرض تفاصيله. | Select an investment account to view its details. |
| Empty year detail | اختر سنة لعرض حالتها ودفعاتها. | Select a year to view its status and payments. |
| Export picker title | حفظ نسخة مشفّرة | Save encrypted backup |
| Restore picker title | فتح نسخة مشفّرة | Open encrypted backup |
| File filter label | نسخة دفتر المشفّرة | Daftar encrypted backup |

No new keyboard instruction banner. Unlock/Continue/Confirm, PIN errors, backoff plurals and recovery copy reuse current resources. The generic device-unlock strings are conditional on a working adapter; do not expose them on PIN-only Windows.

## Acceptance — implementer must complete before release

Test **720×600**, **1024×768**, **1440×900dp client areas** on both macOS and Windows. Also check the default 1200×800 window. Run each in Arabic/English and light/dark, then at 200% text on minimum and large windows. Record screenshots using synthetic fixtures only; no production balances.

- [ ] 720×600: 90dp rail, single page, 582dp inner available width; editors fit at 560dp, PIN stack scrolls fully. Every action reachable by keyboard and pointer, no horizontal clipping.
- [ ] 1024×768: 886dp group, 280dp list + 24dp gap + 582dp detail; Banks, Investments and years have placeholder/selection/back. Home stays single column; capped pages remain centered.
- [ ] 1440×900: Home caps at 1120dp (736 + 24 + 360), Assets has two equal columns; list-detail caps at 1200dp (320 + 24 + 856). Inner detail stays ≤640dp. No extra columns or stretched forms.
- [ ] Drag across 839/840 and 1199/1200dp with a selected record and a dirty draft: no loss, duplicate navigation, focus reset or saved-data mutation. Verify defensive compact branch at 599/600 in a layout harness; do not lower the shipping minimum.
- [ ] Exercise every route in the table, including long Arabic names, largest supported amount, empty/loading/error, hidden values, missing/manual/stale prices and first-run guide. Labels wrap; numeric/unit runs remain LTR isolated. Selection and financial values never leak through tooltips or semantics.
- [ ] PIN keyboard/numpad normalization, Enter, Backspace, tab order, keypad, busy/backoff and focus return work. Incorrect PIN and biometric cancellation never cause a retry loop. Mac Touch ID cold-launch and post-minimize episodes prompt once; unsupported Windows has no biometric UI.
- [ ] Esc and visible Back behave identically, including actual two-pane scenes and draft confirmation. Cmd/Ctrl+L locks immediately. Scrollbars, wheel, focus rings and every disabled/hover/pressed state work in both themes.
- [ ] Capture exclusion, immediate minimize/hide lock, inactivity, hardware storage and no sensitive clipboard/selection are verified on both desktop platforms. Native Save/Open succeeds, authenticates, cancels cleanly; recovery-key copying follows its exception.
- [ ] Restore window bounds after monitor removal, scaling change and maximized quit; title remains brand-only. No hidden/minimized startup or off-screen window.
- [ ] Android phone and iPhone 18 Pro: before/after screenshots at the same logical size, locale, theme, text scale and synthetic data are pixel-identical on all affected surfaces, including Phase 25 PIN and Phase 27 Prices/Home. Repeat phone landscape and keyboard-visible editors; mobile code path remains unchanged even above 600dp. Verify biometric episode regression tests and existing mobile navigation/IME behavior.
- [ ] Implementation runs the repository's required PR checks and applicable security/lifecycle device tests. Add new wide states to DesignCatalog when components are implemented; do not change shared default tokens to satisfy desktop frames.

## Frame index and verification scope

`p28-home-medium` (720×600), `p28-banks` (1024×768), `p28-home-wide` (1440×900), `p28-assets`, `p28-investments`, `p28-years`, `p28-editor`, `p28-prices`, `p28-auth` and `p28-bank-empty` show the layout families. Gallery language/theme controls apply to all. Native window decoration is deliberately outside the client-area frames. Frames are synthetic design previews, not an executable desktop client; security, scene persistence and hardware behavior must be validated in implementation.
