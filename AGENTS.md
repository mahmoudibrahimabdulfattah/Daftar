# Working on Daftar

Daftar (دفتر) is a Kotlin Multiplatform + Compose Multiplatform private wealth notebook for Android, iOS and desktop
(macOS, Windows).
It tracks gold, bank accounts, cash and zakat records, values them at live Egyptian prices, and keeps everything
encrypted on the device behind an app-specific PIN. Arabic (RTL) is the primary language; English is fully supported.

## Workflow

- One phase = one branch = one PR. Open the PR with a short description (what the task was, how it was done),
  squash-merge into `master`, delete the branch locally and on the remote, pull `master`, then start the next phase.
- Commits and PRs never mention AI tools or assistants and carry no `Co-authored-by` trailers.
- Never commit real balances, keystores, `keystore.properties`, signing passwords or Firebase config.
- Never commit `androidApp/google-services.json`, `iosApp/iosApp/GoogleService-Info.plist` or
  `desktopApp/firebase-desktop.properties`. Firebase config is
  ignored, variant-specific, and installed locally or by CI from a secret. Without it the build succeeds and cloud
  backups are unavailable.

## Verify before every PR

```bash
./gradlew spotlessCheck testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug :androidApp:assembleRelease
./gradlew desktopTest :desktopApp:compileKotlinJvm
./gradlew iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' -derivedDataPath build/xcode build
```

Device tests (`connectedAndroidDeviceTest`, `:androidApp:connectedDebugAndroidTest`) run on an emulator or device
and are required for security, Room and lifecycle changes. Run the app on the Android emulator (debug package
`com.mk.daftar.debug`) and, for UI work, on the iOS simulator "iPhone 18 Pro".

Desktop: `./gradlew :desktopApp:run` starts a debug build (separate "Daftar Debug" data directory). For UI work run
`./gradlew :desktopApp:jvmTest`, which drives the real app offscreen with synthetic input and writes screenshots to
`desktopApp/build/screens` (the window itself is excluded from screen capture).

Release builds are minified with R8. Any new reflective, JNI or serialized code needs a rule backed by the release
mapping (`androidApp/build/outputs/mapping/release`), not a package-wide keep.

## Module map

- `build-logic`: convention plugins for every module.
  - `daftar.kmp.library`: KMP library (Android, iosArm64, iosSimulatorArm64, desktop JVM). Warnings are errors.
  - `daftar.kmp.compose`: Compose Multiplatform with module-scoped resources (`com.mk.daftar.<module>.resources`).
  - `daftar.kmp.feature`: a screen feature (Compose, ViewModel, navigation, Koin, core model and design system).
  - `daftar.android.application`: the Android shell.
- `core:common`, `core:model`: primitives, exact decimals, records. No platform code, except `core:common`'s desktop
  source set, which owns the desktop app's private directory (`DesktopAppDirectories`).
- `core:domain`: pure valuation rules (gold by karat at the goldsmith sell price, USD to EGP, totals), the civil Hijri
  calendar and zakat rules, and use cases.
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
- `androidApp`, `iosApp`, `desktopApp`: thin platform shells. Release never enables developer routes. A desktop build
  is debug unless built with `-Pdaftar.desktop.release=true`; the flag is compiled in.

## Security rules

- The database key is derived from the PIN together with a platform-keystore secret. The PIN is never a UI-only gate;
  wrong PINs are backed off persistently.
- Release requires hardware-backed keys. Software-backed keys are accepted only in debug builds, which show a
  persistent banner. The developer entries (design catalog, shell preview) exist only in debug builds.
- `FLAG_SECURE` on every Android window. Lock on background and on foreground inactivity; cover the app in recents.
- No balances, PINs or notes in logs, notifications, the clipboard, semantics or screenshots of released surfaces.
  The one exception is the recovery key, copied only on the owner's tap: marked sensitive, device-only, cleared after
  60 seconds.
- Keychain items are device-only. The database and staged backups are excluded from iCloud and Android backup,
  and transfer is disabled. Staged export files live in a no-backup area and are deleted after use.
- Cleartext traffic is prohibited. The only merged permissions are `INTERNET` and `USE_BIOMETRIC`.
- Changing the PIN rewraps the data key; the database is not re-encrypted.
- Recovery key (optional, created in Settings after the current PIN): 18 random bytes + a CRC-16, shown once as 32
  Crockford base32 characters and never stored. Only its X25519 public key is kept, in a recovery slot sealed by the
  same hardware key as the PIN envelope, beside the DEK sealed to that key. A forgotten PIN is replaced on this device
  with the key (device + key are both required); a new key supersedes the old one. Backups made while a key exists
  use container version 2 and open with the password or the key.
