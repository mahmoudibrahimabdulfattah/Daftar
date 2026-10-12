package com.mk.daftar.android

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds attest with Play Integrity only. There is no fallback to the debug provider: a refused attestation
 * blocks cloud backups and nothing else.
 */
internal fun installCloudAppCheck() {
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
