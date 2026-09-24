package io.github.yozedens.secureauth.core.otpauth

import io.github.yozedens.secureauth.core.base32.Base32
import io.github.yozedens.secureauth.core.error.ParseError
import io.github.yozedens.secureauth.core.error.ParseResult
import io.github.yozedens.secureauth.core.error.ParseWarning
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.OtpParams
import io.github.yozedens.secureauth.core.model.Secret

/**
 * Lenient parser for `otpauth://` URIs as found in real-world QR codes (design §15).
 *
 * `java.net.URI` is deliberately not used: it rejects common real-world input such as
 * unencoded spaces in the label. The parser never throws for any input.
 */
object OtpUriParser {

    /** QR codes cannot hold much more than this. */
    const val MAX_URI_LENGTH = 4096

    /** Maximum length of issuer and account name after sanitizing. */
    const val MAX_LABEL_FIELD_LENGTH = UriText.MAX_FIELD_LENGTH

    private const val SCHEME = "otpauth://"
    private const val MIGRATION_SCHEME = "otpauth-migration://"

    fun parse(input: String): ParseResult<OtpUri> {
        if (input.length > MAX_URI_LENGTH) return fail(ParseError.InvalidUri)
        val uri = input.trim()
        if (uri.startsWith(MIGRATION_SCHEME, ignoreCase = true)) {
            return fail(ParseError.UnsupportedMigrationFormat)
        }
        if (!uri.startsWith(SCHEME, ignoreCase = true)) return fail(ParseError.InvalidScheme)

        val parts = split(uri.substring(SCHEME.length))
        val params = parseQuery(parts.query).valueOr { return it }
        val warnings = mutableListOf<ParseWarning>()
        val kind = parseKind(parts.type, params, warnings).valueOr { return it }
        val algorithm = parseAlgorithm(params["algorithm"]) ?: return fail(ParseError.InvalidAlgorithm)
        val digits = parseInt(params["digits"], OtpParams.DEFAULT_DIGITS, OtpParams.DIGITS_RANGE)
            ?: return fail(ParseError.InvalidDigits)
        val secret = parseSecret(params["secret"]).valueOr { return it }
        val label = parseLabel(parts.rawLabel, params["issuer"])

        return ParseResult.Success(
            OtpUri(
                issuer = label.issuer,
                accountName = label.accountName,
                secret = secret,
                algorithm = algorithm,
                digits = digits,
                kind = kind,
                warnings = warnings,
            ),
        )
    }

    private class Parts(val type: String, val rawLabel: String, val query: String)

    private class Label(val issuer: String?, val accountName: String)

    /** Splits `type/label?query#fragment` (scheme already removed). */
    private fun split(rest: String): Parts {
        val withoutFragment = rest.substringBefore('#')
        val path = withoutFragment.substringBefore('?')
        val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
        return Parts(
            type = path.substringBefore('/').lowercase(),
            rawLabel = path.substringAfter('/', missingDelimiterValue = ""),
            query = query,
        )
    }

    private fun parseKind(
        type: String,
        params: Map<String, String>,
        warnings: MutableList<ParseWarning>,
    ): ParseResult<OtpKind> =
        when (type) {
            "totp" -> parseInt(params["period"], OtpParams.DEFAULT_PERIOD_SECONDS, OtpParams.PERIOD_RANGE)
                ?.let { ParseResult.Success(OtpKind.Totp(it)) }
                ?: fail(ParseError.InvalidPeriod)
            "hotp" -> {
                val rawCounter = params["counter"]
                if (rawCounter == null) {
                    warnings += ParseWarning.HOTP_COUNTER_MISSING
                    ParseResult.Success(OtpKind.Hotp(OtpParams.DEFAULT_COUNTER))
                } else {
                    rawCounter.trim().toLongOrNull()?.takeIf { it >= 0 }
                        ?.let { ParseResult.Success(OtpKind.Hotp(it)) }
                        ?: fail(ParseError.InvalidCounter)
                }
            }
            else -> fail(ParseError.InvalidType)
        }

    private fun parseSecret(raw: String?): ParseResult<Secret> {
        if (raw == null) return fail(ParseError.MissingSecret)
        val bytes = Base32.decode(raw).valueOr { return it }
        try {
            return ParseResult.Success(Secret(bytes))
        } finally {
            bytes.fill(0)
        }
    }

    /** The `issuer` parameter wins over the label prefix; the account may be empty. */
    private fun parseLabel(rawLabel: String, issuerParam: String?): Label {
        val label = UriText.percentDecode(rawLabel, plusAsSpace = false)
        val colon = label.indexOf(':')
        val labelIssuer = if (colon >= 0) UriText.sanitize(label.substring(0, colon)) else ""
        val accountName = UriText.sanitize(if (colon >= 0) label.substring(colon + 1) else label)
        val issuer = UriText.sanitize(issuerParam.orEmpty()).ifEmpty { labelIssuer }
        return Label(issuer.ifEmpty { null }, accountName)
    }

    /** Parameter names are case-insensitive; the first occurrence wins, except a repeated secret is an error. */
    private fun parseQuery(query: String): ParseResult<Map<String, String>> {
        val params = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            val key = UriText.percentDecode(pair.substringBefore('='), plusAsSpace = true).trim().lowercase()
            if (key == "secret" && key in params) return fail(ParseError.DuplicateSecret)
            if (key.isNotEmpty() && key !in params) {
                val rawValue = pair.substringAfter('=', missingDelimiterValue = "")
                params[key] = UriText.percentDecode(rawValue, plusAsSpace = true)
            }
        }
        return ParseResult.Success(params)
    }

    private fun parseInt(raw: String?, default: Int, range: IntRange): Int? =
        if (raw == null) default else raw.trim().toIntOrNull()?.takeIf { it in range }

    private fun parseAlgorithm(raw: String?): Algorithm? {
        if (raw == null) return Algorithm.SHA1
        val normalized = raw.trim().uppercase().replace("-", "")
        return Algorithm.entries.firstOrNull { it.name == normalized }
    }

    private fun fail(error: ParseError): ParseResult.Failure = ParseResult.Failure(error)

    private inline fun <T> ParseResult<T>.valueOr(onFailure: (ParseResult.Failure) -> Nothing): T = when (this) {
        is ParseResult.Success -> value
        is ParseResult.Failure -> onFailure(this)
    }
}