- Biometric auto-prompt (owner-verified on a real phone, 13 Oct 2026; do not regress): with biometrics enabled the
  system prompt appears by itself once per lock episode, on cold launch AND when the app returns from the background.
  The auth view model outlives sessions, so every lock after a successful unlock is a new episode (`openedSinceLock`),
  and a request queued while the app was in the background survives the resume recheck. Cancel, failure, backoff,
  recovery and forgot-PIN never re-prompt by themselves. While the biometric unlock runs the gate keeps the lock surface
  (no "opening" screen before the prompt), and the Android splash stays until the lock surface is ready (capped at
  1.5 s), so the prompt follows the splash at once. Guarded by `AuthViewModelTest` (cold launch, queued request survives
  resume, every lock after an unlock) and `VaultGateModelTest`.

## Desktop (macOS, Windows)

- Layout: phones never take a desktop branch. Wide desktop arrangements apply only on desktop AND width ≥600dp
  (`docs/design/phase-28-desktop.md`); the mobile 600/840 behaviour is unchanged.
- Storage: one private directory per build (`~/Library/Application Support/Daftar` on macOS, excluded from Time Machine;
  `%LOCALAPPDATA%\Daftar` on Windows), owner-only. Security records use the same atomic file stores as Android.
- Database: SQLite3 Multiple Ciphers in its SQLCipher v4 scheme, bound through JNA and keyed with `sqlite3_key` and the
  raw `x'<hex>'` key from native memory that is zeroed after the call. macOS builds the official amalgamation from
  source into a universal dylib; Windows uses the project's official x64 DLL. Both archives are pinned by SHA-256.
- Keys: release requires a hardware protector (Secure Enclave on macOS, TPM on Windows) and refuses with
  `UnsupportedHardware` otherwise. Debug builds may use a software key file and show the debug banner.
- Privacy: every window is excluded from screen capture (`NSWindowSharingNone`, `WDA_EXCLUDEFROMCAPTURE`). Minimizing,
  hiding the app, display or system sleep and session deactivation cover and lock; Cmd+L (macOS) or Ctrl+L (Windows)
  locks at once.
- Cloud: Google sign-in in the system browser (PKCE, loopback), a Firebase session in memory only, and Firestore over
  REST under the same rules. The project values come from the git-ignored `desktopApp/firebase-desktop.properties`;
  without it cloud backups are unavailable. Firestore App Check enforcement stays off for desktop (see `docs/firebase`).

## Backup and restore

- An export is written as `.dftar`: a fixed binary header (Argon2id KDF, XChaCha20-Poly1305 AEAD, fresh salt and nonce)
  around a versioned JSON payload. The header is authenticated; any change fails to decrypt.
- The backup password is separate from the PIN and must be at least 12 characters.
- Container version 1 (password only) is frozen and still written when no recovery key exists. Version 2 encrypts the
  payload under a random backup key, wrapped under the password key and sealed to the recovery public key.
- Corrupt, truncated or wrong-password files fail authentication and are rejected; the header is authenticated.
- Recovery limits: without a recovery key, a lost backup password or a forgotten PIN with no backup means the data
  cannot be recovered. A recovery key opens only backups made after it was set up.
- Optional cloud backups (Android and desktop, Google sign-in) store the same `.dftar` file in Firestore on the Spark plan:
  three fixed slots, a 768 KiB ciphertext cap and no Storage, Functions, Analytics or Crashlytics. Sign-in never
  unlocks the vault, and restoring still requires the backup password. Local OS backup stays disabled.
  The rules and runbook live in `docs/firebase`; deploy them only with `firebase deploy --only firestore`.

## Design system (`core:designsystem`)

- Fully custom theme: `DaftarTheme` (colors, typography, shapes) with companion accessors. Material 3 receives a
  matching scheme only so Material components stay on-brand. Brass is reserved for gold.
- Containers use the Compose Styles API (`DaftarStyles`, `Modifier.styleable`, a `style: Style = Style` parameter on
  every styled component). Text and icon colour come from the component via `ProvideContent`.
- Components are stateless: callbacks and presentation values only. No repositories, security operations or
  navigation ownership in `core:designsystem`.
- Fonts: Alexandria for Arabic text, Rubik for English and every numeric run (tabular figures). Both are bundled
  variable fonts with 400/500/600/700 weights, and their OFL texts ship in `feature:settings` resources.
- Bank identities come from `core/model/src/bankDirectory/banks.json` (country-namespaced IDs such as `eg.nbe`). Official
  marks live in `docs/design/bank-marks/` (original bytes + `manifest.json` with source, date and hashes) and are
  rendered by `tools/bank-marks/render.py` into `core/designsystem/src/officialBankArtwork`. `-Pdaftar.bankArtwork=official`
  (default) packages them; `monogram` packages none and shows brand-coloured initials. Run `./gradlew clean` when
  switching modes; the build fails rather than ship stale marks. Never recolour, invert, stretch or crop a mark.
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
