package io.github.yozedens.secureauth.feature.addaccount

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yozedens.secureauth.AppContainer
import io.github.yozedens.secureauth.core.entry.EntryError
import io.github.yozedens.secureauth.core.entry.EntryOptions
import io.github.yozedens.secureauth.core.entry.EntryResult
import io.github.yozedens.secureauth.core.entry.ManualEntry
import io.github.yozedens.secureauth.core.entry.ManualEntryForm
import io.github.yozedens.secureauth.core.error.ParseError
import io.github.yozedens.secureauth.core.error.ParseResult
import io.github.yozedens.secureauth.core.error.ParseWarning
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.otpauth.OtpUri
import io.github.yozedens.secureauth.core.otpauth.OtpUriParser
import io.github.yozedens.secureauth.core.vault.VaultState
import io.github.yozedens.secureauth.feature.scanner.QrDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why a scanned or picked code was rejected. */
enum class ScanProblem { INVALID, MIGRATION, NO_CODE_IN_IMAGE, IMAGE_UNREADABLE }

/** Summary for the confirm screen: everything except the secret (design §44). */
data class PendingSummary(
    val issuer: String,
    val accountName: String,
    val uri: OtpUriView,
    val duplicate: Boolean,
    val hotpCounterMissing: Boolean,
)

/** Display-only view of the pending account's parameters. */
data class OtpUriView(val algorithm: String, val digits: Int, val periodSeconds: Int?, val counter: Long?)

/** Manual form fields (design §17). The secret is held here, never in saved state. */
data class ManualFormState(
    val issuer: String = "",
    val accountName: String = "",
    val secret: String = "",
    val options: EntryOptions = EntryOptions(),
    val errors: Set<EntryError> = emptySet(),
) {
    override fun toString(): String = "ManualFormState(issuer=$issuer, accountName=$accountName, secret=***)"
}

data class AddAccountUiState(
    val manual: ManualFormState = ManualFormState(),
    val pending: PendingSummary? = null,
    val scanProblem: ScanProblem? = null,
    val busy: Boolean = false,
)

/**
 * Holds the account being added between scan/manual entry and confirmation (design §16).
 * The parsed secret lives only in [pendingUri] in memory: never in navigation arguments,
 * SavedStateHandle or rememberSaveable. Everything is cleared on confirm, cancel or lock.
 */
class AddAccountViewModel(private val c: AppContainer) : ViewModel() {

    private var pendingUri: OtpUri? = null
    private val _state = MutableStateFlow(AddAccountUiState())
    val state: StateFlow<AddAccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            c.vault.state.filter { it != VaultState.Unlocked }.collect { _ -> clear() }
        }
    }

    /** Handles decoded QR text. Returns true if the confirm screen should open. */
    suspend fun onScanned(text: String): Boolean = when (val result = OtpUriParser.parse(text)) {
        is ParseResult.Success -> {
            setPending(result.value)
            true
        }
        is ParseResult.Failure -> {
            val problem = if (result.error == ParseError.UnsupportedMigrationFormat) {
                ScanProblem.MIGRATION
            } else {
                ScanProblem.INVALID
            }
            _state.update { it.copy(scanProblem = problem) }
            false
        }
    }

    /** Decodes a QR code from a picked image. Returns true if the confirm screen should open. */
    suspend fun onImagePicked(resolver: ContentResolver, image: Uri): Boolean =
        when (val result = withContext(c.ioDispatcher) { QrDecoder.decode(resolver, image) }) {
            is QrDecoder.ImageResult.Found -> onScanned(result.text)
            QrDecoder.ImageResult.NoCode -> fail(ScanProblem.NO_CODE_IN_IMAGE)
            QrDecoder.ImageResult.Unreadable -> fail(ScanProblem.IMAGE_UNREADABLE)
        }

    private fun fail(problem: ScanProblem): Boolean {
        _state.update { it.copy(scanProblem = problem) }
        return false
    }

    fun clearScanProblem() = _state.update { it.copy(scanProblem = null) }

    fun updateManual(transform: (ManualFormState) -> ManualFormState) =
        _state.update { it.copy(manual = transform(it.manual).copy(errors = emptySet())) }

    /** Validates the manual form. Returns true if the confirm screen should open. */
    suspend fun submitManual(): Boolean {
        val form = _state.value.manual
        val result = ManualEntry.validate(ManualEntryForm(form.issuer, form.accountName, form.secret, form.options))
        return when (result) {
            is EntryResult.Valid -> {
                setPending(result.value)
                true
            }
            is EntryResult.Invalid -> {
                _state.update { it.copy(manual = it.manual.copy(errors = result.errors)) }
                false
            }
        }
    }

    /** Stores the pending account with the (possibly edited) names. Returns true on success. */
    suspend fun confirm(issuer: String, accountName: String): Boolean {
        val uri = pendingUri ?: return false
        _state.update { it.copy(busy = true) }
        val saved = c.vault.add(ManualEntry.toDraft(uri, issuer, accountName)) is VaultResult.Success
        _state.update { it.copy(busy = false) }
        if (saved) clear()
        return saved
    }

    /** Leaves the add flow and forgets everything entered. */
    fun clear() {
        pendingUri?.secret?.wipe()
        pendingUri = null
        _state.value = AddAccountUiState()
    }

    private suspend fun setPending(uri: OtpUri) {
        pendingUri?.secret?.wipe()
        pendingUri = uri
        val duplicate = c.vault.containsSecret(uri.secret)
        _state.update {
            it.copy(
                scanProblem = null,
                pending = PendingSummary(
                    issuer = uri.issuer.orEmpty(),
                    accountName = uri.accountName,
                    uri = OtpUriView(
                        algorithm = uri.algorithm.name,
                        digits = uri.digits,
                        periodSeconds = (uri.kind as? OtpKind.Totp)?.periodSeconds,
                        counter = (uri.kind as? OtpKind.Hotp)?.counter,
                    ),
                    duplicate = duplicate,
                    hotpCounterMissing = ParseWarning.HOTP_COUNTER_MISSING in uri.warnings,
                ),
            )
        }
    }
}
