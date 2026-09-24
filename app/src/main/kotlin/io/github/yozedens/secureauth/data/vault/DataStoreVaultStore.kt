package io.github.yozedens.secureauth.data.vault

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import io.github.yozedens.secureauth.core.vault.VaultStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream

/**
 * [VaultStore] backed by DataStore (design §22). DataStore only ever sees and caches the
 * encrypted envelope; decryption happens in `VaultRepository` (ADR 0001 §4).
 */
class DataStoreVaultStore(
    context: Context,
    scope: CoroutineScope,
    fileName: String = FILE_NAME,
) : VaultStore {

    private val dataStore: DataStore<ByteArray> = DataStoreFactory.create(
        serializer = RawBytesSerializer,
        scope = scope,
        produceFile = { context.applicationContext.dataStoreFile(fileName) },
    )

    override suspend fun read(): ByteArray? = dataStore.data.first().takeIf { it.isNotEmpty() }

    override suspend fun update(transform: suspend (current: ByteArray?) -> ByteArray) {
        dataStore.updateData { current -> transform(current.takeIf { it.isNotEmpty() }) }
    }

    /** Stores bytes verbatim; an empty array means "no vault". */
    private object RawBytesSerializer : Serializer<ByteArray> {
        override val defaultValue: ByteArray = ByteArray(0)

        override suspend fun readFrom(input: InputStream): ByteArray = input.readBytes()

        override suspend fun writeTo(t: ByteArray, output: OutputStream) = output.write(t)
    }

    companion object {
        const val FILE_NAME = "vault.pb"
    }
}
