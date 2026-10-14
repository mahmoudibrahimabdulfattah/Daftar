# Phase 27 — Prices at a glance

Design decision, 14 October 2026. Mode: Operate. Authority: `daftar.html`, the owner's Arabic phone review, and the existing quote-selection model. This phase changes presentation only. Implement from the `p27-` frames; older Prices/Home frames remain historical. This supersedes Phase 25's instructions to retain the old strip geometry, Details button, repeated tags and source structure; its refresh, privacy and selection rules remain binding.

## Decision and scope

Open Prices to read six gold prices and one USD rate. One table, one USD row, one quiet board freshness paragraph, one **Sources and details** entry. No per-cell provenance, routine tags, source URLs, source heading, warning wall or manual button on the board. Keep the shortened valuation note as plain muted text: the sell/buy distinction affects the owner's interpretation of wealth. Do not box it.

Use a full-height child page for Sources and details, not a bottom sheet: seven quote histories and manual editing need scrolling and reliable 200% text. One tap from Prices; two from Home. Back restores the originating board's scroll and focus; editor return restores the details page. No nested accordions or secondary source sheet. Required ExchangeRate-API credit remains a visible, actionable link on this page whenever the effective quote or the source beneath a manual override uses that provider. Home's whole-card route must lead to that same entry, including offline/cached states. Use the quote's attribution URL, or existing `EXCHANGE_RATE_ATTRIBUTION_URL` fallback. Do not require refresh to reveal attribution.

**Remove the global gold estimate block entirely**, including its formula and unavailable-data explanation, from both board and details. Current `XauPanel` has no usable quote in `PricesBoard`; an empty educational block does not help this task. Do not add a feed, calculate a substitute, or change valuation. This does not remove the *USD mid-market fallback*: that remains an effective rate and must say “Market estimate”.

Only this spec and the HTML reference change in this design phase. No Kotlin, resource, provider, security or domain changes are part of this deliverable.

## Prices structure and dimensions

Use existing `DaftarTheme` and Compose Styles containers, with stateless presentation inputs. Keep the normal shell and bottom navigation. `DaftarTopBar`: back, Prices, refresh with a 48dp target. Body is one vertical scroller, centered at the existing content maximum width, horizontal `md=16`, bottom `xl=24` plus safe inset.

1. Gold prices (`sectionHeading`), `xxs=4` to “EGP / gram”; `sm=12` to table.
2. Table logical columns: **karat | sells to you | buys from you**. RTL places karat at the right; do not reverse sell and buy. Column weights 1:1.6:1.6; `xs=8` gutters, zero asymmetric cell padding. Headers use `caption`, values `quoteNumber`, karat `ledgerTitle`. Use tabular Rubik, two decimals, end-align numeric columns and align each header with its column. Sell values medium brass, buy values ink, missing dashes muted. Header minimum 48dp, rows minimum 56dp with `sm=12` vertical padding and 1dp line dividers; grow to fit text.
3. `xs=8` to the one-line valuation note, natural wrapping allowed. No tinted panel.
4. `xl=24` to USD: a single flat ledger row, minimum 72dp, line divider below. Leading title “US dollar”, supporting unit “EGP / USD”; trailing value in ink at the same numeric size as gold, no brass. Under the title show the short FX basis caption: Banque Misr transfer buy, market estimate, manual, or unknown basis. This is meaning, not a timestamp or a second freshness line.
5. `sm=12` to ONE muted `fineText` freshness paragraph covering all seven visible cells. It may wrap; “one line” means one information element, never a truncation requirement.
6. `xs=8` to Sources and details: full-width plain navigation row, minimum 48dp, primary text and mirrored trailing chevron. No URL preview. At default text, all seven quotes and this entry fit the typical phone viewport without scrolling.

A user refresh failure/partial result may add ONE unboxed Context sentence between freshness and details. Never stack missing, stale, provider and retry panels. The refresh control is the only board retry action; manual entry is always reachable through details, including initial missing state.

