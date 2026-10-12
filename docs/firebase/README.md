# Daftar cloud backups: Firebase runbook

Scope: Android only, Google sign-in, Firebase Auth, Firestore and App Check on the Spark plan. No Storage, Functions,
Analytics, Crashlytics or billing account. Backups are the encrypted `.dftar` file, stored as raw bytes in three fixed
slots per user. Firebase never sees a backup password, vault PIN, database key or decrypted content.

## Values the code expects

- `androidApp/google-services.json` (ignored, one per variant): the Android package IDs `com.mk.daftar` and
  `com.mk.daftar.debug`, with the debug and Play App Signing SHA-1 and SHA-256 fingerprints.
- The OAuth **web client** (client type 3) is generated into `google-services.json`. The google-services plugin exposes
  it as the string resource `default_web_client_id`, which the app reads by name. Without that resource Google sign-in
  reports unavailable.
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

Play Integrity in release, the debug provider in debug builds only. Register Play Integrity in App Check, then enforce
Firestore only after testing on a device. Release has no fallback to the debug provider.

## Spark limits and manual cleanup

- Free quotas are shared by the project: 1 GiB stored, 50,000 reads, 20,000 writes and 20,000 deletes per day.
  Three maximum-size versions use 2.25 MiB per user before overhead.
- There is no automatic cleanup after an app is abandoned, after the Firebase account is deleted externally, or after
  account loss. Manual procedure: block the UID in Authentication, delete its three documents under `backups`, then
  delete the Authentication user. Do not promise immediate erasure from provider-internal retention.
- An interrupted account deletion is finished by signing in again and choosing "Finish account deletion".

## Not done in this release

Account linking is not supported. A Google backup and an Apple backup stay separate; migrate with a local `.dftar`
export and import. iOS has no cloud backup.
