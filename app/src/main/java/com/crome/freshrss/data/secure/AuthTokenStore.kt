package com.crome.freshrss.data.secure

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
import java.io.File
import java.nio.charset.StandardCharsets

/** Persistence for the short-lived GReader ClientLogin token. */
interface AuthTokenStore {
    fun load(): String?
    fun save(content: String)
    fun clear()
}

/**
 * AES-256-GCM encrypted file under the app's private [filesDir]
 * (stable security-crypto 1.0.0 API).
 * Also deletes any legacy plaintext cache left from older builds.
 */
class EncryptedAuthTokenStore(
    context: Context,
    fileName: String = "greader_auth.enc",
) : AuthTokenStore {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, fileName)
    private val legacyPlain = File(appContext.cacheDir, "freshrss-greader.auth")

    private val encryptedFile: EncryptedFile by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        // 1.0.0 builder order: file, context, masterKeyAlias, scheme
        EncryptedFile.Builder(
            file,
            appContext,
            masterKeyAlias,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }

    override fun load(): String? {
        deleteLegacyPlaintext()
        if (!file.isFile || file.length() == 0L) return null
        return try {
            encryptedFile.openFileInput().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            // Corrupt / key loss — drop file so the next login re-creates it.
            clear()
            null
        }
    }

    override fun save(content: String) {
        deleteLegacyPlaintext()
        try {
            // EncryptedFile refuses to overwrite an existing file.
            if (file.exists()) file.delete()
            encryptedFile.openFileOutput().bufferedWriter(StandardCharsets.UTF_8).use { out ->
                out.write(content)
            }
        } catch (_: Exception) {
            // non-fatal; in-memory auth still works for the session
        }
    }

    override fun clear() {
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
        deleteLegacyPlaintext()
    }

    private fun deleteLegacyPlaintext() {
        try {
            if (legacyPlain.exists()) legacyPlain.delete()
        } catch (_: Exception) {
        }
    }
}

/** In-memory only (tests / no-disk). */
class MemoryAuthTokenStore : AuthTokenStore {
    @Volatile
    private var data: String? = null

    override fun load(): String? = data
    override fun save(content: String) {
        data = content
    }
    override fun clear() {
        data = null
    }
}
