# Phase 22 — Zakat calculator and yearly declaration

Approved extension of [daftar.html](daftar.html), journal section. Supersedes only the v1 “recording only” restriction. All amounts in the design are synthetic. No application implementation is included in this change.

## Product decision

Calculate **2.5% of the whole Home asset value** in EGP, including jewelry, all bank balances and cash/wallets. No nisab gate, exclusions, debt deductions, hawl tracking or religious eligibility verdict. These would imply knowledge the notebook does not have. The result is a preparation estimate, never an assessed debt or remaining balance. Payments and completion never deduct assets. The owner updates balances explicitly.

Journal → Zakat owns yearly status, payment log and calculator. Journal also has a direct calculator row. Home gets one small amount-free entry: neutral “Zakat · Prepare for Ramadan” before the date or after completion; warning panel + “Review this year’s zakat” when any tracked year is due and unchecked. The updated Home fixture demonstrates the latter. Keep all four navigation destinations.

No OS notifications, background jobs, badges, widgets or permission requests. Persistent, non-dismissible in-app reminders on Home and Journal, **only after unlock**, meet the requested reminder behavior without exposing private activity outside the vault. A reminder denotes an unconfirmed personal schedule, not a religious ruling. It remains through later years until each overdue year is marked paid.

## Pure domain contract

Inputs: complete/incomplete Home valuation (exact decimals, per-category values, quote provenance), explicit `today: LocalDate`, explicit `now: Instant` for freshness, annual declarations and date overrides. Domain reads neither clock nor repositories. Platform adapter derives today in **Africa/Cairo**, regardless of device travel; uses midnight civil dates, not sunset or 24-hour durations. Recompute on unlock, Cairo midnight while open, clock correction and relevant data changes. No work while locked.

- `T = goldEGP + banksEGP + cashEGP`, using the **same unrounded valuation result as Home**.
- `estimateExact = T × 25 / 1000` (equivalently `T / 40`); never `Double`.
- Gold: net grams × merchant **SELL** quote for each actual karat; no making charges. EGP is nominal; USD uses the selected USD→EGP quote.
- Do not round categories before summing or round `T` before multiplying. Display money at 2 decimals, HALF_UP. A prefilled payment is `roundHalfUp(estimateExact, 2)`; it is an editable draft, never a saved payment. Follow existing amount bounds; reject overflow explicitly rather than truncate.
- Pinned manual quote wins; otherwise live → cached with original success time → manual input. Reuse existing quote-selection rules, not a new zakat price path.
- Any **required** missing quote makes estimate unavailable (`—`); show total `—` and a labeled available subtotal. Do not calculate zakat on that subtotal. Missing USD quote does not matter if no nonzero USD holding needs it. Never substitute zero for an unknown quote.
- Cached data older than **24 hours** is stale (`age > 24h`, not `>=`); stale remains calculable. Show oldest used successful timestamp above the breakdown, and per-source/karat timestamps and provenance in details. Manual quotes show “Manual · entered {dateTime}”; mixed sources are explicitly mixed. Failed refresh does not advance timestamps. Future quote timestamps display “Quote time is ahead of device time; check the date.” and cannot receive a fresh label.
- Empty vault or complete zero holdings gives `0.00`, with no religious conclusion and no prefilled zero payment (payment amounts must be positive). EGP-only holdings need no quote. Negative assets are rejected by existing validation.
- **Always current**: no automatic 1 Ramadan snapshot, no assumed historic valuation if the app was closed that day. Past-year pages contain declarations and payments, never today’s estimate presented as historical liability. The calculator remains titled “Estimated zakat today”; no “amount left”, progress bar or subtraction of payments.

## Hijri calendar: civil arithmetic, explicitly approximate

