package dev.sproutcode.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_FILE   = "ssh_servers"
private const val KEY_SERVERS  = "servers"

class ServerStore(private val context: Context) {

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

    fun list(): List<Server> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> arr.getJSONObject(i).toServer() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(id: String): Server? = list().firstOrNull { it.id == id }

    fun save(server: Server) {
        val current = list().toMutableList()
        val idx = current.indexOfFirst { it.id == server.id }
        if (idx >= 0) current[idx] = server else current.add(server)
        writeAll(current)
    }

    fun delete(id: String) {
        writeAll(list().filter { it.id != id })
    }

    private fun writeAll(servers: List<Server>) {
        val arr = JSONArray()
        servers.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SERVERS, arr.toString()).apply()
    }

    private fun JSONObject.toServer() = Server(
        id         = getString("id"),
        name       = getString("name"),
        host       = getString("host"),
        port       = optInt("port", 22),
        username   = optString("username", "root"),
        password   = optString("password", ""),
        hetznerId  = if (has("hetznerId") && !isNull("hetznerId")) getLong("hetznerId") else null,
        useKeyAuth = optBoolean("useKeyAuth", false)
    )

    private fun Server.toJson(): JSONObject = JSONObject().apply {
        put("id",         id)
        put("name",       name)
        put("host",       host)
        put("port",       port)
        put("username",   username)
        put("password",   password)
        put("useKeyAuth", useKeyAuth)
        if (hetznerId != null) put("hetznerId", hetznerId)
    }
}
