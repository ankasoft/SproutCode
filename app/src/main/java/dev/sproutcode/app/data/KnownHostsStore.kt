package dev.sproutcode.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

private const val PREFS_FILE = "known_hosts"
private const val KEY_HOSTS = "hosts"

class KnownHostsStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getFingerprint(host: String, port: Int): String? {
        val key = "$host:$port"
        val raw = prefs.getString(KEY_HOSTS, null) ?: return null
        return try {
            val json = org.json.JSONObject(raw)
            if (json.has(key)) json.getString(key) else null
        } catch (e: Exception) {
            null
        }
    }

    fun saveFingerprint(host: String, port: Int, fingerprint: String) {
        val key = "$host:$port"
        val raw = prefs.getString(KEY_HOSTS, null)
        val json = try {
            if (raw != null) org.json.JSONObject(raw) else org.json.JSONObject()
        } catch (e: Exception) {
            org.json.JSONObject()
        }
        json.put(key, fingerprint)
        prefs.edit().putString(KEY_HOSTS, json.toString()).apply()
    }

    fun removeFingerprint(host: String, port: Int) {
        val key = "$host:$port"
        val raw = prefs.getString(KEY_HOSTS, null) ?: return
        try {
            val json = org.json.JSONObject(raw)
            json.remove(key)
            prefs.edit().putString(KEY_HOSTS, json.toString()).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun clearAll() {
        prefs.edit().remove(KEY_HOSTS).apply()
    }

    fun listAll(): Map<String, String> {
        val raw = prefs.getString(KEY_HOSTS, null) ?: return emptyMap()
        return try {
            val json = org.json.JSONObject(raw)
            val map = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
