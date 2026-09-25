package io.github.yozedens.secureauth.security.biometric

import androidx.fragment.app.FragmentActivity

/** Biometric gate (design §31). An interface so UI tests can inject a fake (design §50.8). */
interface BiometricAuthenticator {

    enum class Availability { AVAILABLE, NONE_ENROLLED, UNAVAILABLE }

    enum class Result { SUCCESS, USE_PIN, CANCELLED, ERROR }

    fun availability(): Availability

    fun authenticate(activity: FragmentActivity, title: String, negativeText: String, onResult: (Result) -> Unit)
}
