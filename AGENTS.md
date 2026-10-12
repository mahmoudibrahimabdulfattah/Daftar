# Working on Daftar

Daftar (دفتر) is a Kotlin Multiplatform + Compose Multiplatform private wealth notebook for Android and iOS.
It tracks gold, bank accounts, cash and zakat records, values them at live Egyptian prices, and keeps everything
encrypted on the device behind an app-specific PIN. Arabic (RTL) is the primary language; English is fully supported.

## Workflow

- One phase = one branch = one PR. Open the PR with a short description (what the task was, how it was done),
  squash-merge into `master`, delete the branch locally and on the remote, pull `master`, then start the next phase.
- Commits and PRs never mention AI tools or assistants and carry no `Co-authored-by` trailers.
- Never commit real balances, keystores, `keystore.properties`, signing passwords or Firebase config.

## Verify before every PR

```bash
./gradlew spotlessCheck testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug :androidApp:assembleRelease
./gradlew iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' -derivedDataPath build/xcode build
```

Device tests (`connectedAndroidDeviceTest`, `:androidApp:connectedDebugAndroidTest`) run on an emulator or device
and are required for security, Room and lifecycle changes. Run the app on the Android emulator (debug package
`com.mk.daftar.debug`) and, for UI work, on the iOS simulator "iPhone 18 Pro".

Release builds are minified with R8. Any new reflective, JNI or serialized code needs a rule backed by the release
mapping (`androidApp/build/outputs/mapping/release`), not a package-wide keep.

## Module map

- `build-logic`: convention plugins for every module.
  - `daftar.kmp.library`: KMP library (Android, iosArm64, iosSimulatorArm64). Warnings are errors.
  - `daftar.kmp.compose`: Compose Multiplatform with module-scoped resources (`com.mk.daftar.<module>.resources`).
  - `daftar.kmp.feature`: a screen feature (Compose, ViewModel, navigation, Koin, core model and design system).
  - `daftar.android.application`: the Android shell.
- `core:common`, `core:model`: primitives, exact decimals, records. No platform code.
- `core:domain`: pure valuation rules (gold by karat at the goldsmith sell price, USD to EGP, totals) and use cases.
  No I/O, no clock; fully unit-tested. Prices fall back live, then last cached (with its time), then manual.
- `core:security`: PIN and password crypto, backoff, platform key storage (Android Keystore, iOS Keychain and
  Secure Enclave). Software keys are refused in release.
- `core:database`, `core:database-driver`: encrypted SQLite (SQLCipher) with Room. Schema and triggers are versioned.
- `core:datastore`, `core:data`, `core:network`: non-sensitive preferences, repositories, price services.
- `core:backup`: the `.dftar` encrypted export and restore format; the format is frozen (see `BackupFormat`).
- `core:designsystem`, `core:ui`, `core:navigation`: theme, components, shared shell screens, typed routes.
- `feature:*`: auth, home, assets, banks, cash, gold, journal (notes, zakat), prices, backup, settings. Features
  never depend on each other or on `core:data`.
- `shared`: the app entry point, Koin wiring, the vault gate, lifecycle coordination and platform adapters.
- `androidApp`, `iosApp`: thin platform shells. Release never enables developer routes.

## Security rules

- The database key is derived from the PIN together with a platform-keystore secret. The PIN is never a UI-only gate;
  wrong PINs are backed off persistently.
- Release requires hardware-backed keys. Software-backed keys are accepted only in debug builds, which show a
  persistent banner. The developer entries (design catalog, shell preview) exist only in debug builds.
- `FLAG_SECURE` on every Android window. Lock on background and on foreground inactivity; cover the app in recents.
- No balances, PINs or notes in logs, notifications, the clipboard, semantics or screenshots of released surfaces.
- Keychain items are device-only. The database and staged backups are excluded from iCloud and Android backup,
  and transfer is disabled. Staged export files live in a no-backup area and are deleted after use.
- Cleartext traffic is prohibited. The only merged permissions are `INTERNET` and `USE_BIOMETRIC`.
- Changing the PIN rewraps the data key; the database is not re-encrypted.

## Backup and restore

- An export is written as `.dftar`: a fixed binary header (Argon2id KDF, XChaCha20-Poly1305 AEAD, fresh salt and nonce)
  around a versioned JSON payload. The header is authenticated; any change fails to decrypt.
- The backup password is separate from the PIN and must be at least 12 characters.
- Corrupt, truncated or wrong-password files fail authentication and are rejected; the header is authenticated.
- Recovery limits: a lost backup password or a forgotten PIN with no backup means the data cannot be recovered.

## Design system (`core:designsystem`)

- Fully custom theme: `DaftarTheme` (colors, typography, shapes) with companion accessors. Material 3 receives a
  matching scheme only so Material components stay on-brand. Brass is reserved for gold.
- Containers use the Compose Styles API (`DaftarStyles`, `Modifier.styleable`, a `style: Style = Style` parameter on
  every styled component). Text and icon colour come from the component via `ProvideContent`.
- Components are stateless: callbacks and presentation values only. No repositories, security operations or
  navigation ownership in `core:designsystem`.
- Fonts: Alexandria for Arabic text, Rubik for English and every numeric run (tabular figures). Both are bundled
  variable fonts with 400/500/600/700 weights, and their OFL texts ship in `feature:settings` resources.
- The catalog (`DesignCatalog`) is the living reference: every component and state, in light/dark and Arabic/English.
  Keep it current when a component changes.

## Style

- ktlint (android_studio style) and Compose rules via Spotless; run `./gradlew spotlessApply`.
- Composables: `modifier: Modifier = Modifier` directly after the required parameters, then defaulted parameters.
  Lambda parameters in present tense (`onSave`, not `onSaved`); read changing lambdas inside effects through
  `rememberUpdatedState`.
- Arabic text: isolate numbers and units with LTR isolates (`⁦…⁩`), use plurals, write the brand «دفتر».
- Follow Google's Android skills for edge-to-edge, adaptive layouts, Navigation 3 and security.
- UI and UX decisions belong to the design lead. The approved design is `docs/design/daftar.html`; follow it exactly.
