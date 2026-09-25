package io.github.yozedens.secureauth.feature.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yozedens.secureauth.AppContainer
import io.github.yozedens.secureauth.core.base32.Base32
import io.github.yozedens.secureauth.core.entry.EntryError
import io.github.yozedens.secureauth.core.entry.EntryOptions
import io.github.yozedens.secureauth.core.entry.ManualEntry
import io.github.yozedens.secureauth.core.error.ParseError
import io.github.yozedens.secureauth.core.error.ParseResult
import io.github.yozedens.secureauth.core.error.SecretProblem
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.model.AccountMeta
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.OtpParams
import io.github.yozedens.secureauth.core.model.Secret
import io.github.yozedens.secureauth.core.vault.VaultState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editable fields (design §45). The replacement secret is held here only until saved. */
data class EditFormState(
    val accountId: String? = null,
    val issuer: String = "",
    val accountName: String = "",
    val options: EntryOptions = EntryOptions(),
    val newSecret: String = "",
    val errors: Set<EntryError> = emptySet(),
    val busy: Boolean = false,
    val saved: Boolean = false,
    val failed: Boolean = false,
) {
    override fun toString(): String = "EditFormState(accountId=$accountId, issuer=$issuer, newSecret=***)"
}

class EditAccountViewModel(private val c: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(EditFormState())
    val state: StateFlow<EditFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            c.vault.state.filter { it != VaultState.Unlocked }.collect { _ -> _state.value = EditFormState() }
        }
    }

    /** Loads [id] into a fresh form, discarding any unsaved edits. */
    fun load(id: String) {
        val meta = c.vault.accounts.value.firstOrNull { it.id == id } ?: return
        val kind = meta.kind
        _state.value = EditFormState(
            accountId = id,
            issuer = meta.issuer.orEmpty(),
            accountName = meta.accountName,
            options = EntryOptions(
                isHotp = kind is OtpKind.Hotp,
                algorithm = meta.algorithm,
                digits = meta.digits,
                period = ((kind as? OtpKind.Totp)?.periodSeconds ?: OtpParams.DEFAULT_PERIOD_SECONDS).toString(),
                counter = ((kind as? OtpKind.Hotp)?.counter ?: OtpParams.DEFAULT_COUNTER).toString(),
            ),
        )
    }

    fun update(transform: (EditFormState) -> EditFormState) =
        _state.update { transform(it).copy(errors = emptySet(), saved = false, failed = false) }

    suspend fun save() {
        val form = _state.value
        val meta = c.vault.accounts.value.firstOrNull { it.id == form.accountId } ?: return
        val errors = mutableSetOf<EntryError>()
        val issuer = ManualEntry.cleanName(form.issuer)
        val account = ManualEntry.cleanName(form.accountName)
        if (issuer.isEmpty() && account.isEmpty()) errors += EntryError.NameRequired
        val kind = ManualEntry.parseKind(form.options.isHotp, form.options.period, form.options.counter)
        if (kind == null) errors += if (form.options.isHotp) EntryError.InvalidCounter else EntryError.InvalidPeriod
        val secret = parseNewSecret(form.newSecret, errors)
        if (errors.isNotEmpty() || kind == null) {
            _state.update { it.copy(errors = errors) }
            return
        }

        _state.update { it.copy(busy = true) }
        val updated = meta.copy(
            issuer = issuer.ifEmpty { null },
            accountName = account,
            algorithm = form.options.algorithm,
            digits = form.options.digits,
            kind = kind,
        )
        val ok = save(updated, secret)
        _state.update { it.copy(busy = false, saved = ok, failed = !ok, newSecret = if (ok) "" else it.newSecret) }
    }

    /** Permanently deletes the account being edited (design §46). */
    suspend fun delete(): Boolean {
        val id = _state.value.accountId ?: return false
        val ok = c.vault.delete(id) is VaultResult.Success
        if (ok) _state.value = EditFormState()
        return ok
    }

    private suspend fun save(meta: AccountMeta, secret: Secret?): Boolean {
        val metaSaved = c.vault.updateMeta(meta) is VaultResult.Success
        if (!metaSaved || secret == null) return metaSaved
        return try {
            c.vault.replaceSecret(meta.id, secret) is VaultResult.Success
        } finally {
            secret.wipe()
        }
    }

    private fun parseNewSecret(text: String, errors: MutableSet<EntryError>): Secret? {
        if (text.isBlank()) return null
        return when (val decoded = Base32.decode(text)) {
            is ParseResult.Success -> decoded.value.let { bytes ->
                try {
                    Secret(bytes)
                } finally {
                    bytes.fill(0)
                }
            }
            is ParseResult.Failure -> {
                val problem = (decoded.error as? ParseError.InvalidSecret)?.problem ?: SecretProblem.INVALID_CHARACTER
                errors += EntryError.InvalidSecret(problem)
                null
            }
        }
    }
}
