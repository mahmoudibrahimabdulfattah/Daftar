package com.mk.daftar.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.mk.daftar.core.security.AndroidBiometricCrypto
import com.mk.daftar.core.security.BiometricAuthResult
import com.mk.daftar.core.security.BiometricCrypto
import com.mk.daftar.core.security.BiometricPrompter
import com.mk.daftar.shared.resources.Res
import com.mk.daftar.shared.resources.biometric_prompt_negative
import com.mk.daftar.shared.resources.biometric_prompt_title
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/**
 * The activity the next prompt is shown from. The launcher attaches itself while it exists, and it is never read from
 * anywhere else, so a prompt cannot be shown from a stale or destroyed window.
 */
object BiometricPromptHost {
    @Volatile
    private var activity: FragmentActivity? = null

    fun attach(activity: FragmentActivity) {
        this.activity = activity
    }

    fun detach(activity: FragmentActivity) {
        if (this.activity === activity) this.activity = null
    }

    internal fun current(): FragmentActivity? = activity
}

/**
 * Shows the system biometric prompt for one Keystore operation. The prompt authorizes the operation's cipher through a
 * `CryptoObject`, and only strong biometrics are accepted. The negative button returns to the PIN; there is no device
 * credential fallback. Cancelling the coroutine dismisses the prompt.
 */
internal class AndroidBiometricPrompter : BiometricPrompter {
    override suspend fun authorize(crypto: BiometricCrypto): BiometricAuthResult {
        val cipher = (crypto as? AndroidBiometricCrypto)?.cipher ?: return BiometricAuthResult.Failed
        val activity = BiometricPromptHost.current() ?: return BiometricAuthResult.Unavailable
        val title = getString(Res.string.biometric_prompt_title)
        val negative = getString(Res.string.biometric_prompt_negative)
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val authorized = result.cryptoObject?.cipher
                            resumeOnce(
                                continuation,
                                if (authorized == null) {
                                    BiometricAuthResult.Failed
                                } else {
                                    BiometricAuthResult.Authenticated(AndroidBiometricCrypto(authorized))
                                },
                            )
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            resumeOnce(continuation, errorCode.toResult())
                        }
                    },
                )
                continuation.invokeOnCancellation { prompt.cancelAuthentication() }
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setNegativeButtonText(negative)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()
                prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            }
        }
    }

    private fun resumeOnce(continuation: CancellableContinuation<BiometricAuthResult>, result: BiometricAuthResult) {
        if (continuation.isActive) continuation.resume(result)
    }

    private fun Int.toResult(): BiometricAuthResult = when (this) {
        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON ->
            BiometricAuthResult.Cancelled

        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricAuthResult.LockedOut

        BiometricPrompt.ERROR_NO_BIOMETRICS -> BiometricAuthResult.NotEnrolled

        BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE -> BiometricAuthResult.Unavailable

        else -> BiometricAuthResult.Failed
    }
}
