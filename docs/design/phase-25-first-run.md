# Phase 25 — a steady first run

Design decision, 14 October 2026. Audience: first release use on Samsung S25 Ultra, Arabic first; English, Android and iOS parity. This is an implementation specification, not an implementation claim. The approved paper/ink/teal world in `daftar.html` stays authoritative; brass remains exclusive to gold. New frames use `p25-` IDs and the gallery's language/theme controls. Earlier auth/loading frames are historical where they conflict with this phase.

## Evidence and ownership

Read `AGENTS.md`, the approved gallery, `AuthScreens.kt`, `HomeScreen.kt`, `AssetsScreen.kt`, `PricesScreen.kt`/`PricesViewModel.kt`, shell `PriceRefreshAction`, `SessionPriceRefresh`, navigation routes and network fallback policy. Confirm currently adds a top bar, changes its heading and removes its explanation on mismatch; unlock inserts several messages. Home binds pull progress to every refresh. The session controller currently refreshes once per open generation without retries. Prices already retain a ready board during local reads. Preserve that retention and the generation guard.

Implementation belongs to auth presentation, shared refresh coordination and Home/Assets/Prices presentation. Feature modules continue to use domain interfaces, never each other or `core:data`. This delivery edits only this specification and the gallery.

## 1. PIN: one stable geometry

Use a shared presentation layout (`StablePinLayout`, proposed internal auth component), containing the existing `DaftarTopBar`, `DaftarMark`, `DaftarPinField`, `DaftarKeypad`, `PrimaryButton`, `DaftarTextButton` and a single `DaftarNotePanel` message slot. No animation of size, insertion/removal of rows, or recentering after an error.

All measurements are dp except typography, which retains existing sp styles. System safe insets are outside these measurements. Center a maximum 400dp content column; horizontal padding `md=16`, vertical `md=16`. Reserve a 66dp top-bar lane on create, confirm and unlock; only confirm has a Back action. Empty lanes have no semantics. Keep the title in the body, not duplicated in the bar.

Order inside the column:

| Region | Default minimum | Content and spacing |
| --- | --- | --- |
| Intro | 164 | Mark 44; `xs=8`; step line 20; `xs=8`; heading lane 84 (up to two lines). Unlock reserves the step line without speaking it. |
| Flexible space | 0 | Takes remaining height above the input block, never between keys. |
| PIN field | 54 | Existing `pinFieldHeight`; masked input, LTR. |
| Gap | 8 | `xs` |
| Message lane | 80 | 12 padding per side; two 28dp body lines at default scale. Explanation OR one notice. |
| Gap | 12 | `sm` |
| Keypad | 256 | Four rows × 58 minimum plus three `xs=8` gaps; three equal columns, LTR. |
| Gap | 12 | `sm` |
| Primary action | 48 | `buttonMinHeight`, never removed during backoff or work. |
| Gap | 8 | `xs` |
| Footer lane | 56 | Create security footnote; confirm restart action; unlock recovery action. |

Default body minimum is 796dp including top bar and vertical padding, excluding system insets. A shorter available height scrolls: **one outer scroll container for the complete body below the top bar**, never a separately scrolling keypad or clipped message. At or above the minimum the input block is bottom aligned, 16dp above the safe bottom. At smaller heights its document coordinates remain identical across states and the user's scroll position is retained. This deliberately prefers a reachable full keypad over shrinking text or touch targets.

The default dimensions are floors, not hard clipping limits. Before display, measure ALL create/confirm/unlock copy variants at actual available width, locale and font scale, including the maximum formatted backoff duration. Reserve the largest heading, message, footer and button heights for the whole PIN flow. At 200%, allow unlimited wrapping and grow these shared lanes; disable ellipsis and max-line truncation. Default two-line message target is not a 200% limit. Key rows grow equally if their scaled labels need more than 58dp; PIN field grows if necessary. Recompute on width/locale/font changes only, not on error, phase, countdown tick or biometric event. Restore the nearest visible control after a configuration change. Stable means fixed across state transitions at a given configuration, not fixed pixel size across accessibility settings.

