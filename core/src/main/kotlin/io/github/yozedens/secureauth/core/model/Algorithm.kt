package io.github.yozedens.secureauth.core.model

/** HMAC algorithm used for OTP generation (RFC 4226 / RFC 6238). */
enum class Algorithm(val jcaName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512"),
}
