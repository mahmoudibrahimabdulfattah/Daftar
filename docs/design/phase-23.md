# Phase 23 — Everyday unlock, bank identity and navigation

Approved 13 October 2026. Extends [daftar.html](daftar.html) and [Phase 22](zakat-calculator.md). Design handoff only; no application implementation or real balances. Existing tokens, encryption, masking and immediate background lock remain binding. These decisions supersede earlier FX-source and navigation wording.

## 1. Automatic biometrics

Automatically invoke the native biometric prompt after the neutral lock surface is drawn and the host is foreground, resumed and focused. Eligible entries: cold launch with enabled biometrics and a valid wrapped key; return after backgrounding an unlocked vault; foreground inactivity lock. Onboarding, enrollment and PIN changes remain explicit actions. No extra preference: enabling biometrics includes automatic prompting.

A lock episode begins when an unlocked session locks, or on cold launch if no unfinished episode exists. It ends only after successful authentication. Persist a nonsensitive automatic-attempt-consumed flag before invoking the OS; retain it across rotation, process death and reopening while still locked. Clear it on successful unlock. Cancelling then reopening must not nag; the next lock after successful authentication is eligible again.

| Event | Required behavior |
|---|---|
| Eligible entry | Exactly one automatic request; neutral PIN screen underneath. |
| Cancel, Back, Use Daftar PIN | Dismiss, focus PIN, consume auto attempt. No error for cancellation. |
| Fingerprint mismatch within native prompt | OS may retry within the same request; failure callback never launches another prompt. |
| Terminal failure, timeout, unavailable hardware | PIN with explanation; no automatic retry. Manual fingerprint retry only if platform/security policy permits. |
| Temporary/permanent biometric lockout | PIN; disable fingerprint until eligible. Eligibility returning never auto-prompts. |
| PIN backoff | Security layer blocks both PIN submission and biometrics. Expiry enables input but does not auto-prompt. Relaunch cannot bypass delay. |
| PIN typing before queued request | Cancel queued request, consume attempt; never interrupt entered PIN. |
| Forgot PIN or restore | Cancel pending/in-flight request and consume attempt; no prompt over recovery or when returning. Explicit retry exists on lock only. |
| Pause/background/loss of active host | Cancel where necessary; episode remains consumed. OS prompt focus changes, recomposition and rotation are not new triggers. |
| Late success after cancellation/background/recovery | Ignore obsolete request/session token; never unlock from stale callback. |
| Enrollment/key invalidation | App PIN required; explicitly re-enable biometrics after unlock. |

