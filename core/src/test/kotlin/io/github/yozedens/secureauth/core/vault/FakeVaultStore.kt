package io.github.yozedens.secureauth.core.vault

import java.io.IOException

/** In-memory [VaultStore] standing in for DataStore in JVM tests. */
class FakeVaultStore(var bytes: ByteArray? = null) : VaultStore {

    var failWrites = false
    var failReads = false
    var writes = 0
        private set

    override suspend fun read(): ByteArray? {
        if (failReads) throw IOException("read failed")
        return bytes?.copyOf()
    }

    override suspend fun update(transform: suspend (current: ByteArray?) -> ByteArray) {
        val next = transform(read())
        if (failWrites) throw IOException("write failed")
        bytes = next.copyOf()
        writes++
    }
}