Implement the civil Islamic **Friday epoch / type II** calendar in common Kotlin with integer `Long` arithmetic. No Umm al-Qura table, network calendar or platform Hijri dependency. This small reproducible algorithm works offline identically on Android/iOS. Source for the civil epoch and distinction from observed months: [Fourmilab Calendar Converter](https://www.fourmilab.ch/documents/calendar/). Egypt’s sighting authority and local practice: [Dar al-Ifta, sighting procedures in Egypt](https://ramadan.dar-alifta.org/fatwa/details/12825/القواعد-والإجراءات-المتبعة-لرؤية-الهلال-في-مصر). These sources inform calendar behavior, not a zakat ruling.

Exact supported conversion dates: **1 Muharram 1300 AH through 30 Dhu al-Hijjah 1600 AH**, inclusive, corresponding to **1882-11-12 through 2174-11-25** Gregorian. Year 1600 is leap. Reject dates outside this range; do not clamp or silently swap calendars. Internally compute the start of 1601 only as an exclusive sentinel. Supported Ramadan year keys: 1300…1600. Beyond the last supported Ramadan, say the next date is unavailable; do not fabricate 1601. Existing log access and marking existing years remain available even when today is outside conversion range; reminders for stored supported dates can still use Gregorian comparison, but generate no new years.

Epoch: 1 Muharram 1 AH = 622-07-19 proleptic Gregorian = Julian day 1948439.5 = Unix epoch day **−492148**. For validated year `y`, month `m` and day `d`:

```text
leap(y) = ((11*y + 14) mod 30) < 11
monthLength(m,y) = 30 for odd m; 29 for even m;
                   except m=12 is 30 in leap years
monthOffset(m) = (59*(m-1) + 1) / 2        // integer floor
start(y) = -492148 + 354*(y-1) + (3+11*y)/30
epochDay(y,m,d) = start(y) + monthOffset(m) + d - 1
ramadanBase(y) = epochDay(y,9,1)
ramadanDate(y) = ramadanBase(y) + overrideDays(y)
```

Inverse: binary-search supported year starts, then walk at most 12 validated month lengths. Leap positions in each 30-year cycle: 2, 5, 7, 10, 13, 16, 18, 21, 24, 26, 29. No floating Julian dates in runtime code.

Persist a per-year `overrideDays ∈ {-1,0,+1}`, default 0, with explicit `userAdjusted` provenance. Date editor available on **each selected year**, including past due years, not just next Ramadan. Show all three concrete Gregorian options; save atomically. Restoring calculated date clears the override/provenance. No claim of automatic official confirmation. Copy: “Civil Hijri calendar; Egypt confirms Ramadan by sighting. The date may differ by a day.” This is a planning uncertainty, not a guaranteed astronomical error bound. ±1 adjustment does not modify general Hijri conversion or the stable year key.

`nextRamadan = first supported adjusted date >= today`; on the date show “Today”, then advance the following day. `daysRemaining = targetEpochDay - todayEpochDay`. On 2026-10-13, next Ramadan is **1448 AH / 2027**, 2027-02-08, **118 days**. The overdue declaration can simultaneously be **1447 AH / 2026**, date 2026-02-18. Keep the two labels distinct.

## Yearly completion, payments and persistence

The key is **Hijri year of Ramadan**, never Gregorian year: `1447 AH · 2026`. Gregorian label is the year of its adjusted Ramadan date. Two Ramadans in one Gregorian year stay separate. `paid` means the user checked **“I have paid zakat for this year”**, independent of amount, quote availability or payment count.

Store annual records inside encrypted vault: `hijriYear` unique, `paid: Boolean`, nullable `markedAt: Instant`, override and provenance. Store `trackingStartYear` in the encrypted vault too. No sensitive status in plain preferences. A checked year can be completed without any payment record; mark action does not create a fake payment. Unchecking opens confirmation; confirm clears `markedAt` and restores reminders if due. Saving errors preserve prior committed state, retain draft and offer retry; no success feedback before commit. Disable duplicate submissions while saving. Calendar edits never clear a paid mark.

On first upgrade/unlock, initialize tracking transactionally at the **most recent Ramadan on or before today**, and include the next Ramadan year for preparation (within the supported range). Thus 2026-10-13 starts at 1447 / 2026 and also shows upcoming 1448 / 2027. The initial unchecked state means “awaiting your confirmation”, never proof of nonpayment. This makes the owner’s 2026 check mark immediately available. Do not create arrears for any earlier year. If today precedes the first supported Ramadan, start at 1300 with an upcoming status. Show “Tracking begins with {year}. Mark it paid if you already completed it; add an earlier year only if you want to track it.” Existing payments are not evidence of full completion. Do not infer either a paid mark or an annual assignment from an old payment date. Existing payments remain visible as **Unassigned**; user may explicitly assign them. The due 1447 gallery fixture assumes tracking already started in 1447, and its sample payments were explicitly assigned.

After initialization, create missing annual records through the next Ramadan year on unlock, all unchecked; keep every earlier tracked year. A future record explicitly added for an advance payment is allowed only through the next Ramadan year. A past-year picker supports 1300…current civil year, prevents duplicates and explains that adding an unchecked past year activates its reminder. History sorts descending, labels paid / awaiting confirmation / upcoming, and supports selection, marking, unmarking and date adjustment per row. No silent annual reset, no erasure of overdue history. Never infer a mark from a restored old-format payment.

`dueYears = tracked years where today >= ramadanDate(year) && !paid`. Equality includes 1 Ramadan. Reminders persist at Muharram rollover and after later Ramadan dates. Home/Journal show one panel; if several years are due, pluralize their count and open history, ordered oldest-due first for action. No amounts or payment notes in reminder text. Paid in advance remains paid when its date arrives. No snooze/dismiss state.

Extend payments with nullable `forHijriYear`; paidOn stays Gregorian `LocalDate`. Allow partial and advance payments and payments without a year. Sum linked payments only for the chosen year; unassigned entries appear in their own existing log group. Editing/deleting a payment never changes annual completion. Recording a payment **never automatically marks paid** and has no implicit completion checkbox. After saving, return to the chosen year so the separate declaration is available. Direct “Record payment” starts with a blank amount; calculator CTA prefills the currently displayed rounded estimate and clearly asks for the actual amount paid. Default year is the explicitly selected year, else oldest due year, else next Ramadan year; show editable selection before saving. Draft amount does not change during a quote refresh. No form opens with private prefilled amounts while values are hidden: explicitly reveal first.

Schema migration and backup payload migration are implementation requirements. Version Room schema and JSON payload, preserve the frozen `.dftar` cryptographic header/format, and include annual records, tracking start and payment links in encrypted local/cloud backups. Older payloads initialize tracking on first unlock with payments unassigned; newer unsupported versions are rejected. Restore atomically replaces all related state; no merge. Validate year bounds, unique keys, valid overrides, payment references and paid/markedAt consistency. Do not ship completion history that vanishes on export/restore.

## States and accessibility

Gallery uses existing tokens, typography, rows, note panels, buttons and theme/language controls. No new navigation destination or gold-colored zakat styling. Brass remains for gold breakdown only. New frames: `zakat-calc`, `zakat-upcoming`, `zakat-save-error`, `zakat-paid`, `zakat-unmark`, `zakat-history`, `zakat-year-add`, `zakat-date`, `zakat-stale`, `zakat-missing`, `zakat-empty`, `zakat-hidden`; revised `journal`, `zakat`, `zakat-add`, `empty-zakat`, and the shared Home body.

Implement catalog states in AR/EN × light/dark: loading; current/live; cached; mixed/manual; stale/offline; missing required quote; no assets; zero total with assets; hidden; upcoming; due today; overdue; paid early; paid with no log; partial payments still unchecked; several overdue years; no history; unassigned legacy payments; date adjusted; outside range; saving; save failure; refresh failure with retained estimate. Date and persistence errors use existing error panels and retry actions; no blank success screen.

Global eye mode masks all estimates, totals, subtotals, category values, payment amounts, notes and amount-bearing drafts immediately across navigation; do not reset privacy on entry. Date, year and completion status remain visible **inside the unlocked vault**. Show `••••` instead of values; do not leave numeric values in hidden accessible labels/tooltips. In production, amounts and notes never enter semantics even when visibly shown, consistent with the security policy. Demo HTML contains synthetic fixtures, not production privacy enforcement. Clear drafts/routes on lock. Status checkboxes announce full year, role and checked state. Full labeled row is a minimum 48dp hit target; text wraps at 200% font scale. Use native dialogs/pickers and mirrored directional icons.

## Copy contract (Arabic / English)

`{N}` means a formatted numeric run wrapped in LTR isolates U+2066/U+2069. Isolate **number and unit together** for money, percentages and `1447 هـ` / `1447 AH`; `bdi` is the HTML equivalent. Rubik for numeric runs, Alexandria for Arabic words. Dates use localized month names, Gregorian years and explicit AH suffixes. Accept Arabic-Indic digits in year and amount inputs. Use resource plurals, never concatenate an English plural suffix.

| Context | العربية | English |
|---|---|---|
| Calculator / estimate | حاسبة الزكاة / تقدير الزكاة اليوم | Zakat calculator / Estimated zakat today |
| Heading / next date | جهّز لرمضان / رمضان القادم | Prepare for Ramadan / Next Ramadan |
| Year / checkbox | زكاة السنة / أخرجت زكاة هذه السنة | This year’s zakat / I have paid zakat for this year |
| Checkbox helper | إقرار منك، مستقل عن الدفعات | Your declaration, independent of payments |
| Due status / paid / future | بانتظار تأكيدك / تم الإخراج بإقرارك / لم يحن الموعد | Awaiting your confirmation / Marked paid by you / Upcoming |
| Reminder | حلّ موعدك السنوي، ولم تعلّم زكاة السنة كمُخرَجة. | Your annual date has arrived; this year is not marked paid. |
| Review / history | راجع زكاة السنة / حالة السنوات | Review this year’s zakat / Yearly status |
| Neutral Home entry | الزكاة · جهّز لرمضان | Zakat · Prepare for Ramadan |
| Disclaimer | تقدير للتجهيز: ⁦2.5%⁩ من كل الأصول، بما فيها المشغولات. لا يفحص النصاب أو الحول ولا يستبعد الديون أو الإعفاءات. ليس فتوى. | Planning estimate: ⁦2.5%⁩ of all assets, including jewelry. No nisab or hawl assessment, debt deductions or exemptions. Not a fatwa. |
| Date caveat | تقويم هجري حسابي؛ تؤكَّد بداية رمضان في مصر بالرؤية. قد يختلف يومًا. | Civil Hijri calendar; Egypt confirms Ramadan by sighting. The date may differ by a day. |
| Date tags | تاريخ تقديري / ضبطته لهذه السنة | Estimated date / Adjusted by you for this year |
| Adjust / save / reset | ضبط موعد رمضان / حفظ الموعد / الرجوع للتاريخ الحسابي | Adjust Ramadan date / Save date / Restore calculated date |
| Reopen heading | إعادة فتح السنة؟ | Reopen this year? |
| Reopen explanation | ستُزال علامة الإخراج، ويعود التذكير إذا حلّ موعد السنة. لن تُحذف أي دفعة. | The paid mark will be removed and reminders will resume if the year’s date has arrived. No payments will be deleted. |
| Reopen actions | الإبقاء على العلامة / إلغاء العلامة | Keep paid mark / Remove mark |
| Success | تم حفظ إقرارك لهذه السنة. / أُعيد فتح السنة. | Your declaration for this year was saved. / Year reopened. |
| Completion help | توقف التذكير لهذه السنة. لا يتغير الإقرار بتغير التقدير أو تعديل الدفعات. | Reminders for this year have stopped. Changes to estimates or payments do not change your declaration. |
| Estimate help | يتغير التقدير مع الأصول والأسعار؛ لا يُثبَّت في رمضان ولا تُطرح منه الدفعات. | The estimate changes with assets and prices; it is not frozen in Ramadan and payments are not subtracted. |
| Basis | قيمة الأصول / تفصيل الأساس والأسعار / الإجمالي المتاح فقط | Total assets / Basis and price details / Available subtotal only |
| Provenance | حي / محفوظ / يدوي / مصادر مختلطة / أسعار قديمة | Live / Cached / Manual / Mixed sources / Stale prices |
| Time | أقدم سعر مستخدم: {dateTime} / أُدخل في {dateTime} | Oldest used quote: {dateTime} / Entered {dateTime} |
| Missing quote | سعر {asset} مفقود. لا يمكن حساب تقدير كامل. | {asset} quote missing. A complete estimate is unavailable. |
| Price action | مراجعة الأسعار / التقدير غير متاح | Review prices / Estimate unavailable |
| No assets | لا أصول مسجّلة. الصفر يصف دفترًا فارغًا، ولا يحدد التزامك بالزكاة. | No assets recorded. Zero describes an empty notebook, not your zakat obligation. |
| Zero holdings | قيمة الأصول المسجّلة صفر. هذا ليس حكمًا على وجوب الزكاة. | Recorded assets total zero. This does not determine whether zakat is required. |
| Empty log | لا دفعات مسجّلة / يمكنك تأكيد إخراج الزكاة دون تسجيل مبلغ. | No payments recorded / You can confirm completion without recording an amount. |
| Log help | عدم وجود دفعات لا يعني أنك لم تخرج الزكاة. | An empty payment log does not mean you have not paid. |
| Payment | تسجيل دفعة / تسجيل دفعة بهذا التقدير / الدفعات المسجّلة | Record payment / Record payment with this estimate / Recorded payments |
| Payment fields | التاريخ / المبلغ · ج.م / عن سنة · اختياري / ملاحظة · اختياري / بدون ربط بسنة | Date / Amount · EGP / For year · optional / Note · optional / Unassigned |
| Prefill help | من تقدير اليوم؛ عدّله للمبلغ الذي دفعته فعلًا. | From today’s estimate; edit to the amount actually paid. |
| Payment help | تسجيل الدفعة لا يعلّم السنة كمُخرَجة، ولا يخصم من الأصول. يمكنك إتمام السنة من سجل الزكاة. | Recording a payment does not mark the year paid or deduct from assets. Mark completion separately in the zakat log. |
| Year entry | إضافة سنة سابقة / السنة الهجرية / إضافة السنة للمتابعة | Add past year / Hijri year / Add year to tracking |
| First use | تبدأ المتابعة من {year}. علّمها كمُخرَجة إن كنت أخرجتها، وأضف سنة أسبق فقط إذا أردت متابعتها. | Tracking begins with {year}. Mark it paid if you already completed it; add an earlier year only if you want to track it. |
| Add year help | إضافة سنة غير معلّمة كمُخرَجة تُفعّل تذكيرها إذا حلّ موعدها. | Adding an unmarked year activates its reminder if its date has arrived. |
| History help | السنوات السابقة لا تُغلق تلقائيًا. | Past years do not close automatically. |
| Hidden CTA | إظهار القيم لتسجيل التقدير | Show values to record estimate |
| Loading / saving | جارٍ حساب التقدير… / جارٍ الحفظ… | Calculating estimate… / Saving… |
| Save error | تعذّر الحفظ. لم تتغير الحالة المحفوظة. حاول مرة أخرى. | Could not save. The saved status is unchanged. Try again. |
| Refresh error | تعذّر تحديث الأسعار. نستخدم آخر أسعار متاحة بتوقيتها الأصلي. | Could not refresh prices. Using the last available quotes with their original timestamps. |
| Retry | إعادة المحاولة | Retry |
| Range error | التاريخ خارج النطاق المدعوم: ⁦1300–1600 هـ⁩. سجل الدفعات متاح. | Date outside supported range: ⁦1300–1600 AH⁩. Payment log remains available. |
| Next date unavailable | موعد رمضان القادم خارج النطاق المدعوم. | Next Ramadan is outside the supported range. |
| Invalid/duplicate year | أدخل سنة هجرية ضمن النطاق الموضّح. / هذه السنة موجودة بالفعل. | Enter a Hijri year within the shown range. / This year already exists. |
| Invalid amount | أدخل مبلغًا أكبر من صفر، حتى منزلتين عشريتين. | Enter an amount greater than zero, with up to two decimal places. |
| Clock anomaly | وقت السعر يسبق وقت الجهاز؛ راجع التاريخ. | Quote time is ahead of device time; check the date. |

Countdown plurals: AR zero `اليوم`, one `غدًا`, two `بعد يومين`, few (3–10) `بعد {N} أيام`, many (11–99) `بعد {N} يومًا`, other `بعد {N} يوم`; EN zero `Today`, one `In {N} day`, other `In {N} days`. CLDR plural selection handles 102, 103, 111, etc.; do not apply modulo rules by hand. Do not show negative countdowns for overdue years.

Overdue-count plurals: AR zero has no warning, one `سنة بانتظار تأكيدك`, two `سنتان بانتظار تأكيدك`, few `{N} سنوات بانتظار تأكيدك`, many `{N} سنة بانتظار تأكيدك`, other `{N} سنة بانتظار تأكيدك`; EN one `{N} year awaiting your confirmation`, other `{N} years awaiting your confirmation`. Payment counts, if shown: AR zero `لا دفعات`, one `دفعة واحدة`, two `دفعتان`, few `{N} دفعات`, many `{N} دفعة`, other `{N} دفعة`; EN one `{N} payment`, other `{N} payments`.

## Concrete acceptance tests

| Input / action | Expected |
|---|---|
| Gold 677,812.74 + banks 388,500 + cash 9,900 | T=1,076,212.74; exact estimate=26,905.3185; display/prefill=26,905.32 EGP |
| T=100,000 / 1 / 0.20 / 0.19 | 2,500.00 / 0.03 / 0.01 / 0.00 EGP; no zero-payment CTA |
| Exact categories 0.10 + 0.10 | T=0.20; rounded estimate=0.01, not sum of category-rounded estimates (0.00) |
| USD 100 × 48.50, EGP 150, no gold | T=5,000; estimate=125.00; absent gold quotes irrelevant |
| Gold unavailable; known banks+cash=398,400 | Total and estimate `—`; subtotal 398,400.00; no prefill |
| Quote age 24h / 24h+1s; refresh fails | First not stale, second stale; timestamp unchanged; estimate retained |
| Manual pinned rate 50; live FX 48.50; USD 100 | T=5,000; estimate=125.00; manual provenance |
| 1447-09-01 / 1448-09-01 / 1449-09-01 | 2026-02-18 / 2027-02-08 / 2028-01-28 |
| 1300-01-01 / 1600-12-30 | 1882-11-12 / 2174-11-25; next/previous out-of-range day rejected |
| 1447-12-30 / 1448-12-30 | First valid (leap); second invalid (common); all supported dates round-trip |
| 2026-10-13; next 1448 | 118 days; override −1 →117; +1 →119 |
| 1447 unchecked; 2026-02-17 / 18 / 19 | Upcoming / due today / overdue; override +1 shifts due to Feb 19 |
| Paid 1447 with no payments | Reminder suppressed for 1447; calculator still available |
| Partial entries 5,000 + 10,000 linked to 1447 | Linked total 15,000; remains unchecked; estimate still 26,905.32 on fixture assets |
| Mark then edit/delete payment or change prices | Paid flag unchanged; no automatic asset deduction |
| Unmark cancel / confirm; failed save | Cancel preserves paid; confirm resumes due reminder; failed save preserves committed paid flag |
| Next Ramadan arrives with 1447 still unpaid | 1447 remains due alongside new year; marking new year does not hide old reminder |
| First use 2026-10-13 with old unassigned payments | Tracking starts 1447 plus upcoming 1448; 1447 awaits confirmation; no 1446 or earlier reminder |
| Cairo midnight / DST / travel / app closed on Ramadan | Civil-date comparison unchanged by elapsed hours; next unlock reconciles; no historic snapshot |
| Hidden mode → calculator/log/form → lock | No displayed private values or private semantics; reveal before prefill; lock clears drafts |
| Old/new backup restore; duplicate year; invalid override | Migration preserves old payments unassigned; new state round-trips; invalid payload rejected atomically |

Implementation gates: shared domain tests on Android host and iOS; exhaustive supported-date round trip and leap/month boundaries; locale plural tests at 0/1/2/3/10/11/99/100/102/103/111; AR/EN × light/dark and 200% text visual checks. Database/migration/lifecycle changes require device tests and the repository’s full Android/iOS PR checks. This design-only change needs HTML render/link checks; it does not claim those application gates ran.