## Board summary and exception rules

Do not reuse Home's single aggregate badge as if it described every cell. Derive a presentation summary from **all seven displayed selections**, including remote-only buy cells; `PricesBoard.stale` and `missing` currently cover sell/USD only and cannot drive this UI alone. Home summarizes only its four displayed prices with the same algorithm.

- Preserve effective sell/USD selection, pinned override behavior, remote-only buys, provenance and freshness from `PricesSummary.kt`. No inference from another karat or side. `sourceSell` / `usdSource` belong only in details.
- Board norm is the most frequent status among available **remote** selections, using existing `priceBadge()`; ties resolve CLOCK_UNCERTAIN, STALE, CACHED, LIVE in that order. If there are no remote selections, use the most frequent available manual freshness status, then MANUAL. If nothing exists, show waiting/unavailable copy and no timestamp.
- Every quote whose status differs from the norm gets one small text marker directly below its value (`caption`, `xxs=4`, muted, no pill/background). Manual origin must also remain explicit whenever any displayed quote is remote, even if stale/clock status masks it in `priceBadge()`. Combine into “Manual · old” or “Manual · time uncertain” in one marker. All-manual boards say manual in the summary, with only freshness exceptions marked: use “Manual prices · old” / “أسعار يدوية · قديمة” for a stale norm, or “Manual prices · time uncertain” / “أسعار يدوية · الوقت غير مؤكّد” for clock uncertainty. Missing is always `—`, announced “Unavailable”; no missing badge. No LIVE/CACHED tag on every cell.
- Summary template follows the norm; append the localized “Some prices differ” clause if any status/origin exception exists, and “Some prices unavailable” if any cells are missing. At most one paragraph. The markers provide the specifics. If all missing, only waiting/unavailable text.
- Summary timestamp is the **oldest known `referenceAt` among all displayed available selections**, never the last request time or most recent quote time. Say “Oldest update” explicitly, because gold at 14:36 and USD at 14:03 are not one synchronized update. Include day/month/time in Cairo; include year when not current. Never stamp a refresh failure as fresh. Unknown reference times replace the timestamp clause with “Update time unavailable”; clock-uncertain norm suppresses the time entirely. Provider names live in details, avoiding an iSagha label that falsely attributes USD.
- Stale is decided by existing freshness windows, not a new UI timeout. Normal stale board: one “Old prices” summary, no duplicate stale markers. Fresh exceptions get “Updated” markers. Clock uncertainty is distinct from stale. Mixed fresh/cached boards remain truthful through exception markers and oldest time.
- Initial local read: static table/strip placeholders with “Waiting for prices”; no shimmer or spinner. All absent after budget exhaustion: unavailable copy. A vault read failure uses the existing load error, never pretends it is missing market data. Locked state exposes no session surface.
- Automatic launch/resume/network retry stays silent per Phase 25. Retain drawn quotes during refresh. Only a user refresh may spin, inside its existing refresh target; no global or per-cell progress. Joining automatic work follows Phase 25 coalescing/cooldowns. User outcome announces once politely, without price amounts. Automatic updates do not announce or steal focus.

## Home quote card

Replace `QuoteSection`'s detached caption/tag, asymmetric `DaftarQuoteStrip` padding, timestamp-plus-Details footer, and detached USD caption with one contained button. Order inside: title + trailing chevron; unit caption; equal price columns; freshness; FX basis caption. Surface, 1dp line border, existing 12dp quote-strip radius, `md=16` padding; `sm=12` above and below prices, `xxs=4` between label/value and footer lines. Chevron gets its own header slot and never steals width from a price column.

Four equal `weight(1f)` columns, all centered, identical padding, `xs=8` gutters, logical order 24K, 21K, 18K, USD (right to left in Arabic). Values medium Rubik, default `quoteNumber`; all ink here to preserve quiet Home hierarchy. Labels muted; gold meaning is carried by labels, not mandatory brass. Gold values are sell prices; USD is EGP per dollar. Title “Gold and dollar”; caption makes both units explicit. No per-column dividers or lopsided first-column padding.

