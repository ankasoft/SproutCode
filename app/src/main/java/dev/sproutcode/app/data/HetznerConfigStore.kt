package dev.sproutcode.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

private const val PREFS_FILE        = "hetzner_config"
private const val KEY_API_TOKEN     = "api_token"
private const val KEY_LOCATION      = "default_location"
private const val KEY_SERVER_TYPE   = "default_server_type"
private const val KEY_IMAGE         = "default_image"
private const val KEY_GITHUB_TOKEN  = "github_token"

class HetznerConfigStore(private val context: Context) {

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

    fun load(): HetznerConfig = HetznerConfig(
        apiToken          = prefs.getString(KEY_API_TOKEN,    "") ?: "",
        defaultLocation   = prefs.getString(KEY_LOCATION,     "nbg1")         ?: "nbg1",
        defaultServerType = prefs.getString(KEY_SERVER_TYPE,  "cx22")         ?: "cx22",
        defaultImage      = prefs.getString(KEY_IMAGE,        "ubuntu-24.04") ?: "ubuntu-24.04",
        githubToken       = prefs.getString(KEY_GITHUB_TOKEN, "") ?: ""
    )

    fun save(config: HetznerConfig) {
        prefs.edit()
            .putString(KEY_API_TOKEN,    config.apiToken)
            .putString(KEY_LOCATION,     config.defaultLocation)
            .putString(KEY_SERVER_TYPE,  config.defaultServerType)
            .putString(KEY_IMAGE,        config.defaultImage)
            .putString(KEY_GITHUB_TOKEN, config.githubToken)
            .apply()
    }
}
