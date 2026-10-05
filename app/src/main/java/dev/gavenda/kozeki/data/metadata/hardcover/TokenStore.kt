package dev.gavenda.kozeki.data.metadata.hardcover

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What is kept between launches for the Hardcover sign-in. */
@Serializable
data class StoredSession(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    /** Epoch millis after which [accessToken] must be refreshed. */
    val expiresAt: Long = 0L,
    val username: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    /** State and PKCE verifier of a sign-in that is waiting for the browser to come back. */
    val pendingState: String? = null,
    val pendingVerifier: String? = null,
) {
    val isSignedIn: Boolean get() = refreshToken != null
}

/**
 * Keeps the Hardcover tokens encrypted with a key that never leaves the Android Keystore. The
 * file lives in the no-backup directory: a restored copy could not be decrypted on another device
 * anyway, and a refresh token should not travel through cloud backups.
 */
class TokenStore(context: Context) {

    private val file = File(context.noBackupFilesDir, "hardcover_session.bin")
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): StoredSession {
        if (!file.isFile) return StoredSession()
        return try {
            val bytes = file.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            val plain = cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
            json.decodeFromString<StoredSession>(plain.decodeToString())
        } catch (e: Exception) {
            // The key is gone or the file is damaged; either way the user has to sign in again.
            file.delete()
            StoredSession()
        }
    }

    fun write(session: StoredSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json.encodeToString(session).encodeToByteArray())
        // Written to a temporary file first so a crash cannot leave half a session behind. A lost
        // refresh token would silently sign the user out, since each one can be used only once.
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(cipher.iv + encrypted)
        if (!temp.renameTo(file)) {
            file.writeBytes(temp.readBytes())
            temp.delete()
        }
    }

    fun clear() {
        file.delete()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kozeki_hardcover_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
