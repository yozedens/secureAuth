package io.github.yozedens.secureauth.core.list

import io.github.yozedens.secureauth.core.model.AccountMeta
import java.text.Collator
import java.util.Locale

/** Ordering, search and display helpers for the account list (design §18). */
object AccountPresentation {

    /**
     * Sorted by issuer, then account name, using [collator] so that Chinese names sort
     * sensibly. Accounts without an issuer sort by their account name.
     */
    fun sorted(
        accounts: List<AccountMeta>,
        collator: Collator = defaultCollator(),
    ): List<AccountMeta> =
        accounts.sortedWith(
            compareBy<AccountMeta, String>(collator) { it.issuer ?: it.accountName }
                .thenBy(collator) { it.accountName }
                .thenBy { it.createdAt },
        )

    /** Case-insensitive substring match on issuer or account name; blank matches everything. */
    fun matches(account: AccountMeta, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return account.issuer.orEmpty().contains(q, ignoreCase = true) ||
            account.accountName.contains(q, ignoreCase = true)
    }

    /** "123456" → "123 456", "1234567" → "123 4567", "12345678" → "1234 5678". */
    fun groupCode(code: String): String {
        val split = code.length / 2
        return if (code.length < MIN_GROUPED_LENGTH) code else code.substring(0, split) + " " + code.substring(split)
    }

    private const val MIN_GROUPED_LENGTH = 6

    /** Ignores case ("github" == "GitHub") but not accents. */
    private fun defaultCollator(): Collator =
        Collator.getInstance(Locale.CHINA).apply { strength = Collator.SECONDARY }
}
