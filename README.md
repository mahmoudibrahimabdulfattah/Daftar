# Daftar · دفتر

A private notebook for what you own. Gold, bank accounts, cash and zakat records in one place,
valued at live Egyptian prices, encrypted on the device and opened only with your own PIN.

Built with Kotlin Multiplatform and Compose Multiplatform.

## Modules

| Module | What it holds |
|---|---|
| `androidApp` | Android application shell (`com.mk.daftar`) |
| `iosApp` | Xcode project; embeds the `Shared` framework |
| `shared` | App entry point, DI wiring and navigation shared by both platforms |
| `build-logic` | Convention plugins (`daftar.kmp.library`, `daftar.kmp.compose`, `daftar.kmp.feature`, …) |

`core:*` and `feature:*` modules are added phase by phase.

## Build

```bash
./gradlew spotlessCheck :androidApp:assembleDebug testAndroidHostTest
```

iOS (on macOS): open `iosApp/iosApp.xcodeproj` in Xcode and run, or

```bash
./gradlew iosSimulatorArm64Test
```

The design reference is [`docs/design/daftar.html`](docs/design/daftar.html).
