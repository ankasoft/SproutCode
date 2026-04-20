package dev.sproutcode.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream

/**
 * Uygulama kendi SSH anahtar çiftini üretir, private key'i şifreli saklar.
 * Public key Hetzner sunucularına enjekte edilir; private key JSch ile bağlanırken kullanılır.
 */
class SshKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            alias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** OpenSSH public key string: "ssh-ed25519 AAAA... sproutcode@mobile" */
    fun publicKey(): String {
        ensureKeyExists()
        return prefs.getString(KEY_PUBLIC, "").orEmpty()
    }

    /** OpenSSH format private key bytes (for JSch). */
    fun privateKey(): ByteArray {
        ensureKeyExists()
        return prefs.getString(KEY_PRIVATE, "").orEmpty().toByteArray()
    }

    fun regenerate() {
        val (pub, priv) = generateKeypair()
        prefs.edit()
            .putString(KEY_PUBLIC, pub)
            .putString(KEY_PRIVATE, priv)
            .apply()
    }

    private fun ensureKeyExists() {
        if (prefs.getString(KEY_PUBLIC, null).isNullOrBlank()) regenerate()
    }

    private fun generateKeypair(): Pair<String, String> {
        val jsch = JSch()
        val kp = KeyPair.genKeyPair(jsch, KeyPair.RSA, 4096)
        val pubOut = ByteArrayOutputStream()
        kp.writePublicKey(pubOut, "sproutcode@mobile")
        val privOut = ByteArrayOutputStream()
        kp.writePrivateKey(privOut)
        kp.dispose()
        return pubOut.toString().trim() to privOut.toString()
    }

    companion object {
        private const val PREFS_FILE  = "ssh_keys_store"
        private const val KEY_PUBLIC  = "pub"
        private const val KEY_PRIVATE = "priv"
    }
}
