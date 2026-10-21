# Low-memory Argon2id profile (design lead decision, approved by the owner 20 Oct 2026)

Incident: release 1.0.0 (1) crashed on a Samsung Galaxy A10s (2 GB RAM, Android 11): crypto_pwhash returned -1 because 64 MiB could not be allocated.

1. **Profiles**

   New writes use exactly two profiles:

   - **Standard:** `memoryKiB=65536`, `passes=3`.
   - **LowMemory:** `memoryKiB=19456` (19 MiB), `passes=3`.

   Both retain Argon2id v1.3, `p=1`, fresh 16-byte salt and 32-byte output. LowMemory reduces memory by about 70%, while exceeding OWASP’s listed 19 MiB / two-pass baseline. It provides less guessing resistance than Standard; hardware binding and persistent backoff remain mandatory for low-entropy PINs. [OWASP guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#argon2id)

   Preserve read support for existing valid 64 MiB envelopes with passes `3..6`; these are legacy parameters, not additional selectable profiles.

2. **Selection and PIN changes**

   - **Creation:** attempt Standard; on validated `crypto_pwhash` allocation failure (`-1`), release temporary buffers and attempt LowMemory once. Persist only the successful parameters.
   - `isLowRamDevice` and `memoryClass` must not select the profile: allocation success is the deciding evidence.
   - **Unlock:** use stored parameters exactly. Never substitute LowMemory; it derives a different key.
   - **PIN change:** retain the existing exact parameters, including legacy passes. No automatic upgrade or downgrade. Commit the replacement envelope atomically after success.
   - Serialize memory-intensive KDF operations. No retry loops or fallback for wrong credentials, authentication failures or invalid parameters.

   **Standard in practice.** Standard is the existing calibrated 64 MiB profile: creation keeps the calibrator's pass count
   (3 to 6, the largest that stays within its time budget). LowMemory is used only after an allocation failure: when the
   first 64 MiB derivation (pass 3) fails, the vault is created once with LowMemory. When a later calibration step fails
   after pass 3 succeeded, the largest successful Standard pass count is kept rather than dropping to LowMemory.

3. **Backups**

   Header storage alone does **not** establish that the current reader honours its parameters; that remains unverified from this brief.

   Authorize widening the reader’s allowlist to include LowMemory, preserving the binary layout, version, header authentication and existing accepted parameters. Reject unsupported parameters before allocating.

   **Exports:** attempt Standard, then LowMemory once on allocation failure. Keep the password minimum of 12 characters. Backups lack the vault’s hardware binding; LowMemory therefore explicitly reduces their offline guessing cost.

   Ship widened readers before enabling LowMemory writers. Older readers restricted to Standard cannot read these new files.

   **Restore:** derive using the authenticated container’s recorded parameters; never retry a Standard backup with LowMemory. Offer retry with the same parameters. If memory remains insufficient, restoration requires another capable device. Any destination vault creation follows rule 2.

4. **Failure behaviour and exact copy**

   Every returned allocation failure becomes `ResourceUnavailable`, never an uncaught exception or a wrong-credential result. It must not increment wrong-PIN counters. Preserve existing vaults, envelopes, backups and cloud slots; discard incomplete output. Restore commits only after complete verification and successful staging.

   Stay on the relevant screen and show its contextual line:

   | Screen | English | العربية |
   |---|---|---|
   | Vault creation | “Your vault has not been created.” | «لم يتم إنشاء خزنتك.» |
   | Unlock | “Your vault remains locked. Your data is unchanged.” | «ما زالت خزنتك مقفلة. لم تتغير بياناتك.» |
   | PIN change | “Your PIN has not been changed.” | «لم يتم تغيير رمز الدخول.» |
   | Export | “No new backup was saved. Your data and existing backups are unchanged.” | «لم يتم حفظ نسخة احتياطية جديدة. لم تتغير بياناتك أو نسخك الاحتياطية الحالية.» |
   | Restore | “The backup was not restored. Your current data and the backup file are unchanged.” | «لم تتم استعادة النسخة الاحتياطية. لم تتغير بياناتك الحالية أو ملف النسخة الاحتياطية.» |

   Shared title: **“Not enough memory”** / **«الذاكرة المتاحة غير كافية»**

   Shared body:  
   “Daftar couldn’t allocate the memory needed to complete this operation. Close other apps or restart your phone, then try again.”  
   «تعذّر على دفتر توفير الذاكرة اللازمة لإكمال هذه العملية. أغلق التطبيقات الأخرى أو أعد تشغيل هاتفك، ثم حاول مرة أخرى.»

   Buttons: **“Try again” / «إعادة المحاولة»**, **“Cancel” / «إلغاء»**.

   Standard-restore addition:  
   “This backup requires more memory. If retrying fails, restore it on another device with enough available memory.”  
   «تحتاج هذه النسخة الاحتياطية إلى ذاكرة أكبر. إذا فشلت إعادة المحاولة، فاستعدها على جهاز آخر تتوفر فيه ذاكرة كافية.»

   The no-crash requirement covers handled allocation failures; OS termination cannot be prevented, so interrupted operations must also preserve data.

5. **Tests**

   - Inject Standard failure/LowMemory success and both failures; verify exact calls, bounded retries and stored parameters.
   - Verify both profiles and legacy envelopes; unlock and restore never substitute parameters.
   - Verify PIN-change failure preserves the original envelope and PIN.
   - Test backup compatibility, authenticated-header tampering, parameter allowlists and excessive-value rejection.
   - Inject failure/interruption at creation, export and restore boundaries; verify atomicity, unchanged existing data and no incomplete published backups.
   - Verify bilingual copy, retry/cancel, unchanged backoff counters and no sensitive logging.
   - Run required security/device checks, including release testing on the Galaxy A10s under memory pressure.
