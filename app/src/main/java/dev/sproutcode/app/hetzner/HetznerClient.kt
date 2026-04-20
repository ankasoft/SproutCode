package dev.sproutcode.app.hetzner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object HetznerClient {

    private const val BASE_URL = "https://api.hetzner.cloud/v1"

    suspend fun listLocations(token: String): List<HetznerLocation> = withContext(Dispatchers.IO) {
        val root = JSONObject(get("$BASE_URL/locations", token))
        val arr  = root.getJSONArray("locations")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HetznerLocation(
                name        = o.getString("name"),
                city        = o.optString("city"),
                country     = o.optString("country"),
                description = o.optString("description")
            )
        }.sortedBy { it.name }
    }

    suspend fun listServerTypes(token: String): List<HetznerServerType> = withContext(Dispatchers.IO) {
        val root = JSONObject(get("$BASE_URL/server_types?per_page=50", token))
        val arr  = root.getJSONArray("server_types")
        val out  = mutableListOf<HetznerServerType>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val deprecated = o.optBoolean("deprecated", false) || !o.isNull("deprecation")
            if (deprecated) continue
            out += HetznerServerType(
                name         = o.getString("name"),
                description  = o.optString("description"),
                cores        = o.optInt("cores"),
                memory       = o.optDouble("memory", 0.0),
                disk         = o.optInt("disk"),
                architecture = o.optString("architecture", "x86")
            )
        }
        out.sortedWith(compareBy({ it.architecture }, { it.cores }, { it.name }))
    }

    suspend fun listImages(token: String): List<HetznerImage> = withContext(Dispatchers.IO) {
        val out = mutableListOf<HetznerImage>()
        for (type in listOf("system", "app", "snapshot", "backup")) {
            val root = JSONObject(get("$BASE_URL/images?type=$type&per_page=50", token))
            val arr  = root.getJSONArray("images")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (!o.isNull("deprecated")) continue
                if (o.optString("status") != "available") continue
                out += HetznerImage(
                    id          = o.getLong("id"),
                    type        = o.getString("type"),
                    name        = o.optStringOrNull("name"),
                    osFlavor    = o.optString("os_flavor", ""),
                    osVersion   = o.optStringOrNull("os_version"),
                    description = o.optString("description", "")
                )
            }
        }
        val order = mapOf("snapshot" to 0, "backup" to 1, "app" to 2, "system" to 3)
        out.sortedWith(compareBy({ order[it.type] ?: 99 }, { it.identifier }))
    }

    suspend fun listSshKeys(token: String): List<HetznerSshKey> = withContext(Dispatchers.IO) {
        val arr = JSONObject(get("$BASE_URL/ssh_keys?per_page=50", token)).getJSONArray("ssh_keys")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HetznerSshKey(
                id          = o.getLong("id"),
                name        = o.getString("name"),
                fingerprint = o.optString("fingerprint"),
                publicKey   = o.optString("public_key")
            )
        }
    }

    suspend fun uploadSshKey(token: String, name: String, publicKey: String): Long =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("name", name)
                put("public_key", publicKey)
            }
            val resp = post("$BASE_URL/ssh_keys", token, body.toString())
            JSONObject(resp).getJSONObject("ssh_key").getLong("id")
        }

    suspend fun ensureSshKey(token: String, publicKey: String): Long = withContext(Dispatchers.IO) {
        val ourKeyMaterial = publicKey.trim().split(Regex("\\s+")).take(2).joinToString(" ")
        val existing = listSshKeys(token).firstOrNull {
            it.publicKey.trim().split(Regex("\\s+")).take(2).joinToString(" ") == ourKeyMaterial
        }
        existing?.id ?: run {
            val name = "sproutcode-mobile-${System.currentTimeMillis() / 1000}"
            uploadSshKey(token, name, publicKey)
        }
    }

    suspend fun createServer(token: String, req: CreateServerRequest): HetznerServerSummary =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("name", req.name)
                put("server_type", req.serverType)
                put("location", req.location)
                put("image", req.image)
                put("start_after_create", req.startAfterCreate)
                put("ssh_keys", JSONArray().also { arr -> req.sshKeyIds.forEach { arr.put(it) } })
                if (req.userData != null) put("user_data", req.userData)
            }
            val resp = post("$BASE_URL/servers", token, body.toString())
            parseServer(JSONObject(resp).getJSONObject("server"))
        }

    suspend fun getServer(token: String, id: Long): HetznerServerSummary = withContext(Dispatchers.IO) {
        val resp = get("$BASE_URL/servers/$id", token)
        parseServer(JSONObject(resp).getJSONObject("server"))
    }

    suspend fun deleteServer(token: String, id: Long) = withContext(Dispatchers.IO) {
        httpDelete("$BASE_URL/servers/$id", token)
    }

    private fun parseServer(o: JSONObject): HetznerServerSummary {
        val net  = o.optJSONObject("public_net")
        val ipv4 = net?.optJSONObject("ipv4")?.optString("ip")?.takeIf { it.isNotBlank() }
        return HetznerServerSummary(
            id         = o.getLong("id"),
            name       = o.getString("name"),
            status     = o.getString("status"),
            ipv4       = ipv4,
            datacenter = o.optJSONObject("datacenter")?.optString("name") ?: "",
            serverType = o.optJSONObject("server_type")?.optString("name") ?: "",
            image      = o.optJSONObject("image")?.optString("name")
        )
    }

    private fun get(url: String, token: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout    = 15_000
            requestMethod  = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code in 200..299) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
            val body = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            val msg = runCatching {
                JSONObject(body).getJSONObject("error").optString("message")
            }.getOrNull().orEmpty().ifBlank { "HTTP $code" }
            throw HetznerException("Hetzner API error: $msg")
        } finally {
            conn.disconnect()
        }
    }

    private fun post(url: String, token: String, jsonBody: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout    = 30_000
            requestMethod  = "POST"
            setRequestProperty("Authorization",  "Bearer $token")
            setRequestProperty("Content-Type",   "application/json")
            setRequestProperty("Accept",         "application/json")
            doOutput = true
        }
        try {
            conn.outputStream.bufferedWriter().use { it.write(jsonBody) }
            val code = conn.responseCode
            if (code in 200..299) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
            val body = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            val msg = runCatching {
                JSONObject(body).getJSONObject("error").optString("message")
            }.getOrNull().orEmpty().ifBlank { "HTTP $code" }
            throw HetznerException("Hetzner API error: $msg")
        } finally {
            conn.disconnect()
        }
    }

    private fun httpDelete(url: String, token: String) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout    = 15_000
            requestMethod  = "DELETE"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code in 200..299) return
            val body = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            val msg = runCatching {
                JSONObject(body).getJSONObject("error").optString("message")
            }.getOrNull().orEmpty().ifBlank { "HTTP $code" }
            throw HetznerException("Hetzner API error: $msg")
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }
}