**Delete the “محفوظ” pill**. Cached state is the single dated footer sentence inside the card. Keep “Dollar: Banque Misr · transfer buy” as the second footer line, also inside the card, aligned to logical start with title and freshness. Replace it for manual/mid-market/unknown basis; omit if USD missing. This prevents the caption looking like an unrelated item. No nested button, link or separate Details text. Exception markers use the same rules as Prices.

Whole surface has one click handler to Prices, minimum 48dp target (natural content is taller), button role and exact accessibility label below. Merge descendants into one focus stop. Supply a state description containing the four public values with gold sell/unit labels, each exception and board freshness; do not lose values by replacing all semantics with the action label. Do not include private portfolio amounts. Chevron is decorative. Visible focus/pressed treatment uses primary/soft; support Enter/Space, TalkBack and VoiceOver activate. Returning from Prices restores card focus.

## Details page

Top bar “Sources and details”, back only; scroll body `md=16`. First item: full-width outline “Enter a manual price”, minimum 48dp, existing manual editor/selection flow; available even when every quote is missing. Do not enable manual buy quotes or change fallback/pinned semantics.

Then source links, once per distinct provider, human-readable names only, ≥48dp targets. iSagha and Banque Misr open existing provenance URLs; never display raw URLs. Show required “Rates by ExchangeRate-API” attribution link when applicable, including when cached/offline. Link semantics state external destination. No invented provider if provenance absent.

Then seven quote records in table order (24K sell, buy; 21K sell, buy; 18K sell, buy; USD). Each shows its exact effective value, unit, provider, origin (live/cached/manual fallback/manual override), freshness, source time and last successful check/reference time. Include full Cairo date/year/time and zone; retain available seconds. Unknown source times say “Not provided”; never substitute fetched time as source time. Manual records label reference time “Entered”, omit meaningless provider publish time, and distinguish fallback from pinned override. A pinned record also shows the available remote source value/provenance under “Source price”; if absent show “Unavailable”. Metadata is grouped but each cell's time must be recoverable even when identical. No private `MissingDetails` weights, counts or subtotals on either Prices page; valuation warnings/subtotals remain owned by Home/Assets under existing privacy rules.

USD record includes the short basis plus explanatory copy below. Source links remain visible without an accordion. A provider switch updates link, credit and basis together. No global estimate section.

## Accessibility, localization and adaptive behavior

Preserve Alexandria Arabic and Rubik English/numerals; isolate each numeric/unit run using LTR isolates in native text and `bdi dir=ltr` in HTML. Localize dates and labels; source proper names remain LTR. Never reverse a numerical string. Market prices stay public when values are hidden; private balances, weights and notes remain absent from these new surfaces and their semantics.

At 200% or whenever measurement proves a number/header cannot fit: Home reflows to **two equal columns, two rows**, preserving 24K → 21K → 18K → USD reading order. This is the accessibility exception to four equal columns; do not shrink fonts or horizontally scroll. Prices replaces the tight three-column visual with three karat groups, each heading followed by two full-width label/value rows (sell, then buy). A label/value row may stack naturally. Preserve table/collection relationships in semantics. USD and details records stack their label/value naturally. No fixed text heights, ellipsis or clipped numeric runs; very large valid numbers use the stacked layout before they overflow. At 320dp and 200% allow vertical scrolling through all content and safe insets. Headings remain headings; numeric speech includes karat, side, currency/unit and status. Missing never reads as zero. Text markers ensure status is not color-only.

Use approved paper/surface/ink/muted/primary/line tokens in both themes. Brass only for gold sell values; neutral status text even for USD stale/manual. Focus ring primary, not brass. All interactive controls ≥48dp, body contrast ≥4.5:1. No new animation; respect reduced motion.

## Exact AR / EN copy

