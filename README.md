# Daftar · دفتر

A private notebook for what you own: gold, bank accounts, cash and zakat records in one place, valued at live
Egyptian prices. Everything is stored encrypted on the device and opened with your own PIN, with optional biometric
unlock.

Built with Kotlin Multiplatform and Compose Multiplatform. Arabic (RTL) is the primary language; English is fully
supported.

## Supported platforms

- Android 8.0 (API 26) and later. Release builds require a hardware-backed Keystore.
- iOS 16.0 and later, on iPhone and iPad (portrait). Face ID or Touch ID is optional.

## Modules

| Module | What it holds |
|---|---|
| `androidApp` | Android application shell (`com.mk.daftar`, debug builds `com.mk.daftar.debug`) |
| `iosApp` | Xcode project that embeds the `Shared` framework |
| `shared` | App entry point, DI wiring and navigation shared by both platforms |
| `core:common`, `core:model`, `core:domain` | Shared primitives, records, and pure valuation rules (no I/O, no clock) |
| `core:security` | PIN key derivation, platform key storage, backoff, password crypto (libsodium) |
| `core:database`, `core:database-driver` | Encrypted SQLite (SQLCipher) through Room; the iOS SQLCipher build is compiled from its pinned source |
| `core:datastore`, `core:data`, `core:network` | Non-sensitive preferences, repositories, and the price services |
| `core:backup` | Encrypted export and restore format (`.dftar`) |
| `core:designsystem`, `core:ui`, `core:navigation` | Theme and components, shared screens, and navigation |
| `feature:*` | Auth, home, assets, banks, cash, gold, journal (notes and zakat), prices, backup and settings |
| `build-logic` | Convention plugins for every module |

## Build

Requirements: the JDK in the Gradle toolchain, Android SDK platform 37, and Xcode for iOS.

```bash
./gradlew spotlessCheck testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug
./gradlew :androidApp:assembleRelease
```

iOS (macOS): open `iosApp/iosApp.xcodeproj` in Xcode and run, or:

```bash
./gradlew iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' -derivedDataPath build/xcode build
```

The iOS SQLCipher library is compiled from the official SQLCipher 4.19.0 source. The archive is pinned by SHA-256 and
verified on every build, and it is staged under `~/.gradle/caches/daftar-sqlcipher/` per checkout.

## Release setup

Release signing is optional and never uses the debug key.

1. Create an upload keystore outside the repository.
2. Copy `keystore.properties.example` to `keystore.properties` in the repository root and fill in `storeFile`,
   `storePassword`, `keyAlias` and `keyPassword`. The file and `*.jks` / `*.keystore` are git-ignored.
3. Run `./gradlew :androidApp:assembleRelease`. The output is `androidApp-release.apk` when signed, and
   `androidApp-release-unsigned.apk` when `keystore.properties` is absent. An unsigned APK is not distributable.

The release build is not debuggable, minified and shrunk with R8 (`proguard-android-optimize`). Mapping files are
written to `androidApp/build/outputs/mapping/release/`; archive them with the matching build.

Signing passwords belong in CI secret storage, never in the repository or task output.

## Privacy and security

- The database key comes from the PIN and a platform key that stays in the Keychain (iOS, this device only) or the
  Android Keystore (hardware-backed in release). The PIN is checked against that key, not only in the UI.
- No balances in logs, notifications or the clipboard. The app covers its content in recents and locks when it
  leaves the foreground.
- No cloud backup and no device transfer of app data. Staged and exported files are excluded from backup.
- The only network traffic is to the public price services. The app sends no identifiers and collects no data.

## Backup and recovery

- Backups are explicit exports to a `.dftar` file, encrypted with a separate password of at least 12 characters
  (Argon2id and XChaCha20-Poly1305). The file contains no readable financial data.
- A backup can be restored only with its password. A lost backup password cannot be recovered.
- A forgotten PIN cannot be recovered. Restore an encrypted backup to open the data again.
- Changing the PIN rewraps the data key; the database itself is not re-encrypted.

## Fonts and sources

- Alexandria (Arabic) and Rubik (Latin and numerals) are bundled, under the SIL Open Font License 1.1. Their license
  texts and the third-party notices (SQLCipher, libsodium, JNA and others) are shown in Settings > About.
  Sources: [Alexandria](https://github.com/Gue3bara/Alexandria), [Rubik](https://github.com/googlefonts/rubik).
- The design reference is [`docs/design/daftar.html`](docs/design/daftar.html).
