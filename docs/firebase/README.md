# Daftar cloud backups: Firebase runbook

Scope: Android and desktop (macOS, Windows), Google sign-in, Firebase Auth and Firestore on the Spark plan. No Storage, Functions,
Analytics, Crashlytics or billing account. Backups are the encrypted `.dftar` file, stored as raw bytes in three fixed
slots per user. Firebase never sees a backup password, vault PIN, database key or decrypted content.

## Values the code expects

- `androidApp/google-services.json` (ignored, one per variant): the Android package IDs `com.mk.daftar` and
  `com.mk.daftar.debug`, with the debug and Play App Signing SHA-1 and SHA-256 fingerprints.
- The OAuth **web client** (client type 3) is generated into `google-services.json`. The google-services plugin exposes
  it as the string resource `default_web_client_id`, which the app reads by name. Without that resource Google sign-in
  reports unavailable.
- Desktop: `desktopApp/firebase-desktop.properties` (ignored; see the `.example` beside it) with the project's Web API
  key, the project ID and an OAuth client of type **Desktop app** (client ID and its non-confidential secret). The
  desktop signs in through the system browser (PKCE, loopback redirect to 127.0.0.1), exchanges Google's ID token for a
  Firebase session with the Auth REST API, keeps that session in memory only, and uses the Firestore REST API under the
  same rules. Without the file desktop builds report cloud backups as unavailable.
- Firestore: one default database, Standard edition, production (locked) mode. Choose the region deliberately.
- Firestore document layout: `users/{uid}` (`schemaVersion`, `state`, `revision`, `updatedAt`) and
  `users/{uid}/backups/{slot}` with `slot` in `0`, `1`, `2`.

## Deploy rules and index exemptions

    cd docs/firebase
    firebase deploy --only firestore:rules,firestore:indexes --project <project-id>

Use separate development and production projects. Debug App Check tokens belong only to the development project.

## Rules tests

    firebase emulators:exec --only firestore "npm --prefix tests test"

The tests cover cross-UID access, the fourth slot, nested documents, invalid types and lengths, digest format, the
atomic revision check and the deleting state. Run them against the emulator before each deploy.

## App Check

Android installs Play Integrity in release and the debug provider in debug builds only. Firestore App Check enforcement
is **off**: a desktop app cannot be attested on the free plan, so access is limited by Firebase Auth and the per-user
rules above, and every backup stays encrypted with its password. Turning enforcement on again blocks desktop cloud
backups.

## Spark limits and manual cleanup

- Free quotas are shared by the project: 1 GiB stored, 50,000 reads, 20,000 writes and 20,000 deletes per day.
  Three maximum-size versions use 2.25 MiB per user before overhead.
- There is no automatic cleanup after an app is abandoned, after the Firebase account is deleted externally, or after
  account loss. Manual procedure: block the UID in Authentication, delete its three documents under `backups`, then
  delete the Authentication user. Do not promise immediate erasure from provider-internal retention.
- An interrupted account deletion is finished by signing in again and choosing "Finish account deletion".

## Remote Config

Android only. The app reads four parameters from Firebase Remote Config (project `<project-id>`, Android app
`com.mk.daftar`). Every default means "no prompt", so a project without values never interrupts anyone.

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `android_latest_build` | Number (versionCode) | `0` | Newest build. Installed builds below it get a dismissable prompt. |
| `android_latest_name` | String | `""` | Version name shown in the prompt, for example `1.2.0`. Blank shows the prompt without a version. |
| `android_min_build` | Number (versionCode) | `0` | Oldest build allowed. Installed builds below it are offered at least the soft prompt. |
| `android_force_enabled` | Boolean | `false` | With `android_min_build`, blocks builds below the minimum behind a full-screen "update required" screen. |

The prompt target is the greater of `android_latest_build` and `android_min_build`. Forcing needs all of these:
the installed build is below `android_min_build`, `android_force_enabled` is `true`, and the last successful fetch is
less than 24 hours old. A failed fetch never renews that age, so an offline device stops being forced after a day.

Publishing rules:

- **Soft update:** set `android_latest_build` to the new versionCode and `android_latest_name` to its version name.
  Leave `android_min_build` at `0` (or at the old value) and `android_force_enabled` `false`. Users can choose "Later",
  which postpones the prompt for that build for 7 days.
- **Forced update:** set `android_latest_build` and `android_latest_name` as above, then also set `android_min_build`
  to the oldest build still allowed and `android_force_enabled` to `true`. Builds below the minimum see only the
  update screen until they update, or until the policy is older than 24 hours.
- **Clearing a force:** set `android_force_enabled` to `false` (or raise `android_min_build` to `0`). Installed devices
  pick it up on their next resume or real-time update.
- The versionCode in `androidApp/build.gradle.kts` must increase with every release, because the comparison is numeric.
  Release fetches happen at most every 12 hours; debug builds fetch without an interval for testing.

The store opens `market://details?id=com.mk.daftar`, with the Play web page as the fallback.

## Not done in this release

Account linking is not supported. A Google backup and an Apple backup stay separate; migrate with a local `.dftar`
export and import. iOS has no cloud backup. Desktop sessions are not kept after the app quits.