Proposed keys; reuse current resources when semantics match. `{stamp}` is localized Cairo day/month/time (and year when needed); `{exact}` includes year, time, seconds when stored and zone. Isolate dynamic numeric runs, dates and Latin provider names at rendering. Arabic examples below include actual LTR isolates. Summary clauses concatenate with ` · ` in the order status → oldest time → exceptions → unavailable; use localized templates, not English word-order assumptions.

| Key / use | العربية | English |
| --- | --- | --- |
| title | الأسعار | Prices |
| gold | أسعار الذهب | Gold prices |
| gold_unit | ج.م / جرام | EGP / gram |
| karat | العيار | Karat |
| sell | يبيع لك | Sells to you |
| buy | يشتري منك | Buys from you |
| valuation_note | نقيّم ذهبك بسعر «يبيع لك». | Your gold is valued at “Sells to you”. |
| usd | الدولار الأمريكي | US dollar |
| usd_unit | ج.م / دولار | EGP / USD |
| home_title | الذهب والدولار | Gold and dollar |
| home_units | الذهب: ج.م / جرام · الدولار: ج.م / دولار | Gold: EGP / gram · Dollar: EGP / USD |
| home_action (semantics) | أسعار الذهب والدولار، فتح التفاصيل | Gold and dollar prices, open details |
| details | المصادر والتفاصيل | Sources and details |
| refresh | تحديث الأسعار | Refresh prices |
| refreshing (user only) | جارٍ تحديث الأسعار… | Updating prices… |
| norm_live | أسعار محدّثة | Updated prices |
| norm_cached | أسعار محفوظة | Saved prices |
| norm_manual | أسعار يدوية | Manual prices |
| norm_stale | أسعار قديمة | Old prices |
| norm_clock | تعذّر التحقق من وقت الأسعار | Price times could not be verified |
| oldest | أقدم تحديث: {stamp} | Oldest update: {stamp} |
| unknown_time | وقت التحديث غير متاح | Update time unavailable |
| exceptions | بعض الأسعار مختلفة | Some prices differ |
| partial_missing | بعض الأسعار غير متاحة | Some prices unavailable |
| marker_live | محدّث | Updated |
| marker_cached | محفوظ | Saved |
| marker_manual | يدوي | Manual |
| marker_stale | قديم | Old |
| marker_clock | الوقت غير مؤكّد | Time uncertain |
| marker_manual_stale | يدوي · قديم | Manual · old |
| marker_manual_clock | يدوي · الوقت غير مؤكّد | Manual · time uncertain |
| unavailable (dash semantics) | غير متاح | Unavailable |
| waiting | في انتظار الأسعار | Waiting for prices |
| exhausted | الأسعار غير متاحة الآن. | Prices are unavailable right now. |
| failed_retained | تعذّر التحديث. احتفظنا بالأسعار المتاحة. | Could not refresh. Available prices are retained. |
| failed_empty | تعذّر التحديث. حاول لاحقًا أو أدخل سعرًا من التفاصيل. | Could not refresh. Try later or enter a price in details. |
| partial_refresh | تعذّر تحديث بعض الأسعار. | Some prices could not be refreshed. |
| rate_limit | التحديث غير متاح مؤقتًا. حاول لاحقًا. | Refresh is temporarily unavailable. Try later. |
| load_failed | تعذّر قراءة الأسعار. حاول فتح الصفحة مجددًا. | Could not read prices. Try opening the page again. |
| usd_bank | بنك مصر · شراء التحويلات | Banque Misr · transfer buy |
| home_usd_bank | الدولار: بنك مصر · شراء التحويلات | Dollar: Banque Misr · transfer buy |
| usd_estimate | تقدير سوقي | Market estimate |
| home_usd_estimate | الدولار: تقدير سوقي | Dollar: market estimate |
| usd_manual | سعر يدوي | Manual rate |
| home_usd_manual | الدولار: سعر يدوي | Dollar: manual rate |
| usd_unknown | نوع السعر غير متاح | Rate basis unavailable |
| home_usd_unknown | الدولار: نوع السعر غير متاح | Dollar: rate basis unavailable |
| manual_action | إدخال سعر يدوي | Enter a manual price |
| sources_heading | المصادر | Sources |
| quote_details | تفاصيل الأسعار | Quote details |
| isagha_link | ⁦iSagha⁩ | iSagha |
| bank_link | بنك مصر | Banque Misr |
| exchange_credit | أسعار الصرف من ⁦ExchangeRate-API⁩ | Rates by ExchangeRate-API |
| fawaz_link | ⁦Fawaz Ahmed⁩ | Fawaz Ahmed |
| external_hint (semantics) | يفتح موقع {provider} | Opens {provider} website |
| no_source | المصدر غير متاح | Source unavailable |
| provider | المصدر: {provider} | Source: {provider} |
| source_time | وقت المصدر: {exact} | Source time: {exact} |
| checked | آخر تحقق ناجح: {exact} | Last successful check: {exact} |
| entered | أُدخل: {exact} | Entered: {exact} |
| time_absent | غير مذكور | Not provided |
| origin_live | من المصدر | From source |
| origin_cached | نسخة محفوظة | Saved copy |
| origin_fallback | يدوي عند غياب سعر المصدر | Manual fallback |
| origin_override | سعر يدوي مثبّت | Pinned manual override |
| source_price | سعر المصدر | Source price |
| bank_explanation | سعر شراء البنك للتحويلات، مرجع للتقييم وليس وعدًا بسعر التنفيذ. | The bank’s transfer buy rate is a valuation reference, not a promised transaction rate. |
| estimate_explanation | تقدير لسعر السوق، وليس سعر شراء التحويلات لدى البنك. | A market-rate estimate, not the bank’s transfer buy rate. |

