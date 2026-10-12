# Working on Daftar

Daftar (دفتر) is a Kotlin Multiplatform + Compose Multiplatform private wealth notebook for Android and iOS.
It tracks gold, bank accounts, cash and zakat records, values them at live Egyptian prices, and keeps
everything encrypted on the device behind an app-specific PIN.
Arabic (RTL) is the primary language; English is fully supported.

## Workflow

- One phase = one branch = one PR. Open the PR with a short description (what the task was, how it was done),
  squash-merge into `master`, delete the branch locally and on the remote, pull `master`, then start the next phase.
- Commits and PRs never mention AI tools or assistants and carry no `Co-authored-by` trailers.
- Never commit real balances, keystores or signing passwords. Demo data only.

## Verify before every PR

```bash
./gradlew spotlessCheck testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug
./gradlew iosSimulatorArm64Test
```

Then run the app on the Android emulator (debug package `com.mk.daftar.debug`) and, for UI work, on the iOS simulator (always "iPhone 18 Pro").

## Architecture

- `build-logic` convention plugins own all shared Gradle setup:
  - `daftar.kmp.library` — KMP library (Android + iosArm64 + iosSimulatorArm64), warnings are errors.
  - `daftar.kmp.compose` — adds Compose Multiplatform and module-scoped resources (`com.mk.daftar.<module>.resources`).
  - `daftar.kmp.feature` — a screen feature: Compose, ViewModel, navigation, Koin, plus core model/design system.
  - `daftar.android.application` — the Android shell.
- Dependencies point inward: `feature:*` → `core:domain`/`core:ui`/`core:designsystem` → `core:model`.
  Features never depend on each other or on `core:data`; `shared` wires everything together.
- Valuation rules (gold by karat at the goldsmith sell price, USD → EGP, totals) are pure Kotlin in `core:domain`:
  no I/O, no clock, fully unit-tested. Prices fall back live → last cached (with its time) → manual override.
- Platform code goes in `androidMain` / `iosMain` behind interfaces, injected with Koin.

## Security

- The database key is derived from the app PIN together with a platform-keystore secret; the PIN is never a UI-only gate.
- Lock on background, cover the app in recents, no balances in logs, notifications or the clipboard.
- No cloud backup or device transfer of app data; backups are explicit exports encrypted with a separate password.

## Style

- ktlint (android_studio style) + Compose rules via Spotless; run `./gradlew spotlessApply`.
- Composables: `modifier: Modifier = Modifier` as the first optional parameter; lambda parameters in present tense
  (`onSave`, not `onSaved`); read changing lambdas inside effects through `rememberUpdatedState`.
- Arabic text: isolate numbers/units with LTR isolates (`⁦…⁩`), use plurals, write the brand «دفتر».
- Follow Google's Android skills (`android skills`) for edge-to-edge, adaptive layouts, Navigation 3 and security.
- UI/UX decisions belong to the design lead. The approved design is `docs/design/daftar.html`; follow it exactly.
