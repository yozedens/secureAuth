package io.github.yozedens.secureauth.security.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.yozedens.secureauth.security.biometric.BiometricAuthenticator.Availability
import io.github.yozedens.secureauth.security.biometric.BiometricAuthenticator.Result

/**
 * BiometricPrompt wrapper (design §31). v0.1 does not use a CryptoObject (ADR 0001 §1):
 * biometrics unlock the UI gate, with the PIN as fallback.
 */
class SystemBiometricAuthenticator(private val context: Context) : BiometricAuthenticator {

    override fun availability(): Availability = when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NONE_ENROLLED
        else -> Availability.UNAVAILABLE
    }

    override fun authenticate(
        activity: FragmentActivity,
        title: String,
        negativeText: String,
        onResult: (Result) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(Result.SUCCESS)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(
                        when (errorCode) {
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> Result.USE_PIN
                            BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> Result.CANCELLED
                            else -> Result.ERROR
                        },
                    )
                }
                // onAuthenticationFailed (e.g. unrecognized finger) keeps the prompt open.
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}
