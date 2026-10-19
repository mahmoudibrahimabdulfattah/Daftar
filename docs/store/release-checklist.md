# Google Play release checklist

Code already covers: R8 release build signed from the ignored `keystore.properties`, `targetSdk 36`, `FLAG_SECURE`,
no sensitive permissions, Crashlytics, Analytics and the Remote Config update prompt in Android
release builds, and in-app links to the privacy policy and the account-deletion page. Declarations live in
[play-console.md](play-console.md).

The steps below need the Firebase and Play Console accounts.

## Firebase (`daftar-f7c67`)

1. **Android app `com.mk.daftar`**: add the Play App Signing SHA-1 and SHA-256 (Play Console → Test and release →
   App integrity → App signing) and the upload-key fingerprints, then re-download `google-services.json` into
   `androidApp/` (git-ignored). Without the release entry, release builds ship without Firebase.
2. **Crashlytics**: Firebase Console → Crashlytics → enable for `com.mk.daftar`. The first crash appears after a
   release build crashes and is relaunched.
3. **Analytics**: Project settings → Integrations → Google Analytics → link a property. In that GA4 property:
   - Admin → Data collection → **Google signals off**, **ads personalization off** for every region.
   - Admin → Data retention → **2 months** (the policy promises this).
   - Do not link Google Ads or AdMob.
4. **Remote Config**: create the parameters in [`../firebase/README.md`](../firebase/README.md#remote-config) (all
   defaults are safe: no prompt).
5. **App Check**: Apps → `com.mk.daftar` → Play Integrity → register (link the Play Console project).
6. **Hosting**: `cd docs/firebase && firebase deploy --only hosting --project daftar-f7c67`, then open
   <https://daftar-f7c67.web.app/privacy> and <https://daftar-f7c67.web.app/delete-account>.

## Build

1. Bump `versionCode` (must increase every upload; Remote Config compares against it) and `versionName` in
   `androidApp/build.gradle.kts`.
2. Run the checks in `AGENTS.md`, then `./gradlew :androidApp:bundleRelease`.
   Output: `androidApp/build/outputs/bundle/release/androidApp-release.aab`. Keep the R8 mapping; Crashlytics uploads it
   during the build when `google-services.json` lists `com.mk.daftar`.

## Play Console

1. Create the app «دفتر» (default language Arabic – ar), free, app (not game).
2. Main store listing: Arabic and English texts from [listing.md](listing.md), icon 512×512, feature graphic 1024×500, phone screenshots made from
   demo data (see play-console.md).
3. App content: privacy policy, app access, ads, content rating, target audience, data safety, financial features,
   government/news/health declarations — answers in [play-console.md](play-console.md).
4. Upload the `.aab` to **Internal testing** first, install from Play on a real phone, check:
   unlock + biometric auto-prompt, price refresh, cloud sign-in/upload/restore, About → Privacy policy opens.
5. New personal developer accounts must run a **closed test with at least 12 testers for 14 days** before production
   access is granted.

## Publishing an update later

1. Upload the new build and roll it out.
2. Once it is live, set Remote Config `android_latest_build` (and `android_latest_name`) to the new `versionCode` for a
   dismissible prompt. For a required update also set `android_min_build` and `android_force_enabled = true`.
3. Publish the Remote Config change. Users see it on the next launch or resume.