Message priority: persistent PIN backoff > PIN mismatch/incorrect/open failure > biometric notice > phase explanation. Do not stack panels or remove the reserved lane. Keep phase heading unchanged on mismatch. Error uses Error tone; biometric/context uses Context tone; normal explanation is unboxed muted text in the same padded lane. A superseded biometric notice is discarded, not shown after the user starts a PIN attempt. Errors remain until the next input or deliberate action; no timed disappearance. Busy caption replaces the message in place; the button may indicate cryptographic work within its reserved bounds. Backoff disables keypad and primary action, retaining the recovery action under existing security rules.

Accessibility: heading semantics once; message `liveRegion=Polite` for newly appearing errors/notices, no focus stealing. Announce backoff once and completion once; expose the changing duration on focus without announcing every second. Exclude empty slots and decorative mark. Preserve digit-count-only PIN semantics, never PIN contents. Focus order: back, heading, PIN, message, keypad, primary, footer. Preserve current once-per-lock-episode biometric auto-prompt, splash timing, cancellation and recovery behavior exactly.

Acceptance: at fixed width/scale, create → confirm → mismatch and unlock → incorrect → backoff → biometric notice have identical PIN, digit and action bounds (≤1dp rounding tolerance); no lost scroll offset. Verify Arabic/English, light/dark, 320dp width, short 480dp usable height, S25 Ultra, iPhone 18 Pro, and 100%/200% text. All controls and complete messages remain reachable. TalkBack/VoiceOver announces one error event, no PIN, and no countdown chatter.

## 2. Prices: silent automatic work, explicit feedback

Separate refresh origin (`Automatic`, `User`) from transport status. A user gesture joining an automatic request promotes that same request to user-visible work; never duplicate the fetch. `PriceRefreshAction` stays usable during automatic work. After a user request it shows a small progress indicator in its existing 48dp target and disables duplicate taps. Pull-to-refresh shows progress only for a pull initiated on that surface. A button refresh must not also drop a pull indicator over Home. Keep origin and gesture owner in the shared controller so navigation cannot start duplicate work. Other surfaces may expose “Updating prices…” as the refresh action's accessible status; they do not introduce a second spinner. Finish all user feedback when that attempt finishes; later automatic retries are silent.

Automatic means first open, later unlock/resume, retry and network regain: no spinner, pull animation, shimmer, loading caption, snackbar, success announcement or “previous total” relabeling. Local database opening/failure is separate; do not disguise a vault read failure as a missing quote. Retain already drawn content while local reads settle; an initial local-read placeholder is static, never driven by network state.

| Surface/state | Decision |
| --- | --- |
| Empty Home | Guide first, then quiet quote strip. No total hero, zero total, allocation or missing-price warning wall. Saving records never waits for quotes. |
| Quote strip | Keep `DaftarQuoteStrip` geometry and gold/USD labels; unavailable values are `—`. Static muted “Waiting for prices”. No fabricated time or live badge. Use existing details action. |
| Home/Assets with records | Value EGP cash/accounts and recorded EGP investment values immediately. If any holding needs an unavailable gold karat/USD rate, total is `—`, caption “Waiting for prices”; retain the separately labelled “Available subtotal”. Category totals are `—` only where incomplete. Never substitute zero or present subtotal as total. No allocation until complete. |
| Prices | Show table headings, karats, USD row and `—` cells immediately; one Context panel, no missing-panel stack. Keep source-details structure; unknown timestamps use `—`. Manual-price action is available from the first attempt. |
| Some quotes ready | Fill only accepted values; pending cells stay `—`. Aggregate missing-price explanation once. Preserve gold buy/sell pairing and existing source selection. |
| Cached/manual available | Render immediately using live → last cached (with original time) → manual per quote key. Retain cached/manual/stale tags and genuine source/fetch times. Retry does not update timestamps. Manual is not an override of usable cached/live quotes. No blanking during refresh. |
| Retry budget exhausted | One muted Context note, replacing waiting copy in the quote area: unavailable copy if missing, retained-price copy if usable fallback exists. Details/manual remain available. No dialog, toast or full-screen error. |
| Explicit refresh failure | End progress immediately; same one Context note (“Could not update…”). Announce once, no amounts. Subsequent retries stay quiet. Success replaces values/timestamps atomically without moving focus. |
| Hidden values | Existing masking wins over dashes/subtotals and every new view; no balance, weight, note or PIN in semantics, notifications, logs or recents. Market quote semantics follow existing public-price policy. |

