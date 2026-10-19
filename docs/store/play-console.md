# Google Play Console answers

Answers for the **App content** declarations of `com.mk.daftar`. They must match the published
[privacy policy](https://daftar-f7c67.web.app/privacy) (source: [`../firebase/hosting/privacy.html`](../firebase/hosting/privacy.html))
and the code. Re-check this file whenever an SDK, permission or network call changes.

Hosted pages (Firebase Hosting, project `daftar-f7c67`):

- Privacy policy: <https://daftar-f7c67.web.app/privacy>
- Account deletion: <https://daftar-f7c67.web.app/delete-account>

## Privacy policy

Paste <https://daftar-f7c67.web.app/privacy>. The app links to it from Settings and from About.

## Data safety

- Does your app collect or share any of the required user data types? **Yes**
- Is all of the user data collected by your app encrypted in transit? **Yes** (HTTPS only, cleartext is prohibited)
- Do you provide a way for users to request that their data is deleted? **Yes**
- Delete account URL: <https://daftar-f7c67.web.app/delete-account>
- Account creation: **optional**, "My app allows users to create an account" with Google via Firebase Authentication,
  only for cloud backups. The app is fully usable without an account.

| Data type | Collected | Shared | Ephemeral | Required / optional | Purposes |
| --- | --- | --- | --- | --- | --- |
| Personal info → Name | Yes | No¹ | No | Optional (cloud sign-in only) | Account management |
| Personal info → Email address | Yes | No¹ | No | Optional (cloud sign-in only) | Account management |
| Personal info → User IDs (Firebase uid) | Yes | No¹ | No | Optional (cloud sign-in only) | Account management |
| App activity → App interactions (screen names, sessions) | Yes | No¹ | No | Required | Analytics |
| App info and performance → Crash logs | Yes | No¹ | No | Required | Analytics, App functionality |
| App info and performance → Diagnostics | Yes | No¹ | No | Required | Analytics |
| Device or other IDs (Analytics app-instance ID, Crashlytics and Firebase installation IDs) | Yes | No¹ | No | Required | Analytics, App functionality |
| Device or other IDs (App Check / Play Integrity token) | Yes | No¹ | Yes | Optional (cloud backups only) | Fraud prevention, security, and compliance |
| Location → Approximate location (region derived from IP by Analytics) | Yes | No¹ | No | Required | Analytics |

¹ Google receives this data as a service provider processing it on Daftar's behalf, which Play does not count as
sharing.

**Not declared, and why:**

- **Financial info** (balances, gold, accounts, investments, zakat) and **notes**: processed only on the device
  (Play's on-device exemption). The optional cloud backup is the `.dftar` file encrypted on the device with a key only
  the user holds (backup password or recovery key); neither the developer nor Google can read it, which is Play's
  end-to-end encryption exemption. Its metadata (size, SHA-256 of the ciphertext, creation time, format version) is
  not user data.
- **Price requests** to iSagha, Banque Misr, open.er-api.com and the fawazahmed0 currency API carry no user data.
- **Advertising ID**: not collected (`AD_ID` permission removed, Analytics ad-ID collection disabled).
- Contacts, photos, files, messages, health, web history, precise location, audio: never accessed.

Do not answer "No data collected": Analytics, Crashlytics and the optional sign-in all send data off the device.

## Financial features

The declaration is mandatory even for testing tracks. Daftar is a manual, offline net-worth notebook: it does not
connect to banks, move money, lend, trade, sell crypto, insure or give advice. The values shown are the user's own
entries valued at public market prices.

- Select **Support services → Other**.
- Description: "Offline personal net-worth notebook. Users manually record their own gold, bank balances, cash and
  zakat; the app values them at public Egyptian market prices. No bank connection, payments, transfers, lending,
  trading or financial advice."

Fallback: if review rejects "Other", answer **"My app doesn't provide any financial features"** with the same
description in the appeal. Do not select "Banking" or "Stock trading and portfolio management"; they require licences
the app does not need.

## Content rating (IARC questionnaire)

- Category: **All other app types** (a personal utility; not a game, social, news or reference app).
- Violence, sexuality, language, controlled substances, crude humour, gambling: **No** to all.
- Users can interact or exchange content: **No**.
- Shares the user's current physical location with other users: **No**.
- Allows purchases of digital goods: **No**.
- Unrestricted internet browsing: **No**.

Expected rating: Everyone / PEGI 3 / USK 0.

## Target audience and content

- Target age groups: **18 and over** only. The app is not designed for children, so the Families policy does not
  apply.
- Appeals to children: **No**.

## Other declarations

- Ads: **No, my app does not contain ads.**
- Government app: **No**. News app: **No**. Health app: **No**. COVID-19 app: **No**.
- Data deletion questions: same answers as Data safety above.
- Permissions: `INTERNET` and `USE_BIOMETRIC` (plus the legacy `USE_FINGERPRINT` from androidx.biometric), and the
  normal `ACCESS_NETWORK_STATE` and `WAKE_LOCK` used by Firebase uploads; no sensitive permission declarations are
  needed.

## App access (instructions for review)

Choose **All or some functionality is restricted** and add one instruction set:

- Name: "Create your own PIN"
- Username / password: leave empty.
- Instructions:

  > Daftar has no login. On first launch, create any 6-digit PIN (for example 246810) and confirm it. Everything is
  > stored encrypted on the device behind that PIN. The app locks when it goes to the background; unlock with the same
  > PIN. Prices load from public Egyptian price sources; if they are unreachable, the last saved or a manual price is
  > used. Optional: Settings → Cloud backups uses your own Google account to upload an encrypted backup, protected by
  > a separate backup password of at least 12 characters. Screenshots are blocked on purpose (FLAG_SECURE) to protect
  > financial data.

## Store listing notes

- Screenshots cannot be captured on a device (`FLAG_SECURE`). Use the desktop screenshot run
  (`./gradlew :desktopApp:jvmTest`, output in `desktopApp/build/screens`) or the debug design catalog with demo data,
  never real balances.
- Never claim "nothing leaves your device" or "local only"; say records are stored on the device and an encrypted
  backup is uploaded only by the user's choice.
