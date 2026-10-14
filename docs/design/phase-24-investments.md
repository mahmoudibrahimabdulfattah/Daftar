# Phase 24 — Simple investment balances

**Owner decision, 14 October 2026.** This contract replaces the previous Phase 24 positions design in full. Record one manually maintained current EGP value per broker account, like an ordinary balance. No individual stocks, quantities, acquisition costs, fees, prices, P/L, positions, archive/close workflow or separate broker cash. [Reference frames](daftar.html#phase-24) follow [Phase 23's category system](phase-23.md#category-tiles-owner-feedback). All figures are synthetic. This is a design handoff, not native implementation.

## Smallest consistent information architecture

Keep **Investments as the fourth category in Assets**, after Gold, Banks, Cash & wallets. It reuses the existing category → list → detail/editor structure while keeping a broker's current investment value distinct from spendable cash. Adding an Investment subtype to Cash would save one category but complicate cash forms, currency rules and category totals. Keep the same four top-level destinations: Home, Assets, Journal, Settings.

Each entry is a **broker account balance**, not a separate broker record with children. The row shows broker identity, user display name, current value and value-as-of time. Multiple entries with the same broker are allowed; never group, merge or sum them a second time through a provider entity. List order is created time then ID. Show the number of accounts, not holdings. One primary action on detail: **Update value**, opening the same editor focused on amount. Edit name/provider/note and Delete are secondary actions. Add opens the offline provider picker, then the form; selecting a provider alone never saves.

Routes: `Assets → Investments → InvestmentDetail(id)`; `InvestmentEditor(id?)`, `BrokerPicker`, `ConvertCashPicker`, `ConvertCashReview(sourceId, providerId?)`. Native Delete/Discard confirmations are not new destinations. Arguments contain IDs only, never balances or notes. `shared` owns navigation between features. No investment refresh icon, swipe refresh, live-price surface or network action. Home's existing refresh updates gold/FX only and never changes investment values or timestamps.

- **<600dp:** existing bottom navigation, Assets selected through pushed routes; pinned title/eye, scrollable content, pinned Save above IME/safe area. Back pops normally.
- **600–839dp:** existing four-item rail, one pane; editor max width 560dp.
- **≥840dp:** four-item rail, 320dp leading investment list and detail/editor in remaining pane (minimum 400dp). Mirror in RTL. No provider/account hierarchy or third pane. With no selection, say Select an account; no redundant detail Back arrow when the list is visible. Retain selection only within unlocked session.
- Reselect Assets returns to its root; dirty drafts require Keep editing / Discard. Lock clears routes, draft, keyboard composition and pending confirmation without prompting. Rotation/resizing retains the unlocked draft. Normal navigation never silently saves.

## Entry and calculation contract

| Field | Contract |
|---|---|
| Broker identity | Offline `eg.thndr` or Other (`null`). Unknown well-formed restored IDs retained with generic identity. |
| Display name | Required, trimmed 1–120 Unicode code points; selecting Thndr initially suggests localized Thndr. User name then stays verbatim across language changes. Duplicate names allowed. |
| Current value | Required EGP Decimal, **0…999,999,999,999.99**, at most 2 fractional digits after trailing-zero normalization. Zero valid; blank invalid. No USD/currency picker. |
| Value as of | Required Instant, editable date/time in explicit Africa/Cairo timezone. Defaults to now on creation and whenever the numeric amount changes. |
| Note | Optional, trimmed ≤4,000 Unicode code points; blank→null. |

“Current value” means the current **total account value**, not deposits paid in or purchase cost. If the broker's total includes uninvested cash, it is already part of this one number: do not record that money again in Cash & wallets. The app does not ask for a breakdown or calculate one. User copies the total they intend to track and updates it occasionally.

Use existing exact `Decimal` and canonical TEXT storage/JSON strings. No Float/Double or SQLite REAL arithmetic. Normalize supported Arabic-Indic/Persian digits and decimal separators with existing common utilities; reject negatives, exponents, nonfinite values, ambiguous grouping, excess precision and overflow without truncation/clamping. Existing 64-character external numeric cap applies. Amounts display at 2dp HALF_UP, grouped Rubik numerals with number and currency isolated together. Derived sums are not reparsed through the input bounds; aggregate values may exceed a single-entry maximum.

```text
investmentsEGP = sum(investmentEntry.amount)
Home T = goldEGP + banksEGP + cashEGP + investmentsEGP
zakatEstimateExact = T × 0.025
zakatDisplay = roundHalfUp(zakatEstimateExact, 2)
```

Sum exact inputs before rounding; never round category zakat estimates and then add. Investments always contribute a present amount, including zero. No missing-price, incomplete-investment, cost fallback or available-investment-subtotal state exists. Existing missing gold/FX handling remains unchanged: an unrelated missing quote can still make Home/zakat incomplete, while the investment amount contributes to the available subtotal. All-empty investments = zero with an empty-list explanation, not an unavailable valuation.

Zakat keeps Phase 22's planning-only disclaimer and declaration/payment behavior. The entry includes the manually recorded account value without assessing its contents or religious treatment. Add a brief disclosure: “Includes the investment values you recorded; Daftar does not assess their zakat treatment.” No inferred shares classification, remaining zakat debt, deductions or payment-triggered balance changes.

## Value-as-of and gentle update hint

Store `valueAsOf` separately from record `updatedAt`. Entered amount and date/time save atomically. Date/time must represent 1900-01-01 00:00 Cairo through now for newly entered values; reject invalid local times and future selections. Native date/time picker shows the Cairo zone; nonexistent DST times fail validation, ambiguous times require explicit offset choice. Store UTC seconds/nanos and display localized date/time in Cairo.

On new entry, default is **Now when saved**. On edit, changing the numeric amount selects Now automatically; formatting-only changes such as `100`→`100.00` do not. Choosing a custom as-of time afterward replaces that default. Changing the amount again resets to Now, visibly. Name/provider/note-only edits preserve the prior valueAsOf. Opening/cancelling an editor, viewing Home, refreshing gold/FX or exporting a backup never touches it. To confirm an unchanged balance as current, use **Set to now** beside Value as of, then Save. Never advance it merely because Save was tapped.

At age **>24 hours** show a quiet clock line, “Update the value when convenient,” with Update value action. Exactly 24h is not due. This is a reminder to review the user's balance, **not a stale market-price warning**: use muted text/default clock, no amber panel, warning badge, dashboard alarm, notifications or red state. Old values still contribute fully to totals. Reevaluate on unlock and at the next threshold while foreground; no background work/network polling. A future restored timestamp or clock rollback shows a neutral “Check the value date” hint and remains calculable, never silently marked current or reset. Restore must not fail solely because the receiving clock is earlier.

## Explicit conversion of an existing cash entry

Choose **Convert an existing cash entry**, not a move into an existing account. Offer it on the Investments empty/list surface and in the Cash entry overflow. User manually selects an EGP cash/wallet entry, then a provider (Thndr or Other); never infer identity from “Thundr wallet” or other names. No automatic migration or detection banner.

The review shows source Cash & wallets → Investments, chosen provider, unchanged name/note, unchanged amount, value-as-of control, and **Total unchanged**. It states: “This entry will leave Cash & wallets and appear in Investments. No funds move at your broker.” Confirm explicitly with Convert entry / Cancel. No required checkbox, transfer amount, destination cash field, receipt table or permanent transfer history. Amount is read-only during conversion; afterward Update value can change it. Default as-of is Now when saved, **explicitly asking the user to confirm this amount as current or choose its actual date**. Cancel leaves source untouched.

One transaction rechecks source ID/revision/currency, validates the amount fits the investment bounds, creates the investment with the same name/amount/note and selected provider/as-of, then deletes that source cash row using compare-and-set. This is a user-confirmed **reclassification preserving all user-authored content**, not money movement or record loss. If any operation fails, roll back everything; no zero-value source is left to confuse the list. Zero-balance EGP sources are eligible. USD or out-of-bound legacy amounts cannot convert: explain why and keep the source. Do not convert currency or silently reduce the value.

A nullable unique `converted_from_cash_id` on the investment provides idempotency without a second table. New investment ID is generated once per conversion draft. A retry finds the existing investment by source ID and returns “Already converted,” never creates another. Later value edits do not erase this provenance. If the source is gone and no matching investment exists, fail safely rather than recreate from a stale draft. Source names/notes remain in the investment; creation metadata is for the new investment. Cross-table source absence plus unique provenance is validated on restore. Cash IDs must never be reused.

If the user already entered the same money as a separate investment, conversion would preserve an existing duplication; do **not** merge/overwrite that investment. Review says “If this money is already recorded in Investments, cancel and correct the duplicate entry.” User explicitly edits/deletes the mistaken record using normal controls. This is smaller and safer than guessing which records represent the same funds. Account amounts entered manually also carry a short reminder not to record the same money twice.

Delete investment uses ordinary Delete / Keep confirmation, states that its value will leave totals, and never implies withdrawal or sale. No close/archive model. Delete removes the record; normal backup recovery is unchanged. Conflict requires review latest values, not silent last-write-wins. Repositories invalidate Cash, Investments, Home and zakat together after commit so observers never display the intermediate double count.

## Visual identity and offline directory

Keep Investment category tone and glyph in Assets, Home, zakat basis and restore-review aggregate. Provider rows/detail/picker/form use the provider identity instead, with no extra category backdrop. Currency stays EGP text, not a second leading tile. Other/unknown uses Default tone + Investments glyph. Yellow is confined to Thndr's 38dp identity tile.

| Tokens | Light background / foreground | Dark background / foreground | Contrast |
|---|---|---|---|
| investmentTile / onInvestmentTile | #385D89 / #FFFEF8 | #8EAEE0 / #1B2B40 | > 4.5:1 |
| cashTile / onCashTile (revised) | #78566F / #FFFEF8 | #C5A2BC / #30232D | > 4.5:1 |
| cashAccent / investmentAccent (allocation) | #78566F / #385D89 | #C5A2BC / #8EAEE0 | > 3:1 on surface |

Revision after owner review on a phone: each category tile now uses the same colour as its allocation segment
(gold brass, banks olive, cash plum, investments blue), so the bar reads at a glance.
| Thndr monogram fallback | #FFE147 / #262D24 | Same | 10.86:1 |

Opaque sRGB; contrast via WCAG luminance. Tile 38×38dp, radius 11dp; asset glyph 24dp, stroke 1.7, round caps/joins, no fill or mirroring. Keep `DaftarTileTone.Investment`, `DaftarStyles.investmentTileIcon`, paired foreground through `ProvideContent`, container through `Modifier.styleable`, `style: Style = Style`, existing stateless leading override. No gain/loss colors, charts or performance affordances.

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
     stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <path d="M4 20V10h4v10M10 20V4h4v16M16 20V7h4v13M3 20h18"/>
</svg>
```

Use a separate **`core/model/src/brokerDirectory/brokers.json`**, matching banks.json's actual flat schema, not mixing broker records into banks. Other is an action storing null, not a fabricated institution ID.

```json
{
  "schemaVersion": 1, "directoryVersion": 1,
  "countries": [{"code":"EG","nameAr":"مصر","nameEn":"Egypt"}],
  "institutions": [{
    "id":"eg.thndr", "country":"EG", "nameAr":"ثاندر", "nameEn":"Thndr",
    "aliasesAr":["ثندر"], "aliasesEn":["Thundr"], "monogram":"TH",
    "brandBackground":"#FFE147", "monogramForeground":"#262D24",
    "officialAsset":"broker_eg_thndr", "manifestRef":"eg.thndr", "officialBacking":"brand"
  }]
}
```

IDs are 1–64 ASCII chars matching `^[a-z]{2}\.[a-z0-9]+(?:[.-][a-z0-9]+)*$`; preserve unknown valid IDs without lookup failure. Directory version is independent of payload/database version. Show two choices; no search box for a two-choice picker. Names/aliases remain bundled for future directory expansion, never used for matching legacy wallets.

**Official mark is the default shipping identity.** Obtain unmodified bytes from [Thndr's official site](https://thndr.app/), preserve aspect ratio/clear space and actual colors. Manifest follows `docs/design/bank-marks/manifest.json`'s `version`/`marks` format: institutionId, exact official page and asset URL, retrievedAtUtc, original byte count/SHA-256, output density/file/hash, backing and conversion notes. Store originals under `docs/design/broker-marks/`; render offline to `core/designsystem/src/officialBrokerArtwork` in the bank pipeline's densities. Never redraw, recolor, crop or invent hashes. Yellow fallback color above is a design token, not a claim about an official color specification.

The **same** `-Pdaftar.bankArtwork=official|monogram` switch controls both identity sets; default official, both platforms use the same configuration, monogram mode excludes all official bank/broker resources. Official-mode build fails if required artwork/manifest is absent or mismatched; unexpected runtime corruption falls back to TH. Other/unknown never uses yellow. No runtime logo fetching, WebView, broker sign-in or account telemetry. Update DesignCatalog with official/fallback/Other, category, value and old-value hint in both themes/locales.

**Asset status for this handoff:** the official site was readable, but local asset acquisition failed DNS resolution (`Could not resolve host: thndr.app`). No verified official mark is bundled by this change. The gallery explicitly shows the TH fallback specimen; it must not be mistaken for the official logo. Acquisition and faithful packaging remain a native implementation gate, not a substituted product decision.

## Room v3 → v4: one new table

Create **`investment_accounts` only**. No broker-parent, position, cash-move, history or archive tables. Existing v3 tables remain byte-for-byte unchanged during upgrade. No initial investment rows or automatic wallet conversions.

| Column | SQLite type / nullability / rule |
|---|---|
| id | TEXT NOT NULL PRIMARY KEY; typed InvestmentAccountId |
| created_seconds, created_nanos | INTEGER NOT NULL; normal record creation metadata |
| updated_seconds, updated_nanos | INTEGER NOT NULL; monotonic updated time, ≥created |
| revision | INTEGER NOT NULL; starts 0, increments exactly 1 per update |
| provider_id | TEXT nullable; Other=null; validated country-namespaced ID otherwise |
| name | TEXT NOT NULL; 1–120 trimmed Unicode code points |
| amount | TEXT NOT NULL; canonical nonnegative EGP decimal within bound, ≤2dp |
| value_as_of_seconds, value_as_of_nanos | INTEGER NOT NULL; independently preserved effective Instant |
| note | TEXT nullable; ≤4,000 trimmed code points |
| converted_from_cash_id | TEXT nullable; unique nonnull historical source ID, no FK because source is removed |

Indexes: `(created_seconds,created_nanos,id)` and UNIQUE `(converted_from_cash_id)` (multiple NULLs allowed). No unique provider/name constraint. EGP is fixed by model; no currency column. Metadata seconds use Long, nanos 0…999,999,999; ID validation follows existing record IDs. Converted source ID is immutable once set and not available in ordinary edit forms. A user can delete the investment normally; stale conversion retries still cannot recreate it without a source row.

`Migration3To4` creates table/indexes and `ValidationTriggersV4`; register on Android/iOS, add Room entity/DAO and export schema 4.json. Add v4 triggers to DatabaseFactory integrity verification while retaining all earlier sets; fresh database installs identical definitions. Triggers enforce enum-free identity/decimal/text bounds, nanos, immutable ID/created/provenance and revision discipline. Exact decimal bound checks must use canonical text logic, never REAL. Value-as-of range validation does not depend on the current device clock in SQL; app/domain checks future time for new edits, restore permits valid future instants with UI hint. No destructive fallback or downgrade path.

Repository contracts in core:domain: observe/read/list, add/update/delete with expected revision, and atomic `convertCashToInvestment(sourceId, expectedRevision, draftId, providerId, valueAsOf)`. core:data owns the transaction across cash and investment DAOs, including exact copied amount and preserved name/note. UI has no direct DAO access. All work requires an unlocked session; cancellation/failure/lock cannot produce half-converted data. Optimistic revision conflicts retain drafts while unlocked and require a new review. Source chosen before editing its cash amount cannot silently convert a different value.

## Backup JSON payload v4

Freeze v3 decoder/fixtures, add v4 with the same existing fields plus required **`investmentAccounts`** array. Frozen `.dftar` header/container, Argon2id, XChaCha20-Poly1305, authenticated fields, passwords and cloud ciphertext cap stay unchanged. Snapshot all tables in one transaction, deterministic ID ordering. Each entry serializes `id`, `createdAt`, `updatedAt`, `revision`, `providerId`, `name`, `amount`, `valueAsOf`, `note`, `convertedFromCashId`; decimal canonical strings, UTC Instant strings, explicit nulls.

Export includes all investment values, names, notes, IDs/as-of times and conversion provenance; no logo bytes, directory, derived totals or session draft. v1/v2/v3 restore produces empty investmentAccounts and preserves all legacy cash (including names resembling Thndr), bank identities, quotes and zakat data. Run only existing earlier migration rules. v4 restore requires the array; reject duplicate investment IDs, duplicate nonnull convertedFromCashId, malformed fields, out-of-bound values, invalid timestamps or a converted source ID still present in cashBalances. Unknown valid provider ID renders generic; no network lookup. An investment with unknown broker is still fully valued.

Validate the whole payload before atomic replacement; invalid/newer payload leaves the vault intact. Keep all existing record/file/string/resource caps, counting investment records within total limits. Cloud remains 768 KiB ciphertext; offer local export for oversized data rather than dropping entries. Restore review adds an investment-account count using the slate-teal tile. Reimported timestamps stay original; old-value hints may appear immediately. A converted source absent from both cash and investment after intentional deletion is not reconstructed by restore.

## Copy and accessibility

Reuse existing paper/olive theme, Alexandria Arabic / Rubik English and numeric runs, ≥48dp targets and 200% text wrapping. Isolate amount+EGP and dates with LTR isolates, mirror navigation/panes but never marks/glyphs. Hidden-values mode masks **amounts and notes**, including form drafts, Home/zakat values, conversion review and delete confirmation; name/provider/currency/as-of time remain visible. Require explicit reveal before saving/converting financial data. Production financial values/notes never enter semantics even when visibly revealed, per AGENTS.md; expose field labels/errors, row identity/action, button state and dates. No amount-bearing announcements, clipboard, logs or released screenshots; lock clears private drafts/routes and recents cover follows existing policy. Static gallery values are synthetic and not a privacy implementation.

| Context | العربية | English |
|---|---|---|
| Category / add | الاستثمارات / إضافة حساب استثمار | Investments / Add investment account |
| Empty | لا استثمارات مسجّلة. أضف حسابًا وسجّل قيمته الحالية. | No investments recorded. Add an account and record its current value. |
| Provider / Other | اختر شركة الوساطة / شركة وساطة أخرى | Choose broker / Other broker |
| Name / amount | الاسم الظاهر / القيمة الحالية · ج.م | Display name / Current value · EGP |
| Amount helper | إجمالي قيمة حسابك الآن، وليس المبلغ الذي أودعته. | Your total account value now, not the amount you deposited. |
| No duplicate | سجّل الأموال نفسها مرة واحدة فقط. إذا شمل الإجمالي نقدًا لدى الوسيط، فلا تضفه إلى المحافظ أيضًا. | Record the same money only once. If the total includes broker cash, do not add it to wallets too. |
| Date / zone | القيمة بتاريخ / بتوقيت القاهرة | Value as of / Cairo time |
| Now choices | الآن عند الحفظ / تعيين الوقت الحالي / اختيار تاريخ ووقت | Now when saved / Set to now / Choose date and time |
| Update / old hint | تحديث القيمة / حدّث القيمة عندما يناسبك. | Update value / Update the value when convenient. |
| Clock | راجع تاريخ القيمة. | Check the value date. |
| Manual | قيمة تُحدّثها بنفسك. | A value you update yourself. |
| Note | ملاحظة · اختياري | Note · optional |
| Convert entry / source | تحويل قيد نقدي إلى استثمار / اختر القيد النقدي | Convert a cash entry to an investment / Choose cash entry |
| Review title | تحويل القيد إلى الاستثمارات؟ | Convert entry to Investments? |
| Review explanation | سيُنقل القيد من النقد والمحافظ إلى الاستثمارات بنفس المبلغ والاسم والملاحظة. لا تتحرّك أموال لدى الوسيط. | The entry moves from Cash & wallets to Investments with the same amount, name and note. No funds move at your broker. |
| Review as-of | أكّد أن المبلغ حالي، أو اختر تاريخ قيمته الفعلي. | Confirm the amount is current, or choose its actual value date. |
| Review duplicate | إذا سجّلت هذه الأموال في الاستثمارات بالفعل، ألغِ العملية وصحّح القيد المكرّر. | If this money is already recorded in Investments, cancel and correct the duplicate entry. |
| Review total / action | الإجمالي لا يتغيّر / تحويل القيد / إلغاء | Total unchanged / Convert entry / Cancel |
| Converted | تم تحويل القيد. / تم تحويل هذا القيد بالفعل. | Entry converted. / This entry was already converted. |
| Unsupported source | التحويل متاح لقيود الجنيه المصري ضمن الحد المسموح فقط. | Conversion supports EGP entries within the allowed amount limit only. |
| No source | لا توجد قيود نقدية مناسبة للتحويل. | No eligible cash entries to convert. |
| Source gone | لم يعد القيد متاحًا. ارجع إلى القائمة. | This entry is no longer available. Return to the list. |
| Conflict | تغيّر القيد. راجع أحدث قيمة قبل المتابعة. | This entry changed. Review the latest value before continuing. |
| Delete | حذف الحساب؟ ستُزال قيمته من الإجمالي. لا يعني ذلك سحب الأموال من الوسيط. | Delete account? Its value will leave the total. This does not withdraw funds from your broker. |
| Delete actions | حذف / إبقاء الحساب | Delete / Keep account |
| Zakat basis | يشمل تقدير التجهيز قيم الاستثمارات التي سجّلتها؛ لا يحدّد دفتر حكم زكاتها. | The planning estimate includes the investment values you recorded; Daftar does not assess their zakat treatment. |
| Amount error | أدخل مبلغًا من صفر إلى ⁦999,999,999,999.99⁩، حتى منزلتين عشريتين. | Enter an amount from zero to 999,999,999,999.99, with up to two decimal places. |
| Name / note error | أدخل اسمًا من ⁦1–120⁩ حرفًا. / الملاحظة أطول من ⁦4,000⁩ حرف. | Enter a name of 1–120 characters. / Note exceeds 4,000 characters. |
| Date error | اختر وقتًا صحيحًا من ⁦1 يناير 1900⁩ حتى الآن. | Choose a valid time from 1 January 1900 through now. |
| Loading / saving | جارٍ التحميل… / جارٍ الحفظ… | Loading… / Saving… |
| Load / save failure | تعذّر التحميل. حاول مجددًا. / تعذّر الحفظ. لم تتغيّر البيانات المحفوظة. | Could not load. Try again. / Could not save. Saved data is unchanged. |
| Hidden confirmation | أظهر القيم للمراجعة والحفظ. | Show values to review and save. |
| Common | حفظ / تعديل / إعادة المحاولة / متابعة التعديل / تجاهل التعديلات | Save / Edit / Retry / Keep editing / Discard changes |

Account count plurals: AR zero `لا حسابات`, one `حساب واحد`, two `حسابان`, few `{N} حسابات`, many/other `{N} حسابًا`; EN one `{N} account`, other `{N} accounts`. Select CLDR categories, test 0/1/2/3/11/100/102/103/111. All numeric placeholders isolated.

## Gallery and implementation checklist

Frames: `p24-assets`, `p24-list`, `p24-empty`, `p24-provider`, `p24-add`, `p24-other`, `p24-detail`, `p24-edit`, `p24-update`, `p24-old`, `p24-zero`, `p24-hidden`, `p24-convert-picker`, `p24-convert-provider`, `p24-convert-review`, `p24-converted`, `p24-delete`, `p24-home`, `p24-zakat`, `p24-loading`, `p24-error`, `p24-invalid`, `p24-conflict`, plus `p24-wide`. Old positions, close/archive, price, P/L, missing-investment and wallet-move frames are removed. Conversion is a new reclassification flow, not the previous cash-reconciliation flow. Demo controls navigate synthetic states; no write or broker action occurs.

Implementation scope: model + directory; domain total/use cases; one entity/migration/repository; payload v4; feature:investments list/editor; Home/Assets/zakat integration; shared DI/routes; identity resources and DesignCatalog. Reuse the feature convention, common editor/numeric validation, theme and backup machinery. No investment network integration, background service or new permission. Native implementation must run AGENTS.md gates including device tests for Room/lifecycle changes.

| Test | Expected |
|---|---|
| Base assets 1,076,212.74 + investment 157,000 | Home 1,233,212.74; exact zakat 30,830.3185; display 30,830.32. |
| Two investments 0.10 + 0.10, no other assets | Total 0.20; estimate 0.005; HALF_UP 0.01. |
| Amount 0 / 999999999999.99 | Both accepted; zero contributes zero and remains editable. |
| Blank / −0.01 / 1000000000000 / 1.001 / 1e3 | Reject without truncation, save or timestamp mutation. |
| Arabic ١٢٣٫٤٥ / formatting 123.4500 | Canonical 123.45; trailing zeros normalize; value unchanged. |
| Amount 100→120 | Save amount 120 with now unless user explicitly picks another as-of after changing it. |
| Edit note/name/provider only; 100→100.00 | Prior as-of unchanged; no automatic confirmation. |
| Same amount + Set to now + Save | Advance as-of explicitly; total unchanged. |
| Date age 24h / 24h+1ns | No hint / gentle update hint; same complete total. |
| Home refresh / export / cancelled editor | Amount and as-of unchanged. |
| Future restored date / clock rollback | Neutral check-date hint; amount counted; timestamp preserved. |
| Cash fixture 9,900 converted to investment | Cash decreases 9,900, Investments increases 9,900; Home unchanged at 1,076,212.74; estimate stays 26,905.32. |
| Conversion source named “Thundr wallet” | No preselected provider or automatic conversion; explicit review required. |
| Convert EGP zero / USD / legacy oversized | Zero allowed; USD/oversized rejected, source intact. |
| Changed source revision; failed insertion/deletion; lock during review | No partial conversion or duplicate total; conflict/retry or draft cleared. |
| Double submit / retry after process death / later edit | One investment by source ID; no second insert; later edits not overwritten. |
| Delete converted investment then retry stale conversion | No source and no investment: unavailable, never recreate stale value. |
| Unknown broker ID; two accounts at same provider | Generic icon, values included; no forced merge. |
| v3→v4 including legacy wallet | One empty new table; all existing rows unchanged. |
| v1/v2/v3 restore | Empty investment array; legacy wallets retained, no conversion. |
| v4 round trip | Amounts, zero, names, notes, as-of and conversion provenance exact; no logos exported. |
| v4 absent array / bad bound / duplicated source / converted source still in cash / newer version | Atomic rejection, existing vault unchanged. |
| AR/EN × light/dark × three widths × 200% | Correct four destinations, wrapping, pane mirroring, isolated dates/amounts. |
| Hidden mode on list/detail/editor/conversion/Home/zakat | Amounts and notes absent from visible UI and semantics; reveal before financial confirmation. |
| Official/monogram clean builds | Faithful offline logo in official; no official resources packaged in monogram; missing manifest fails official build. |

Design verification must distinguish generated-markup checks from rendered/device checks. The previous browser local-file preview was blocked by URL policy; no workaround or visual verification is claimed. Native tests are implementation gates, not performed by this design-only revision.

Handoff checks, 14 October 2026: both embedded scripts parse; 23 Phase 24 phone frames generate successfully in AR/EN × light/dark × hidden/revealed, plus the expanded-frame template. Frame IDs are unique and Phase 24 links resolve. Old position/close/price/wallet-move frame identifiers are absent. Hidden generated markup contains neither fixture amounts nor note text. Exact Home/zakat and conversion-conservation fixtures pass; diff whitespace checks pass. The design detector reports only the established approved ivory palette. Browser rendering, native/device tests and official-mark packaging remain unverified as described above.