Only one request may be active. Manual retry receives a new request token. Strong biometrics must actually unwrap the key; phone credentials cannot substitute for the app PIN. Follow the native prompt and error handling model in [Android biometric guidance](https://developer.android.com/identity/sign-in/biometric-auth). iOS uses its supported native biometric modality, not fingerprint-specific promises on Face ID devices. Successful unlock opens Home. Lock clears private routes and drafts immediately, without discard confirmation.

| Copy | Arabic | English |
|---|---|---|
| Native title | فتح دفتر | Unlock Daftar |
| Native explanation | تحقّق باستخدام بصمة الجهاز. | Verify with your device biometrics. |
| Negative action | استخدام رمز دفتر | Use Daftar PIN |
| Lock helper | أدخل رمز دفتر، أو استخدم البصمة. | Enter your Daftar PIN, or use biometrics. |
| Retry label | فتح دفتر بالبصمة | Unlock Daftar with biometrics |
| Terminal error | تعذّر التحقق بالبصمة. استخدم رمز دفتر أو حاول مجددًا. | Biometric verification failed. Use your Daftar PIN or try again. |
| Lockout | البصمة غير متاحة الآن. استخدم رمز دفتر. | Biometrics are unavailable now. Use your Daftar PIN. |
| Invalid key | تغيّرت إعدادات البصمة. افتح برمز دفتر ثم فعّل البصمة مجددًا. | Biometric settings changed. Unlock with your Daftar PIN, then enable biometrics again. |

## 2. Offline bank directory and artwork

Add bank opens a full-screen searchable offline directory with pinned Back/title and an Other bank action outside filtered results. Match Arabic/English names and curated aliases; ignore case, diacritics, tatweel and harmless whitespace. Sort by localized name, with no popularity ranking or remote search. Selecting an institution opens the form with editable display name; selection alone never saves. Other opens a required free-name field. Account forms choose among already-created banks, with Add bank opening this directory.

Exactly 14 v1 identities, all in Egypt; IDs never reused. The institution ID is separate from the existing local record BankId. The directory is a bundled, versioned data file, not a Kotlin enum or per-bank conditional. Each record contains id, countryCode (ISO 3166-1 alpha-2, uppercase EG), names.ar/en, aliases.ar/en, monogram, brandBackground, monogramForeground, officialAsset and manifest reference. IDs use lowercase country namespaces such as eg.nbe; future sa.* and ae.* entries require data/assets only, no database or backup schema migration. Country is public catalog metadata, not a user's residence. No geolocation or network country detection. Search includes local aliases and localized country names; show Egypt / مصر as the current directory context. A future multi-country directory groups results by country and offers an All countries filter, without inferring a country from ambiguous legacy names.

| institutionId | Arabic | English / aliases | Official asset acquisition domain |
|---|---|---|---|
| eg.nbe | البنك الأهلي المصري | National Bank of Egypt; NBE; AlAhli | nbe.com.eg |
| eg.banque-misr | بنك مصر | Banque Misr; Bank Misr; BM | banquemisr.com |
| eg.cib | البنك التجاري الدولي | Commercial International Bank; CIB | cibeg.com |
| eg.qnb | كيو إن بي مصر | QNB Egypt; QNB Alahli | qnb.com.eg |
| eg.banque-du-caire | بنك القاهرة | Banque du Caire; BDC | bdc.com.eg |
| eg.aaib | البنك العربي الأفريقي الدولي | Arab African International Bank; AAIB | aaib.com |
| eg.hsbc | إتش إس بي سي مصر | HSBC Egypt | hsbc.com.eg |
| eg.alexbank | بنك الإسكندرية | ALEXBANK; Alex Bank | alexbank.com |
| eg.faisal | بنك فيصل الإسلامي المصري | Faisal Islamic Bank of Egypt; Faisal | faisalbank.com.eg |
| eg.adib | مصرف أبوظبي الإسلامي مصر | Abu Dhabi Islamic Bank Egypt; ADIB | adib.eg |
| eg.emirates-nbd | بنك الإمارات دبي الوطني مصر | Emirates NBD Egypt; ENBD | emiratesnbd.com.eg |
| eg.credit-agricole | كريدي أجريكول مصر | Crédit Agricole Egypt; Credit Agricole | ca-egypt.com |
| eg.bank-abc | بنك ABC مصر | Bank ABC Egypt; Arab Banking Corporation | bank-abc.com |
| eg.egebank | إي جي بنك | EGBANK; Egyptian Gulf Bank | eg-bank.com |

**Owner override: ship OFFICIAL bank marks now in the private app.** This supersedes the previous permission-before-bundling gate. Record the owner's product decision; do not represent it as a bank license. Store publication can select the monogram build described below. No further approval is needed for this design decision.

Acquire at development time only from each bank's own website/brand page. Keep original bytes; sanitize SVG without changing visible artwork, removing scripts, fonts and external references, then convert faithfully to supported Compose vectors. If conversion changes appearance, bundle an official PNG in 1x/2x/3x densities, preserving aspect ratio. Never retrace, redraw, recolor, invert or stretch a mark. No logo aggregators, runtime fetch, remote fallback, WebView, account telemetry or uploaded custom artwork.

Every acquired mark has a manifest record: institutionId, exact bank-owned page URL, exact asset URL, retrieval date in UTC, original SHA-256, sanitized/converted output SHA-256 per file, format/density, conversion notes and any source attribution notes. Hash actual bytes, never fill invented dates/hashes. Validate offline references and manifest completeness before an official-mode release. Acquisition domains above are targets, not claims of retrieved assets.

### One build switch, two complete visual sets

Define the single Gradle property **daftar.bankArtwork=official|monogram**, default official for the current private app. Reject other values. Generate one immutable common Kotlin configuration value used by Android and iOS and the shared bank-identity component. A future store build sets `-Pdaftar.bankArtwork=monogram`; no source edits or data migrations. This is not a user preference, remote flag or backup field. In monogram mode exclude official artwork from the packaged resources as well as from rendering; inspection must verify both platform packages. Both modes use identical names, IDs, layout, semantics and selection behavior. Unexpected corrupt/missing artwork in official mode uses the corresponding brand monogram offline; missing required manifest/assets fail release validation rather than silently shipping an incomplete official set.

Every catalog institution requires initials and a brand-colored background even when it has an official asset. The following are the approved monogram palette tokens (design choices, not assertions of bank-issued color specifications). Initials are **#FFFFFF** on these backgrounds in BOTH themes; all pairs must pass WCAG AA 4.5:1. Brand color is confined to the bank tile, not screen accents or currency badges. Other and unknown institution IDs retain the neutral bank outline using soft/primary.

| Institution | Initials | Background |
|---|---|---|
| eg.nbe | NBE | #006341 |
| eg.banque-misr | BM | #9D1535 |
| eg.cib | CIB | #003B71 |
| eg.qnb | QNB | #621344 |
| eg.banque-du-caire | BDC | #A53B12 |
| eg.aaib | AAIB | #00594C |
| eg.hsbc | HSBC | #DB0011 |
| eg.alexbank | ALEX | #A64000 |
| eg.faisal | FIB | #006A3C |
| eg.adib | ADIB | #003B5C |
| eg.emirates-nbd | ENBD | #003B71 |
| eg.credit-agricole | CA | #006B5E |
| eg.bank-abc | ABC | #005D83 |
| eg.egebank | EG | #9A4300 |

Use the existing 38dp tile, 11dp radius and 4dp internal clearance. Official artwork is aspect-fit on white in both themes with line boundary; keep required clear space and use the brand monogram if illegible. Initials use Rubik 12sp/500, never mirrored. Other uses the existing bank outline. Adjacent bank name is mandatory. Artwork is decorative; row semantics announce name/action once and exclude financial values. Names/search remain inside the unlocked vault. The bank-specific tile palette is an explicit exception to ordinary olive tiles; Daftar brass remains for gold.

Gallery shows the official-mode contract and a complete brand-monogram specimen. Official asset acquisition is not complete in this documentation revision: local download failed DNS resolution. Missing specimens are explicitly labeled; they are not redrawn logos, invented source records or claimed bundled assets.

### Migration and backup

Add nullable institutionId and identityReviewed Boolean to encrypted bank records. First migration uses exact unique matches against a frozen alias table after normalization. Never fuzzy-match or match substrings; ambiguous الأهلي alone remains null. Preserve every existing name, record ID, relationship and timestamp; never merge banks. Keep the user's name in either locale. Explicit identity changes are separate from editing the display name. Other sets null + reviewed, preventing later rematching; catalog selection sets reviewed too.

Use Room v2→v3 and JSON backup payload v3; frozen .dftar container/header/KDF/AEAD do not change. Export identity/review flag, not artwork. Read v1/v2 through these migration rules and preserve Phase 22 data. IDs allow lowercase ASCII letters, digits, dots and hyphens, 1–64 characters; preserve unknown valid IDs with generic icon. Reject malformed identity fields atomically. Unsupported newer payload versions still fail safely. Restore must not fetch logos. FX provenance below shares this migration. Automatic matching runs only in this migration, never repeatedly on directory updates.

| Copy | Arabic | English |
|---|---|---|
| Picker / search | اختر البنك / ابحث باسم البنك | Choose bank / Search bank names |
| Other | بنك آخر | Other bank |
| Empty | لا توجد نتائج. يمكنك إضافة بنك آخر. | No matches. You can add another bank. |
| Name | الاسم الظاهر | Display name |
| Identity actions | تغيير هوية البنك / استخدام بنك مخصّص | Change bank identity / Use custom bank |
| Privacy | القائمة والشعارات على جهازك. لا نتصل بالبنك. | The directory and logos are on your device. We do not connect to your bank. |
| Validation | أدخل اسم البنك. | Enter the bank name. |
| Save error | تعذّر حفظ البنك. حاول مجددًا. | Could not save the bank. Try again. |

Keep existing name bounds and duplicate-name policy. Failed write retains draft; no network loading state for the directory. Missing/corrupt artwork falls back instantly.

## 3. Currency badges

Use **$** for USD in both languages; **ج.م** for Arabic EGP and **E£** for English EGP. These are typographic currency badges. Existing 38dp soft/primary tile; Rubik 18sp/500 for $ and E£; Alexandria 12sp/500 for ج.م. Isolate glyph runs LTR, never mirror. No flags or gold tint.

Account rows, detail/header and selected currency in forms show the badge; bank rows show institution identity. Keep EGP/USD text with balances. Semantics announce account name and دولار أمريكي / US dollars or جنيه مصري / Egyptian pounds, not a separate badge focus stop or monetary values. Hidden mode preserves currency/name, removes balances, conversion formulas and notes. Currency edits still relabel rather than convert the stored balance.

## 4. Banque Misr FX with disclosed fallback

Use **Transfer → Buy**: the bank buys the user's USD and gives EGP. This remains the notebook's single USD reference, including cash USD; it is not a cash-exchange promise. No per-bank rates or cash/transfer split in Phase 23. If these differ, retain transfer BUY everywhere and disclose the reference basis. Gold keeps merchant SELL by the owner's deliberate choice. Never call the mixed-convention total liquidation proceeds. Zakat consumes the same exact, unrounded Home valuation.

[Banque Misr's official table](https://www.banquemisr.com/Home/CAPITAL%20MARKETS/Exchange%20Rates%20and%20Currencies), checked 13 October 2026, explicitly labels Notes Buy/Sell and Transfer Buy/Sell. Gallery 48.50 is synthetic, not today's quote. [ExchangeRate-API Open Access](https://www.exchangerate-api.com/docs/free) remains a fallback with required attribution; retain the existing fawazahmed0 currency-api adapter as the last online source.

Selection order:

1. Pinned manual override wins until removed.
2. Refresh sequentially: Banque Misr → open.er-api.com → fawazahmed0 currency-api. First valid fresh quote wins. Stale upstream HTTP success does not count as fresh; continue.
3. With no fresh online quote, use newest valid cache, including valid stale responses, by original effective timestamp. Equal-time ties prefer the provider order above. Never overwrite newer cache with older data.
4. Without any quote show —, available subtotal and manual entry, never zero/default.

**NBE is excluded.** Mahmoud's supplied feasibility test found encrypted middleware behind a WAF rejecting non-browser requests. This handoff accepts that finding, without claiming an independent middleware test. No WAF bypass, browser automation, proxy or third-party rate labeled NBE. Tell Mahmoud directly; revisit only when a reliable documented authorized feed exists.

All users request the same public FX chain regardless of bank identities/currencies/balances. No account data in URLs, headers, referrers, logs or analytics; no bank webpage scripts/assets execute. HTTPS, one refresh per unlock/on demand, coalesced requests, no background polling or retry loop. Source links open only on explicit tap; leaving the app locks immediately.

### Parsing and timestamps

Parse HTML structurally from labeled columns and the unique USD/US Dollar row, never first-row/third-number assumptions. Expand row/column spans. Require four expected Notes/Transfer Buy/Sell cells and unambiguous mapping. Reordered known headers may map; unknown headers fail closed. Normalize only supported decimal/grouping conventions into exact decimals. Reject blanks, zero/negative/nonfinite/overflow, duplicate USD, buy > sell within either pair, WAF/login HTML and unexpected content type. Missing transfer cannot borrow cash. Bound body to 1 MiB, request to 10 seconds and sequence to 30 seconds.

Persist provider ID, basis (bankTransferBuy / midMarketEstimate / manual / legacyUnknown), effective timestamp, fetchedAt and optional publication timestamp. Interpret timezone-less bank publication time as Africa/Cairo and document that adapter assumption. If absent, use fetch time with Checked wording, never invented publication time. Re-fetching an unchanged dated quote does not advance freshness. Cache/backup retain provenance. Legacy sources become mid-market only where provider identity proves it; otherwise legacyUnknown.

Stale is age **>24h**, not ≥. Future timestamps cannot be fresh: show clock warning and continue fallbacks. Failed refresh never advances time. Weekend closure does not waive staleness. Stale quotes remain calculable with warning; absent required quotes make Home/zakat incomplete. Fresh fallback outranks stale bank cache but carries **Estimate**. A later successful bank refresh replaces fallback unless manual is pinned. Retain visible values until atomic update; failure retains prior quote.

| Copy | Arabic | English |
|---|---|---|
| Basis | بنك مصر · شراء التحويلات | Banque Misr · transfer buy |
| Explanation | سعر شراء البنك لدولارك. مرجع موحّد للتقييم، وليس وعدًا بسعر التنفيذ. | The bank's buying rate for your USD. A shared valuation reference, not a guaranteed exchange rate. |
| Fallback | سعر تقديري من مصدر بديل؛ تعذّر تحديث سعر بنك مصر. | Estimated rate from an alternative source; Banque Misr could not be updated. |
| Attribution | تقدير متوسط السوق · {provider} | Mid-market estimate · {provider} |
| Times | نُشر: {time} / تم التحقق: {time} | Published: {time} / Checked: {time} |
| Cache/stale | محفوظ / قديم · آخر سعر: {time} | Cached / Stale · last quote: {time} |
| Failure | تعذّر التحديث. ما زلنا نستخدم السعر السابق. | Refresh failed. The previous quote is still in use. |
| Missing | سعر الدولار غير متاح. أدخل سعرًا يدويًا. | USD rate unavailable. Enter a manual rate. |
| Clock | وقت السعر يسبق وقت الجهاز؛ راجع التاريخ. | Quote time is ahead of device time; check the date. |
| Manual | يدوي · أُدخل: {time} | Manual · entered: {time} |

Prices shows source link, basis and times per quote. Home labels USD separately from gold SELL; account conversion/zakat inherit provenance and estimate/stale warnings. Public quote remains visible when holdings are hidden, but private conversion formula does not. Keep required ExchangeRate-API attribution link with cached fallback. Provider terms/access are release checks; readable HTML is not an availability guarantee.

## 5. Tab reselect

Keep four independent in-memory tab stacks during an unlocked session. A feature pushed from Home belongs to Home even when also reachable from Assets. Switching restores the target stack, scroll, filters and selection. It does not pop the previous tab. Dirty forms can remain there; switching alone does not discard and therefore needs no confirmation. Never persist private drafts/routes to plaintext saved-instance state.

Reselect from a pushed screen pops that tab to its root and preserves root scroll. A later tap while already at root scrolls primary content to top. No timed double-tap, refresh, duplicate destination, filter reset or eye reset. Already at top is a no-op. Use existing 180ms movement or immediate movement for reduced motion.

If popping removes any dirty form, show one discard confirmation before navigation changes. Keep editing cancels pending navigation; Discard changes completes the requested pop. Failed saves retain guarded drafts. Ignore reselect during an active commit; repeated taps cannot queue dialogs/jumps. Back and predictive Back commit use the same guard; cancelled gestures change nothing. Root Back keeps current platform exit behavior; tab history is not a chronological Back stack.

Rail uses the same rules. Reselect clears pushed detail selection and returns root composition; root tap scrolls primary list, not both panes. Resize is not navigation. Lock overrides dialogs, clears every stack/draft/position, then successful unlock starts Home. Modal surfaces block taps behind them.

| Copy | Arabic | English |
|---|---|---|
| Title | تجاهل التغييرات؟ | Discard changes? |
| Body | ستفقد التعديلات غير المحفوظة وتعود إلى بداية القسم. | Unsaved changes will be lost and you will return to this tab's root. |
| Safe primary | متابعة التعديل | Keep editing |
| Outlined action | تجاهل التغييرات | Discard changes |

## 6. Pinned toolbar

**Keep the compact toolbar pinned, with BOTH privacy eye and price refresh wherever those actions exist.** Neither action belongs only inside scrolling content or an overflow menu. Home, asset lists/details and valuation/zakat surfaces expose both. Where only one action is applicable, pin that action; do not add financial controls to lock, recovery or setup. Existing refresh-only Prices surfaces pin refresh. Global eye and shared quote refresh work from any applicable unlocked surface, including while values are hidden. Refresh never reveals values, resets navigation/scroll, overwrites a form draft or removes a pinned manual override. Keep one coalesced request and show refreshing/disabled state in the fixed action slot, with errors next to retained content. Eye remains usable while refresh runs. Labels: تحديث الأسعار / Refresh prices; جارٍ تحديث الأسعار / Refreshing prices.

Large headings and greetings scroll. At the logical trailing edge, order eye then refresh, mirrored with the layout in RTL; Back stays at the leading edge. Both actions have independent ≥48dp targets; wrap/grow the title instead of shrinking targets or hiding either action. Expanded panes keep the applicable actions in pinned pane headers and use the same global state. Forms keep Back and keyboard-safe Save footer; any existing eye/refresh actions stay pinned. On S25 Ultra, predictable Back/privacy/refresh access matters more than reclaiming the compact bar's height.

[Material app-bar guidance](https://developer.android.com/develop/ui/compose/components/app-bars) distinguishes pinned, enterAlways and exitUntilCollapsed. Pinned fits the incumbent compact bar. enterAlways requires reverse scrolling to recover actions, so reject it. exitUntilCollapsed can support a future expanded title while preserving compact controls; Phase 23 adds no expanded app bar. This is our product choice, not a Material mandate.

Follow [insets guidance](https://developer.android.com/develop/ui/compose/system/insets): paint edge-to-edge backgrounds, consume status/cutout/IME insets once, protect system icon contrast, keep controls inside safe areas. Mirror Back/layout in RTL; never logos, currency or fingerprint. Follow [adaptive guidance](https://developer.android.com/develop/ui/compose/layouts/adaptive): preserve existing <600dp bottom bar, 600–839dp rail and ≥840dp two-pane contract where content fits. Independent pane scrolling keeps titles/actions visible; omit redundant detail Back when list is visible. iOS retains equivalent safe-area behavior.

Eye labels: إخفاء القيم / Hide values; إظهار القيم / Show values. Announce state, use ≥48dp targets. Hiding is immediate/global and removes private semantics. No private values or bank identities outside the unlocked vault.

## Acceptance cases and handoff

| Area | Required scenarios / results |
|---|---|
| Auto prompt | Cold start, unlocked→background→resume, inactivity: one each; disabled/no enrollment: PIN only. |
| Anti-loop | Cancel→rotate→background→resume→process restart: no automatic repeat until successful unlock; eligible explicit retry works. |
| Races | Queued prompt vs PIN entry/recovery; pause before success; duplicate callbacks: no stale unlock/dialog. |
| Failure | Nonterminal mismatch stays in same request; terminal failure no relaunch; lockout and invalid enrollment use PIN. |
| Backoff | Five wrong PINs, expiry, restart and manual biometric tapping cannot bypass security delay. |
| Hardware | S25 Ultra Android 17 StrongBox/fingerprint: actual key unwrap, screen-off, cancel and recents cover; iOS native biometric fallback. Synthetic data only. |
| Directory | All 14 AR/EN/alias searches offline; Other in empty/no-results states; no network on opening/searching/selecting. |
| Artwork/accessibility | All 14 official marks have bank-owned URLs, retrieval dates and verified SHA-256; no altered artwork or external SVG references. Missing/corrupt runtime asset uses brand monogram; official release validation catches missing assets. All 14 monograms pass ≥4.5:1 in both themes; Other remains neutral; one name announcement. |
| Build modes | Default official; monogram property changes every bank surface on Android/iOS, and excludes official resources from both packages. Invalid property fails. Both modes work offline and retain account/backup data identically. |
| Directory expansion | Data-only sa.*/ae.* fixture with AR/EN names/aliases/country shows in search/grouping without schema migration. Same initials across countries do not conflate IDs. Old client preserves unknown ID with neutral icon. |
| Migration | Exact alias only, ambiguous الأهلي null, no rename/merge, Other persists, name edit preserves identity. |
| Backup | v1/v2 migration, v3 round trip, unknown valid ID, malformed ID/newer payload/failed restore: preserve all relationships and zakat or reject atomically. |
| Currency | USD/EGP list/detail/form, AR/EN, RTL, both themes, hidden; editing warns and never converts balance. |
| FX value | Synthetic cash 52.70/52.80, transfer 52.77/52.87: USD 100 → EGP 5,277.00, including common cash reference. |
| Parser | Reordered rows/columns, merged headers, Arabic digits, missing/duplicate USD, invalid decimals, zero/inverted rates, WAF page, timeout/oversize: exact extraction or fallback. |
| Selection | Bank success; each fallback; all fail→cache; none→missing; pinned manual survives; fallback never labeled bank BUY. |
| Time | Exactly 24h, +1ms, unchanged publication re-fetch, future clock, weekend and older response cannot advance/replace newer cache. |
| Valuation | Home/cash/accounts/zakat share quote; fallback/stale disclosed; missing unused USD does not invalidate EGP-only holdings; never zakat on partial subtotal. |
| Navigation | Pushed→reselect root; later root tap scroll; switching retains stacks; dirty switch retained; dirty reselect cancel/discard; repeated taps/active save safe. |
| Lock boundary | Lock during form/dialog/refresh clears private state; unlock Home; recovery never auto-prompts. |
| Layout | AR/EN × light/dark, 100%/200% text, landscape, rail/two-pane, IME, gesture/three-button navigation: Back/eye/refresh stay visible while scrolling, values wrap, targets ≥48dp. Refresh while hidden stays hidden; repeated refresh coalesces; eye works during refresh; manual override/draft stays intact. |

Update the living catalog for artwork/fallback, currency badges, pinned eye, basis tags and guarded reselect. Implementation requires all AGENTS.md PR gates, Room/security/lifecycle device tests, Android emulator, iPhone 18 Pro and real-phone scenarios. This design-only handoff uses HTML script/render/link checks; it does not claim native implementation gates ran.

Handoff verification: both gallery scripts parse; the base gallery generates 76 frames with no unresolved navigation targets, and eight Phase 23 frames are added. All 14 white-on-brand monograms pass AA in both themes (minimum contrast 5.22:1). Diff whitespace checks pass. The design detector flags only the explicitly approved ivory palette. Browser rendering was not verified: local server binding is denied by the sandbox and the browser URL policy disallows file URLs. The gallery remains a synthetic state reference, not an implementation of native authentication, persistent tab stacks or vault-wide security.

## Category tiles (owner feedback)

Approved 13 October 2026. This amendment responds to the S25 Ultra feedback that category icons are too quiet. It supersedes the pale gold tile and Default tone for **asset categories only**. Design handoff: the HTML is updated; native implementation remains a follow-up.

Use stronger, opaque tile fills within the existing olive/brass family. Banks gets an ivory-white glyph on deep olive. Cash gets a dark olive wallet on lighter sage: clearly distinct without introducing a competing brand accent. Reject red for wallets because it implies an error, debt or loss without any corresponding financial state. Gold gets a solid, pale-gold ingot on bronze. Its broad face and cut-out lower edge supply the metal/bar cue without gradients, sparkle, shine animation or a second accent. Brass remains exclusive to gold.

### Exact theme tokens and contrast

Add the six named color roles to `DaftarColors`, exposed through `DaftarTheme.colors`. Keep the existing `gold`, `goldSoft`, `primary`, `onPrimary`, `soft`, status and Material scheme roles unchanged: category fills are independent of actions and status. All values below are opaque sRGB. Contrast is glyph/foreground against its tile, calculated with WCAG relative luminance and rounded to two decimals.

| Tone / role | Background token | Glyph token | Light background | Light glyph | Light contrast | Dark background | Dark glyph | Dark contrast |
|---|---|---|---|---|---|---|---|---|
| `Gold` | `goldTile` | `onGoldTile` | `#59401F` | `#F3CE88` | 6.43:1 | `#6B4C24` | `#FFE0A3` | 6.13:1 |
| `Bank` (new) | `bankTile` | `onBankTile` | `#45583D` | `#FFFEF8` | 7.65:1 | `#526747` | `#FFFEF8` | 6.13:1 |
| `Cash` (new) | `cashTile` | `onCashTile` | `#CCD8BE` | `#2C4225` | 7.39:1 | `#97AD80` | `#1E3018` | 5.77:1 |
| `Default` (unchanged) | `soft` | `primary` | `#E4E8D9` | `#45583D` | 6.20:1 | `#35432E` | `#BFD0AD` | 6.44:1 |

Every pair passes the requested ≥3:1 glyph threshold and ≥4.5:1 normal-text threshold. Category tiles contain no letters, amounts, currency symbols or purity stamps; names remain beside the tile in existing ink. These ratios do not authorize recoloring official bank marks. Currency badges retain `soft`/`primary`, and bank monogram text retains its existing white-on-brand palette and separate ≥4.5:1 checks.

### Glyphs and application map

| Surface / category | Tone and glyph | Where it applies |
|---|---|---|
| Gold | `Gold` + new `DaftarIcons.GoldBar` | Every gold holding (bars and jewelry), Gold category in Assets, Home breakdown (including hidden/offline states), zakat valuation breakdown and restore-review gold count. The bar identifies the asset class; the row's text still distinguishes jewelry from bars. |
| Banks category | `Bank` + existing `DaftarIcons.Bank` | Assets category, Home breakdown, zakat valuation breakdown and aggregate bank count in restore review. Never infer this tone merely because the glyph is Bank. |
| Cash & wallets | `Cash` + existing `DaftarIcons.Wallet` | Assets category, Home breakdown, individual cash/wallet ledger rows and zakat valuation breakdown. Any later aggregate cash tile uses the same pair. |
| Zakat | `Default` + existing `DaftarIcons.Heart` | Home reminder, Journal entry, calculator entry and payment rows. Keep the same tile whether due, paid or upcoming; status remains in label/tag. |
| Notes | `Default` + existing `DaftarIcons.Note` | Journal category and any existing note ledger tile. Existing notebook cards get no new decorative tile. |
| Year history / total-assets summary | `Default` + existing `Book`, `Clock` or `Check` according to the current row | Preserve existing glyph meaning and status copy. No gold styling on totals containing non-gold assets. |
| Prices | No new category tile | Current Prices uses tables, quote strips and an FX currency badge; preserve those. A gold row that already uses a tile follows Gold. If a generic Prices entry is introduced later, use `Default` + `Refresh`, not brass or a bank mark. Toolbar refresh remains a plain action icon. |
| Backup and other utility rows | `Default` + existing `Export`, `Import`, etc. | Existing utility ledger tiles remain quiet; no financial category tone. |

Individual bank rows, picker results, bank-edit previews and expanded bank lists retain **Phase 23 official mark / brand monogram tiles** through `DaftarLedgerRow.leading`. Other/unknown banks keep `Default` + `Bank`. Account and FX rows retain their currency badges. No new palette is applied behind official artwork or monograms. The override slot takes precedence over `icon` and `tone` and must not gain an extra surrounding category tile.

Keep all tile containers **38 × 38dp, 11dp radius**, centered, with no outline or shadow added. Asset glyphs (`Gold`, `Bank`, `Cash`) become **24 × 24dp** instead of 20dp, leaving 7dp bounding-box clearance. Bank and Wallet reuse their exact existing 24-unit paths with **1.7-unit round strokes**, round caps/joins, no fill. Default glyphs retain **20 × 20dp** and existing stroke geometry. Official artwork keeps Phase 23's 4dp clearance; initials remain Rubik 12sp/500. Do not enlarge row gaps, tile containers or bank artwork. Tiles are decorative inside a labeled row, never independent touch targets; retain row targets ≥48dp, normal wrapping and private-value masking.

`GoldBar` is a 24 × 24 vector, one solid path, even-odd fill, no stroke. The second subpath cuts the lower face seam out to the tile background. Render it in `onGoldTile` at 24dp. Do not mirror it (or Bank/Wallet) in RTL. Use this exact SVG geometry:

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <path fill="currentColor" fill-rule="evenodd"
        d="M6 5H18L22 16V19H2V16Z M4.5 15L3.8 17H20.2L19.5 15Z"/>
</svg>
```

For Compose, add `DaftarIcons.GoldBar` with `ImageVector.Builder` (24dp, viewport 24f × 24f), `SolidColor(Color.Black)` and `PathFillType.EvenOdd`; tint at the component boundary. Preserve the existing `DaftarIcons.Gold` outside category tiles, including the Assets navigation icon and empty-state emblems. This change does not restyle navigation, controls, warning panels or empty-state illustrations.

### Compose Styles handoff

- Extend `DaftarTileTone` to `Default`, `Gold`, `Bank`, `Cash`. Set tones explicitly at category/holding call sites; do not map all Bank glyphs to the category tone or derive tone from localized labels.
- Keep `DaftarStyles.tileIcon` as the shared Default shape/fill. Change `goldTileIcon` to `colors.goldTile`; add `bankTileIcon` and `cashTileIcon` with their semantic fills and the existing `shapes.tile`. Apply the chosen style with `Modifier.styleable`; add/retain `style: Style = Style` on the styled `DaftarTileIcon` and compose caller overrides after the base style.
- In `DaftarTileIcon`, resolve tone to the paired foreground via `DaftarTheme.colors` and supply it through `ProvideContent`/the component's icon tint, never through a container style. Resolve glyph size to 24dp for asset tones and 20dp for Default; do not globally change every use of `DaftarSpacing.tileIconSize`.
- Keep `DaftarLedgerRow` stateless and preserve its `leading` override and row interactions. Bank identity, currency badge, status and selected-row styling remain independent of the category palette. Existing pressed feedback does not imply selection or financial change.
- Update `DesignCatalog` with all four tones, GoldBar, mixed category rows, official/monogram neighbors, Other bank and currency badges in both themes and languages. No new Material color role or screen-local hex value.

### Test checklist

- [ ] Native token values match all eight background/foreground pairs above; recompute unrounded ratios and require ≥3:1 for glyphs, ≥4.5:1 for any text. Check pressed compositing too; keep category glyphs at full foreground opacity when values are hidden or quotes are stale/missing.
- [ ] Home, Assets, Gold holdings (including jewelry), Cash rows, zakat breakdown and restore review use the mapped tone/glyph. Hidden/offline/zero/missing-price states retain the same category identity.
- [ ] On S25 Ultra, inspect 38dp tiles at normal viewing distance in light/dark and normal/dim brightness: recognize the ingot and its seam; Bank/Wallet lines remain distinct. No shimmer, gradient or loss/error cue.
- [ ] Arabic RTL and English LTR: row alignment mirrors; glyphs, bank marks and currency badges do not. At 100%/200% text, labels/amounts wrap without squeezing tiles or touch targets.
- [ ] All 14 banks in official and monogram modes remain unchanged in list/picker/edit/expanded layouts; Other/unknown stays neutral. USD/EGP badges stay neutral, legible and unmirrored. No category backdrop leaks into `leading` content.
- [ ] Zakat due/paid/upcoming and warning/error/estimate states retain their existing status labels and colors; no new red/brass semantics. Notes cards, Prices tables, navigation icons and empty-state emblems remain unchanged.
- [ ] TalkBack/VoiceOver announce the row name/action once; decorative glyphs have null descriptions. Masked financial values stay absent from semantics. Tiles do not introduce extra focus stops.
- [ ] HTML theme/language switches and Home privacy rerender preserve all new tones; token specimens and affected frames agree with this table. Run script syntax, rendered-markup/navigation-target and diff whitespace checks.
- [ ] During native implementation, run the AGENTS.md PR gates, catalog previews, Android emulator, iPhone 18 Pro and the owner's S25 Ultra visual check. This documentation revision does not claim those native checks passed.

Category-tile handoff verification: both HTML scripts parse. Generated markup checks pass for Arabic/English × light/dark: all 76 base frames, the new category specimen, Home/Assets/zakat tone assignments, unchanged bank/utility defaults, unchanged navigation/empty-state glyphs, and resolved base navigation targets. CSS/spec hex values and the SVG path match; all eight color pairs exceed 4.5:1 (minimum 5.77:1). Diff whitespace checks pass. The design detector reports only the incumbent approved ivory palette. Browser visual inspection is pending: the browser URL policy blocks the local file preview. No native code or native/device tests were changed or run for this amendment.
