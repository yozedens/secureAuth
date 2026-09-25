package io.github.yozedens.secureauth.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yozedens.secureauth.AppContainer
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.list.AccountPresentation
import io.github.yozedens.secureauth.core.list.Ticker
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.vault.VaultState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One card on the list (design §36): codes only, never the secret. */
data class AccountUiModel(
    val id: String,
    val issuer: String?,
    val accountName: String,
    /** Grouped code for display, e.g. "123 456"; null for an HOTP code not generated yet. */
    val displayCode: String?,
    /** Raw code for copying. */
    val code: String?,
    val remainingSeconds: Int?,
    val periodSeconds: Int?,
    val counter: Long?,
) {
    val isHotp: Boolean get() = counter != null
}

data class AccountListUiState(
    val accounts: List<AccountUiModel> = emptyList(),
    val query: String = "",
    val totalCount: Int = 0,
    val busy: Boolean = false,
    val lastCopiedId: String? = null,
)

/**
 * Combines account metadata with codes computed by the repository (design §47).
 * The ViewModel never sees a Secret. HOTP codes shown on screen are kept only in memory
 * and dropped as soon as the vault locks.
 */
class AccountListViewModel(private val c: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")
    private val hotpCodes = MutableStateFlow<Map<String, String>>(emptyMap())
    private val flags = MutableStateFlow(Flags())

    private data class Flags(val busy: Boolean = false, val lastCopiedId: String? = null)

    private val ticks = Ticker(System::currentTimeMillis).ticks()

    val uiState: StateFlow<AccountListUiState> =
        combine(c.vault.accounts, ticks, query, hotpCodes, flags) { accounts, now, q, hotp, f ->
            val visible = AccountPresentation.sorted(accounts).filter { AccountPresentation.matches(it, q) }
            AccountListUiState(
                accounts = visible.map { meta ->
                    when (val kind = meta.kind) {
                        is OtpKind.Totp -> {
                            val code = c.vault.totpAt(meta.id, now)
                            AccountUiModel(
                                id = meta.id,
                                issuer = meta.issuer,
                                accountName = meta.accountName,
                                displayCode = code?.value?.let(AccountPresentation::groupCode),
                                code = code?.value,
                                remainingSeconds = code?.remainingSeconds,
                                periodSeconds = kind.periodSeconds,
                                counter = null,
                            )
                        }
                        is OtpKind.Hotp -> AccountUiModel(
                            id = meta.id,
                            issuer = meta.issuer,
                            accountName = meta.accountName,
                            displayCode = hotp[meta.id]?.let(AccountPresentation::groupCode),
                            code = hotp[meta.id],
                            remainingSeconds = null,
                            periodSeconds = null,
                            counter = kind.counter,
                        )
                    }
                },
                query = q,
                totalCount = accounts.size,
                busy = f.busy,
                lastCopiedId = f.lastCopiedId,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AccountListUiState())

    init {
        // Drop generated HOTP codes the moment the vault locks (design §28).
        viewModelScope.launch {
            c.vault.state.filter { it != VaultState.Unlocked }.collect { _ ->
                hotpCodes.value = emptyMap()
                flags.update { it.copy(lastCopiedId = null) }
            }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Persists counter+1 first; the code appears only after the write succeeded (design §20). */
    fun generateHotp(id: String) {
        if (flags.value.busy) return
        viewModelScope.launch {
            flags.update { it.copy(busy = true) }
            try {
                val result = c.vault.nextHotp(id)
                if (result is VaultResult.Success) hotpCodes.update { it + (id to result.value.value) }
            } catch (ignored: IllegalStateException) {
                // Vault locked or account deleted while the tap was in flight: nothing to show.
            } catch (ignored: IllegalArgumentException) {
                // Same race, detected before the write.
            } finally {
                flags.update { it.copy(busy = false) }
            }
        }
    }

    /** Copies the code currently shown; never increments an HOTP counter (design §20). */
    fun copy(account: AccountUiModel) {
        val code = account.code ?: return
        c.clipboard.copyCode(code)
        flags.update { it.copy(lastCopiedId = account.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