### Retry and rate policy (implementation requirements)

Provider evidence: [ExchangeRate-API open-access documentation](https://www.exchangerate-api.com/docs/free) specifies daily updates, caching, attribution and an hourly request cadence that avoids rate limiting. The one-hour gate below applies even during failure bursts; no numerical quota is asserted for iSagha or Banque Misr.

One scheduler in `SessionPriceRefresh`, not screen effects. Initial automatic attempt only while foreground + vault open; resume is an event, not a periodic poll. Retry only transient connectivity, timeout and 5xx failures, only the failed quote source/group. Successful gold/FX groups are not fetched again by the other's retry. Keep existing free source chain and attribution; no paid service, new API key or background worker.

Nominal delays **after completion**: 5s, 15s, 45s, 120s, with positive 0–20% jitter; four retries maximum after the initial attempt. These are lower bounds, not permission to call a provider that often. Apply the later of backoff, provider cooldown and Retry-After. Do not retry parsing/schema/invalid quote/401/403/404 failures in the burst; retain fallback and wait for a later eligible resume/manual request. Accepted stale data remains labelled stale and does not cause an endless “freshness” retry loop.

Provider gates: preserve a per-provider next-eligible time across navigation, lock and process restart (non-sensitive timing metadata only). For iSagha and Banque Misr, whose quotas are not established here, use a conservative 15-minute minimum after any HTTP attempt; do not claim this proves provider permission. For daily FX fallback feeds, cache the response until its declared next update, or 24h after success if absent; on transport failure use at least 15 minutes, except ExchangeRate-API which has a one-hour minimum between issued requests (including failed/manual requests). Both Fawaz mirrors share one budget. Rate-limit responses: honor Retry-After seconds AND HTTP-date; absent/invalid header means at least 60 minutes. A stricter published provider rule always wins. No rapid retry burst against scraped pages. Thus the nominal 5/15/45/120 schedule can run only for failures before a provider request was issued or a provider explicitly allowing that cadence. Release acceptance includes checking current provider terms and configuring stricter limits if needed.

Bound each attempt to existing network timeouts; FX chain retains its 30s total budget. No layered HTTP retry multiplication. Per provider cap: five issued requests per rolling hour, including manual, automatic, initial and fallback calls; longer cooldowns usually yield fewer. Manual refresh can join work or initiate one eligible attempt, never bypass cooldown/cap. If all sources are gated, do not show a spinner; show the rate-limit note. Do not sleep with a spinner while waiting for eligibility.

Stop active work and scheduled delays on background/lock/session-generation change. Discard late generation results; do not write them into the new vault. Keep retry count and cooldowns; unlock does not reset limits. After four retries, stop timers. A subsequent foreground/resume or offline→online event may authorize **one** eligible attempt, with at least 15 minutes since the last automatic attempt; it does not refill the burst. Reset the failure burst after a successful group fetch or a 24-hour quiet interval. Coalesce lifecycle/network events. No network observation or wakes solely for prices while backgrounded. Without a supported connectivity callback, use next foreground/manual refresh rather than adding a permission solely for this feature.

Acceptance: airplane-mode first launch has no network progress on any screen and add flows work. Joining an auto-fetch causes one request and one user indicator. Repeated taps/resumes and switching screens respect shared caps; simulated Retry-After seconds/date, partial successes, timeouts and process restarts retain gates. After exhaustion, no timer or background wake remains. Cached/manual timestamps and selection order survive every failure. Missing required rates never produce a misleading zero total. These are required implementation tests, not tests run by this design change.

## 3. First record: three choices, no onboarding wall

Replace Home's large empty illustration/CTA with one restrained `DaftarNotePanel(Context)` guide, padding `md=16`, heading `sectionHeading`, `xs=8` to body, `sm=12` to choices. Use `DaftarLedgerRow` for three optional entry points; rows minimum 64dp, natural height at large fonts, existing tile tones (gold only brass). No progress percentage, checkboxes, numbered obligations or completion animation. A visible `DaftarTextButton` “Hide guide” has a 48dp minimum target. Place it after the rows, not in a tiny close icon. Quote strip follows at `xl=24`; existing navigation remains usable.

1. Gold → `GoldAddRoute` directly; save returns through normal navigation to Home.
2. Bank accounts & cash → small choice page (not nested action targets): “Add bank account” → existing bank selection/add flow (`BankAddRoute`, then `AccountAddRoute(bankId)`); “Add cash or wallet” → `CashAddRoute`. Existing banks can be selected, never duplicated automatically.
3. Investments → `InvestmentAddRoute` (existing broker/account creation), not cash conversion or a brokerage sign-in.

Eligibility: local vault is confirmed empty of every user-created record (including bank shells, accounts, gold, cash, investments, notes and zakat records/declarations), and guide has neither been dismissed nor completed. Quotes/preferences do not count. Use a per-vault local flag; no identity, analytics or tracking. Mark completed on first successful record save/import, never on opening or cancelling an editor. Do not show during unknown/loading reads. Restore/import with records suppresses it immediately. Dismissal persists across restart; deleting all records later does not bring the guide back. A genuinely new reset vault gets a fresh flag. After dismissal or subsequent emptying, use existing `DaftarEmptyState` with the short copy below and “Add an asset” to the normal asset chooser. No undo snackbar or reminder to finish the guide.

Add the optional gold hint as ordinary supporting text under the first gold editor heading, before the karat selector. Show while this vault has never saved gold, including repeat visits after cancellation. Hide once gold has been saved; never show an overlay. Use no network-derived example value.

Acceptance: every choice opens the proper existing flow; cancel leaves guide eligible; save/import removes it on next Home render. Dismiss/restart stays dismissed, delete-all stays dismissed, reset produces a new guide. In Arabic/English at 200%, rows wrap, actions remain ≥48dp and focus follows visual order. No pre-PIN content, tracking, notifications or portfolio values in accessibility announcements.

## Exact AR / EN copy

Keys below are proposed resource IDs; reuse existing IDs when semantics match. Visible numbers/units use Rubik and LTR isolates in Arabic. `{time}` is localized source date + time (include date, never imply “now”); `{duration}` uses platform plural resources, not string concatenation. No truncation of these messages.

| Key / use | العربية | English |
| --- | --- | --- |
| p25_pin_step_create | الخطوة ⁦1⁩ من ⁦3⁩ | Step 1 of 3 |
| p25_pin_step_confirm | الخطوة ⁦2⁩ من ⁦3⁩ | Step 2 of 3 |
| p25_pin_create_title | رمز خاص بدفترك | A PIN for your notebook |
| p25_pin_confirm_title | أكّد رمزك | Confirm your PIN |
| p25_pin_unlock_title | افتح دفتر | Open Daftar |
| p25_pin_create_body | اختر من ⁦6⁩ إلى ⁦16⁩ رقمًا، مختلفة عن رمز هاتفك. | Choose 6–16 digits, different from your phone PIN. |
| p25_pin_confirm_body | أدخل الرمز نفسه مرة ثانية. | Enter the same PIN again. |
| p25_pin_unlock_body | أدخل رمز دفتر للمتابعة. | Enter your Daftar PIN to continue. |
| p25_pin_mismatch | الرمزان غير متطابقين. أعد إدخال الرمز. | PINs do not match. Enter the PIN again. |
| p25_pin_incorrect | الرمز غير صحيح. حاول مرة أخرى. | Incorrect PIN. Try again. |
| p25_pin_backoff | انتظر {duration} قبل المحاولة. | Wait {duration} before trying again. |
| p25_pin_ready (announcement) | يمكنك المحاولة الآن. | You can try again now. |
| p25_pin_open_failed | تعذّر فتح دفتر. حاول مرة أخرى. | Could not open Daftar. Try again. |
| p25_pin_biometric_unavailable | البصمة غير متاحة الآن. استخدم رمز دفتر. | Biometrics are unavailable. Use your Daftar PIN. |
| p25_pin_biometric_locked | البصمة متوقفة مؤقتًا. استخدم رمز دفتر. | Biometrics are temporarily locked. Use your Daftar PIN. |
| p25_pin_biometric_disabled | افتح دفتر بالرمز، ثم فعّل البصمة من الإعدادات. | Unlock with your PIN, then enable biometrics in Settings. |
| p25_pin_busy | جارٍ فتح دفتر… | Opening Daftar… |
| p25_pin_creating | جارٍ إنشاء دفترك… | Creating your notebook… |
| p25_pin_continue | متابعة | Continue |
| p25_pin_confirm | تأكيد | Confirm |
| p25_pin_unlock | فتح | Unlock |
| p25_pin_restart | اختيار رمز آخر | Choose another PIN |
| p25_pin_forgot | نسيت الرمز؟ | Forgot PIN? |
| p25_pin_security | لا يمكن استعادة الرمز إذا نسيته. | Your PIN cannot be recovered if forgotten. |
| p25_price_waiting | في انتظار الأسعار | Waiting for prices |
| p25_price_unavailable | الأسعار غير متاحة الآن. يمكنك إضافة أصولك. | Prices are unavailable right now. You can still add assets. |
| p25_price_retained | تعذّر تحديث الأسعار. نستخدم آخر أسعار متاحة. | Could not update prices. Using the last available prices. |
| p25_price_user_failed | تعذّر تحديث الأسعار الآن. حاول لاحقًا أو أدخل سعرًا يدويًا. | Could not update prices now. Try later or enter a manual price. |
| p25_price_rate_limit | التحديث غير متاح مؤقتًا. حاول لاحقًا. | Refresh is temporarily unavailable. Try later. |
| p25_price_refresh | تحديث الأسعار | Refresh prices |
| p25_price_refreshing | جارٍ تحديث الأسعار… | Updating prices… |
| p25_price_cached | محفوظ · {time} | Cached · {time} |
| p25_price_manual | يدوي · {time} | Manual · {time} |
| p25_price_subtotal | مجموع الأصول المتاح تقييمها | Available subtotal |
| p25_price_manual_action | إدخال سعر يدوي | Enter a manual price |
| p25_guide_title | ابدأ بما لديك | Start with what you have |
| p25_guide_body | أضف أول أصل، وأكمل الباقي وقتما تحب. | Add your first asset. Add the rest whenever you like. |
| p25_guide_gold_title | أضف ذهبك | Add your gold |
| p25_guide_gold_body | اختر العيار وسجّل الوزن. | Choose the karat and enter the weight. |
| p25_guide_money_title | حساباتك البنكية ونقدك | Your bank accounts and cash |
| p25_guide_money_body | سجّل حسابًا أو نقدًا أو محفظة. | Record an account, cash or a wallet. |
| p25_guide_investment_title | أضف استثماراتك | Add your investments |
| p25_guide_investment_body | سجّل القيمة الحالية لحسابك. | Enter your account’s current value. |
| p25_guide_dismiss | إخفاء الدليل | Hide guide |
| p25_guide_bank_action | إضافة حساب بنكي | Add bank account |
| p25_guide_cash_action | إضافة نقد أو محفظة | Add cash or wallet |
| p25_empty_title | دفترك جاهز | Your notebook is ready |
| p25_empty_body | أضف أصلًا عندما تكون مستعدًا. | Add an asset when you’re ready. |
| p25_empty_action | إضافة أصل | Add an asset |
| p25_gold_hint | العيار هو درجة نقاء الذهب؛ سجّل وزنه بالجرام دون الأحجار. | Karat is gold purity; enter its weight in grams without stones. |

Duration resources: AR zero `لحظات`, one `ثانية واحدة`, two `ثانيتين`, few `⁦{n}⁩ ثوانٍ`, many `⁦{n}⁩ ثانية`, other `⁦{n}⁩ ثانية`; EN one `1 second`, other `{n} seconds`. Use the actual security countdown, never alter lock duration for layout. Existing field labels, keypad accessibility labels, table headings, source attribution and navigation labels remain unchanged.