Example cached board: `أسعار محفوظة · أقدم تحديث: ⁦14 أكتوبر، 14:03⁩` / `Saved prices · Oldest update: 14 Oct, 14:03`. With a pinned 21K quote add `بعض الأسعار مختلفة` / `Some prices differ`, and only that sell cell shows `يدوي` / `Manual`.

## Reference frames and implementation acceptance

`p27-live`, `p27-cached`, `p27-manual`, `p27-missing`, `p27-stale`, `p27-clock`, `p27-partial`, `p27-refresh`, `p27-details`, `p27-fallback-details`, `p27-home`, `p27-home-missing`, `p27-home-manual`, `p27-board-200`, `p27-home-200`. Gallery language/theme controls apply to all; fixture numbers are illustrative public market prices, never user balances. Details fixtures demonstrate metadata and required attribution, not a new data feed. Refresh preview is explicitly user-triggered; it does not call a provider.

Implementation acceptance (not claimed as native tests run by this design task): all seven prices legible; one board freshness element; no routine cell badges; partial buy missing/stale included in aggregation; mixed timestamps never advertised as a synchronized update. Verify live/cached/manual fallback/pinned/stale/clock/missing and user failure with unequal provider times. Whole Home card opens Prices from every point, one accessibility focus stop and no private values. Verify details/back/manual return, offline source-credit reachability and conditional attribution. Check AR/EN, light/dark, 320dp, 200%, S25 Ultra and iPhone 18 Pro with TalkBack/VoiceOver. Preserve automatic silent refresh, generation guards, hidden-value rules and exact quote selection. Update DesignCatalog during implementation, not in this two-file design phase.

Design-file verification: all 15 frame builders executed for Arabic/English and light/dark; IDs are unique within each rendering and attribution is present. JavaScript and `git diff --check` pass. The mechanical design check flags only the deliberately retained approved cream palette. Local-file preview was blocked by browser URL policy, so visual rendering and native accessibility checks remain unverified.

## Six-line summary

Prices opens with one gold table and one USD row.
One truthful freshness summary replaces repeated provenance.
Only exceptional quote states get small text markers.
Sources, exact times, attribution and manual entry share one details page.
Remove the unused global estimate and its formula.
Home becomes one accessible card with balanced columns and large-text reflow.
